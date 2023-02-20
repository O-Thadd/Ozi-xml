package com.othadd.ozi.gaming

import android.os.CountDownTimer
import android.util.Log
import com.othadd.ozi.*
import com.othadd.ozi.database.*
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.ui.getPromptSnackBar
import com.othadd.ozi.utils.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

const val RESPOND_TO_GAME_REQUEST_PROMPT_TYPE = "respond to game request"
const val DUMMY_STRING = "dummy string"
const val CARRYING_GAME_MODERATOR_ID_CONTENT_DESC = "carrying game moderator id"


// TODO: remove oziApp as constructor parameter after refactoring is complete
class GameManager(private val oziApp: OziApplication, private val messagingRepo: MessagingRepoX) {

    companion object{
        private var INSTANCE: GameManager? = null

        fun getInstance(oziApp: OziApplication, messagingRepo: MessagingRepoX): GameManager{
            if(INSTANCE != null){
                return INSTANCE as GameManager
            }

            INSTANCE = GameManager(oziApp, messagingRepo)
            return INSTANCE as GameManager
        }
    }

    private val chatMateId get() = messagingRepo.chatMateId.value
    private val thisUserId get() = messagingRepo.thisUserId



    private var currentChatChat: DBChat? = null
    private var currentPromptType: String = DUMMY_STRING
    private var gameRequestSenderId: String = DUMMY_STRING
    private var gameModeratorId: String = DUMMY_STRING

    private val chatDao: ChatDao = oziApp.database.chatDao()
    private val settingsRepo: SettingsRepo = SettingsRepo(oziApp)
//    private val messagingRepo = messagingRepoX

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

    suspend fun sendGameRequest() {
        val dialogState = getNotifyDialogType(
            oziApp.getString(
                R.string.game_request_sending_game_request
            ), false
        )
        updateDialogState(chatMateId, dialogState)
        val message =
            Message(thisUserId, chatMateId, "", GAME_REQUEST_MESSAGE_TYPE, SERVER_SENDER_TYPE)

        // this delay is only for user experience(the busy animation maybe too brief). makes no difference functionally
        delay(500L)
        val success = messagingRepo.sendGamingMessage(message)
        if (success){
            startTimer(chatMateId, TIMER_TO_RECEIVE_RESPONSE)
        }
        else{
            updateDialogState(chatMateId, getNoDialogDialogType())
            showNetworkErrorToast(oziApp, "could not send game request")
        }
    }

    private fun findOrCreateTimer(userId: String): OziCountDownTimer {
        var timer = timers.find { it.userId == userId }
        if (timer == null){
            timer = OziCountDownTimer(userId, oziApp) { countDownTimer, chatMateId, timerType ->
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

        return timer


//        return timers.find { it.userId == userId } ?: OziCountDownTimer(userId, oziApp) { countDownTimer, chatMateId, timerType ->
//            timers.remove(countDownTimer)
//            if (currentPromptType == RESPOND_TO_GAME_REQUEST_PROMPT_TYPE) {
//                respondToServerDeclineGameRequest(chatMateId)
//                currentPromptType = DUMMY_STRING
//            }
//
//            // if this is a timer to receive a response, then a 'game declined' message has to be sent to self
//            // in order to account for the 'permanent request pending' bug.
//            // (bug is explained in the updateDialogState method in MessagingRepo)
//            if (timerType == TIMER_TO_RECEIVE_RESPONSE) {
//                selfDeclineGameRequest()
//            }
//        }
    }

    private suspend fun startTimer(userId: String, type: String) {
        val timer = findOrCreateTimer(userId)
        timer.start(type)
        timers.add(timer)
        1+1
    }

    private fun stopTimer(userId: String) {
        val timer = findOrCreateTimer(userId)
        timer.stop()
        timers.remove(timer)
    }

    suspend fun startGameRequestSenderChat(){
        messagingRepo.startChat(gameRequestSenderId)
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
                    messagingRepo.updateGameModeratorIdAndKeyboardMode(messagePackage.gameModeratorId)
//                    settingsRepo.apply {
//                        updateGameModeratorId(messagePackage.gameModeratorId)
//                        updateKeyboardMode(true)
//                    }
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
                            DISABLE_GAME_MODE_KEYBOARD_INSTRUCTION -> settingsRepo.updateKeyboardMode(
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
                            stopTimer(message.senderId)
                            val dialogState = getNotifyDialogType(
                                oziApp.getString(
                                    R.string.game_request_response_user_already_playing,
                                    getUsername(message.senderId)
                                ), true
                            )
                            updateDialogState(message.senderId, dialogState)
                        }

                        USER_HAS_PENDING_REQUEST_MESSAGE_BODY -> {
                            stopTimer(message.senderId)
                            val dialogState = getNotifyDialogType(
                                oziApp.getString(
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
                                oziApp.getString(R.string.game_request_declined),
                                true
                            )
                            updateDialogState(message.senderId, dialogState)
                        }

                        GAME_REQUEST_ACCEPTED_MESSAGE_BODY -> {
                            stopTimer(message.senderId)
                            val dialogState = getNotifyDialogType(
                                oziApp.getString(
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
                    val chat = chatDao.getChatByChatmateIdFlow(message.senderId).first()
                    if (!chat.messages.any { it.id == message.id }) {
                        messagingRepo.saveIncomingMessage(message.toMessage())
                        currentPromptType = RESPOND_TO_GAME_REQUEST_PROMPT_TYPE
                        startTimer(message.senderId, TIMER_TO_RESPOND)
                        gameRequestSenderId = message.senderId
//                        updateDialogState(message.senderId, getPromptDialogType())
                        settingsRepo.updateSnackBarState(
                            getPromptSnackBar(
                                "${getUsername(message.senderId)} has challenged you!",
                                "Go to chat"
                            )
                        )
                    }
                }

                //there are messages sent to the game manager for handling that are none of the two categories above.
                //this is because of the newer 'game manager message type'
                //example of such a message type is message to update keyboard mode.
                // further explanation in the handleReceivedMessages() method of the messagingRepo
                else -> {
                    if (sendMessage) {
                        messagingRepo.saveIncomingMessage(message.toMessage())
                    }
                }
            }
        }

    }

    private suspend fun resolveConflictIfAny() {
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
                messagingRepo.saveIncomingMessage(message.toMessage())
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

        // TODO: implement try-catch
        NetworkApi.retrofitService.resolveGameConflict(
            settingsRepo.getGameModeratorId(),
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

        messagingRepo.saveIncomingMessage(resolvedMessage1.toMessage())
        messagingRepo.saveIncomingMessage(resolvedMessage2.toMessage())

        waitingMessagesList.clear()
    }

    private fun getRoundSummaryFromNWMessage(nwMessage: NWMessage): RoundSummary {
        val messagePackage = stringToMessagePackage(nwMessage.messagePackage)
        return stringToRoundSummary(messagePackage.roundSummary)
    }

    private fun getUsername(userId: String): String {
        return messagingRepo.getUsername(userId)
    }

    private suspend fun updateDialogState(
        chatMateId: String,
        dialogState: DialogState
    ) {
        messagingRepo.updateDialogState(chatMateId, dialogState)
    }

    suspend fun notifyDialogOkayPressed() {
//        currentChatChat?.let {
//            updateDialogState(
//                it.chatMateId,
//                getNoDialogDialogType()
//            )
//        }

        updateDialogState(chatMateId, getNoDialogDialogType())
    }

    suspend fun givePositiveResponse() {
        acceptGameRequest()
    }

    private suspend fun acceptGameRequest() {
        updateDialogState(chatMateId, getNoDialogDialogType())
//        val thisUserId = settingsRepo.getUserId()
//        val chatMateId = currentChatChat!!.chatMateId
        stopTimer(chatMateId)
        val message = Message(
            thisUserId,
            chatMateId,
            GAME_REQUEST_ACCEPTED_MESSAGE_BODY,
            GAME_REQUEST_RESPONSE_MESSAGE_TYPE,
            SERVER_SENDER_TYPE
        )
//        try {
            val success = messagingRepo.sendGamingMessage(message)
//            NetworkApi.retrofitService.sendMessage(message.toNWMessage())
//        } catch (e: Exception) {
        if (!success){
            Log.e("game manager", "acceptGameRequest() method. exception trying to accept game request")
            showToast(
                oziApp,
                "game manager encountered exception trying to accept game request"
            )
        }

//        }
    }

    suspend fun giveNegativeResponse() {
        declineGameRequest()
    }

    private suspend fun declineGameRequest() {
        updateDialogState(chatMateId, getNoDialogDialogType())
//        val thisUserId = settingsRepo.getUserId()
//        val chatMateId = currentChatChat!!.chatMateId
        stopTimer(chatMateId)
        val message = Message(
            thisUserId,
            chatMateId,
            GAME_REQUEST_DECLINED_MESSAGE_BODY,
            GAME_REQUEST_RESPONSE_MESSAGE_TYPE,
            SERVER_SENDER_TYPE
        )

//        try {
//            NetworkApi.retrofitService.sendMessage(message.toNWMessage())
//        } catch (e: Exception) {
//            Log.e("game manager", "exception trying to decline game request. $e")
//            showNetworkErrorToast(oziApp, "Error tyring to decline game request")
//        }

        val success = messagingRepo.sendGamingMessage(message)
        if (!success){
            Log.e("game manager", "declineGameRequest() method. exception trying to decline game request")
            showToast(
                oziApp,
                "game manager encountered exception trying to decline game request"
            )
        }
    }

    private fun selfDeclineGameRequest() {
        runBlocking {
//            val thisUserId = settingsRepo.getUserId()
            val message = Message(
                thisUserId,
                thisUserId,
                GAME_REQUEST_DECLINED_MESSAGE_BODY,
                GAME_REQUEST_RESPONSE_MESSAGE_TYPE,
                SERVER_SENDER_TYPE
            )

//            try {
//                NetworkApi.retrofitService.sendMessage(message.toNWMessage())
//            } catch (e: Exception) {
//                Log.e("game manager", "exception trying to self-decline game request. $e")
//                showNetworkErrorToast(oziApp, "Error tyring to self-decline game request")
//            }

            val success = messagingRepo.sendGamingMessage(message)
            if (!success){
                Log.e("game manager", "selfDeclineGameRequest() method. exception trying to self-decline game request")
                showToast(
                    oziApp,
                    "game manager encountered exception trying to self-decline game request"
                )
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

//            try {
//                NetworkApi.retrofitService.sendMessage(message.toNWMessage())
//            } catch (e: Exception) {
//                Log.e("game manager", "exception trying to decline game request. $e")
//                showNetworkErrorToast(oziApp, "Error trying to decline game request")
//            }

            val success = messagingRepo.sendGamingMessage(message)
            if (!success){
                Log.e("game manager", "respondToServerDeclineGameRequest() method. exception trying to decline game request with server")
                showToast(
                    oziApp,
                    "game manager encountered exception trying to decline game request with server"
                )
            }
        }
    }

    fun getGameRequestSenderId(): String {
        return gameRequestSenderId
    }

    fun getGameModeratorId(): String {
//        return settingsRepo.getGameModeratorId()
        return messagingRepo.getGameModeratorId()
    }

    private fun getPackageFromMessage(message: Message): MessagePackage {
        return stringToMessagePackage(message.messagePackage)
    }

}