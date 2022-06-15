package com.othadd.ozi

import android.app.Application
import android.os.CountDownTimer
import androidx.lifecycle.*
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.othadd.ozi.database.DBChat
import com.othadd.ozi.database.toUIChat
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.network.User
import com.othadd.ozi.utils.WORKER_MESSAGE_KEY
import com.othadd.ozi.utils.messageToString
import com.othadd.ozi.workers.SendMessageWorker
import kotlinx.coroutines.launch
import java.util.*

const val USERNAME_CHECK_UNDONE = 0
const val USERNAME_CHECK_CHECKING = 1
const val USERNAME_CHECK_PASSED = 2
const val USERNAME_CHECK_FAILED = 3

const val DUMMY_NUMBER = 0

class ChatViewModel(
    private val settingsRepo: SettingsRepo,
    private val messagingRepo: MessagingRepo,
    application: Application
) :
    AndroidViewModel(application) {

    private var userId: String = settingsRepo.getUserId()

    val chats: LiveData<List<UIChat>> = Transformations.map(messagingRepo.chats) {
        it.toUIChat()
    }

    val chat: LiveData<DBChat> = messagingRepo.chat
    val chatMateUsername get() = chat.value?.chatMateUsername

    val messages: LiveData<List<UIMessage>> = Transformations.map(messagingRepo.chat) { dbChat ->
        dbChat.messages.map { it.toUIMessage(userId) }
    }


    val userIsRegistered = Transformations.map(settingsRepo.username().asLiveData()) {
        it != NO_USERNAME
    }

    private var _isRegistering = MutableLiveData<Boolean>()
    val isRegistering: LiveData<Boolean> get() = _isRegistering

    private var _users = MutableLiveData<List<User>>()
    val users: LiveData<List<User>> get() = _users

    private var _usernameCheckStatus = MutableLiveData<Int>()
    val usernameCheckStatus: LiveData<Int> get() = _usernameCheckStatus

    var genderSelectionPopupIsShowing = false

    var genderHasBeenSelected = false

    private var _signUpConditionsMet = MutableLiveData<Boolean>()
    val signUpConditionsMet: LiveData<Boolean> get() = _signUpConditionsMet

    var hasAttemptedRegistration: Boolean = false

    private var _showConfirmGameRequestDialog = MutableLiveData<Boolean>()
    val showConfirmGameRequestDialog: LiveData<Boolean> get() = _showConfirmGameRequestDialog

    private var _countdownMessage = MutableLiveData<String>()
    val countdownMessage: LiveData<String> get() = _countdownMessage

    private var _showCountDownDialog = MutableLiveData<Boolean>()
    val showCountDownDialog: LiveData<Boolean> get() = _showCountDownDialog

    private var _showCountDownEndedDialog = MutableLiveData<Boolean>()
    val showCountDownEndedDialog: LiveData<Boolean> get() = _showCountDownEndedDialog


    fun sendMessage(message: String, senderId: String = userId) {
        viewModelScope.launch {
            messagingRepo.createAndScheduleMessage(senderId, message)
        }
    }

    fun registerUser(username: String, gender: String) {
        viewModelScope.launch {
            _isRegistering.value = true
            messagingRepo.registerUser(settingsRepo, username, gender)
            _isRegistering.value = false
        }
    }

    fun startChat(username: String?) {
        viewModelScope.launch {
            val user = _users.value?.find { it.username == username }
            messagingRepo.startChat(user)
        }
    }

    fun setChat(chatMateUsername: String) {
        viewModelScope.launch {
            messagingRepo.setChat(chatMateUsername)
        }
    }

    fun getLatestUsers() {
        viewModelScope.launch {
            _users.value = messagingRepo.getUsers()
        }
    }

    fun checkUsername(username: String) {
        viewModelScope.launch {
            _usernameCheckStatus.value = USERNAME_CHECK_CHECKING
            updateSignUpConditionsStatus()
            val usernameCheckPassed = NetworkApi.retrofitService.checkUsername(username)
            if (usernameCheckPassed) {
                _usernameCheckStatus.value = USERNAME_CHECK_PASSED
                updateSignUpConditionsStatus()
            } else {
                _usernameCheckStatus.value = USERNAME_CHECK_FAILED
                updateSignUpConditionsStatus()
            }
        }
    }

    fun resetUsernameCheck() {
        _usernameCheckStatus.value = USERNAME_CHECK_UNDONE
    }

    fun updateSignUpConditionsStatus() {
        _signUpConditionsMet.value =
            genderHasBeenSelected && usernameCheckStatus.value == USERNAME_CHECK_PASSED
    }

    fun confirmSendGameRequest() {
        _showConfirmGameRequestDialog.value = true
    }

    fun sendGameRequest() {
        viewModelScope.launch {
            _showConfirmGameRequestDialog.value = false
            messagingRepo.sendGameRequest(userId)
            timer.start()
            _showCountDownDialog.value = true
        }
    }

    fun countDownEnded() {
        _showCountDownDialog.value = false
        _countdownMessage.value = getApplication<OziApplication>().getString(
            R.string.game_request_no_response,
            chatMateUsername
        )
        _showCountDownEndedDialog.value = true
    }

    fun cancelSendGameRequest() {
        _showConfirmGameRequestDialog.value = false
    }

    fun okayAfterCountdownEnded() {
        _showCountDownEndedDialog.value = false
    }

    private val timer: CountDownTimer = object : CountDownTimer(10000, 1000) {

        override fun onTick(millisUntilFinished: Long) {
            val countDownTime = (millisUntilFinished / 1000).toInt()
            _countdownMessage.value = getApplication<OziApplication>().getString(
                R.string.game_request_response_countdown,
                chatMateUsername,
                countDownTime
            )
        }

        override fun onFinish() {
            countDownEnded()
        }
    }


    //    dummy liveData. is observed in fragment. just to get when code block to run
    val dummyLiveData: LiveData<Int> = Transformations.map(messagingRepo.gamingStatus) {
        when (it) {
            STATUS_USER_ALREADY_PLAYING -> {
                _showCountDownDialog.value = false
            }
        }
        return@map DUMMY_NUMBER
    }


    init {
        _usernameCheckStatus.value = USERNAME_CHECK_UNDONE
        _signUpConditionsMet.value = false
        _showConfirmGameRequestDialog.value = false

    }

}

class ChatViewModelFactory(
    private val settingsRepo: SettingsRepo,
    private val messagingRepo: MessagingRepo,
    private val application: Application
) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(settingsRepo, messagingRepo, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }

}