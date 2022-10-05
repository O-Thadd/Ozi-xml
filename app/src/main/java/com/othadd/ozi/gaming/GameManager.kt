package com.othadd.ozi.gaming

import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import com.othadd.ozi.*
import com.othadd.ozi.database.DBChat
import com.othadd.ozi.database.DialogState
import com.othadd.ozi.database.getNoDialogDialogType
import com.othadd.ozi.database.getNotifyDialogType
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.ui.getPromptSnackBar
import com.othadd.ozi.utils.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

const val RESPOND_TO_GAME_REQUEST_PROMPT_TYPE = "respond to game request"
const val DUMMY_STRING = "dummy string"
const val CARRYING_GAME_MODERATOR_ID_CONTENT_DESC = "carrying game moderator id"


object GameManager {

    private var oziApplication: OziApplication? = null
    private fun getChatDao(application: OziApplication) = application.database.chatDao()
    private var currentChatChat: DBChat? = null
    private var currentPromptType: String = DUMMY_STRING
    private var gameRequestSenderId: String = DUMMY_STRING
    private var gameModeratorId: String = DUMMY_STRING

    private val timers: MutableList<OziCountDownTimer> = mutableListOf()

    private var conflictTimer = object : CountDownTimer(2000, 500) {
        override fun onTick(millisUntilFinished: Long) {}
        override fun onFinish() {
            runBlocking {
                Log.e(
                    "conflict Resolution",
                    "timer ended. waiting list size: ${waitingMessagesList.size}"
                )
                resolveConflictIfAny(oziApplication!!)
            }
        }
    }

    private val waitingMessagesList = mutableListOf<NWMessage>()

    suspend fun sendGameRequest(application: OziApplication) {
        val thisUserId = SettingsRepo(application).getUserId()
        val receiverId = currentChatChat!!.chatMateId
        val message =
            Message(thisUserId, receiverId, "", GAME_REQUEST_MESSAGE_TYPE, SERVER_SENDER_TYPE)
        val dialogState = getNotifyDialogType(
            application.getString(
                R.string.game_request_sending_game_request
            ), false
        )
        updateDialogState(receiverId, dialogState, application)

        Handler().postDelayed({
            runBlocking {
                try {
                    NetworkApi.retrofitService.sendMessage(message.toNWMessage())
                    startTimer(receiverId, TIMER_TO_RECEIVE_RESPONSE, application)
                } catch (e: Exception) {
                    updateDialogState(receiverId, getNoDialogDialogType(), application)
                    showNetworkErrorToast(application, "could not send game request")
                }
            }
        }, 500)

    }

    private fun findOrCreateTimer(userId: String, application: OziApplication): OziCountDownTimer {
        return timers.find { it.userId == userId } ?: OziCountDownTimer(
            userId,
            application
        ) { countDownTimer, chatMateId, timerType ->
            timers.remove(countDownTimer)
            if (currentPromptType == RESPOND_TO_GAME_REQUEST_PROMPT_TYPE) {
                respondToServerDeclineGameRequest(chatMateId, application)
                currentPromptType = DUMMY_STRING
            }

            // if this is a timer to receive a response, then a 'game declined' message has to be sent to self
            // in order to account for the 'permanent request pending' bug.
            // (bug is explained in the updateDialogState method)
            if (timerType == TIMER_TO_RECEIVE_RESPONSE){
                selfDeclineGameRequest(application)
            }
        }
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

    fun setCurrentChat(chat: DBChat) {
        currentChatChat = chat
    }

    suspend fun handleMessage(messages: List<NWMessage>, application: OziApplication) {

        for (message in messages) {
            var messagePackage: MessagePackage? = null
            var sendMessage = true

            var containsRoundSummary = false
            var containsGameModeratorId = false
            var containsInstructions = false

            try {
                messagePackage = getPackageFromMessage(message.toMessage())
            } catch (e: Exception) { }


            if (messagePackage != null) {


                if (messagePackage.gameModeratorId != NOT_INITIALIZED) {
                    containsGameModeratorId = true
                    gameModeratorId = messagePackage.gameModeratorId
                    SettingsRepo(application).updateKeyboardMode(true)
                }

                if (messagePackage.roundSummary != NOT_INITIALIZED) {
                    containsRoundSummary = true
                    waitingMessagesList.add(message)
                    Log.e("conflict Resolution", "added message to waiting list")
                    Log.e("conflict Resolution", message.body)
                    Log.e(
                        "conflict Resolution",
                        "waiting list size now ${waitingMessagesList.size}"
                    )
                    if (waitingMessagesList.size == 1) {
                        conflictTimer.cancel()
                        oziApplication = application
                        conflictTimer.start()
                        Log.e("conflict Resolution", "conflict timer restarted")
                    }
                }


                if (messagePackage.instructions != NOT_INITIALIZED) {
                    containsInstructions = true
                    val instructions = stringToInstructions(messagePackage.instructions)
                    for (instruction in instructions) {
                        when (instruction) {
                            DISABLE_GAME_MODE_KEYBOARD_INSTRUCTION -> SettingsRepo(application).updateKeyboardMode(
                                false
                            )
                        }
                    }
                }


                sendMessage =
                    !(containsGameModeratorId || containsInstructions || containsRoundSummary)

            }


            when (message.type) {

                GAME_REQUEST_RESPONSE_MESSAGE_TYPE -> {
                    when (message.body) {
                        USER_ALREADY_PLAYING_MESSAGE_BODY -> {
                            stopTimer(message.senderId, application)
                            val dialogState = getNotifyDialogType(
                                application.getString(
                                    R.string.game_request_response_user_already_playing,
                                    getUsername(message.senderId, application)
                                ), true
                            )
                            updateDialogState(message.senderId, dialogState, application)
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
                                application.getString(
                                    R.string.game_request_accepted,
                                    getUsername(message.senderId, application)
                                ),
                                true
                            )
                            updateDialogState(message.senderId, dialogState, application)
                        }
                    }
                }

                GAME_REQUEST_MESSAGE_TYPE -> {
                    val chat = getChatDao(application).getChatByChatmateId(message.senderId).first()
                    if (!chat.messages.any { it.id == message.id }) {
                        MessagingRepoX.saveIncomingMessage(application, message.toMessage())
                        currentPromptType = RESPOND_TO_GAME_REQUEST_PROMPT_TYPE
                        startTimer(message.senderId, TIMER_TO_RESPOND, application)
                        gameRequestSenderId = message.senderId
                        SettingsRepo(application).updateSnackBarState(
                            getPromptSnackBar(
                                "${getUsername(message.senderId, application)} has challenged you!",
                                "Go to chat"
                            )
                        )
                    }
                }

                else -> {
                    if (sendMessage) {
                        MessagingRepoX.saveIncomingMessage(application, message.toMessage())
                    }
                }
            }
        }

    }

    private suspend fun resolveConflictIfAny(application: OziApplication) {
        Log.e(
            "conflict Resolution",
            "resolveConflictIfAny method called. waiting list size: ${waitingMessagesList.size}"
        )

        if (waitingMessagesList.size < 2) {
            Log.e(
                "conflict Resolution",
                "resolveConflictIfAny method returning without performing any action"
            )
            for (message in waitingMessagesList) {
                MessagingRepoX.saveIncomingMessage(application, message.toMessage())
            }
            Log.e("conflict Resolution", "posted all waiting messages")
            waitingMessagesList.clear()
            Log.e("conflict Resolution", "waiting list cleared")
            return
        }

        Log.e("conflict Resolution", "commencing conflict resolution")
        waitingMessagesList.sortBy { getRoundSummaryFromNWMessage(it).answerTime }
        val resolvedMessage1 = waitingMessagesList[0]
        val resolvedMessage1Package = getPackageFromMessage(resolvedMessage1.toMessage())
        val resolvedRoundSummary = getRoundSummaryFromNWMessage(resolvedMessage1)
        val roundSummaryToSendToServerStringX = roundSummaryToString(resolvedRoundSummary)
        NetworkApi.retrofitService.resolveGameConflict(
            gameModeratorId,
            roundSummaryToSendToServerStringX
        )

        val resolvedMessage2: NWMessage =
            if (resolvedMessage1Package.contentDesc == WINNER_ANNOUNCEMENT) {
                waitingMessagesList.find {
                    getRoundSummaryFromNWMessage(it).answerTime == resolvedRoundSummary.answerTime && getPackageFromMessage(
                        it.toMessage()
                    ).contentDesc == WORD_TO_TYPE
                }!!
            } else {
                waitingMessagesList.find {
                    getRoundSummaryFromNWMessage(it).answerTime == resolvedRoundSummary.answerTime && getPackageFromMessage(
                        it.toMessage()
                    ).contentDesc == WINNER_ANNOUNCEMENT
                }!!
            }

        MessagingRepoX.saveIncomingMessage(application, resolvedMessage1.toMessage())
        MessagingRepoX.saveIncomingMessage(application, resolvedMessage2.toMessage())

        waitingMessagesList.clear()
    }

    private fun getRoundSummaryFromNWMessage(nwMessage: NWMessage): RoundSummary {
        val messagePackage = stringToMessagePackage(nwMessage.messagePackage)
        return stringToRoundSummary(messagePackage.roundSummary)
    }

    private suspend fun getUsername(userId: String, application: OziApplication): String {
        return getChatDao(application).getChatByChatmateId(userId).first().chatMateUsername
    }

    private suspend fun updateDialogState(
        chatMateId: String,
        dialogState: DialogState,
        application: OziApplication
    ) {

        /*
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

            A better implementation would be to just to implement a timer on the server side.
         */

        val chat = try {
            getChatDao(application).getChatByChatmateId(chatMateId).first()
        } catch (e: Exception) { return }
        chat.dialogState = dialogState
        getChatDao(application).update(chat)
    }

    suspend fun notifyDialogOkayPressed(application: OziApplication) {
        currentChatChat?.let {
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
        updateDialogState(currentChatChat!!.chatMateId, getNoDialogDialogType(), application)
        val thisUserId = SettingsRepo(application).getUserId()
        val chatMateId = currentChatChat!!.chatMateId
        stopTimer(chatMateId, application)
        val message = Message(
            thisUserId,
            chatMateId,
            GAME_REQUEST_ACCEPTED_MESSAGE_BODY,
            GAME_REQUEST_RESPONSE_MESSAGE_TYPE,
            SERVER_SENDER_TYPE
        )
        try {
            NetworkApi.retrofitService.sendMessage(message.toNWMessage())
        } catch (e: Exception) {
            Log.e("game manager", "exception trying to accept game request. $e")
            showNetworkErrorToast(
                application,
                "game manager encountered exception trying to accept game request"
            )
        }
    }

    suspend fun giveNegativeResponse(application: OziApplication) {
        declineGameRequest(application)
    }

    private suspend fun declineGameRequest(application: OziApplication) {
        updateDialogState(currentChatChat!!.chatMateId, getNoDialogDialogType(), application)
        val thisUserId = SettingsRepo(application).getUserId()
        val chatMateId = currentChatChat!!.chatMateId
        stopTimer(chatMateId, application)
        val message = Message(
            thisUserId,
            chatMateId,
            GAME_REQUEST_DECLINED_MESSAGE_BODY,
            GAME_REQUEST_RESPONSE_MESSAGE_TYPE,
            SERVER_SENDER_TYPE
        )

        try {
            NetworkApi.retrofitService.sendMessage(message.toNWMessage())
        } catch (e: Exception) {
            Log.e("game manager", "exception trying to decline game request. $e")
            showNetworkErrorToast(application, "Error tyring to decline game request")
        }
    }

    private fun selfDeclineGameRequest(application: OziApplication) {
        runBlocking {
            val thisUserId = SettingsRepo(application).getUserId()
            val message = Message(
                thisUserId,
                thisUserId,
                GAME_REQUEST_DECLINED_MESSAGE_BODY,
                GAME_REQUEST_RESPONSE_MESSAGE_TYPE,
                SERVER_SENDER_TYPE
            )

            try {
                NetworkApi.retrofitService.sendMessage(message.toNWMessage())
            } catch (e: Exception) {
                Log.e("game manager", "exception trying to self-decline game request. $e")
                showNetworkErrorToast(application, "Error tyring to self-decline game request")
            }
        }
    }

    private fun respondToServerDeclineGameRequest(chatMateId: String, application: OziApplication) {
        runBlocking {
            val thisUserId = SettingsRepo(application).getUserId()
            val message = Message(
                thisUserId,
                chatMateId,
                GAME_REQUEST_DECLINED_MESSAGE_BODY,
                GAME_REQUEST_RESPONSE_MESSAGE_TYPE,
                SERVER_SENDER_TYPE
            )

            try {
                NetworkApi.retrofitService.sendMessage(message.toNWMessage())
            } catch (e: Exception) {
                Log.e("game manager", "exception trying to decline game request. $e")
                showNetworkErrorToast(application, "Error trying to decline game request")
            }
        }
    }

    fun getGameRequestSenderId(): String {
        return gameRequestSenderId
    }

    fun getGameModeratorId(): String {
        return gameModeratorId
    }

    private fun getPackageFromMessage(message: Message): MessagePackage {
        return stringToMessagePackage(message.messagePackage)
    }
}