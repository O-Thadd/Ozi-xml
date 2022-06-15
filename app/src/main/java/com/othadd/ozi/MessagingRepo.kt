package com.othadd.ozi

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.othadd.ozi.database.DBChat
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.network.User
import com.othadd.ozi.utils.MessagesHolder
import com.othadd.ozi.utils.WORKER_MESSAGE_KEY
import com.othadd.ozi.utils.messageToString
import com.othadd.ozi.utils.sortIntoGroupsByChatMate
import com.othadd.ozi.workers.SendMessageWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.*

const val STATUS_NO_UPDATE = "No Update"
const val STATUS_USER_ALREADY_PLAYING = "User is already playing"

class MessagingRepo(private val application: OziApplication) {

    private val chatDao = application.database.chatDao()

    private val _chats = chatDao.getChats().asLiveData()
    val chats: LiveData<List<DBChat>> get() = _chats

    private val blankInitialDBChat = DBChat(0, "", mutableListOf(), "", "")
    private var _chat = MutableStateFlow(blankInitialDBChat)
    val chat: LiveData<DBChat> get() = _chat.asLiveData()
    private val chatInstance = suspend { _chat.first() }

    private var _gamingStatus = MutableLiveData<String>()
    val gamingStatus: LiveData<String> get() = _gamingStatus

    companion object{
        private lateinit var INSTANCE: MessagingRepo
        fun getInstance(application: OziApplication): MessagingRepo{
            if (INSTANCE == null){
                MessagingRepo(application)
            }
            return INSTANCE
        }
    }

    private fun getChat(userId: String): DBChat? {
        var chat: DBChat?
        runBlocking {
            val chatsInDB: List<DBChat> = chatDao.getChats().first()
            chat = chatsInDB.find { it.chatMateId == userId }
        }
        return chat
    }

    fun getMessages(context: Context) {
        runBlocking {
            val newMessages =
                NetworkApi.retrofitService.getMessages(SettingsRepo(context).getUserId())
            handleReceivedMessages(newMessages)
        }
    }

    private fun sendNewMessagesNotification(
        newMessagesAndChats: Pair<Int, Int>,
        appContext: Context
    ) {
        if (newMessagesAndChats.first != 0) {
            val notificationManager = ContextCompat.getSystemService(
                appContext,
                NotificationManager::class.java
            ) as NotificationManager
            notificationManager.sendNotification(
                "${newMessagesAndChats.first} new Message(s) in ${newMessagesAndChats.second} chat(s)",
                appContext
            )
        }
    }

    private suspend fun saveMessages(messagesGroupsSortedByChatMate: List<MessagesHolder>) {
        for (messageGroup in messagesGroupsSortedByChatMate) {
            var chat = getChat(messageGroup.chatMateId)
            if (chat == null) {
                val user = NetworkApi.retrofitService.getUser(messageGroup.chatMateId)
                chat = DBChat(
                    chatMateId = messageGroup.chatMateId,
                    messages = mutableListOf(),
                    chatMateUsername = user.username,
                    chatMateGender = user.gender
                )
                chat.addMessages(messageGroup.messages)
                chatDao.insert(chat)
            } else {
                chat.addMessages(messageGroup.messages)
                chatDao.update(chat)
            }
        }
    }

    private suspend fun saveMessage(message: Message) {
        val chat = getChat(message.senderId)!!
        chat.addMessage(message)
        chatDao.update(chat)
    }

    private suspend fun handleReceivedMessages(newMessages: List<NWMessage>) {
        val chatMessages = newMessages.filter { it.type == CHAT_MESSAGE || it.type == FROM_SERVER }
        val chatMessagesSortedByChatMate = chatMessages.sortIntoGroupsByChatMate()
        saveMessages(chatMessagesSortedByChatMate)
        sendNewMessagesNotification(
            Pair(newMessages.size, chatMessagesSortedByChatMate.size),
            application
        )

        newMessages.toMutableList().removeAll(chatMessages)

        for (message in newMessages) {
            when (message.type) {
                USER_ALREADY_PLAYING -> {
                    _gamingStatus.value = STATUS_USER_ALREADY_PLAYING
                    saveMessage(message.toMessage())
                }
            }
        }
    }

    suspend fun createAndScheduleMessage(senderId: String, message: String) {
        val receiverId = chatInstance.invoke().chatMateId

        val newMessage = Message(senderId, receiverId, message, Calendar.getInstance().timeInMillis)
        val newMessageString = messageToString(newMessage)
        val workManager = WorkManager.getInstance(application)
        val workRequest = OneTimeWorkRequestBuilder<SendMessageWorker>()
            .setInputData(workDataOf(WORKER_MESSAGE_KEY to newMessageString))
            .build()

        workManager.enqueue(workRequest)
    }

    suspend fun sendMessage(message: Message) {

        val chatsInDB: List<DBChat> = chatDao.getChats().first()
        val chat = chatsInDB.find { it.chatMateId == message.receiverId }!!

        chat.addMessage(message)
        chatDao.update(chat)

        NetworkApi.retrofitService.sendMessage(message.toNWMessage())
    }

    suspend fun getUsername(userId: String) {
        val userName = NetworkApi.retrofitService.getUser(userId).username
        val chat = getChat(userId)!!
        chat.chatMateUsername = userName
        chatDao.update(chat)
    }

    fun registerUser(settingsRepo: SettingsRepo, username: String, gender: String) {
        runBlocking {
            NetworkApi.retrofitService.registerUser(settingsRepo.getUserId(), username, gender)
            settingsRepo.storeUsername(username)
        }
    }

    suspend fun getUsers(): List<User> {
        return NetworkApi.retrofitService.getUsers()
    }

    suspend fun startChat(user: User?) {
        user!!
        val chat = DBChat(
            chatMateId = user.userId,
            chatMateUsername = user.username,
            messages = mutableListOf(),
            chatMateGender = user.gender
        )
        chatDao.insert(chat)
    }

    suspend fun setChat(chatMateUsername: String) {
        chatDao.getChatByChatmateUsername(chatMateUsername).collect {
            _chat.value = it
        }
    }

    fun getId(chatMateUsername: String): String {
        return runBlocking {
            chatDao.getChatByChatmateUsername(chatMateUsername).first().chatMateId
        }
    }

    suspend fun sendGameRequest(senderId: String) {
        val message = Message(senderId, chatInstance.invoke().chatMateId, GAME_REQUEST_MESSAGE)
        NetworkApi.retrofitService.sendMessage(message.toNWMessage())
    }

    init {
        _gamingStatus.value = STATUS_NO_UPDATE
        INSTANCE = this
    }
}


