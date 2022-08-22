package com.othadd.ozi.ui

import android.app.Application
import androidx.lifecycle.*
import com.othadd.ozi.CHAT_MESSAGE_TYPE
import com.othadd.ozi.Message
import com.othadd.ozi.MessagingRepo
import com.othadd.ozi.database.DBChat
import com.othadd.ozi.database.toUIChat
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.network.User
import com.othadd.ozi.utils.NO_USERNAME
import com.othadd.ozi.utils.SettingsRepo
import com.othadd.ozi.utils.showNetworkErrorToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

    var thisUserId: String = settingsRepo.getUserId()

    private var _chatSetFromActivityIntent = MutableLiveData<Boolean>()
    val chatSetFromActivityIntent: LiveData<Boolean> get() = _chatSetFromActivityIntent

    val chats: LiveData<List<UIChat>> = Transformations.map(messagingRepo.chats) { dbChat ->
        dbChat.toUIChat().sortedByDescending { it.lastMessageDateTime }
    }

    private var _chat: LiveData<DBChat> = MutableLiveData()
    val chat: LiveData<DBChat> get() = _chat

    val sendNewGameRequestWorkerStatus =
        Transformations.map(messagingRepo.sendNewGameRequestWorkerStatus) {
            if (it) {
                viewModelScope.launch { messagingRepo.startTimerToReceiveResponse() }
            }
        }

//    val messages: LiveData<List<UIMessage>> = Transformations.map(chat) { dbChat ->
//        dbChat.messages.sortedBy { it.dateTime }.map { it.toUIMessage(thisUserId) }
//    }

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

    val allMessagesSent = Transformations.map(messagingRepo.allMessagesSent) { workInfoList ->

        val value = !workInfoList.any {
            !it.state.isFinished
        }
        val finalValue = value && workInfoList.isNotEmpty()
        finalValue
    }

    private var _navigateFromChatsToChatFragment = MutableLiveData<Boolean>()
    val navigateFromChatsToChatFragment: LiveData<Boolean> get() = _navigateFromChatsToChatFragment

    private fun getChatMateId() = _chat.value?.chatMateId

    fun sendMessage(messageBody: String, senderId: String = thisUserId) {
        viewModelScope.launch {
            val message = Message(thisUserId, _chat.value?.chatMateId!!, CHAT_MESSAGE_TYPE, messageBody)
            val chat = messagingRepo.chatDao.getChatByChatmateId(_chat.value?.chatMateId!!).first()
            chat.addMessage(message)
            messagingRepo.chatDao.update(chat)
            messagingRepo.sendMessage(senderId, _chat.value!!, messageBody)
        }
    }

    fun refreshMessages(failToastMessage: String) {
        viewModelScope.launch {
            try {
                messagingRepo.getMessages(getApplication())
            } catch (e: Exception) {
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

    fun startChat(userId: String?) {
        viewModelScope.launch {
            val user = _users.value?.find { it.userId == userId }
            messagingRepo.startChat(user)
        }
    }

    fun setChat(chatMateUserId: String) {
        viewModelScope.launch {
//            messagingRepo.setChatX(chatMateUserId)
            _chat = messagingRepo.chatDao.getChatByChatmateId(chatMateUserId).asLiveData()
            messagingRepo.setChatX(chatMateUserId)
            _navigateFromChatsToChatFragment.value = true
        }

//        viewModelScope.launch {
////            messagingRepo.markAllMessagesAsRead()
//            _navigateFromChatsToChatFragment.value = true
//        }

    }

    fun getLatestUsers() {
        viewModelScope.launch {
            _users.value = messagingRepo.getUsers(thisUserId)
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
            } catch (e: Exception) {
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

    fun respondPositive() {
        viewModelScope.launch {
            messagingRepo.givePositiveResponse()
        }
    }

    fun respondNegative() {
        viewModelScope.launch {
            messagingRepo.giveNegativeResponse()
        }
    }

    fun notifyDialogOkayPressed() {
        viewModelScope.launch {
            messagingRepo.notifyDialogOkayPressed()
        }
    }

    fun setChatFromActivityIntent(messageSenderId: String) {
        viewModelScope.launch {
            messagingRepo.getMessages(getApplication())
            setChat(messageSenderId)
            _chatSetFromActivityIntent.value = true
        }
    }

    fun setValueForChatFromActivityIntent(newValue: Boolean) {
        _chatSetFromActivityIntent.value = newValue
    }

    fun markAllMessagesSent() {
        viewModelScope.launch {
            messagingRepo.markAllMessagesAsSent(getChatMateId()!!)
        }
    }

    fun markAllMessagesRead() {
        viewModelScope.launch {
            messagingRepo.markAllMessagesAsRead(getChatMateId()!!)
        }
    }

    fun updateNavigateFromChatsToChat(newValue: Boolean) {
        _navigateFromChatsToChatFragment.value = newValue
    }

    init {
        _usernameCheckStatus.value = USERNAME_CHECK_UNDONE
        _signUpConditionsMet.value = false
        _showConfirmGameRequestDialog.value = false
        _chatSetFromActivityIntent.value = false
//        refreshMessages("viewModel could not load messages on initialization")
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