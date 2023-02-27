package com.othadd.ozi

import android.util.Log
import androidx.lifecycle.*
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.messaging.FirebaseMessaging
import com.othadd.ozi.database.DBChat
import com.othadd.ozi.database.DialogState
import com.othadd.ozi.database.getNoDialogDialogType
import com.othadd.ozi.gaming.DUMMY_STRING
import com.othadd.ozi.gaming.GameManager
import com.othadd.ozi.models.ProfileFetchResponse
import com.othadd.ozi.models.UsersFetchResponse
import com.othadd.ozi.network.*
import com.othadd.ozi.ui.*
import com.othadd.ozi.utils.*
import com.othadd.ozi.workers.SendChatMessageWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

const val RESPOND_TO_GAME_REQUEST_PROMPT_TYPE = "respond to game request"

class MessagingRepoX(private val oziApp: OziApplication) {

    private val chatDao = oziApp.database.chatDao()
    private val settingsRepo = SettingsRepo(oziApp)
    private val gameManager = GameManager.getInstance(oziApp, this)


    val thisUserId get() = settingsRepo.getUserId()

    val chats = chatDao.getChatsFlow()
    val chatMateIdFlow = MutableStateFlow(ARBITRARY_STRING)
    val chatMateId get() = chatMateIdFlow.value

    @OptIn(ExperimentalCoroutinesApi::class)
    val chat = chatMateIdFlow.flatMapLatest {
        chatDao.getChatByChatmateIdFlow(it)
    }

    val userIsRegistered = settingsRepo.username().map { it != NO_USERNAME }
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

    val darkModeSet = settingsRepo.darkModeFlow()

    private val _refreshError = MutableStateFlow(false)
    val refreshError = _refreshError.asStateFlow()

    private var currentPromptType = DUMMY_STRING

    val newGameRequestFlow = gameManager.newGameRequest.map {
        it ?: return@map Triple(null, null, chatMateId)
        gameRequestSenderId = it
        Triple(it, getUsernameX(it), chatMateId)
    }
    private lateinit var gameRequestSenderId: String



    suspend fun startChat(userId: String) {
        var chat = withContext(Dispatchers.Default) { chatDao.getChatByChatmateId(userId) }
        if (chat == null) {
            // TODO: implement try-catch
            val user = NetworkApi.retrofitService.getUser(userId)
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
            chatDao.insert(chat)
        }
        chatMateIdFlow.value = userId
    }

    suspend fun markMessagesSent() = coroutineScope {
        launch(Dispatchers.IO) {
            try {
                val chat = chatDao.getChatByChatmateId(chatMateIdFlow.value)
                chat?.let {
                    it.markAllMessagesSent()
                    chatDao.update(it)
                }
            } catch (e: NullPointerException) {
                Log.e("messagingRepo", "null pointer exception trying to mark all messages sent")
            }
        }
    }

    suspend fun markMessagesRead() = coroutineScope {
        launch(Dispatchers.IO) {
            try {
                val chat = chatDao.getChatByChatmateId(chatMateIdFlow.value)
                chat?.let {
                    it.markMessagesRead()
                    chatDao.update(it)
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
                        NetworkApi.retrofitService.registerNewUserWithToken(
                            thisUserId,
                            username,
                            gender,
                            token
                        )
                        _registrationStatus.value = PASSED
                        settingsRepo.storeUsername(username)
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
                val users = NetworkApi.retrofitService.getUsers(thisUserId)
                UsersFetchResponse(users, PASSED)
            }
            else{
                val users = NetworkApi.retrofitService.getUsersWithMatch(thisUserId, searchTerm)
                UsersFetchResponse(users, PASSED)
            }
        } catch (e: Exception) {
            UsersFetchResponse(emptyList(), FAILED)
        }
    }

    suspend fun getProfile(){
        _profile.value = ProfileFetchResponse(null, BUSY)
        try {
            _profile.value = ProfileFetchResponse(NetworkApi.retrofitService.getUser(thisUserId), PASSED)
        } catch (e: Exception) {
            _profile.value = ProfileFetchResponse(null, FAILED)
        }
    }

    suspend fun sendChatMessage(
        messageBody: String,
        senderId: String = thisUserId,
        receiverId: String = chatMateIdFlow.value,
    ) {
        val newMessage =
            Message(senderId, receiverId, messageBody, Calendar.getInstance().timeInMillis)
        sendMessageObject(newMessage)
    }

    private suspend fun sendMessageObject(message: Message){
        val messagePackage = if(message.messagePackage == NOT_INITIALIZED) MessagePackage() else stringToMessagePackage(message.messagePackage)
        messagePackage.gameModeratorId = getGameModeratorId()
        val packageString = messagePackageToString(messagePackage)
        message.messagePackage = packageString

        saveOutGoingMessage(message)
        scheduleMessageForSendToServer(message)
    }

    suspend fun sendGamingMessage(message: Message): Boolean {
        val messagePackage =
            if (message.messagePackage == NOT_INITIALIZED) MessagePackage() else stringToMessagePackage(
                message.messagePackage
            )
        messagePackage.gameModeratorId = getGameModeratorId()
        val packageString = messagePackageToString(messagePackage)
        message.messagePackage = packageString

        return try {
            NetworkApi.retrofitService.sendMessage(message.toNWMessage())
            true
        } catch (e: Exception) {
            Log.e("messagingRepo", "sendGamingMessage() method. ${e.message ?: e}")
            false
        }
    }

    suspend fun refreshMessages() {
        withContext(Dispatchers.IO){
            val newMessages: List<NWMessage>
            try {
                newMessages = NetworkApi.retrofitService.getMessages(settingsRepo.getUserId())
                handleReceivedMessages(newMessages)
                _refreshError.value = false
            } catch (e: Exception) {
                _refreshError.value = true
                Log.e("messagingRepo", "refreshMessages() method. ${e.message?: e}")
            }
        }
    }

    suspend fun checkUsername(username: String) {
        _usernameCheckStatus.value = BUSY
        try {
            val usernameCheckPassed = NetworkApi.retrofitService.checkUsername(username)
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

    fun updateGameModeratorIdAndKeyboardMode(gameModeratorId: String){
        settingsRepo.apply {
            updateGameModeratorId(gameModeratorId)
            updateKeyboardMode(true)
        }
    }

    fun getUsername(userId: String): String {
        return chatDao.getChatByChatmateId(userId)!!.chatMateUsername
    }

    suspend fun getUsernameX(userId: String): String {
        return withContext(Dispatchers.IO) { chatDao.getChatByChatmateId(userId)!!.chatMateUsername }
    }

    suspend fun refreshUser(userId: String = chatMateIdFlow.value) {
        withContext(Dispatchers.IO) {
            val user = NetworkApi.retrofitService.getUser(userId)
            val dbChat = getChat(userId)!!
            dbChat.verificationStatus = user.verificationStatus
            dbChat.onlineStatus = user.onlineStatus
            chatDao.update(dbChat)
            settingsRepo.updateKeyboardMode(user.gameState == CURRENTLY_PLAYING_GAME_STATE)
        }
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

    fun getGameModeratorId(): String{
        return settingsRepo.getGameModeratorId()
    }

    private fun scheduleMessageForSendToServer(newMessage: Message) {
        val newMessageString = messageToString(newMessage)
        val workManager = WorkManager.getInstance(oziApp)
        val workRequest = OneTimeWorkRequestBuilder<SendChatMessageWorker>()
            .setInputData(workDataOf(WORKER_MESSAGE_KEY to newMessageString))
            .build()

        workManager.enqueue(workRequest)
    }

    private suspend fun findOrCreateChat(userId: String): Pair<DBChat, Boolean> {

        val chatIsNew: Boolean
        var chat = getChat(userId)
        if (chat == null) {
            val user = NetworkApi.retrofitService.getUser(userId)
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
            chatIsNew = true
        } else chatIsNew = false

        return Pair(chat, chatIsNew)
    }

    private suspend fun getChat(userId: String): DBChat? {
        return withContext(Dispatchers.IO) { chatDao.getChatByChatmateId(userId) }
    }

    private suspend fun saveMessage(
        newMessage: Message,
        chatMateId: String
    ) {
        settingsRepo.updateScroll(false)
        delay(100)

        newMessage.dateTime = Calendar.getInstance().timeInMillis
        val resultOfFindChat = findOrCreateChat(chatMateId)
        val chat = resultOfFindChat.first
        val chatIsNew = resultOfFindChat.second
        chat.addMessage(newMessage)
        if (chatIsNew) chatDao.insert(chat) else chatDao.update(chat)

        delay(100L) //if the scroll follows without delay, the screen maybe scrolled just before the latest message has appeared.
        settingsRepo.updateScroll(true)
    }

    private suspend fun saveOutGoingMessage(newMessage: Message) {
        val chatMateId = newMessage.receiverId
        saveMessage(newMessage, chatMateId)
    }

    suspend fun saveIncomingMessage(newMessage: Message) {
        val chatMateId = newMessage.senderId

        //delayPosting is an older implementation. it indicates only if a message should be delayed or not. And delays by a default of 2secs
        //delayPostingBy is a newer implementation. it indicates the exact length of time by which a message should be delayed.
        val delayPosting = try { getPackageFromMessage(newMessage).delayPosting } catch (e: Exception) { false }
        val delayDuration = try { getPackageFromMessage(newMessage).delayPostingBy } catch (e: Exception) { 0 }

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            if (delayPosting) {
                delay(2000L)
                saveMessage(newMessage, chatMateId)
                return@launch
            }

            if (delayDuration != 0) {
                delay(delayDuration.toLong())
                saveMessage(newMessage, chatMateId)
                return@launch
            }

            saveMessage(newMessage, chatMateId)
        }
    }

    suspend fun sendMessageToServer(message: Message) {
        try {
            NetworkApi.retrofitService.sendMessage(message.toNWMessage())
        } catch (e: Exception) {
            throw e
        }
    }

    private suspend fun handleReceivedMessages(newMessages: List<NWMessage>) {

        // handle regular chat messages
        val chatMessages = newMessages.filter { it.type == CHAT_MESSAGE_TYPE }
        for (message in chatMessages) {
            saveIncomingMessage(message.toMessage())
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
        val chat = findOrCreateChat(message.senderId).first
        when (message.body) {
            USER_ONLINE -> chat.onlineStatus = true
            USER_OFFLINE -> chat.onlineStatus = false
            USER_VERIFIED -> chat.verificationStatus = true
            USER_UNVERIFIED -> chat.verificationStatus = false
        }
        chatDao.update(chat)
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
            RESPOND_TO_GAME_REQUEST_PROMPT_TYPE -> gameManager.declineGameRequest(chatMateId)
        }
    }

    suspend fun notifyDialogOkayPressed() {
        updateDialogState(chatMateId, getNoDialogDialogType())
    }

    private fun getPackageFromMessage(message: Message): MessagePackage {
        return stringToMessagePackage(message.messagePackage)
    }
}