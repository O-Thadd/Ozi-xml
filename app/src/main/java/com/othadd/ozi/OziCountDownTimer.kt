package com.othadd.ozi

import android.os.CountDownTimer
import com.othadd.ozi.database.DialogState
import com.othadd.ozi.database.getNotifyDialogType
import com.othadd.ozi.database.getPromptDialogType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

const val TIMER_TO_RECEIVE_RESPONSE = "timer to receive response"
const val TIMER_TO_RESPOND = "timer to respond"

const val TICK_COUNTDOWN_STAGE = "tick"
const val FINISH_COUNTDOWN_STAGE = "finish"

class OziCountDownTimer(val id: String, val application: OziApplication, val onCountDownFinish: (OziCountDownTimer) -> Unit) {

    private var timer: CountDownTimer? = null
    private lateinit var type: String

    private val chatDao = application.database.chatDao()
    private suspend fun getChat() = chatDao.getChatByChatmateId(id).first()
    private suspend fun getUsername() = getChat().chatMateUsername

    private suspend fun getDialog(countDownTime: Int, countDownStage: String): DialogState {

        val dialogState: DialogState = if (type == TIMER_TO_RECEIVE_RESPONSE){
            if (countDownStage == TICK_COUNTDOWN_STAGE){
                getNotifyDialogType(application.getString(R.string.game_request_response_countdown, getUsername(), countDownTime), false)
            } else {
                getNotifyDialogType(application.getString(R.string.game_request_no_response, getUsername()), true)
            }
        } else{
            if (countDownStage == TICK_COUNTDOWN_STAGE){
                getPromptDialogType(application.getString(R.string.new_game_request_countdown, getUsername(), countDownTime), "Accept!", "Decline")
            } else{
                getNotifyDialogType(application.getString(R.string.game_request_auto_declined), true)
            }
        }
        return dialogState
    }

    suspend fun start(type: String) {
        timer?.cancel()
        this.type = type
        val duration: Long = if (type == TIMER_TO_RECEIVE_RESPONSE) 10000 else 8000

        timer = object : CountDownTimer(duration, 1000) {

            override fun onTick(millisUntilFinished: Long) {
                runBlocking {
                    val countDownTime = (millisUntilFinished / 1000).toInt()
                    val dialogState = getDialog(countDownTime, TICK_COUNTDOWN_STAGE)
                    updateDialogState(dialogState)
                }
            }

            override fun onFinish() {
                runBlocking {
                    val dialogState = getDialog(0, FINISH_COUNTDOWN_STAGE)
                    updateDialogState(dialogState)
                    onCountDownFinish(this@OziCountDownTimer)
                }
            }
        }.start()
//        (timer as CountDownTimer).start()
    }

    fun stop(){
        timer?.cancel()
    }

    private suspend fun updateDialogState(dialogState: DialogState) {
        val chat = getChat()
        chat.dialogState = dialogState
        chatDao.update(chat)
    }

}