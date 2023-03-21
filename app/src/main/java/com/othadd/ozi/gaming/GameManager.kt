package com.othadd.ozi.gaming

import android.os.CountDownTimer
import android.util.Log
import com.othadd.ozi.*
import com.othadd.ozi.data.database.DialogState
import com.othadd.ozi.data.database.getNoDialogDialogType
import com.othadd.ozi.data.database.getNotifyDialogType
import com.othadd.ozi.data.repos.MessageRepo
import com.othadd.ozi.data.repos.UtilRepo
import com.othadd.ozi.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

const val DUMMY_STRING = "dummy string"
const val CARRYING_GAME_MODERATOR_ID_CONTENT_DESC = "carrying game moderator id"


class GameManager(private val messageRepo: MessageRepo, private val utilRepo: UtilRepo) {

    private var _newGameRequest = MutableStateFlow<String?>(null)
    val newGameRequest get() = _newGameRequest.asStateFlow()

    private val thisUserId = utilRepo.thisUserId

    private var currentPromptType: String = DUMMY_STRING
    private var gameRequestSenderId: String = DUMMY_STRING


    private val timers: MutableList<OziCountDownTimer> = mutableListOf()

    private var conflictTimer = object : CountDownTimer(2000, 500) {
        override fun onTick(millisUntilFinished: Long) {}
        override fun onFinish() {
            runBlocking {
                Log.e(
                    "conflict Resolution",
                    "timer ended. waiting list size: ${waitingMessagesList.size}"
                )
                resolveConflictIfAny()
            }
        }
    }

    private val waitingMessagesList = mutableListOf<NWMessage>()

    suspend fun sendGameRequest(chatMateId: String) {
        val dialogState = getNotifyDialogType(
            utilRepo.getString(
                R.string.game_request_sending_game_request
            ), false
        )
        updateDialogState(chatMateId, dialogState)
        val message =
            Message(thisUserId, chatMateId, "", GAME_REQUEST_MESSAGE_TYPE, SERVER_SENDER_TYPE)

        // this delay is only for user experience(the busy animation maybe too brief). makes no difference functionally
        delay(500L)
        val success = messageRepo.sendGamingMessage(message)
        if (success){
            startTimer(chatMateId, TIMER_TO_RECEIVE_RESPONSE)
        }
        else{
            updateDialogState(chatMateId, getNoDialogDialogType())
            utilRepo.showToast("could not send game request")
        }
    }

    private fun findOrCreateTimer(userId: String): OziCountDownTimer {
        return timers.find { it.userId == userId } ?: OziCountDownTimer(userId, utilRepo.oziApp) { countDownTimer, chatMateId, timerType ->
            timers.remove(countDownTimer)
            if (currentPromptType == RESPOND_TO_GAME_REQUEST_PROMPT_TYPE) {
                respondToServerDeclineGameRequest(chatMateId)
                currentPromptType = DUMMY_STRING
            }

            // if this is a timer to receive a response, then a 'game declined' message has to be sent to self
            // in order to account for the 'permanent request pending' bug.
            // (bug is explained in the updateDialogState method in MessagingRepo)
            if (timerType == TIMER_TO_RECEIVE_RESPONSE) {
                selfDeclineGameRequest()
            }
        }
    }

    private suspend fun startTimer(userId: String, type: String) {
        val timer = findOrCreateTimer(userId)
        timer.start(type)
        timers.add(timer)
    }

    private fun stopTimer(userId: String) {
        val timer = findOrCreateTimer(userId)
        timer.stop()
        timers.remove(timer)
    }

    suspend fun handleMessage(messages: List<NWMessage>) {
        for (message in messages) {
            var messagePackage: MessagePackage? = null
            var sendMessage = true

            var containsRoundSummary = false
            var containsGameModeratorId = false
            var containsInstructions = false

            try {
                messagePackage = getPackageFromMessage(message.toMessage())
            } catch (_: Exception) {  }

            if (messagePackage != null) {
                if (messagePackage.gameModeratorId != NOT_INITIALIZED) {
                    containsGameModeratorId = true
                    utilRepo.updateGameModeratorIdAndKeyboardMode(messagePackage.gameModeratorId)
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
                        conflictTimer.start()
                        Log.e("conflict Resolution", "conflict timer restarted")
                    }
                }

                if (messagePackage.instructions != NOT_INITIALIZED) {
                    containsInstructions = true
                    val instructions = stringToInstructions(messagePackage.instructions)
                    for (instruction in instructions) {
                        when (instruction) {
                            DISABLE_GAME_MODE_KEYBOARD_INSTRUCTION -> utilRepo.updateKeyboardMode(false)
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
                            stopTimer(message.senderId)
                            val dialogState = getNotifyDialogType(
                                utilRepo.getStringWithFormatting(
                                    R.string.game_request_response_user_already_playing,
                                    getUsername(message.senderId)
                                ), true
                            )
                            updateDialogState(message.senderId, dialogState)
                        }

                        USER_HAS_PENDING_REQUEST_MESSAGE_BODY -> {
                            stopTimer(message.senderId)
                            val dialogState = getNotifyDialogType(
                                utilRepo.getStringWithFormatting(
                                    R.string.game_request_response_user_has_pending,
                                    getUsername(message.senderId)
                                ),
                                true,
                            )
                            updateDialogState(message.senderId, dialogState)
                        }

                        GAME_REQUEST_DECLINED_MESSAGE_BODY -> {
                            stopTimer(message.senderId)
                            val dialogState = getNotifyDialogType(
                                utilRepo.getString(R.string.game_request_declined),
                                true
                            )
                            updateDialogState(message.senderId, dialogState)
                        }

                        GAME_REQUEST_ACCEPTED_MESSAGE_BODY -> {
                            stopTimer(message.senderId)
                            val dialogState = getNotifyDialogType(
                                utilRepo.getStringWithFormatting(
                                    R.string.game_request_accepted,
                                    getUsername(message.senderId)
                                ),
                                true
                            )
                            updateDialogState(message.senderId, dialogState)
                        }
                    }
                }

                GAME_REQUEST_MESSAGE_TYPE -> {
                    // a bug where sometimes game request message appears twice. with the following check, if the message is already saved no further action is taken
                    if (messageRepo.receivedMessageExists(message.toMessage())) { return }
                    messageRepo.saveIncomingMessage(message.toMessage())
                    _newGameRequest.value = message.senderId
                    currentPromptType = RESPOND_TO_GAME_REQUEST_PROMPT_TYPE

                    CoroutineScope(Dispatchers.Main).launch {
                        startTimer(message.senderId, TIMER_TO_RESPOND)
                    }

                    gameRequestSenderId = message.senderId
                    _newGameRequest.value = null
                }

                //there are messages sent to the game manager for handling that are none of the two categories above.
                //this is because of the newer 'game manager message type'
                //example of such a message type is message to update keyboard mode.
                // further explanation in the handleReceivedMessages() method of the messagingRepo
                else -> {
                    if (sendMessage) {
                        messageRepo.saveIncomingMessage(message.toMessage())
                    }
                }
            }
        }
    }

    private suspend fun resolveConflictIfAny() {
        if (waitingMessagesList.size < 2) {
            for (message in waitingMessagesList) {
                messageRepo.saveIncomingMessage(message.toMessage())
            }
            waitingMessagesList.clear()
            return
        }

        waitingMessagesList.sortBy { getRoundSummaryFromNWMessage(it).answerTime }
        val resolvedMessage1 = waitingMessagesList[0]
        val resolvedMessage1Package = getPackageFromMessage(resolvedMessage1.toMessage())
        val resolvedRoundSummary = getRoundSummaryFromNWMessage(resolvedMessage1)
        val roundSummaryToSendToServerStringX = roundSummaryToString(resolvedRoundSummary)

        val successful = messageRepo.resolveConflict(roundSummaryToSendToServerStringX)
        if (!successful){
            utilRepo.showToast("Game Manager error. Game may not proceed normally")
            Log.e("GameManager", "resolveConflictIfAny(). Error trying to resolve conflict")
        }

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

        messageRepo.saveIncomingMessage(resolvedMessage1.toMessage())
        messageRepo.saveIncomingMessage(resolvedMessage2.toMessage())

        waitingMessagesList.clear()
    }

    private fun getRoundSummaryFromNWMessage(nwMessage: NWMessage): RoundSummary {
        val messagePackage = stringToMessagePackage(nwMessage.messagePackage)
        return stringToRoundSummary(messagePackage.roundSummary)
    }

    private fun getUsername(userId: String): String {
        return utilRepo.getUsername(userId)
    }

    private suspend fun updateDialogState(
        chatMateId: String,
        dialogState: DialogState
    ) {
        utilRepo.updateDialogState(chatMateId, dialogState)
    }

    suspend fun acceptGameRequest(chatMateId: String) {
        updateDialogState(chatMateId, getNoDialogDialogType())
        stopTimer(chatMateId)
        val message = Message(
            thisUserId,
            chatMateId,
            GAME_REQUEST_ACCEPTED_MESSAGE_BODY,
            GAME_REQUEST_RESPONSE_MESSAGE_TYPE,
            SERVER_SENDER_TYPE
        )
        val success = messageRepo.sendGamingMessage(message)
        if (!success) {
            Log.e(
                "game manager",
                "acceptGameRequest() method. exception trying to accept game request"
            )
            utilRepo.showToast("game manager encountered exception trying to accept game request")
        }
    }

    suspend fun declineGameRequest(chatMateId: String) {
        updateDialogState(chatMateId, getNoDialogDialogType())
        stopTimer(chatMateId)
        val message = Message(
            thisUserId,
            chatMateId,
            GAME_REQUEST_DECLINED_MESSAGE_BODY,
            GAME_REQUEST_RESPONSE_MESSAGE_TYPE,
            SERVER_SENDER_TYPE
        )

        val success = messageRepo.sendGamingMessage(message)
        if (!success){
            Log.e("game manager", "declineGameRequest() method. exception trying to decline game request")
            utilRepo.showToast("game manager encountered exception trying to decline game request")
        }
    }

    private fun selfDeclineGameRequest() {
        runBlocking {
            val message = Message(
                thisUserId,
                thisUserId,
                GAME_REQUEST_DECLINED_MESSAGE_BODY,
                GAME_REQUEST_RESPONSE_MESSAGE_TYPE,
                SERVER_SENDER_TYPE
            )
            val success = messageRepo.sendGamingMessage(message)
            if (!success){
                Log.e("game manager", "selfDeclineGameRequest() method. exception trying to self-decline game request")
                utilRepo.showToast("game manager encountered exception trying to self-decline game request")
            }
        }
    }

    private fun respondToServerDeclineGameRequest(chatMateId: String) {
        runBlocking {
//            val thisUserId = settingsRepo.getUserId()
            val message = Message(
                thisUserId,
                chatMateId,
                GAME_REQUEST_DECLINED_MESSAGE_BODY,
                GAME_REQUEST_RESPONSE_MESSAGE_TYPE,
                SERVER_SENDER_TYPE
            )

            val success = messageRepo.sendGamingMessage(message)
            if (!success){
                Log.e("game manager", "respondToServerDeclineGameRequest() method. exception trying to decline game request with server")
                utilRepo.showToast("game manager encountered exception trying to decline game request with server")
            }
        }
    }

    private fun getPackageFromMessage(message: Message): MessagePackage {
        return stringToMessagePackage(message.messagePackage)
    }

}