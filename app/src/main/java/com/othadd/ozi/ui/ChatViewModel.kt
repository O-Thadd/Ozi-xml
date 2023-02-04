package com.othadd.ozi.ui

import android.app.Application
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.*
import com.google.firebase.messaging.FirebaseMessaging
import com.othadd.ozi.MessagingRepoX
import com.othadd.ozi.OziApplication
import com.othadd.ozi.database.ChatDao
import com.othadd.ozi.database.DBChat
import com.othadd.ozi.database.getNoDialogDialogType
import com.othadd.ozi.database.toUIChat
import com.othadd.ozi.network.CURRENTLY_PLAYING_GAME_STATE
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.network.User
import com.othadd.ozi.utils.*
import dagger.hilt.android.internal.Contexts.getApplication
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

const val DEFAULT = 0
const val BUSY = 1
const val PASSED = 2
const val FAILED = 3


@HiltViewModel
open class ChatViewModel @Inject constructor(
    application: OziApplication
) : ViewModel() {

    private val chatDao = application.database.chatDao()
    private val settingsRepo = SettingsRepo(application)
    private val messagingRepoX = MessagingRepoX(application)

    val thisUserId: String = settingsRepo.getUserId()

    val chats: LiveData<List<UIChat>> =
        Transformations.map(chatDao.getChats().asLiveData()) { listOfDBChats ->
            listOfDBChats.sortedByDescending { it.lastMessage()?.dateTime }.toUIChat()
        }

    private var _chat = MutableLiveData<DBChat>()
    val chat: LiveData<DBChat> get() = _chat
    private lateinit var currentChatChatMateId: String

    val userIsRegistered = Transformations.map(settingsRepo.username().asLiveData()) {
        it != NO_USERNAME
    }

    private var _registrationStatus = MutableLiveData<Int>()
    val registrationStatus: LiveData<Int> get() = _registrationStatus

    private var _usersFetchStatus = MutableLiveData<Int>()
    val usersFetchStatus: LiveData<Int> get() = _usersFetchStatus

    private var _searchStatus = MutableLiveData<Int>()
    val searchStatus: LiveData<Int> get() = _searchStatus

    private var _profileFetchStatus = MutableLiveData<Int>()
    val profileFetchStatus: LiveData<Int> get() = _profileFetchStatus

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

    private val chatsAndMessagesSize = mutableListOf<ChatAndMessagesSize>()

    val markAllMessagesSent = settingsRepo.markSentFlow().asLiveData()

    var chatStartedByActivity = false

    val snackBarState: LiveData<SnackBarState> =
        Transformations.map(settingsRepo.snackBarStateFlow().asLiveData()) {

        // this checks if the snackBar is a noSnackBar snackBar. done by simply checking if the message is an empty string.
        // a better implementation would be to include a field that indicates snackBar type in the snackBar class, and then check with that field.
            if (it.message != "") {
                snackBarTimer.cancel()
                snackBarTimer.start()
            }

            it
        }

    val shouldScrollChat: LiveData<Boolean> = settingsRepo.scrollFlow().asLiveData()

    val darkMode: LiveData<Boolean> = settingsRepo.darkModeFlow().asLiveData()

    private val snackBarTimer = object : CountDownTimer(7000, 1000) {
        override fun onTick(millisUntilFinished: Long) {}
        override fun onFinish() {
            closeSnackBar()
        }
    }

    private var _profile = MutableLiveData<User>()
    val profile: LiveData<User> get() = _profile

    val shouldEnableGameModeKeyboard: LiveData<Boolean> = settingsRepo.keyBoardModeFlow().asLiveData()


    fun sendMessage(messageBody: String, receiverId: String, senderId: String = thisUserId) {
        viewModelScope.launch {
            messagingRepoX.sendMessage(
                senderId,
                receiverId,
                messageBody
            )
        }
    }

    fun refreshMessages(failToastMessage: String) {
        viewModelScope.launch {
            try {
                messagingRepoX.refreshMessages()
            } catch (e: Exception) {
//                showNetworkErrorToast(getApplication(), failToastMessage)
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
                            messagingRepoX.registerUser(settingsRepo.getUserId(), username, gender, token)
                            _registrationStatus.value = PASSED
                            settingsRepo.storeUsername(username)
                        } catch (e: Exception) {
                            _registrationStatus.value = FAILED
                            Log.e("registration error", e.toString())
//                            showNetworkErrorToast(getApplication(), "encountered error trying to register")
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
            currentChatChatMateId = userId
            closeSnackBar()

            val chats = chatDao.getChats().first()
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
                chatDao.insert(chat)
            }
            _chat = chatDao.getChatByChatmateId(userId).asLiveData() as MutableLiveData<DBChat>
            messagingRepoX.setChat(chat)

            _navigateToChatFragment.value = true
        }

        viewModelScope.launch {
//            refreshUserStatus(userId)
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
//                showNetworkErrorToast(getApplication(), "Could not fetch users")
                _usersFetchStatus.value = DEFAULT
            }
        }
    }

    fun getMatchingUsers(usernameSubString: String) {
        viewModelScope.launch {
            try {
                _searchStatus.value = BUSY
                if (usernameSubString.isBlank()) {
                    val users = NetworkApi.retrofitService.getUsers(thisUserId)
                    _users.value = users
                } else {
                    val users = NetworkApi.retrofitService.getUsersWithMatch(thisUserId, usernameSubString)
                    _users.value = users
                }
                _searchStatus.value = PASSED
            } catch (e: Exception) {
                _searchStatus.value = FAILED
//                showNetworkErrorToast(getApplication(), "Could not fetch users")
                _searchStatus.value = DEFAULT
            }
        }
    }

    fun getProfile() {
        viewModelScope.launch {
            try {
                _profileFetchStatus.value = BUSY
                val profile = NetworkApi.retrofitService.getUser(thisUserId)
                _profile.value = profile
                _profileFetchStatus.value = PASSED
            } catch (e: Exception) {
                _profileFetchStatus.value = FAILED
//                showNetworkErrorToast(getApplication(), "Could not fetch profile")
                _profileFetchStatus.value = DEFAULT
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
//                    showNetworkErrorToast(getApplication(), "try a different username.\n '$username' has been taken.")
                }
                updateSignUpConditionsStatus()
            } catch (e: Exception) {
//                showNetworkErrorToast(getApplication(), "username verification failed!")
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
            messagingRepoX.sendGameRequest()
            _showConfirmGameRequestDialog.value = false
        }
    }

    fun cancelSendGameRequest() {
        _showConfirmGameRequestDialog.value = false
    }

    fun respondPositive() {
        viewModelScope.launch {
            messagingRepoX.givePositiveResponse()
        }
    }

    fun respondNegative() {
        viewModelScope.launch {
            messagingRepoX.giveNegativeResponse()
        }
    }

    fun notifyDialogOkayPressed() {
        viewModelScope.launch {
            messagingRepoX.notifyDialogOkayPressed()
        }
    }

    fun resetNavigateChatsToChatFragment() {
        _navigateToChatFragment.value = false
    }

    fun markMessagesSent() {
        viewModelScope.launch {
            try {
                val chat = chatDao.getChatByChatmateId(_chat.value?.chatMateId!!).first()
                chat.markAllMessagesSent()
                chatDao.update(chat)
            } catch (e: NullPointerException) {
                Log.e("viewModel", "null pointer exception trying to mark all messages sent")
            }
        }
    }

    fun markMessagesRead() {
        viewModelScope.launch {
            try {
                val chat = chatDao.getChatByChatmateId(_chat.value?.chatMateId!!).first()
                chat.markMessagesRead()
                chatDao.update(chat)
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
        startChat(messagingRepoX.getGameRequestSenderId())
    }

    fun snackBarNavigateToChatFromChatFragment() {
        _navigateToChatsFragment.value = true
        startChat(getGameRequestSenderId())
    }

    fun getGameRequestSenderId(): String {
        return messagingRepoX.getGameRequestSenderId()
    }

    fun toggleDarkMode(){
        val currentMode = darkMode.value
        settingsRepo.updateDarkMode(!currentMode!!)
    }

    fun refreshUserStatus(userId: String = currentChatChatMateId) {
        viewModelScope.launch {
            try {
                messagingRepoX.refreshUser(userId)
            } catch (e: Exception) {
                Log.e("viewModel", "error refreshing user status. $e")
            }
        }

        viewModelScope.launch {
            val user = NetworkApi.retrofitService.getUser(currentChatChatMateId)
            settingsRepo.updateKeyboardMode(user.gameState == CURRENTLY_PLAYING_GAME_STATE)
        }
    }

    fun saveUserId(userId: String) {
        settingsRepo.updateUserId(userId)
    }


    init {
        _usernameCheckStatus.value = DEFAULT
        _signUpConditionsMet.value = false
        _showConfirmGameRequestDialog.value = false
        _navigateToChatFragment.value = false
        _navigateToChatsFragment.value = false
        _registrationStatus.value = DEFAULT
    }

    data class ChatAndMessagesSize(val userId: String, var messagesSize: Int)
}

//class ChatViewModelFactory(
//    private val chatDao: ChatDao,
//    private val settingsRepo: SettingsRepo,
//    private val messagingRepoX: MessagingRepoX
//) :
//    ViewModelProvider.Factory {
//    override fun <T : ViewModel> create(modelClass: Class<T>): T {
//        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
//            @Suppress("UNCHECKED_CAST")
//            return ChatViewModel(chatDao, settingsRepo, messagingRepoX) as T
//        }
//        throw IllegalArgumentException("Unknown ViewModel class")
//    }
//
//}