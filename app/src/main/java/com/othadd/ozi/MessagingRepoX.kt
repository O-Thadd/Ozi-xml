package com.othadd.ozi

import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.othadd.ozi.database.DBChat
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.utils.WORKER_MESSAGE_KEY
import com.othadd.ozi.utils.messageToString
import com.othadd.ozi.workers.GetMessagesWorker
import com.othadd.ozi.workers.SendChatMessageWorker
import kotlinx.coroutines.flow.first
import java.util.*

const val SEND_CHAT_MESSAGE_WORKER_TAG = "send chat message worker tag"

object MessagingRepoX {

    suspend fun sendMessage(
        senderId: String,
        receiverId: String,
        messageBody: String,
        application: OziApplication
    ) {
        val newMessage =
            Message(senderId, receiverId, messageBody, Calendar.getInstance().timeInMillis)
        saveOutGoingMessage(application, newMessage)
        scheduleMessageForSendToServer(newMessage, application)
    }

    private fun scheduleMessageForSendToServer(
        newMessage: Message,
        application: OziApplication
    ) {
        val newMessageString = messageToString(newMessage)
        val workManager = WorkManager.getInstance(application)
        val workRequest = OneTimeWorkRequestBuilder<SendChatMessageWorker>()
            .setInputData(workDataOf(WORKER_MESSAGE_KEY to newMessageString))
            .addTag(SEND_CHAT_MESSAGE_WORKER_TAG)
            .build()

        workManager.enqueue(workRequest)
    }

    private suspend fun saveMessage(
        application: OziApplication,
        newMessage: Message,
        chatMateId: String
    ) {
        val chatDao = application.database.chatDao()
        val chat = chatDao.getChatByChatmateId(chatMateId).first()
        chat.addMessage(newMessage)
        chatDao.update(chat)
    }

    private suspend fun saveOutGoingMessage(application: OziApplication, newMessage: Message) {
        val chatMateId = newMessage.receiverId
        saveMessage(application, newMessage, chatMateId)
    }

    private suspend fun saveIncomingMessage(application: OziApplication, newMessage: Message) {
        val chatMateId = newMessage.senderId
        saveMessage(application, newMessage, chatMateId)
    }

    suspend fun sendMessageToServer(message: Message) {
        NetworkApi.retrofitService.sendMessage(message.toNWMessage())
    }

    suspend fun refreshMessages(application: OziApplication) {

        val newMessages: List<NWMessage>
        try {
            newMessages =
                NetworkApi.retrofitService.getMessages(SettingsRepo(application).getUserId())
        } catch (e: Exception) {
            throw e
        }
        handleReceivedMessages(newMessages, application)
    }

    private suspend fun handleReceivedMessages(
        newMessages: List<NWMessage>,
        application: OziApplication
    ) {

//        handle regular chat messages
        val chatMessages = newMessages.filter { it.type == CHAT_MESSAGE_TYPE }
        for (message in chatMessages) {
            saveIncomingMessage(application, message.toMessage())
        }

//        handle other types
        val nonChatMessages = newMessages.toMutableList()
        nonChatMessages.removeAll(chatMessages)
        if (nonChatMessages.isNotEmpty()) {
            GameManager.handleMessage(nonChatMessages, application)
        }
    }

    suspend fun registerUser(userId: String, username: String, gender: String) {
        NetworkApi.retrofitService.registerUser(userId, username, gender)
    }

    suspend fun sendGameRequest(application: OziApplication) {
        GameManager.sendGameRequest(application)
    }

    fun setChat(chat: DBChat) {
        GameManager.setCurrentChat(chat)
    }

    suspend fun givePositiveResponse(application: OziApplication){
        GameManager.givePositiveResponse(application)
    }

    suspend fun giveNegativeResponse(application: OziApplication){
        GameManager.giveNegativeResponse(application)
    }

    suspend fun notifyDialogOkayPressed(application: OziApplication) {
        GameManager.notifyDialogOkayPressed(application)
    }

}