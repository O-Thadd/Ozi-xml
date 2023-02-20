package com.othadd.ozi.ui

import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.*
import com.othadd.ozi.MessagingRepoX
import com.othadd.ozi.OziApplication
import com.othadd.ozi.database.ChatDao
import com.othadd.ozi.database.toUIChat
import com.othadd.ozi.database.toUIChat2
import com.othadd.ozi.gaming.GameManager
import com.othadd.ozi.models.ChatsFragmentUIState
import com.othadd.ozi.models.FindUsersFragmentUIState
import com.othadd.ozi.models.ProfileFragmentUIState
import com.othadd.ozi.models.RegisterFragmentUIState
import com.othadd.ozi.network.CURRENTLY_PLAYING_GAME_STATE
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.utils.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

const val DEFAULT = 0
const val BUSY = 1
const val PASSED = 2
const val FAILED = 3
const val ERROR = 4


@HiltViewModel
open class ChatViewModel @Inject constructor(
    private val chatDao: ChatDao,
    private val settingsRepo: SettingsRepo,
    private val messagingRepoX: MessagingRepoX,
    private val oziApplication: OziApplication
) : ViewModel() {

    private val gameManager = GameManager.getInstance(oziApplication, messagingRepoX)
    private val thisUserId: String = settingsRepo.getUserId()

    val chats = Transformations.map(messagingRepoX.chats.asLiveData()) { listOfDBChats ->
        listOfDBChats.sortedByDescending { it.lastMessage()?.dateTime }.toUIChat()
    }
    val chat: LiveData<UIChat2> =
        Transformations.map(messagingRepoX.chat.asLiveData()){ it.toUIChat2(thisUserId) }
    private val currentChatChatMateId get() = messagingRepoX.chatMateId.value

    val darkMode: LiveData<Boolean> = messagingRepoX.darkModeSet.asLiveData()

    val findUsersFragmentUIState =
        combine(messagingRepoX.users, messagingRepoX.searchedUsers) { users, searchedUsers ->
            if (searchedUsers.timeStamp > users.timeStamp) {
                FindUsersFragmentUIState(searchedUsers.status, DEFAULT, searchedUsers.users)
            } else {
                FindUsersFragmentUIState(DEFAULT, users.status, users.users)
            }
        }.asLiveData()

    val profileFragmentUIState = messagingRepoX.profile.map {
        ProfileFragmentUIState(it.status, it.user)
    }.asLiveData()

    val chatsFragmentUIState =
        combine(messagingRepoX.chats, messagingRepoX.darkModeSet) { chats, darkModeSet ->
            val uiChats = chats.sortedByDescending { it.lastMessage()?.dateTime }.toUIChat()
            ChatsFragmentUIState(darkModeSet, uiChats)
        }.asLiveData()

    private val genderSelectionPopupIsShowing = MutableStateFlow(false)
    private val genderHasBeenSelected = MutableStateFlow(false)
    val registerFragmentUIState = combine(
        messagingRepoX.userIsRegistered,
        messagingRepoX.registeringStatus,
        messagingRepoX.usernameCheckStatus,
        genderSelectionPopupIsShowing,
        genderHasBeenSelected
    ) { userIsRegistered, registeringStatus, usernameCheckStatus, genderPopupShowing, genderSelected ->
        val signUpConditionsMet = genderSelected && usernameCheckStatus == PASSED
        RegisterFragmentUIState(
            userIsRegistered,
            usernameCheckStatus,
            signUpConditionsMet,
            registeringStatus,
            genderPopupShowing
        )
    }.asLiveData()

    val refreshError = messagingRepoX.refreshError.asLiveData()






    private var _showConfirmGameRequestDialog = MutableLiveData<Boolean>()
    val showConfirmGameRequestDialog: LiveData<Boolean> get() = _showConfirmGameRequestDialog

    private var _navigateToChatFragment = MutableLiveData<Boolean>()
    val navigateToChatFragment: LiveData<Boolean> get() = _navigateToChatFragment

    private var _navigateToChatsFragment = MutableLiveData<Boolean>()
    val navigateToChatsFragment: LiveData<Boolean> get() = _navigateToChatsFragment

    val markAllMessagesSent = settingsRepo.markSentFlow().asLiveData()

    var chatStartedByActivity = false

    val snackBarState: LiveData<SnackBarState> =
        Transformations.map(settingsRepo.snackBarStateFlow().asLiveData()) {

            // this checks if the snackBar is a noSnackBar snackBar. done by simply checking if the message is an empty string.
            // a better implementation would be to include a field that indicates snackBar type in the snackBar class, and then check with that field.
            if (it.message != "") {
                snackBarTimer.cancel()
                snackBarTimer.start()
            }

            it
        }

    val shouldScrollChat: LiveData<Boolean> = settingsRepo.scrollFlow().asLiveData()


    private val snackBarTimer = object : CountDownTimer(7000, 1000) {
        override fun onTick(millisUntilFinished: Long) {}
        override fun onFinish() {
            closeSnackBar()
        }
    }

    val shouldEnableGameModeKeyboard: LiveData<Boolean> =
        settingsRepo.keyBoardModeFlow().asLiveData()





    fun sendMessage(messageBody: String) {
        viewModelScope.launch {
            messagingRepoX.sendChatMessage(messageBody)
        }
    }

    fun refreshMessages() {
        viewModelScope.launch {
            messagingRepoX.refreshMessages()
        }
    }

    fun registerUser(username: String, gender: String) {
        viewModelScope.launch {
            messagingRepoX.registerUser(username, gender, viewModelScope)
        }
    }

    fun startChat(userId: String) {
        viewModelScope.launch {
            messagingRepoX.startChat(userId)
            //there is a very brief period between when the chat is opened and when the data is loaded.
            //so the user briefly sees a blank chat
            // this delay tries to ensure the data is all loaded before the chat is opened.
            delay(50L)
            _navigateToChatFragment.value = true
        }
    }

    fun getLatestUsers() {
        viewModelScope.launch {
            messagingRepoX.getUsers()
        }
    }

    fun getMatchingUsers(usernameSubString: String) {
        viewModelScope.launch {
            if (usernameSubString.isBlank()) {
                messagingRepoX.getUsers()
            } else {
                messagingRepoX.searchUsers(usernameSubString)
            }
        }
    }

    fun getProfile() {
        viewModelScope.launch {
            messagingRepoX.getProfile()
        }
    }

    fun checkUsername(username: String) {
        viewModelScope.launch {
            messagingRepoX.checkUsername(username)
        }
    }

    fun resetUsernameCheck() {
        messagingRepoX.resetUsernameCheck()
    }

    fun showGenderSelectionPopup(){
        genderSelectionPopupIsShowing.value = true
    }

    fun hideGenderSelectionPopup(){
        genderSelectionPopupIsShowing.value = false
    }

    fun toggleGenderSelectionPopup(){
        genderSelectionPopupIsShowing.value = !genderSelectionPopupIsShowing.value
    }

    fun updateGenderSelected(newValue: Boolean){
        genderHasBeenSelected.value = newValue
    }

    fun markMessagesSent() {
        viewModelScope.launch {
            messagingRepoX.markMessagesSent()
        }
    }

    fun markMessagesRead() {
        viewModelScope.launch {
            messagingRepoX.markMessagesRead()
        }
    }

    fun confirmSendGameRequest() {
        _showConfirmGameRequestDialog.value = true
    }

    fun sendGameRequest() {
        viewModelScope.launch {
            messagingRepoX.sendGameRequest()
            _showConfirmGameRequestDialog.value = false
        }
    }

    fun cancelSendGameRequest() {
        _showConfirmGameRequestDialog.value = false
    }

    fun respondPositive() {
        viewModelScope.launch {
            messagingRepoX.givePositiveResponse()
        }
    }

    fun respondNegative() {
        viewModelScope.launch {
            messagingRepoX.giveNegativeResponse()
        }
    }

    fun notifyDialogOkayPressed() {
        viewModelScope.launch {
            messagingRepoX.notifyDialogOkayPressed()
        }
    }

    fun resetNavigateChatsToChatFragment() {
        _navigateToChatFragment.value = false
    }

    fun startChatFromActivity(senderId: String) {
        chatStartedByActivity = true
        startChat(senderId)
    }

    fun resetChatStartedByActivity() {
        chatStartedByActivity = false
    }

    fun toggleDarkMode() {
        val currentMode = darkMode.value
        settingsRepo.updateDarkMode(!currentMode!!)
    }

    fun saveUserId(userId: String) {
        settingsRepo.updateUserId(userId)
    }

    fun goToGameRequestSenderChat() {
//        startChat(messagingRepoX.getGameRequestSenderId())
        viewModelScope.launch {
            gameManager.startGameRequestSenderChat()
        }
    }












    fun closeSnackBar() {
        settingsRepo.updateSnackBarState(getNoSnackBarSnackBar())
    }

    fun snackBarNavigateToChatFromChatFragment() {
        _navigateToChatsFragment.value = true
        startChat(getGameRequestSenderId())
    }

    fun getGameRequestSenderId(): String {
        return messagingRepoX.getGameRequestSenderId()
    }

    fun refreshUserStatus() {
        viewModelScope.launch {
            try {
                messagingRepoX.refreshUser()
            } catch (e: Exception) {
                Log.e("viewModel", "error refreshing user status. $e")
            }
        }


        // upper part of method fixed, following part should be fixed next.
//        viewModelScope.launch {
//            try {
//                val user = NetworkApi.retrofitService.getUser(currentChatChatMateId)
//                settingsRepo.updateKeyboardMode(user.gameState == CURRENTLY_PLAYING_GAME_STATE)
//            } catch (e: Exception) {
//                Log.e(
//                    "viewModel",
//                    "error getting keyboard mode for chatmate in refreshUserStatus() $e"
//                )
//            }
//        }
    }




    init {
        _showConfirmGameRequestDialog.value = false
        _navigateToChatFragment.value = false
        _navigateToChatsFragment.value = false
    }

//    companion object {
//
//        val Factory: ViewModelProvider.AndroidViewModelFactory = object : ViewModelProvider.AndroidViewModelFactory() {
//            @Suppress("UNCHECKED_CAST")
//            override fun <T : ViewModel> create(
//                modelClass: Class<T>,
//                extras: CreationExtras
//            ): T {
//                // Get the Application object from extras
//                val application = checkNotNull(extras[APPLICATION_KEY])
//                // Create a SavedStateHandle for this ViewModel from extras
//                val savedStateHandle = extras.createSavedStateHandle()
//                val oziApp = application as OziApplication
//
//                return ChatViewModel(
//                    oziApp.database.chatDao(),
//                    SettingsRepo(oziApp),
//                    MessagingRepoX(oziApp)
//                ) as T
//            }
//        }
//    }
}

//class ChatViewModelFactory(
//    private val chatDao: ChatDao,
//    private val settingsRepo: SettingsRepo,
//    private val messagingRepoX: MessagingRepoX
//) :
//    ViewModelProvider.Factory {
//    override fun <T : ViewModel> create(modelClass: Class<T>): T {
//        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
//            @Suppress("UNCHECKED_CAST")
//            return ChatViewModel(chatDao, settingsRepo, messagingRepoX) as T
//        }
//        throw IllegalArgumentException("Unknown ViewModel class")
//    }
//
//}