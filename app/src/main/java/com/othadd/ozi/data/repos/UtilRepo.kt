package com.othadd.ozi.data.repos

import android.widget.Toast
import com.othadd.ozi.OziApplication
import com.othadd.ozi.data.database.DialogState
import com.othadd.ozi.data.network.NetworkApi
import com.othadd.ozi.data.network.User
import com.othadd.ozi.utils.SettingsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class UtilRepo(val oziApp: OziApplication) {

    private val chatDao = oziApp.database.chatDao()
    private val settingsRepo = SettingsRepo(oziApp)

    val thisUserId get() = settingsRepo.getUserId()

    private val _useGameKeyboard = MutableStateFlow(false)
    val useGameKeyboard = _useGameKeyboard.asStateFlow()

    fun getThisUserNameFlow() = settingsRepo.username()

    fun darkModeStatusFlow() = settingsRepo.darkModeFlow()

    suspend fun getUser(userId: String) = NetworkApi.retrofitService.getUser(userId)

    suspend fun registerUser(username: String, gender: String, token: String){
        NetworkApi.retrofitService.registerNewUserWithToken(
            thisUserId,
            username,
            gender,
            token
        )
    }

    fun saveUsername(username: String){
        settingsRepo.storeUsername(username)
    }

    suspend fun getUsers(searchTerm: String? = null): List<User> {
        return if (searchTerm == null){
            NetworkApi.retrofitService.getUsers(thisUserId)
        }
        else{
            NetworkApi.retrofitService.getUsersWithMatch(thisUserId, searchTerm)
        }
    }

    suspend fun getProfile(): User {
        return NetworkApi.retrofitService.getUser(thisUserId)
    }

    fun gameModeratorId(): String = settingsRepo.getGameModeratorId()

    suspend fun checkUsername(username: String): Boolean {
        return NetworkApi.retrofitService.checkUsername(username)
    }

    fun updateGameModeratorIdAndKeyboardMode(gameModeratorId: String){
        settingsRepo.updateGameModeratorId(gameModeratorId)
        _useGameKeyboard.value = true
    }

    fun updateKeyboardMode(update: Boolean){
        _useGameKeyboard.value = update
    }

    fun getUsername(userId: String): String {
        return chatDao.getChatByChatmateId(userId)!!.chatMateUsername
    }

    suspend fun updateDialogState(
        chatMateId: String,
        dialogState: DialogState
    ) {

        /*
        this explains why getting the chat from the db can return null in a particular scenario.
        and why it's perfectly fine to return without further action if that happens.

            there is a bug where if a user never gets a response to a game request,
            the user remains in 'has game request pending' mode and is therefore permanently unable to receive game requests.
            to fix this, the user sends a 'game request declined' to itself.
            this is just to trigger the server into resetting the user's game mode.
            the implication of this is that the user receives a 'game request declined' message.
            normally, the chat of the chatMate who declined the request is retrieved and the dialog is reset.
            however, in this case, the user is the chatMate since the user is both sender and receiver.
            since there is no chat whose chatMate id is the userid, a chat won't be found,
            and an exception will be thrown. that is fine, because by that time the goal would have already been achieved,
            which is to reset game mode with the server. so the function can simply return.

            A better implementation would be to just implement a timer on the server side.
         */

        withContext(Dispatchers.IO){
            val chat = chatDao.getChatByChatmateId(chatMateId) ?: return@withContext
            chat.dialogState = dialogState
            chatDao.update(chat)
        }
    }

    fun showToast(message: String) {
        if (OziApplication.inForeGround) {
            Toast.makeText(oziApp, message, Toast.LENGTH_LONG).show()
        }
    }

    fun getString(stringResourceId: Int): String{
        return oziApp.getString(stringResourceId)
    }

    fun getStringWithFormatting(stringResourceId: Int, vararg arguments: Any ): String{
        return oziApp.getString(stringResourceId, *arguments)
    }

    suspend fun toggleDarkMode() {
        val currentMode = darkModeStatusFlow().first()
        settingsRepo.updateDarkMode(!currentMode)
    }

    fun saveUserId(userId: String){
        settingsRepo.updateUserId(userId)
    }
}