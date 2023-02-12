package com.othadd.ozi.ui

import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.*
import com.othadd.ozi.MessagingRepoX
import com.othadd.ozi.database.ChatDao
import com.othadd.ozi.database.DBChat
import com.othadd.ozi.database.toUIChat
import com.othadd.ozi.models.ChatFragmentUIState
import com.othadd.ozi.models.FindUsersFragmentUIState
import com.othadd.ozi.models.ProfileFragmentUIState
import com.othadd.ozi.network.CURRENTLY_PLAYING_GAME_STATE
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.utils.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

const val DEFAULT = 0
const val BUSY = 1
const val PASSED = 2
const val FAILED = 3


@HiltViewModel
open class ChatViewModel @Inject constructor(
    private val chatDao: ChatDao,
    private val settingsRepo: SettingsRepo,
    private val messagingRepoX: MessagingRepoX
) : ViewModel() {

    val thisUserId: String = settingsRepo.getUserId()

    val chats = Transformations.map(messagingRepoX.chats.asLiveData()) { listOfDBChats ->
        listOfDBChats.sortedByDescending { it.lastMessage()?.dateTime }.toUIChat()
    }
    val chat: LiveData<DBChat> = messagingRepoX.chat.asLiveData()
    private val currentChatChatMateId get() = messagingRepoX.chatMateId.value

    val userIsRegistered = messagingRepoX.userIsRegistered.asLiveData()
    val registrationStatus = messagingRepoX.registrationStatus.asLiveData()

    val findUsersFragmentUIState =
        combine(messagingRepoX.users, messagingRepoX.searchedUsers) { users, searchedUsers ->
            if (searchedUsers.timeStamp > users.timeStamp) {
                FindUsersFragmentUIState(searchedUsers.status, DEFAULT, searchedUsers.users)
            } else {
                FindUsersFragmentUIState(DEFAULT, users.status, users.users)
            }
        }.asLiveData()

    val profileFragmentUIState = messagingRepoX.profile.map {
        ProfileFragmentUIState(it.status, it.user)
    }.asLiveData()

    val chatsFragmentUIState =
        combine(messagingRepoX.chats, messagingRepoX.darkModeSet) { chats, darkModeSet ->
            val uiChats = chats.sortedByDescending { it.lastMessage()?.dateTime }.toUIChat()
            ChatFragmentUIState(darkModeSet, uiChats)
        }.asLiveData()
    val darkMode: LiveData<Boolean> = messagingRepoX.darkModeSet.asLiveData()


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


    private val snackBarTimer = object : CountDownTimer(7000, 1000) {
        override fun onTick(millisUntilFinished: Long) {}
        override fun onFinish() {
            closeSnackBar()
        }
    }

    val shouldEnableGameModeKeyboard: LiveData<Boolean> =
        settingsRepo.keyBoardModeFlow().asLiveData()





    fun sendMessage(messageBody: String, receiverId: String, senderId: String = thisUserId) {
        viewModelScope.launch {
            messagingRepoX.sendMessageX(
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
//        _registrationStatus.value = BUSY
//        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
//
//            Handler(Looper.getMainLooper()).postDelayed({
//                viewModelScope.launch {
//                    if (task.isSuccessful) {
//
//                        // Get new FCM registration token
//                        val token = task.result
//
//                        // do something with token
//                        try {
//                            messagingRepoX.registerUser(settingsRepo.getUserId(), username, gender, token)
//                            _registrationStatus.value = PASSED
//                            settingsRepo.storeUsername(username)
//                        } catch (e: Exception) {
//                            _registrationStatus.value = FAILED
//                            Log.e("registration error", e.toString())
////                            showNetworkErrorToast(getApplication(), "encountered error trying to register")
//                        }
//                    } else {
//                        _registrationStatus.value = FAILED
//                    }
//                }
//            }, 500)
//        }
        viewModelScope.launch {
            messagingRepoX.registerUser(username, gender, viewModelScope)
        }
    }

    fun startChat(userId: String) {
        viewModelScope.launch {
//            currentChatChatMateId = userId
//            closeSnackBar()
//
//            val chats = chatDao.getChatsFlow().first()
//            var chat: DBChat?
//            chat = chats.find { it.chatMateId == userId }
//            if (chat == null) {
//                val user = NetworkApi.retrofitService.getUser(userId)
//                chat = DBChat(
//                    chatMateId = userId,
//                    messages = mutableListOf(),
//                    chatMateUsername = user.username,
//                    chatMateGender = user.gender,
//                    dialogState = getNoDialogDialogType(),
//                    hasUnreadMessage = false,
//                    onlineStatus = user.onlineStatus,
//                    verificationStatus = user.verificationStatus
//                )
//                chatDao.insert(chat)
//            }
//            _chat = chatDao.getChatByChatmateIdFlow(userId).asLiveData() as MutableLiveData<DBChat>
//            messagingRepoX.setChat(chat)
//
//            _navigateToChatFragment.value = true

            messagingRepoX.startChat(userId)
            delay(50L)
            _navigateToChatFragment.value = true
        }
    }

    fun getLatestUsers() {
        viewModelScope.launch {
            messagingRepoX.getUsers()
        }
    }

    fun getMatchingUsers(usernameSubString: String) {
        viewModelScope.launch {
            if (usernameSubString.isBlank()) {
                messagingRepoX.getUsers()
            } else {
                messagingRepoX.searchUsers(usernameSubString)
            }
        }
    }

    fun getProfile() {
        viewModelScope.launch {
            messagingRepoX.getProfile()
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
            messagingRepoX.markMessagesSent()
        }
    }

    fun markMessagesRead() {
        viewModelScope.launch {
            messagingRepoX.markMessagesRead()
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

    fun toggleDarkMode() {
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
            try {
                val user = NetworkApi.retrofitService.getUser(currentChatChatMateId)
                settingsRepo.updateKeyboardMode(user.gameState == CURRENTLY_PLAYING_GAME_STATE)
            } catch (e: Exception) {
                Log.e(
                    "viewModel",
                    "error getting keyboard mode for chatmate in refreshUserStatus() $e"
                )
            }
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
    }

//    companion object {
//
//        val Factory: ViewModelProvider.AndroidViewModelFactory = object : ViewModelProvider.AndroidViewModelFactory() {
//            @Suppress("UNCHECKED_CAST")
//            override fun <T : ViewModel> create(
//                modelClass: Class<T>,
//                extras: CreationExtras
//            ): T {
//                // Get the Application object from extras
//                val application = checkNotNull(extras[APPLICATION_KEY])
//                // Create a SavedStateHandle for this ViewModel from extras
//                val savedStateHandle = extras.createSavedStateHandle()
//                val oziApp = application as OziApplication
//
//                return ChatViewModel(
//                    oziApp.database.chatDao(),
//                    SettingsRepo(oziApp),
//                    MessagingRepoX(oziApp)
//                ) as T
//            }
//        }
//    }
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