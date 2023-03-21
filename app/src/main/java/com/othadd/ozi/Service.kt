package com.othadd.ozi

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.othadd.ozi.data.database.DBChat
import com.othadd.ozi.data.database.getNoDialogDialogType
import com.othadd.ozi.data.network.*
import com.othadd.ozi.data.repos.MessageRepo
import com.othadd.ozi.data.repos.UtilRepo
import com.othadd.ozi.gaming.DUMMY_STRING
import com.othadd.ozi.gaming.GameManager
import com.othadd.ozi.models.ProfileFetchResponse
import com.othadd.ozi.models.UsersFetchResponse
import com.othadd.ozi.ui.*
import com.othadd.ozi.utils.ARBITRARY_STRING
import com.othadd.ozi.utils.NO_USERNAME
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.*

class Service(val messageRepo: MessageRepo, val utilRepo: UtilRepo, val gameManager: GameManager) {

    val thisUserId = utilRepo.thisUserId

    val chatMateIdFlow = MutableStateFlow(ARBITRARY_STRING)
    val chatMateId get() = chatMateIdFlow.value

    @OptIn(ExperimentalCoroutinesApi::class)
    val chat = chatMateIdFlow.flatMapLatest { messageRepo.getChatFlow(it) }

    val chats = messageRepo.chats

    val userIsRegistered = utilRepo.getThisUserNameFlow().map { it != NO_USERNAME }
    private val _registrationStatus = MutableStateFlow(DEFAULT)
    val registeringStatus = _registrationStatus.asStateFlow()
    private val _usernameCheckStatus = MutableStateFlow(DEFAULT)
    val usernameCheckStatus = _usernameCheckStatus.asStateFlow()

    private val _users = MutableStateFlow(UsersFetchResponse.getDefault())
    val users = _users.asStateFlow()
    private val _searchedUsers = MutableStateFlow(UsersFetchResponse.getDefault())
    val searchedUsers = _searchedUsers.asStateFlow()
    private val currentUsers get() = _users.value.users

    private val _profile = MutableStateFlow(ProfileFetchResponse.getDefault())
    val profile = _profile.asStateFlow()

    val darkModeSet = utilRepo.darkModeStatusFlow()

    private val _refreshError = MutableStateFlow(false)
    val refreshError = _refreshError.asStateFlow()

    private var currentPromptType = DUMMY_STRING

    val newGameRequestFlow = gameManager.newGameRequest.map {
        it ?: return@map Triple(null, null, chatMateId)
        gameRequestSenderId = it
        currentPromptType = RESPOND_TO_GAME_REQUEST_PROMPT_TYPE
        Triple(it, getUsername(it), chatMateId)
    }
    private lateinit var gameRequestSenderId: String

    val scrollMessages = messageRepo.scrollMessages

    val useGameKeyboard = utilRepo.useGameKeyboard





    private suspend fun getUsername(userId: String): String {
        return withContext(Dispatchers.IO) { messageRepo.getChat(userId)!!.chatMateUsername }
    }

    suspend fun startChat(userId: String) {
        var chat = withContext(Dispatchers.Default) { messageRepo.getChat(userId) }
        if (chat == null) {
            // TODO: implement try-catch
            val user = utilRepo.getUser(userId)
            chat = DBChat(
                chatMateId = userId,
                messages = mutableListOf(),
                chatMateUsername = user.username,
                chatMateGender = user.gender,
                dialogState = getNoDialogDialogType(),
                hasUnreadMessage = false,
                onlineStatus = user.onlineStatus,
                verificationStatus = user.verificationStatus
            )
            messageRepo.saveNewChat(chat)
        }
        chatMateIdFlow.value = userId
    }

    suspend fun markMessagesSent(chatMateId: String? = null) = coroutineScope {
        launch(Dispatchers.IO) {
            try {
                val chat = messageRepo.getChat(chatMateId ?: chatMateIdFlow.value)
                chat?.let {
                    it.markAllMessagesSent()
                    messageRepo.updateChat(it)
                }
            } catch (e: NullPointerException) {
                Log.e("messagingRepo", "null pointer exception trying to mark all messages sent")
            }
        }
    }

    suspend fun markMessagesRead() = coroutineScope {
        launch(Dispatchers.IO) {
            try {
                val chat = messageRepo.getChat(chatMateIdFlow.value)
                chat?.let {
                    it.markMessagesRead()
                    messageRepo.updateChat(it)
                }
            } catch (e: NullPointerException) {
                Log.e("messagingRepo", "null pointer exception trying to mark messages read")
            }
        }
    }

    fun registerUser(username: String, gender: String, scope: CoroutineScope) {
        _registrationStatus.value = BUSY
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            scope.launch {
                if (task.isSuccessful) {

                    // Get new FCM registration token
                    val token = task.result

                    // do something with token
                    try {
                        utilRepo.registerUser(username, gender, token)
                        _registrationStatus.value = PASSED
                        utilRepo.saveUsername(username)
                    } catch (e: Exception) {
                        _registrationStatus.value = FAILED
                        Log.e("registration error", e.toString())
                    }
                } else {
                    _registrationStatus.value = FAILED
                    Log.e("registration error", "getting fcm token task failed")
                }
                delay(500L)
            }
        }
    }

    suspend fun getUsers(){
        _users.value = UsersFetchResponse(currentUsers, BUSY)
        _users.value = fetchUsers()
    }

    suspend fun searchUsers(searchTerm: String){
        _searchedUsers.value = UsersFetchResponse(currentUsers, BUSY)
        _users.value = fetchUsers(searchTerm)
    }

    private suspend fun fetchUsers(searchTerm: String? = null): UsersFetchResponse{
        return try {
            return if (searchTerm == null){
                val users = utilRepo.getUsers()
                UsersFetchResponse(users, PASSED)
            }
            else{
                val users = utilRepo.getUsers(searchTerm)
                UsersFetchResponse(users, PASSED)
            }
        } catch (e: Exception) {
            UsersFetchResponse(emptyList(), FAILED)
        }
    }

    suspend fun getProfile(){
        _profile.value = ProfileFetchResponse(null, BUSY)
        try {
            _profile.value = ProfileFetchResponse(utilRepo.getProfile(), PASSED)
        } catch (e: Exception) {
            _profile.value = ProfileFetchResponse(null, FAILED)
        }
    }

    suspend fun sendChatMessage(
        messageBody: String,
        senderId: String = utilRepo.thisUserId,
        receiverId: String = chatMateIdFlow.value,
    ) {
        val newMessage =
            Message(senderId, receiverId, messageBody, Calendar.getInstance().timeInMillis)
        messageRepo.sendMessageObject(newMessage)
    }

    suspend fun refreshMessages() {
        withContext(Dispatchers.IO){
            val newMessages: List<NWMessage>
            try {
                newMessages = messageRepo.getMessages()
                handleReceivedMessages(newMessages)
                _refreshError.value = false
            } catch (e: Exception) {
                _refreshError.value = true
                Log.e("messagingRepo", "refreshMessages() method. ${e.message?: e}")
            }
        }
    }

    private suspend fun handleReceivedMessages(newMessages: List<NWMessage>) {

        // handle regular chat messages
        val chatMessages = newMessages.filter { it.type == CHAT_MESSAGE_TYPE }
        for (message in chatMessages) {
            messageRepo.saveIncomingMessage(message.toMessage())
        }

//        handle status update messages
        val statusUpdateMessages = newMessages.filter { it.type == STATUS_UPDATE_MESSAGE_TYPE }
        for (message in statusUpdateMessages) {
            handleStatusUpdateMessage(message)
        }

        // handle other types
        // (currently, just gaming messages. which are 'game request message type', 'game request response message type' and 'game manager message type').
        // The 'game manager message type' is the rightful type for all messages meant for the game manager.
        // however, the 'game request message type' and 'game request response message type' were implemented before 'game manager message type'.
        // so currently, messages of the 3 types are sent to game manager.
        // there should be a reimplementation on both client and server side to collapse the older 2 types to the newer 'game manager message type'.
        val gamingMessages = newMessages.toMutableList()
        gamingMessages.removeAll(chatMessages)
        gamingMessages.removeAll(statusUpdateMessages)
        if (gamingMessages.isNotEmpty()) {
            gameManager.handleMessage(gamingMessages)
        }
    }

    private suspend fun handleStatusUpdateMessage(newMessage: NWMessage) {
        val message = newMessage.toMessage()
        val chat = messageRepo.getChat(message.senderId) ?: return
        when (message.body) {
            USER_ONLINE -> chat.onlineStatus = true
            USER_OFFLINE -> chat.onlineStatus = false
            USER_VERIFIED -> chat.verificationStatus = true
            USER_UNVERIFIED -> chat.verificationStatus = false
        }
        messageRepo.updateChat(chat)
    }

    suspend fun checkUsername(username: String) {
        _usernameCheckStatus.value = BUSY
        try {
            val usernameCheckPassed = utilRepo.checkUsername(username)
            if (usernameCheckPassed) {
                _usernameCheckStatus.value = PASSED
            } else {
                _usernameCheckStatus.value = FAILED
            }
        } catch (e: Exception) {
            _usernameCheckStatus.value = ERROR
        }
    }

    fun resetUsernameCheck() {
        _usernameCheckStatus.value = DEFAULT
    }

    suspend fun refreshUser(userId: String = chatMateIdFlow.value) {
        withContext(Dispatchers.IO) {
            val user = utilRepo.getUser(userId)
            val dbChat = messageRepo.getChat(userId)!!
            dbChat.verificationStatus = user.verificationStatus
            dbChat.onlineStatus = user.onlineStatus
            messageRepo.updateChat(dbChat)
            utilRepo.updateKeyboardMode(user.gameState == CURRENTLY_PLAYING_GAME_STATE)
        }
    }

    suspend fun startGameRequestSenderChat(){
        startChat(gameRequestSenderId)
    }

    suspend fun sendGameRequest() {
        gameManager.sendGameRequest(chatMateId)
    }

    suspend fun promptDialogPositiveButtonPressed(){
        when(currentPromptType){
            RESPOND_TO_GAME_REQUEST_PROMPT_TYPE -> gameManager.acceptGameRequest(chatMateId)
        }
    }

    suspend fun promptDialogNegativeButtonPressed(){
        when(currentPromptType){
            RESPOND_TO_GAME_REQUEST_PROMPT_TYPE -> {
                gameManager.declineGameRequest(chatMateId)
            }
        }
    }

    suspend fun notifyDialogOkayPressed() {
        utilRepo.updateDialogState(chatMateId, getNoDialogDialogType())
    }

    suspend fun toggleDarkMode() {
        utilRepo.toggleDarkMode()
    }

    fun saveUserId(userId: String){
        utilRepo.saveUserId(userId)
    }
}