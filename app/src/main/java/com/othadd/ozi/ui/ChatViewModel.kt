package com.othadd.ozi.ui

import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.*
import com.othadd.ozi.Service
import com.othadd.ozi.data.database.toUIChat
import com.othadd.ozi.data.database.toUIChat2
import com.othadd.ozi.models.ChatsFragmentUIState
import com.othadd.ozi.models.FindUsersFragmentUIState
import com.othadd.ozi.models.ProfileFragmentUIState
import com.othadd.ozi.models.RegisterFragmentUIState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

const val DEFAULT = 0
const val BUSY = 1
const val PASSED = 2
const val FAILED = 3
const val ERROR = 4


@HiltViewModel
open class ChatViewModel @Inject constructor(
    private val service: Service
) : ViewModel() {

    private val thisUserId: String get() = service.thisUserId

    val chats = Transformations.map(service.chats.asLiveData()) { listOfDBChats ->
        listOfDBChats.sortedByDescending { it.lastMessage()?.dateTime }.toUIChat()
    }
    val chat: LiveData<UIChat2> =
        Transformations.map(service.chat.asLiveData()) { it.toUIChat2(thisUserId) }
    private val currentChatChatMateId get() = service.chatMateIdFlow.value

    val darkMode: LiveData<Boolean> = service.darkModeSet.asLiveData()

    val findUsersFragmentUIState =
        combine(service.users, service.searchedUsers) { users, searchedUsers ->
            if (searchedUsers.timeStamp > users.timeStamp) {
                FindUsersFragmentUIState(searchedUsers.status, DEFAULT, searchedUsers.users)
            } else {
                FindUsersFragmentUIState(DEFAULT, users.status, users.users)
            }
        }.asLiveData()

    val profileFragmentUIState = service.profile.map {
        ProfileFragmentUIState(it.status, it.user)
    }.asLiveData()

    val chatsFragmentUIState =
        combine(service.chats, service.darkModeSet) { chats, darkModeSet ->
            val uiChats = chats.sortedByDescending { it.lastMessage()?.dateTime }.toUIChat()
            ChatsFragmentUIState(darkModeSet, uiChats)
        }.asLiveData()

    private val genderSelectionPopupIsShowing = MutableStateFlow(false)
    private val genderHasBeenSelected = MutableStateFlow(false)
    val registerFragmentUIState = combine(
        service.userIsRegistered,
        service.registeringStatus,
        service.usernameCheckStatus,
        genderSelectionPopupIsShowing,
        genderHasBeenSelected
    ) { userIsRegistered, registeringStatus, usernameCheckStatus, genderPopupShowing, genderSelected ->
        val signUpConditionsMet = genderSelected && usernameCheckStatus == PASSED
        RegisterFragmentUIState(
            userIsRegistered,
            usernameCheckStatus,
            signUpConditionsMet,
            registeringStatus,
            genderPopupShowing
        )
    }.asLiveData()

    val refreshError = service.refreshError.asLiveData()

    private var _snackBarStateX = MutableLiveData<SnackBarState>()
    val snackBarStateX: LiveData<SnackBarState> get() = _snackBarStateX

    private val chatFragmentShowing = MutableStateFlow(false)
    private var chatFragmentShowingX = false

    private var _showConfirmGameRequestDialog = MutableLiveData<Boolean>()
    val showConfirmGameRequestDialog: LiveData<Boolean> get() = _showConfirmGameRequestDialog

    private var _navigateToChatFragment = MutableLiveData<Boolean>()
    val navigateToChatFragment: LiveData<Boolean> get() = _navigateToChatFragment

    var chatStartedByActivity = false

    val shouldScrollChat: LiveData<Boolean> = service.scrollMessages.asLiveData()

    private val snackBarTimer = object : CountDownTimer(7000, 6000) {
        override fun onTick(millisUntilFinished: Long) {}
        override fun onFinish() {
            closeSnackBar()
        }
    }

    val shouldEnableGameModeKeyboard: LiveData<Boolean> = service.useGameKeyboard.asLiveData()


    fun sendMessage(messageBody: String) {
        viewModelScope.launch {
            service.sendChatMessage(messageBody)
        }
    }

    fun refreshMessages() {
        viewModelScope.launch {
            service.refreshMessages()
        }
    }

    fun registerUser(username: String, gender: String) {
        viewModelScope.launch {
            service.registerUser(username, gender, viewModelScope)
        }
    }

    fun startChat(userId: String) {
        viewModelScope.launch {
            service.startChat(userId)
            navigateToChatFragment()
        }
    }

    fun getLatestUsers() {
        viewModelScope.launch {
            service.getUsers()
        }
    }

    fun getMatchingUsers(usernameSubString: String) {
        viewModelScope.launch {
            if (usernameSubString.isBlank()) {
                service.getUsers()
            } else {
                service.searchUsers(usernameSubString)
            }
        }
    }

    fun getProfile() {
        viewModelScope.launch {
            service.getProfile()
        }
    }

    fun checkUsername(username: String) {
        viewModelScope.launch {
            service.checkUsername(username)
        }
    }

    fun resetUsernameCheck() {
        service.resetUsernameCheck()
    }

    fun hideGenderSelectionPopup() {
        genderSelectionPopupIsShowing.value = false
    }

    fun toggleGenderSelectionPopup() {
        genderSelectionPopupIsShowing.value = !genderSelectionPopupIsShowing.value
    }

    fun updateGenderSelected(newValue: Boolean) {
        genderHasBeenSelected.value = newValue
    }

    fun markMessagesRead() {
        viewModelScope.launch {
            service.markMessagesRead()
        }
    }

    fun confirmSendGameRequest() {
        _showConfirmGameRequestDialog.value = true
    }

    fun sendGameRequest() {
        viewModelScope.launch {
            service.sendGameRequest()
            _showConfirmGameRequestDialog.value = false
        }
    }

    fun cancelSendGameRequest() {
        _showConfirmGameRequestDialog.value = false
    }

    fun respondPositive() {
        viewModelScope.launch {
            service.promptDialogPositiveButtonPressed()
        }
    }

    fun respondNegative() {
        viewModelScope.launch {
            service.promptDialogNegativeButtonPressed()
        }
    }

    fun notifyDialogOkayPressed() {
        viewModelScope.launch {
            service.notifyDialogOkayPressed()
        }
    }

    fun resetNavigateChatsToChatFragment() {
        _navigateToChatFragment.value = false
    }

    fun startChatFromActivity(senderId: String) {
        chatStartedByActivity = true
        startChat(senderId)
    }

    fun resetChatStartedByActivity() {
        chatStartedByActivity = false
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            service.toggleDarkMode()
        }
    }

    fun saveUserId(userId: String) {
        service.saveUserId(userId)
    }

    fun goToGameRequestSenderChat() {
        viewModelScope.launch {
            snackBarTimer.cancel()
            closeSnackBar()
            service.startGameRequestSenderChat()
            navigateToChatFragment()
            _navigateToChatFragment.value = false
        }
    }

    private suspend fun navigateToChatFragment() {
        //there is a very brief period between when the chat is opened and when the data is loaded.
        //so the user briefly sees a blank chat
        // this delay tries to ensure the data is all loaded before the chat is opened.
        delay(50L)
        _navigateToChatFragment.value = true
    }

    fun refreshUserStatus() {
        viewModelScope.launch {
            try {
                service.refreshUser()
            } catch (e: Exception) {
                Log.e("viewModel", "refreshUserStatus(). $e")
            }
        }
    }

    fun updateChatFragmentShowing(newStatus: Boolean) {
        chatFragmentShowing.value = newStatus
        chatFragmentShowingX = newStatus
    }

    fun closeSnackBar() {
        _snackBarStateX.value = getNoSnackBarSnackBar()
    }

    init {
        _showConfirmGameRequestDialog.value = false
        _navigateToChatFragment.value = false
        _snackBarStateX.value = getNoSnackBarSnackBar()

        viewModelScope.launch {
            service.newGameRequestFlow.collect { newGameRequestData ->
                if (newGameRequestData.first == null) {
                    return@collect
                }

                if (!chatFragmentShowingX) {
                    snackBarTimer.cancel()
                    snackBarTimer.start()
                    _snackBarStateX.value = getPromptSnackBar(
                        "${newGameRequestData.second} has challenged you!",
                        "Open chat"
                    )
                    return@collect
                }

                if (newGameRequestData.first == currentChatChatMateId) {
                    _snackBarStateX.value = getNoSnackBarSnackBar()
                } else {
                    snackBarTimer.cancel()
                    snackBarTimer.start()
                    _snackBarStateX.value = getPromptSnackBar(
                        "${newGameRequestData.second} has challenged you!",
                        "Open chat"
                    )
                }
            }
        }
    }
}