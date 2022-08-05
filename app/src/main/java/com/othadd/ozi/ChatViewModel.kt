package com.othadd.ozi

import android.app.Application
import androidx.lifecycle.*
import com.othadd.ozi.database.DBChat
import com.othadd.ozi.database.toUIChat
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.network.User
import com.othadd.ozi.utils.showNetworkErrorToast
import kotlinx.coroutines.launch

const val USERNAME_CHECK_UNDONE = 0
const val USERNAME_CHECK_CHECKING = 1
const val USERNAME_CHECK_PASSED = 2
const val USERNAME_CHECK_FAILED = 3


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

    private var _showConfirmGameRequestDialog = MutableLiveData<Boolean>()
    val showConfirmGameRequestDialog: LiveData<Boolean> get() = _showConfirmGameRequestDialog


    fun sendMessage(message: String, senderId: String = userId) {
        viewModelScope.launch {
            messagingRepo.createAndScheduleMessage(senderId, message)
        }
    }

    fun refreshMessages(failToastMessage: String){
        viewModelScope.launch {
            try{
                messagingRepo.getMessages(getApplication())
            }
            catch (e: Exception){
                showNetworkErrorToast(getApplication(), failToastMessage)
            }
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
            try {
                _usernameCheckStatus.value = USERNAME_CHECK_CHECKING
                updateSignUpConditionsStatus()
                val usernameCheckPassed = NetworkApi.retrofitService.checkUsername(username)
                if (usernameCheckPassed) {
                    _usernameCheckStatus.value = USERNAME_CHECK_PASSED
                } else {
                    _usernameCheckStatus.value = USERNAME_CHECK_FAILED
                }
                updateSignUpConditionsStatus()
            }
            catch (e: Exception){
                showNetworkErrorToast(getApplication())
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
            messagingRepo.sendGameRequest()
            _showConfirmGameRequestDialog.value = false
        }
    }

    fun cancelSendGameRequest() {
        _showConfirmGameRequestDialog.value = false
    }

    fun respondPositive(){
        viewModelScope.launch {
            messagingRepo.givePositiveResponse()
        }
    }

    fun respondNegative(){
        viewModelScope.launch {
            messagingRepo.giveNegativeResponse()
        }
    }

    fun notifyDialogOkayPressed(){
        viewModelScope.launch {
            messagingRepo.notifyDialogOkayPressed()
        }
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