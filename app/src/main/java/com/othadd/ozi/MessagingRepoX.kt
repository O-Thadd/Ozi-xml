package com.othadd.ozi

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.*
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.messaging.FirebaseMessaging
import com.othadd.ozi.database.DBChat
import com.othadd.ozi.database.getNoDialogDialogType
import com.othadd.ozi.gaming.GameManager
import com.othadd.ozi.models.ProfileFetchResponse
import com.othadd.ozi.models.UsersFetchResponse
import com.othadd.ozi.network.*
import com.othadd.ozi.ui.BUSY
import com.othadd.ozi.ui.DEFAULT
import com.othadd.ozi.ui.FAILED
import com.othadd.ozi.ui.PASSED
import com.othadd.ozi.utils.*
import com.othadd.ozi.workers.SendChatMessageWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

class MessagingRepoX(private val oziApp: OziApplication) {

    private val chatDao = oziApp.database.chatDao()
    private val settingsRepo = SettingsRepo(oziApp)
    private val gameManager = GameManager(oziApp, this)


    private val thisUserId get() = settingsRepo.getUserId()

    val chats = chatDao.getChatsFlow()
    val chatMateId = MutableStateFlow(ARBITRARY_STRING)

    @OptIn(ExperimentalCoroutinesApi::class)
    val chat = chatMateId.flatMapLatest {
        chatDao.getChatByChatmateIdFlow(it)
    }

    val userIsRegistered = settingsRepo.username().map { it != NO_USERNAME }
    private val _registrationStatus = MutableStateFlow(DEFAULT)
    val registrationStatus = _registrationStatus.asStateFlow()

    private val _users = MutableStateFlow(UsersFetchResponse.getDefault())
    val users = _users.asStateFlow()
    private val _searchedUsers = MutableStateFlow(UsersFetchResponse.getDefault())
    val searchedUsers = _searchedUsers.asStateFlow()
    private val currentUsers get() = _users.value.users

    private val _profile = MutableStateFlow(ProfileFetchResponse.getDefault())
    val profile = _profile.asStateFlow()

    val darkModeSet = settingsRepo.darkModeFlow()




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
        chatMateId.value = userId
    }

    suspend fun markMessagesSent() = coroutineScope {
        launch(Dispatchers.IO) {
            try {
                val chat = chatDao.getChatByChatmateId(chatMateId.value)
                chat.markAllMessagesSent()
                chatDao.update(chat)
            } catch (e: NullPointerException) {
                Log.e("messagingRepo", "null pointer exception trying to mark all messages sent")
            }
        }
    }

    suspend fun markMessagesRead() = coroutineScope {
        launch(Dispatchers.IO) {
            try {
                val chat = chatDao.getChatByChatmateId(chatMateId.value)
                chat.markMessagesRead()
                chatDao.update(chat)
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

    suspend fun sendMessageX(
        receiverId: String,
        messageBody: String,
        senderId: String = thisUserId
    ) {
        val newMessage =
            Message(senderId, receiverId, messageBody, Calendar.getInstance().timeInMillis)

        val messagePackage = MessagePackage()
        messagePackage.gameModeratorId = getGameModeratorId()
        val packageString = messagePackageToString(messagePackage)
        newMessage.messagePackage = packageString

        saveOutGoingMessage(newMessage)
        scheduleMessageForSendToServer(newMessage)
    }


    suspend fun sendMessage(
        senderId: String,
        receiverId: String,
        messageBody: String
    ) {
        val newMessage =
            Message(senderId, receiverId, messageBody, Calendar.getInstance().timeInMillis)

        val messagePackage = MessagePackage()
        messagePackage.gameModeratorId = getGameModeratorId()
        val packageString = messagePackageToString(messagePackage)
        newMessage.messagePackage = packageString

        saveOutGoingMessage(newMessage)
        scheduleMessageForSendToServer(newMessage)
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
        val chat: DBChat?
        val chatsInDB = chatDao.getChatsFlow().first()
        chat = chatsInDB.find { it.chatMateId == userId }

        return chat
    }

    private suspend fun saveMessage(
        newMessage: Message,
        chatMateId: String
    ) {

        newMessage.dateTime = Calendar.getInstance().timeInMillis
        val resultOfFindChat = findOrCreateChat(chatMateId)
        val chat = resultOfFindChat.first
        val chatIsNew = resultOfFindChat.second
        chat.addMessage(newMessage)
        if (chatIsNew) chatDao.insert(chat) else chatDao.update(chat)


        Handler().postDelayed({
            settingsRepo.updateScroll(true)
        }, 100)

        Handler().postDelayed({
            settingsRepo.updateScroll(false)
        }, 200)

    }

    private suspend fun saveOutGoingMessage(newMessage: Message) {
        val chatMateId = newMessage.receiverId
        saveMessage(newMessage, chatMateId)
    }

    suspend fun saveIncomingMessage(newMessage: Message) {
        val chatMateId = newMessage.senderId

        val delay = try {
            getPackageFromMessage(newMessage).delayPostingBy
        } catch (e: Exception) {
            0
        }

        if (delay == 0) {
            saveMessage(newMessage, chatMateId)
        } else {

            Handler().postDelayed({
                runBlocking {
                    saveMessage(newMessage, chatMateId)
                }
            }, delay.toLong())
        }
    }

    suspend fun sendMessageToServer(message: Message) {
        try {
            NetworkApi.retrofitService.sendMessage(message.toNWMessage())
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun refreshMessages() {

        val newMessages: List<NWMessage>
        try {
            newMessages =
                NetworkApi.retrofitService.getMessages(settingsRepo.getUserId())
        } catch (e: Exception) {
            throw e
        }
        handleReceivedMessages(newMessages)
    }

    private suspend fun handleReceivedMessages(newMessages: List<NWMessage>) {

        // handle regular chat messages
        val chatMessages = newMessages.filter { it.type == CHAT_MESSAGE_TYPE }
        for (message in chatMessages) {

            val delayPosting = try {
                getPackageFromMessage(message.toMessage()).delayPosting
            } catch (e: Exception) {
                false
            }

            if (delayPosting) {
                Handler().postDelayed({
                    runBlocking {
                        saveIncomingMessage(message.toMessage())
                    }
                }, 2000)
            } else {
                saveIncomingMessage(message.toMessage())
            }

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

    suspend fun registerUser(userId: String, username: String, gender: String, token: String) {
        NetworkApi.retrofitService.registerNewUserWithToken(userId, username, gender, token)
    }

    suspend fun sendGameRequest() {
        gameManager.sendGameRequest()
    }

    fun setChat(chat: DBChat) {
        gameManager.setCurrentChat(chat)
    }

    suspend fun givePositiveResponse() {
        gameManager.givePositiveResponse()
    }

    suspend fun giveNegativeResponse() {
        gameManager.giveNegativeResponse()
    }

    suspend fun notifyDialogOkayPressed() {
        gameManager.notifyDialogOkayPressed()
    }

    fun getGameRequestSenderId(): String {
        return gameManager.getGameRequestSenderId()
    }

    private fun getGameModeratorId(): String {
        return gameManager.getGameModeratorId()
    }

    private fun getPackageFromMessage(message: Message): MessagePackage {
        return stringToMessagePackage(message.messagePackage)
    }

    suspend fun refreshUser(userId: String) {
        val user = NetworkApi.retrofitService.getUser(userId)
        val dbChat = getChat(userId)!!
        dbChat.verificationStatus = user.verificationStatus
        dbChat.onlineStatus = user.onlineStatus
        chatDao.update(dbChat)
    }

}