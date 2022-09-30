package com.othadd.ozi

import android.os.Handler
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.othadd.ozi.database.ChatDao
import com.othadd.ozi.database.DBChat
import com.othadd.ozi.database.getNoDialogDialogType
import com.othadd.ozi.gaming.GameManager
import com.othadd.ozi.network.*
import com.othadd.ozi.utils.*
import com.othadd.ozi.workers.SendChatMessageWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.*

object MessagingRepoX {

    suspend fun sendMessage(
        senderId: String,
        receiverId: String,
        messageBody: String,
        application: OziApplication
    ) {
        val newMessage =
            Message(senderId, receiverId, messageBody, Calendar.getInstance().timeInMillis)

        val messagePackage = MessagePackage()
        messagePackage.gameModeratorId = getGameModeratorId()
        val packageString = messagePackageToString(messagePackage)
        newMessage.messagePackage = packageString

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
            .build()

        workManager.enqueue(workRequest)
    }

    private suspend fun findOrCreateChat(userId: String, chatDao: ChatDao): Pair<DBChat, Boolean> {

        val chatIsNew: Boolean
        var chat = getChat(userId, chatDao)
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

    private suspend fun getChat(userId: String, chatDao: ChatDao): DBChat? {
        val chat: DBChat?
        val chatsInDB = chatDao.getChats().first()
        chat = chatsInDB.find { it.chatMateId == userId }

        return chat
    }

    private suspend fun saveMessage(
        application: OziApplication,
        newMessage: Message,
        chatMateId: String
    ) {

        newMessage.dateTime = Calendar.getInstance().timeInMillis
        val chatDao = application.database.chatDao()
        val resultOfFindChat = findOrCreateChat(chatMateId, chatDao)
        val chat = resultOfFindChat.first
        val chatIsNew = resultOfFindChat.second
        chat.addMessage(newMessage)
        if (chatIsNew) chatDao.insert(chat) else chatDao.update(chat)


        Handler().postDelayed({
            SettingsRepo(application).updateScroll(true)
        }, 100)

        Handler().postDelayed({
            SettingsRepo(application).updateScroll(false)
        }, 200)

    }

    private suspend fun saveOutGoingMessage(application: OziApplication, newMessage: Message) {
        val chatMateId = newMessage.receiverId
        saveMessage(application, newMessage, chatMateId)
    }

    suspend fun saveIncomingMessage(application: OziApplication, newMessage: Message) {
        val chatMateId = newMessage.senderId

        val delay = try { getPackageFromMessage(newMessage).delayPostingBy }
        catch (e: Exception) { 0 }

        if (delay == 0) {
            saveMessage(application, newMessage, chatMateId)
        } else {

            Handler().postDelayed({
                runBlocking {
                    saveMessage(application, newMessage, chatMateId)
                }
            }, delay.toLong())
        }
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

        // handle regular chat messages
        val chatMessages = newMessages.filter { it.type == CHAT_MESSAGE_TYPE }
        for (message in chatMessages) {

            val delayPosting = try { getPackageFromMessage(message.toMessage()).delayPosting }
            catch (e: Exception) { false }

            if (delayPosting) {
                Handler().postDelayed({
                    runBlocking {
                        saveIncomingMessage(application, message.toMessage())
                    }
                }, 2000)
            } else {
                saveIncomingMessage(application, message.toMessage())
            }

        }

//        handle status update messages
        val statusUpdateMessages = newMessages.filter { it.type == STATUS_UPDATE_MESSAGE_TYPE }
        for (message in statusUpdateMessages) {
            handleStatusUpdateMessage(message, application.database.chatDao())
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
            GameManager.handleMessage(gamingMessages, application)
        }
    }

    private suspend fun handleStatusUpdateMessage(newMessage: NWMessage, chatDao: ChatDao) {
        val message = newMessage.toMessage()
        val chat = findOrCreateChat(message.senderId, chatDao).first
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

    suspend fun sendGameRequest(application: OziApplication) {
        GameManager.sendGameRequest(application)
    }

    fun setChat(chat: DBChat) {
        GameManager.setCurrentChat(chat)
    }

    suspend fun givePositiveResponse(application: OziApplication) {
        GameManager.givePositiveResponse(application)
    }

    suspend fun giveNegativeResponse(application: OziApplication) {
        GameManager.giveNegativeResponse(application)
    }

    suspend fun notifyDialogOkayPressed(application: OziApplication) {
        GameManager.notifyDialogOkayPressed(application)
    }

    fun getGameRequestSenderId(): String {
        return GameManager.getGameRequestSenderId()
    }

    private fun getGameModeratorId(): String {
        return GameManager.getGameModeratorId()
    }

    private fun getPackageFromMessage(message: Message): MessagePackage {
        return stringToMessagePackage(message.messagePackage)
    }

    suspend fun refreshUser(userId: String, chatDao: ChatDao) {
        val user = NetworkApi.retrofitService.getUser(userId)
        val dbChat = getChat(userId, chatDao)!!
        dbChat.verificationStatus = user.verificationStatus
        dbChat.onlineStatus = user.onlineStatus
        chatDao.update(dbChat)
    }

}