package com.othadd.ozi
//
//import android.app.NotificationManager
//import android.content.Context
//import androidx.core.content.ContextCompat
//import androidx.lifecycle.LiveData
//import androidx.lifecycle.asLiveData
//import androidx.work.OneTimeWorkRequestBuilder
//import androidx.work.WorkManager
//import androidx.work.workDataOf
//import com.othadd.ozi.database.DBChat
//import com.othadd.ozi.database.DialogState
//import com.othadd.ozi.database.NO_DIALOG_DIALOG_TYPE
//import com.othadd.ozi.network.NetworkApi
//import com.othadd.ozi.network.User
//import com.othadd.ozi.utils.WORKER_MESSAGE_KEY
//import com.othadd.ozi.utils.messageToString
//import com.othadd.ozi.utils.sortIntoGroupsByChatMate
//import com.othadd.ozi.workers.SendMessageWorker
//import kotlinx.coroutines.flow.*
//import kotlinx.coroutines.runBlocking
//import java.util.*
//
//class MessagingRepo(private val application: OziApplication) {
//
////    private val notificationManager = ContextCompat.getSystemService(
////        application,
////        NotificationManager::class.java
////    ) as NotificationManager
//
//    private val chatDao = application.database.chatDao()
//    private val gameManager = GameManager
//
//    private fun getUserId() = SettingsRepo(application).getUserId()
//
//    private val _chats = chatDao.getChats().asLiveData()
//    val chats: LiveData<List<DBChat>> get() = _chats
//
//    private val newDialogState = DialogState("", "", "", false, NO_DIALOG_DIALOG_TYPE)
//    private val dummyDialogState = DialogState("", "", "", false, "")
//    private val dummyInitialDBChat = DBChat(0, "", mutableListOf(), "", "", dummyDialogState)
//    private var _chat = MutableStateFlow(dummyInitialDBChat)
//    val chat: LiveData<DBChat> get() = _chat.asLiveData()
//    private val chatInstance = suspend { _chat.first() }
//
//    companion object{
//        private var INSTANCE: MessagingRepo? = null
//        fun getInstance(application: OziApplication): MessagingRepo{
//            return INSTANCE ?: MessagingRepo(application)
//        }
//    }
//
//    private suspend fun findOrCreateChat(userId: String): Pair<DBChat, Boolean> {
//
//        val chatIsNew: Boolean
//        var chat = getChat(userId)
//        if (chat == null) {
//            val user = NetworkApi.retrofitService.getUser(userId)
//            chat = DBChat(
//                chatMateId = userId,
//                messages = mutableListOf(),
//                chatMateUsername = user.username,
//                chatMateGender = user.gender,
//                dialogState = newDialogState
//            )
//            chatIsNew = true
//        } else chatIsNew = false
//
//        return Pair(chat, chatIsNew)
//    }
//
//    private suspend fun getChat(userId: String): DBChat? {
//        val chat: DBChat?
//        val chatsInDB = chatDao.getChats().first()
//        chat = chatsInDB.find { it.chatMateId == userId }
//
//        return chat
//    }
//
//    suspend fun getMessages(context: Context) {
//        val newMessages: List<NWMessage>
//        try {
//            newMessages = NetworkApi.retrofitService.getMessages(SettingsRepo(context).getUserId())
//        }
//        catch (e: Exception){
//            throw e
//        }
//        handleReceivedMessages(newMessages)
//    }
//
////    private fun sendNewMessagesNotification(
////        numberOfNewMessagesAndChats: Pair<Int, Int>,
////        appContext: Context
////    ) {
////        if (numberOfNewMessagesAndChats.first != 0) {
////            notificationManager.sendNewMessageNotification(
////                "${numberOfNewMessagesAndChats.first} new Message(s) in ${numberOfNewMessagesAndChats.second} chat(s)",
////                appContext
////            )
////        }
////    }
//
//    private suspend fun saveMessages(messages: List<NWMessage>): Pair<Int, Int> {
//        val messageGroups = messages.sortIntoGroupsByChatMate()
//
//        for (messageGroup in messageGroups) {
//            val responseOfGetChat = findOrCreateChat(messageGroup.chatMateId)
//            val chat = responseOfGetChat.first
//            chat.addMessages(messageGroup.messages)
//            if (responseOfGetChat.second) {
//                chatDao.insert(chat)
//            } else {
//                chatDao.update(chat)
//            }
//
//        }
//        return Pair(messages.size, messageGroups.size)
//    }
//
//    suspend fun saveMessage(message: Message) {
//        val responseOfGetChat = findOrCreateChat(message.senderId)
//        val chat = responseOfGetChat.first
//        chat.addMessage(message)
//        if (responseOfGetChat.second) {
//            chatDao.insert(chat)
//        } else chatDao.update(chat)
//    }
//
//
//    private suspend fun handleReceivedMessages(newMessages: List<NWMessage>) {
//
////        handle regular chat messages
//        val chatMessages = newMessages.filter { it.type == CHAT_MESSAGE_TYPE }
//        saveMessages(chatMessages)
//
////        handle other types
//        newMessages.toMutableList().removeAll(chatMessages)
//        if (newMessages.isNotEmpty()){
//            gameManager.handleMessage(newMessages, application)
//        }
//    }
//
//    suspend fun createAndScheduleMessage(senderId: String = getUserId(), message: String) {
//        val receiverId = chatInstance.invoke().chatMateId
//        val newMessage = Message(senderId, receiverId, message, Calendar.getInstance().timeInMillis)
//        scheduleMessageForSend(newMessage)
//    }
//
//    private fun scheduleMessageForSend(message: Message) {
//        val newMessageString = messageToString(message)
//        val workManager = WorkManager.getInstance(application)
//        val workRequest = OneTimeWorkRequestBuilder<SendMessageWorker>()
//            .setInputData(workDataOf(WORKER_MESSAGE_KEY to newMessageString))
//            .build()
//
//        workManager.enqueue(workRequest)
//    }
//
//    suspend fun sendMessage(message: Message) {
//
//        val chatsInDB: List<DBChat> = chatDao.getChats().first()
//        val chat = chatsInDB.find { it.chatMateId == message.receiverId }!!
//
//        chat.addMessage(message)
//        chatDao.update(chat)
//
//        NetworkApi.retrofitService.sendMessage(message.toNWMessage())
//    }
//
//    fun registerUser(settingsRepo: SettingsRepo, username: String, gender: String) {
//        runBlocking {
//            try {
//                NetworkApi.retrofitService.registerUser(settingsRepo.getUserId(), username, gender)
//                settingsRepo.storeUsername(username)
//            } catch (e: Exception) {
//                throw e
//            }
//        }
//    }
//
////    suspend fun getUsers(): List<User> {
////        return try {
////            NetworkApi.retrofitService.getUsers()
////        } catch (e: Exception) {
////            throw e
////        }
////    }
//
//    suspend fun startChat(user: User?) {
//        user!!
//        val chat = DBChat(
//            chatMateId = user.userId,
//            chatMateUsername = user.username,
//            messages = mutableListOf(),
//            chatMateGender = user.gender,
//            dialogState = newDialogState
//        )
//        chatDao.insert(chat)
//    }
//
//    suspend fun setChat(chatMateUsername: String) {
//        chatDao.getChatByChatmateUsername(chatMateUsername).collect {
//            _chat.value = it
//            gameManager.setCurrentChat(it)
//        }
//    }
//
//    suspend fun sendGameRequest() {
//        gameManager.sendGameRequest(application)
//    }
//
//    suspend fun declineGameRequest(
//        senderId: String = getUserId(),
//        messageType: String = GAME_REQUEST_RESPONSE_MESSAGE_TYPE,
//        body: String = GAME_REQUEST_DECLINED_MESSAGE_BODY
//    ) {
//        val message = Message(senderId, chatInstance.invoke().chatMateId, messageType, body)
//
//        try {
//            NetworkApi.retrofitService.sendMessage(message.toNWMessage())
//        } catch (e: Exception) {
//            throw e
//        }
//    }
//
//    suspend fun acceptGameRequest(
//        senderId: String = getUserId(),
//        messageType: String = GAME_REQUEST_RESPONSE_MESSAGE_TYPE,
//        body: String = GAME_REQUEST_ACCEPTED_MESSAGE_BODY
//    ) {
//        val message = Message(senderId, chatInstance.invoke().chatMateId, messageType, body)
//
//        try {
//            NetworkApi.retrofitService.sendMessage(message.toNWMessage())
//        } catch (e: Exception) {
//            throw e
//        }
//    }
//
//    suspend fun notifyDialogOkayPressed() {
//        gameManager.notifyDialogOkayPressed(application)
//    }
//
//    suspend fun givePositiveResponse() {
//        gameManager.givePositiveResponse(application)
//    }
//
//    suspend fun giveNegativeResponse() {
//        gameManager.giveNegativeResponse(application)
//    }
//
//    init {
//        INSTANCE = this
//    }
//}
//
//
