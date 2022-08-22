package com.othadd.ozi

import android.content.Context
import android.os.Handler
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.asLiveData
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.othadd.ozi.database.DBChat
import com.othadd.ozi.database.DialogState
import com.othadd.ozi.database.getNoDialogDialogType
import com.othadd.ozi.gaming.GameManager
import com.othadd.ozi.gaming.NEW_GAME_REQUEST_WORKER_TAG
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.network.User
import com.othadd.ozi.utils.SettingsRepo
import com.othadd.ozi.utils.WORKER_MESSAGE_KEY
import com.othadd.ozi.utils.messageToString
import com.othadd.ozi.utils.sortIntoGroupsByChatMate
import com.othadd.ozi.workers.SendChatMessageWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

class MessagingRepo(private val application: OziApplication) {

    val chatDao = application.database.chatDao()
    private val gameManager = GameManager.getInstance(application)

    private var sendNewGameRequestWorkerHasRun = false

    val sendNewGameRequestWorkerStatus = Transformations.map(
        WorkManager.getInstance(application)
            .getWorkInfosByTagLiveData(SettingsRepo(application).getUserId() + NEW_GAME_REQUEST_WORKER_TAG)
    )
    { workInfoList ->
        var shouldStartTimerToResponse = false

        if (workInfoList.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.BLOCKED || it.state == WorkInfo.State.ENQUEUED }) {
            sendNewGameRequestWorkerHasRun = true
        } else {
            shouldStartTimerToResponse =
                !workInfoList.any { !it.state.isFinished } && sendNewGameRequestWorkerHasRun
            sendNewGameRequestWorkerHasRun = false
        }

        return@map shouldStartTimerToResponse
    }

    private var _allMessagesSent = MutableLiveData<List<WorkInfo>>()
    val allMessagesSent: LiveData<List<WorkInfo>> get() = _allMessagesSent

    private fun getUserId() = SettingsRepo(application).getUserId()

    private val _chats = chatDao.getChats().asLiveData()
    val chats: LiveData<List<DBChat>> get() = _chats

    private val dummyDialogState = DialogState("", "", "", false, "")
    private var _chat = MutableLiveData<DBChat>()

    //    val chat: LiveData<DBChat> get() = _chat.asLiveData()
//    private val chatInstance = suspend { _chat.first() }
//    private val chatInstanceX: DBChat? get() = _chat.value

    companion object {
        private var INSTANCE: MessagingRepo? = null
        fun getInstance(application: OziApplication): MessagingRepo {
            return INSTANCE ?: MessagingRepo(application)
        }
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
                dialogState = getNoDialogDialogType()
            )
            chatIsNew = true
        } else chatIsNew = false

        return Pair(chat, chatIsNew)
    }

    private suspend fun getChat(userId: String): DBChat? {
        val chat: DBChat?
        val chatsInDB = chatDao.getChats().first()
        chat = chatsInDB.find { it.chatMateId == userId }

        return chat
    }

    suspend fun getMessages(context: Context) {
        val newMessages: List<NWMessage>
        try {
            newMessages = NetworkApi.retrofitService.getMessages(SettingsRepo(context).getUserId())
        } catch (e: Exception) {
            throw e
        }
        handleReceivedMessages(newMessages)
    }

    private suspend fun saveMessages(messages: List<NWMessage>): Pair<Int, Int> {
        val messageGroups = messages.sortIntoGroupsByChatMate()

        for (messageGroup in messageGroups) {
            val responseOfGetChat = findOrCreateChat(messageGroup.chatMateId)
            val chat = responseOfGetChat.first
            chat.addMessages(messageGroup.messages)
            if (responseOfGetChat.second) {
                chatDao.insert(chat)
            } else {
                chatDao.update(chat)
            }

        }
        return Pair(messages.size, messageGroups.size)
    }

    private suspend fun handleReceivedMessages(newMessages: List<NWMessage>) {

//        handle regular chat messages
        val chatMessages = newMessages.filter { it.type == CHAT_MESSAGE_TYPE }
        saveMessages(chatMessages)

//        handle other types
        val nonChatMessages = newMessages.toMutableList()
        nonChatMessages.removeAll(chatMessages)
        if (nonChatMessages.isNotEmpty()) {
            gameManager.handleMessage(newMessages, application)
        }
    }

    suspend fun sendMessage(senderId: String = getUserId(), chat: DBChat, message: String) {
//        val receiverId = chatInstance.invoke().chatMateId
        val newMessage =
            Message(senderId, chat.chatMateId, message, Calendar.getInstance().timeInMillis)
//        saveMessage(newMessage, chat)
        scheduleMessageForSendToServer(newMessage)
    }

    private fun scheduleMessageForSendToServer(message: Message) {
        val newMessageString = messageToString(message)
        val workManager = WorkManager.getInstance(application)
        val workRequest = OneTimeWorkRequestBuilder<SendChatMessageWorker>()
            .setInputData(workDataOf(WORKER_MESSAGE_KEY to newMessageString))
            .addTag(message.receiverId)
            .build()

        workManager.enqueue(workRequest)
    }

    suspend fun saveMessage(message: Message, chat: DBChat) {
//        val chat = chatDao.getChatByChatmateId(message.receiverId).first()
//        val chat = getChat(message.receiverId)
            chat.addMessage(message)
            chatDao.update(chat)
    }

    suspend fun sendMessageToServer(message: Message) {
        NetworkApi.retrofitService.sendMessage(message.toNWMessage())
    }

    fun registerUser(settingsRepo: SettingsRepo, username: String, gender: String) {
        runBlocking {
            try {
                NetworkApi.retrofitService.registerUser(settingsRepo.getUserId(), username, gender)
                settingsRepo.storeUsername(username)
            } catch (e: Exception) {
                throw e
            }
        }
    }

    suspend fun getUsers(requesterId: String): List<User> {
        return try {
            NetworkApi.retrofitService.getUsers(requesterId)
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun startChat(user: User?) {
        user!!
        val chat = DBChat(
            chatMateId = user.userId,
            chatMateUsername = user.username,
            messages = mutableListOf(),
            chatMateGender = user.gender,
            dialogState = getNoDialogDialogType()
        )
        chatDao.insert(chat)
    }

    suspend fun setChatX(chatMateUserId: String) {
        setIdForSendWorkRequestsStatus(chatMateUserId)
        gameManager.setCurrentChat(chatMateUserId)
    }

    suspend fun sendGameRequest() {
        gameManager.sendGameRequest(application)
    }

    suspend fun notifyDialogOkayPressed() {
        gameManager.notifyDialogOkayPressed(application)
    }

    suspend fun givePositiveResponse() {
        gameManager.givePositiveResponse(application)
    }

    suspend fun giveNegativeResponse() {
        gameManager.giveNegativeResponse(application)
    }

    suspend fun startTimerToReceiveResponse() {
        gameManager.startTimerToReceiveResponse(application)
    }

    private fun setIdForSendWorkRequestsStatus(userId: String) {
        _allMessagesSent = WorkManager.getInstance(application)
            .getWorkInfosByTagLiveData(userId) as MutableLiveData<List<WorkInfo>>
    }

    suspend fun markAllMessagesAsSent(chatMateUserId: String) {
        val chat = chatDao.getChatByChatmateId(chatMateUserId).first()
        chat.markAllMessagesSent()
        chatDao.update(chat)
    }

    suspend fun markAllMessagesAsRead(chatMateUserId: String) {
        val chat = chatDao.getChatByChatmateId(chatMateUserId).first()
        chat.markAllMessagesRead()
        chatDao.update(chat)
    }

    init {
        INSTANCE = this
    }
}


