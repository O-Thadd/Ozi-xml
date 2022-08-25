package com.othadd.ozi

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.othadd.ozi.database.DBChat
import com.othadd.ozi.database.getNoDialogDialogType
import com.othadd.ozi.database.toUIChat
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.network.User
import com.othadd.ozi.utils.showNetworkErrorToast
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val USERNAME_CHECK_UNDONE = 0
const val USERNAME_CHECK_CHECKING = 1
const val USERNAME_CHECK_PASSED = 2
const val USERNAME_CHECK_FAILED = 3


class ChatViewModel(
    application: Application
) :
    AndroidViewModel(application) {

    private val settingsRepo = SettingsRepo(getApplication())
    var thisUserId: String = settingsRepo.getUserId()
    private fun getChatDao() = getApplication<OziApplication>().database.chatDao()

//    val chats: LiveData<List<UIChat>> = Transformations.map(messagingRepo.chats) {
//        it.toUIChat()
//    }

//    private var _chats = getChatDao().getChats().asLiveData() as MutableLiveData
    val chats: LiveData<List<UIChat>> = Transformations.map(getChatDao().getChats().asLiveData()) {listOfDBChats ->
        listOfDBChats.sortedByDescending { it.lastMessage()?.dateTime }.toUIChat()
    }

//    val chat: LiveData<DBChat> = messagingRepo.chat

    private var _chat = MutableLiveData<DBChat>()
    val chat: LiveData<DBChat> get() = _chat

//    val messages: LiveData<List<UIMessage>> = Transformations.map(messagingRepo.chat) { dbChat ->
//        dbChat.messages.map { it.toUIMessage(userId) }
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

    private var _navigateChatFragment = MutableLiveData<Boolean>()
    val navigateToChatFragment: LiveData<Boolean> get() = _navigateChatFragment

    var scrollToBottomOfChat = true


    fun sendMessage(messageBody: String, receiverId: String, senderId: String = thisUserId) {
        viewModelScope.launch {
            MessagingRepoX.sendMessage(senderId, receiverId, messageBody, getApplication())
        }
    }

    fun refreshMessages(failToastMessage: String) {
        viewModelScope.launch {
            try {
                MessagingRepoX.refreshMessages(getApplication())
            } catch (e: Exception) {
                showNetworkErrorToast(getApplication(), "$failToastMessage $e")
                Log.e("viewModelRefreshMessages", e.toString())
            }
        }
    }

    fun registerUser(username: String, gender: String) {
        viewModelScope.launch {
            _isRegistering.value = true
            MessagingRepoX.registerUser(thisUserId, username, gender)
            settingsRepo.storeUsername(username)
            _isRegistering.value = false
        }
    }

    fun startChat(userId: String) {
        viewModelScope.launch {
            val chats = getChatDao().getChats().first()
            var chat: DBChat?
            chat = chats.find { it.chatMateId == userId }
            if (chat == null){
                val user = NetworkApi.retrofitService.getUser(userId)
                chat = DBChat(chatMateId = userId, messages = mutableListOf(), chatMateUsername = user.username, chatMateGender = user.gender, dialogState = getNoDialogDialogType())
                getChatDao().insert(chat)
            }
            _chat = getChatDao().getChatByChatmateId(userId).asLiveData() as MutableLiveData<DBChat>
            MessagingRepoX.setChat(chat)
            _navigateChatFragment.value = true
        }
    }

    fun getLatestUsers() {
        viewModelScope.launch {
            val users = NetworkApi.retrofitService.getUsers(thisUserId)
            _users.value = users
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
            MessagingRepoX.sendGameRequest(getApplication())
            _showConfirmGameRequestDialog.value = false
        }
    }

    fun cancelSendGameRequest() {
        _showConfirmGameRequestDialog.value = false
    }

    fun respondPositive() {
        viewModelScope.launch {
            MessagingRepoX.givePositiveResponse(getApplication())
        }
    }

    fun respondNegative() {
        viewModelScope.launch {
            MessagingRepoX.giveNegativeResponse(getApplication())
        }
    }

    fun notifyDialogOkayPressed() {
        viewModelScope.launch {
            MessagingRepoX.notifyDialogOkayPressed(getApplication())
        }
    }

    fun resetNavigateChatsToChatFragment() {
        _navigateChatFragment.value = false
    }

    init {
        _usernameCheckStatus.value = USERNAME_CHECK_UNDONE
        _signUpConditionsMet.value = false
        _showConfirmGameRequestDialog.value = false
        _navigateChatFragment.value = false
    }

}

class ChatViewModelFactory(
    private val application: Application
) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }

}