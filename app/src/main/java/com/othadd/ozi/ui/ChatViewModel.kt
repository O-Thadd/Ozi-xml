package com.othadd.ozi

import android.app.Application
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.*
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessaging
import com.othadd.ozi.database.DBChat
import com.othadd.ozi.database.getNoDialogDialogType
import com.othadd.ozi.database.toUIChat
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.network.User
import com.othadd.ozi.ui.SnackBarState
import com.othadd.ozi.ui.getNoSnackBarSnackBar
import com.othadd.ozi.ui.getNotifySnackBar
import com.othadd.ozi.utils.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val DEFAULT = 0
const val BUSY = 1
const val PASSED = 2
const val FAILED = 3


class ChatViewModel(
    application: Application
) :
    AndroidViewModel(application) {

    private val settingsRepo = SettingsRepo(getApplication())
    var thisUserId: String = settingsRepo.getUserId()
    private fun getChatDao() = getApplication<OziApplication>().database.chatDao()

    val chats: LiveData<List<UIChat>> =
        Transformations.map(getChatDao().getChats().asLiveData()) { listOfDBChats ->
            listOfDBChats.sortedByDescending { it.lastMessage()?.dateTime }.toUIChat()
        }

    private var _chat = MutableLiveData<DBChat>()
    val chat: LiveData<DBChat> get() = _chat

    val userIsRegistered = Transformations.map(settingsRepo.username().asLiveData()) {
        it != NO_USERNAME
    }

    private var _registrationStatus = MutableLiveData<Int>()
    val registrationStatus: LiveData<Int> get() = _registrationStatus

    private var _usersFetchStatus = MutableLiveData<Int>()
    val usersFetchStatus: LiveData<Int> get() = _usersFetchStatus

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

    private var _navigateToChatFragment = MutableLiveData<Boolean>()
    val navigateToChatFragment: LiveData<Boolean> get() = _navigateToChatFragment

    private var _navigateToChatsFragment = MutableLiveData<Boolean>()
    val navigateToChatsFragment: LiveData<Boolean> get() = _navigateToChatsFragment

    var scrollToBottomOfChat = true

    var allMessagesSentForChat: LiveData<Boolean> =
        Transformations
            .map(
                WorkManager.getInstance(getApplication()).getWorkInfosByTagLiveData(thisUserId)
            ) { workInfoList ->
                workInfoList.all { it.state == WorkInfo.State.SUCCEEDED }
            }

    var chatStartedByActivity = false

    val snackBarState: LiveData<SnackBarState> =
        Transformations.map(settingsRepo.snackBarStateFlow().asLiveData()) {

//            snackBarHideAnimationDistanceOffset = if (it.showActionButton) 0f else 35f

//        this checks if the snackBar is a noSnackBar snackBar. done by simply checking if the message is an empty string.
//        a better implementation would be to include a field that indicates snackBar type in the snackBar class, and then check with that field.
            if (it.message != "") {
                snackBarTimer.cancel()
                snackBarTimer.start()
            }

            it
        }

    private val snackBarTimer = object : CountDownTimer(7000, 1000) {
        override fun onTick(millisUntilFinished: Long) {}
        override fun onFinish() {
            closeSnackBar()
        }
    }

//    var snackBarHideAnimationDistanceOffset = 30f


    fun sendMessage(messageBody: String, receiverId: String, senderId: String = thisUserId) {
        viewModelScope.launch {
            MessagingRepoX.sendMessage(
                senderId,
                receiverId,
                messageBody,
                getApplication(),
                thisUserId
            )
        }
    }

    fun refreshMessages(failToastMessage: String) {
        viewModelScope.launch {
            try {
                MessagingRepoX.refreshMessages(getApplication())
            } catch (e: Exception) {
                showNetworkErrorToast(getApplication(), failToastMessage)
                Log.e("viewModelRefreshMessages", e.toString())
            }
        }
    }

    fun registerUser(username: String, gender: String) {
        _registrationStatus.value = BUSY
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->

            Handler(Looper.getMainLooper()).postDelayed({
                viewModelScope.launch {
                    if (task.isSuccessful) {

                        // Get new FCM registration token
                        val token = task.result

                        // do something with token
                        try {
                            MessagingRepoX.registerUser(thisUserId, username, gender, token)
                            _registrationStatus.value = PASSED
                            settingsRepo.storeUsername(username)
                        } catch (e: Exception) {
                            _registrationStatus.value = FAILED
                            Log.e("registration error", e.toString())
                            showNetworkErrorToast(getApplication(), "encountered error trying to register")
                        }
                    } else {
                        _registrationStatus.value = FAILED
                    }
                }
            }, 500)
        }
    }

    fun startChat(userId: String) {
        viewModelScope.launch {
            closeSnackBar()

            val chats = getChatDao().getChats().first()
            var chat: DBChat?
            chat = chats.find { it.chatMateId == userId }
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
                getChatDao().insert(chat)
            }
            _chat = getChatDao().getChatByChatmateId(userId).asLiveData() as MutableLiveData<DBChat>
            MessagingRepoX.setChat(chat)

            _navigateToChatFragment.value = true
        }
    }

    fun getLatestUsers() {
        viewModelScope.launch {
            try {
                _usersFetchStatus.value = BUSY
                val users = NetworkApi.retrofitService.getUsers(thisUserId)
                _users.value = users
                _usersFetchStatus.value = PASSED
            } catch (e: Exception) {
                _usersFetchStatus.value = FAILED
                showNetworkErrorToast(getApplication(), "Could not fetch users")
                _usersFetchStatus.value = DEFAULT
            }
        }
    }

    fun checkUsername(username: String) {
        viewModelScope.launch {
            try {
                _usernameCheckStatus.value = BUSY
                updateSignUpConditionsStatus()
                val usernameCheckPassed = NetworkApi.retrofitService.checkUsername(username)
                if (usernameCheckPassed) {
                    _usernameCheckStatus.value = PASSED
                } else {
                    _usernameCheckStatus.value = FAILED
                    showNetworkErrorToast(
                        getApplication(),
                        "try a different username.\n '$username' has been taken."
                    )
                }
                updateSignUpConditionsStatus()
            } catch (e: Exception) {
                showNetworkErrorToast(getApplication(), "username verification failed!")
                _usernameCheckStatus.value = DEFAULT
            }

        }
    }

    fun resetUsernameCheck() {
        _usernameCheckStatus.value = DEFAULT
    }

    fun updateSignUpConditionsStatus() {
        _signUpConditionsMet.value =
            genderHasBeenSelected && usernameCheckStatus.value == PASSED
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
        _navigateToChatFragment.value = false
    }

    fun updateNavigateChatsToChatFragment(newValue: Boolean) {
        _navigateToChatFragment.value = newValue
    }

    fun markMessagesSent() {
        viewModelScope.launch {
            try {
                val chat = getChatDao().getChatByChatmateId(_chat.value?.chatMateId!!).first()
                chat.markAllMessagesSent()
                getChatDao().update(chat)
            } catch (e: NullPointerException) {
                Log.e("viewModel", "null pointer exception trying to mark all messages sent")
            }
        }
    }

    fun markMessagesRead() {
        viewModelScope.launch {
            try {
                val chat = getChatDao().getChatByChatmateId(_chat.value?.chatMateId!!).first()
                chat.markMessagesRead()
                getChatDao().update(chat)
            } catch (e: NullPointerException) {
                Log.e("viewModel", "null pointer exception trying to mark messages read")
            }
        }
    }

    fun startChatFromActivity(senderId: String) {
        chatStartedByActivity = true
        startChat(senderId)
    }

    fun resetChatStartedByActivity() {
        chatStartedByActivity = false
    }

    fun resetSignUpConditionsMet() {
        _signUpConditionsMet.value = false
    }

    fun closeSnackBar() {
        settingsRepo.updateSnackBarState(getNoSnackBarSnackBar())
    }

    fun goToGameRequestSenderChat() {
        startChat(MessagingRepoX.getGameRequestSenderId())
    }

    fun snackBarNavigateToChatFromChatFragment() {
        _navigateToChatsFragment.value = true
        startChat(getGameRequestSenderId())
    }

    fun getGameRequestSenderId(): String {
        return MessagingRepoX.getGameRequestSenderId()
    }


    init {
        _usernameCheckStatus.value = DEFAULT
        _signUpConditionsMet.value = false
        _showConfirmGameRequestDialog.value = false
        _navigateToChatFragment.value = false
        _navigateToChatsFragment.value = false
        _registrationStatus.value = DEFAULT
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