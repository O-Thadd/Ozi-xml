package com.othadd.ozi.gaming

import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.othadd.ozi.*
import com.othadd.ozi.database.*
import com.othadd.ozi.utils.SettingsRepo
import com.othadd.ozi.utils.WORKER_MESSAGE_KEY
import com.othadd.ozi.utils.messageToString
import com.othadd.ozi.workers.SendMessageWorker
import kotlinx.coroutines.flow.first

//
//const val STATUS_NO_UPDATE = "No Update"
//const val STATUS_USER_ALREADY_PLAYING = "User is already playing"
//const val STATUS_NEW_GAME_REQUEST = "New game request"
//const val STATUS_REQUEST_DECLINED = "Game request declined"
//const val STATUS_REQUEST_ACCEPTED = "Game request accepted"

const val RESPOND_TO_GAME_REQUEST_PROMPT_TYPE = "respond to game request"
const val NEW_GAME_REQUEST_WORKER_TAG = "game request work tag"

class GameManager(private val application: OziApplication) {

    private fun getChatDao(application: OziApplication) = application.database.chatDao()
    private var currentChat: DBChat? = null
    private lateinit var currentPromptType: String

    private val timers: MutableList<OziCountDownTimer> = mutableListOf()

    companion object{
        private var INSTANCE: GameManager? = null
        fun getInstance(application: OziApplication): GameManager {
            return INSTANCE ?: GameManager(application)
        }
    }

    suspend fun sendGameRequest(application: OziApplication) {
        val thisUserId = SettingsRepo(application).getUserId()
        val receiverId = currentChat!!.chatMateId
        val message =
            Message(thisUserId, receiverId, "", GAME_REQUEST_MESSAGE_TYPE, SERVER_SENDER_TYPE)
        val dialogState = getNotifyDialogType(
            application.getString(
                R.string.game_request_sending_game_request
            ), false
        )
        updateDialogState(receiverId, dialogState, application)

        sendNewGameRequestMessage(message, application)

//        NetworkApi.retrofitService.sendMessage(message.toNWMessage())
    }

    suspend fun startTimerToReceiveResponse(application: OziApplication){
        startTimer(currentChat!!.chatMateId, TIMER_TO_RECEIVE_RESPONSE, application)
    }

    private fun findOrCreateTimer(userId: String, application: OziApplication): OziCountDownTimer {
        return timers.find { it.id == userId } ?: OziCountDownTimer(
            userId,
            application
        ) { timers.remove(it) }
    }

    private suspend fun startTimer(userId: String, type: String, application: OziApplication) {
        val timer = findOrCreateTimer(userId, application)
        timer.start(type)
        timers.add(timer)
    }

    private fun stopTimer(userId: String, application: OziApplication) {
        val timer = findOrCreateTimer(userId, application)
        timer.stop()
        timers.remove(timer)
    }

    suspend fun setCurrentChat(chatMateId: String) {
        currentChat = getChatDao(application).getChatByChatmateId(chatMateId).first()
    }

    suspend fun handleMessage(messages: List<NWMessage>, application: OziApplication) {
        if (messages.isNotEmpty()){
            Log.e("wahala", "gamemanager handling message. message is ${messages[0].body}")
        }
        else{
            Log.e("wahala", "gamemanager handling message. empty list of messages supplied")
        }

        for (message in messages) {
            when (message.type) {

                GAME_REQUEST_RESPONSE_MESSAGE_TYPE -> {
                    Log.e("wahala", "gamemanager handling request response")
                    when (message.body) {
                        USER_ALREADY_PLAYING_MESSAGE_BODY -> {
                            Log.e("wahala", "chatmate already playing")
                            stopTimer(message.senderId, application)
                            Log.e("wahala", "timer stopped")
                            val dialogState = getNotifyDialogType(
                                application.getString(
                                    R.string.game_request_response_user_already_playing,
                                    getUsername(message.senderId, application)
                                ), true
                            )
                            updateDialogState(message.senderId, dialogState, application)
                            Log.e("wahala", "dialog updated")
                        }

                        USER_HAS_PENDING_REQUEST_MESSAGE_BODY -> {
                            stopTimer(message.senderId, application)
                            val dialogState = getNotifyDialogType(
                                application.getString(
                                    R.string.game_request_response_user_has_pending,
                                    getUsername(message.senderId, application)
                                ),
                                true,
                            )
                            updateDialogState(message.senderId, dialogState, application)
                        }

                        GAME_REQUEST_DECLINED_MESSAGE_BODY -> {
                            stopTimer(message.senderId, application)
                            val dialogState = getNotifyDialogType(
                                application.getString(R.string.game_request_declined),
                                true
                            )
                            updateDialogState(message.senderId, dialogState, application)
                        }

                        GAME_REQUEST_ACCEPTED_MESSAGE_BODY -> {
                            stopTimer(message.senderId, application)
                            val dialogState = getNotifyDialogType(
                                application.getString(R.string.game_request_accepted, getUsername(message.senderId, application)),
                                true
                            )
                            updateDialogState(message.senderId, dialogState, application)
                        }
                    }
                }

                GAME_REQUEST_MESSAGE_TYPE -> {
                    MessagingRepo(application).saveMessage(message.toMessage(), currentChat!!)
                    currentPromptType = RESPOND_TO_GAME_REQUEST_PROMPT_TYPE
                    startTimer(message.senderId, TIMER_TO_RESPOND, application)
                }
            }
        }

    }

    private suspend fun getUsername(userId: String, application: OziApplication): String {
        return getChatDao(application).getChatByChatmateId(userId).first().chatMateUsername
    }

    private suspend fun updateDialogState(
        chatMateId: String,
        dialogState: DialogState,
        application: OziApplication
    ) {
        val chat = getChatDao(application).getChatByChatmateId(chatMateId).first()
        chat.dialogState = dialogState
        getChatDao(application).update(chat)
    }

    suspend fun notifyDialogOkayPressed(application: OziApplication) {
        currentChat?.let {
            updateDialogState(
                it.chatMateId,
                getNoDialogDialogType(),
                application
            )
        }
    }

    suspend fun givePositiveResponse(application: OziApplication) {
        acceptGameRequest(application)
    }

    private suspend fun acceptGameRequest(application: OziApplication) {
        updateDialogState(currentChat!!.chatMateId, getNoDialogDialogType(), application)
        val thisUserId = SettingsRepo(application).getUserId()
        val chatMateId = currentChat!!.chatMateId
        stopTimer(chatMateId, application)
        val message = Message(
            thisUserId,
            chatMateId,
            GAME_REQUEST_ACCEPTED_MESSAGE_BODY,
            GAME_REQUEST_RESPONSE_MESSAGE_TYPE,
            SERVER_SENDER_TYPE
        )
//        NetworkApi.retrofitService.sendMessage(message.toNWMessage())
        sendMessage(message, application)
    }

    suspend fun giveNegativeResponse(application: OziApplication) {
        declineGameRequest(application)
    }

    private suspend fun declineGameRequest(application: OziApplication) {
        updateDialogState(currentChat!!.chatMateId, getNoDialogDialogType(), application)
        val thisUserId = SettingsRepo(application).getUserId()
        val chatMateId = currentChat!!.chatMateId
        stopTimer(chatMateId, application)
        val message = Message(
            thisUserId,
            chatMateId,
            GAME_REQUEST_DECLINED_MESSAGE_BODY,
            GAME_REQUEST_RESPONSE_MESSAGE_TYPE,
            SERVER_SENDER_TYPE
        )
//        NetworkApi.retrofitService.sendMessage(message.toNWMessage())
        sendMessage(message, application)
    }

    private fun sendNewGameRequestMessage(message: Message, application: OziApplication){
        val newMessageString = messageToString(message)
        val workManager = WorkManager.getInstance(application)
        val workRequest = OneTimeWorkRequestBuilder<SendMessageWorker>()
            .addTag(SettingsRepo(application).getUserId() + NEW_GAME_REQUEST_WORKER_TAG)
            .setInputData(workDataOf(WORKER_MESSAGE_KEY to newMessageString))
            .build()

        workManager.enqueue(workRequest)
    }

    private fun sendMessage(message: Message, application: OziApplication){
        val newMessageString = messageToString(message)
        val workManager = WorkManager.getInstance(application)
        val workRequest = OneTimeWorkRequestBuilder<SendMessageWorker>()
            .setInputData(workDataOf(WORKER_MESSAGE_KEY to newMessageString))
            .build()

        workManager.enqueue(workRequest)
    }

    init {
        INSTANCE = this
    }
}