package com.othadd.ozi.data.repos

import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.othadd.ozi.*
import com.othadd.ozi.data.database.DBChat
import com.othadd.ozi.data.database.getNoDialogDialogType
import com.othadd.ozi.data.network.NetworkApi
import com.othadd.ozi.utils.*
import com.othadd.ozi.workers.SendChatMessageWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import javax.inject.Inject

class MessageRepo (private val oziApp: OziApplication) {

    private val chatDao = oziApp.database.chatDao()
    private val settingsRepo = SettingsRepo(oziApp)
    private val thisUserId = settingsRepo.getUserId()

    val chats = chatDao.getChatsFlow()

    private val _scrollMessages = MutableStateFlow(false)
    val scrollMessages = _scrollMessages.asStateFlow()

    fun getChatFlow(chatMateId: String): Flow<DBChat> {
        return chatDao.getChatByChatmateIdFlow(chatMateId)
    }

    fun getChat(userId: String): DBChat? {
        return chatDao.getChatByChatmateId(userId)
    }

    suspend fun saveNewChat(chat: DBChat){
        chatDao.insert(chat)
    }

    suspend fun updateChat(chat: DBChat){
        chatDao.update(chat)
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

    suspend fun sendMessageObject(message: Message){
        val messagePackage = if(message.messagePackage == NOT_INITIALIZED) MessagePackage() else stringToMessagePackage(message.messagePackage)
        messagePackage.gameModeratorId = getGameModeratorId()
        val packageString = messagePackageToString(messagePackage)
        message.messagePackage = packageString

        saveOutGoingMessage(message)
        scheduleMessageForSendToServer(message)
    }

    private fun getGameModeratorId(): String {
        return settingsRepo.getGameModeratorId()
    }

    private suspend fun saveOutGoingMessage(newMessage: Message) {
        val chatMateId = newMessage.receiverId
        saveMessage(newMessage, chatMateId)
    }

    private fun scheduleMessageForSendToServer(newMessage: Message) {
        val newMessageString = messageToString(newMessage)
        val workManager = WorkManager.getInstance(oziApp)
        val workRequest = OneTimeWorkRequestBuilder<SendChatMessageWorker>()
            .setInputData(workDataOf(WORKER_MESSAGE_KEY to newMessageString))
            .build()

        workManager.enqueue(workRequest)
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

        delay(100L) //if the scroll follows without delay, the screen maybe scrolled just before the latest message has appeared.
        _scrollMessages.value = true
        delay(100)
        _scrollMessages.value = false
    }

    private suspend fun findOrCreateChat(userId: String): Pair<DBChat, Boolean> {

        val chatIsNew: Boolean
        var chat = getChatInContext(userId)
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

    private suspend fun getChatInContext(userId: String): DBChat? {
        return withContext(Dispatchers.IO) { chatDao.getChatByChatmateId(userId) }
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

    private fun getPackageFromMessage(message: Message): MessagePackage {
        return stringToMessagePackage(message.messagePackage)
    }

    suspend fun getMessages(): List<NWMessage> {
        return NetworkApi.retrofitService.getMessages(thisUserId)
    }

    suspend fun sendMessageToServer(message: Message) {
        try {
            NetworkApi.retrofitService.sendMessage(message.toNWMessage())
        } catch (e: Exception) {
            throw e
        }
    }

    fun receivedMessageExists(message: Message): Boolean{
        val chat = getChat(message.senderId) ?: return false
        return chat.messages.any { it.id == message.id }
    }

    suspend fun resolveConflict(roundSummaryString: String): Boolean{
        return try {
            NetworkApi.retrofitService.resolveGameConflict(getGameModeratorId(), roundSummaryString)
            true
        }
        catch (e: Exception){
            Log.e("MessagingRepo", "resolveConflict(). ${e.message ?: e}")
            false
        }
    }
}