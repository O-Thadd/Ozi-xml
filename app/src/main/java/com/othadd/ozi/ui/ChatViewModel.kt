package com.othadd.ozi.ui

import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.*
import com.othadd.ozi.MessagingRepoX
import com.othadd.ozi.OziApplication
import com.othadd.ozi.database.ChatDao
import com.othadd.ozi.database.toUIChat
import com.othadd.ozi.database.toUIChat2
import com.othadd.ozi.gaming.GameManager
import com.othadd.ozi.models.ChatsFragmentUIState
import com.othadd.ozi.models.FindUsersFragmentUIState
import com.othadd.ozi.models.ProfileFragmentUIState
import com.othadd.ozi.models.RegisterFragmentUIState
import com.othadd.ozi.utils.SettingsRepo
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
    private val chatDao: ChatDao,
    private val settingsRepo: SettingsRepo,
    private val messagingRepoX: MessagingRepoX,
    private val oziApplication: OziApplication
) : ViewModel() {

    private val thisUserId: String = settingsRepo.getUserId()
    private val gameManager = GameManager.getInstance(oziApplication, messagingRepoX)

    val chats = Transformations.map(messagingRepoX.chats.asLiveData()) { listOfDBChats ->
        listOfDBChats.sortedByDescending { it.lastMessage()?.dateTime }.toUIChat()
    }
    val chat: LiveData<UIChat2> =
        Transformations.map(messagingRepoX.chat.asLiveData()){ it.toUIChat2(thisUserId) }
    private val currentChatChatMateId get() = messagingRepoX.chatMateIdFlow.value

    val darkMode: LiveData<Boolean> = messagingRepoX.darkModeSet.asLiveData()

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
            ChatsFragmentUIState(darkModeSet, uiChats)
        }.asLiveData()

    private val genderSelectionPopupIsShowing = MutableStateFlow(false)
    private val genderHasBeenSelected = MutableStateFlow(false)
    val registerFragmentUIState = combine(
        messagingRepoX.userIsRegistered,
        messagingRepoX.registeringStatus,
        messagingRepoX.usernameCheckStatus,
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

    val refreshError = messagingRepoX.refreshError.asLiveData()

    private var _snackBarStateX = MutableLiveData<SnackBarState>()
    val snackBarStateX: LiveData<SnackBarState> get() = _snackBarStateX

    private val chatFragmentShowing = MutableStateFlow(false)








    private var _showConfirmGameRequestDialog = MutableLiveData<Boolean>()
    val showConfirmGameRequestDialog: LiveData<Boolean> get() = _showConfirmGameRequestDialog

    private var _navigateToChatFragment = MutableLiveData<Boolean>()
    val navigateToChatFragment: LiveData<Boolean> get() = _navigateToChatFragment

    private var _navigateToChatsFragment = MutableLiveData<Boolean>()
    val navigateToChatsFragment: LiveData<Boolean> get() = _navigateToChatsFragment

    val markAllMessagesSent = settingsRepo.markSentFlow().asLiveData()

    var chatStartedByActivity = false

    val shouldScrollChat: LiveData<Boolean> = settingsRepo.scrollFlow().asLiveData()


    private val snackBarTimer = object : CountDownTimer(7000, 6000) {
        override fun onTick(millisUntilFinished: Long) {}
        override fun onFinish() {
            closeSnackBar()
        }
    }

    val shouldEnableGameModeKeyboard: LiveData<Boolean> =
        settingsRepo.keyBoardModeFlow().asLiveData()





    fun sendMessage(messageBody: String) {
        viewModelScope.launch {
            messagingRepoX.sendChatMessage(messageBody)
        }
    }

    fun refreshMessages() {
        viewModelScope.launch {
            messagingRepoX.refreshMessages()
        }
    }

    fun registerUser(username: String, gender: String) {
        viewModelScope.launch {
            messagingRepoX.registerUser(username, gender, viewModelScope)
        }
    }

    fun startChat(userId: String) {
        viewModelScope.launch {
            messagingRepoX.startChat(userId)
//            _navigateToChatFragment.value = true
            navigateToChatFragment()
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
            messagingRepoX.checkUsername(username)
        }
    }

    fun resetUsernameCheck() {
        messagingRepoX.resetUsernameCheck()
    }

    fun showGenderSelectionPopup(){
        genderSelectionPopupIsShowing.value = true
    }

    fun hideGenderSelectionPopup(){
        genderSelectionPopupIsShowing.value = false
    }

    fun toggleGenderSelectionPopup(){
        genderSelectionPopupIsShowing.value = !genderSelectionPopupIsShowing.value
    }

    fun updateGenderSelected(newValue: Boolean){
        genderHasBeenSelected.value = newValue
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
            messagingRepoX.promptDialogPositiveButtonPressed()
        }
    }

    fun respondNegative() {
        viewModelScope.launch {
            messagingRepoX.promptDialogNegativeButtonPressed()
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

    fun startChatFromActivity(senderId: String) {
        chatStartedByActivity = true
        startChat(senderId)
    }

    fun resetChatStartedByActivity() {
        chatStartedByActivity = false
    }

    fun toggleDarkMode() {
        val currentMode = darkMode.value
        settingsRepo.updateDarkMode(!currentMode!!)
    }

    fun saveUserId(userId: String) {
        settingsRepo.updateUserId(userId)
    }

    fun goToGameRequestSenderChat() {
        viewModelScope.launch {
            snackBarTimer.cancel()
            closeSnackBar()
            messagingRepoX.startGameRequestSenderChat()
            navigateToChatFragment()
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
                messagingRepoX.refreshUser()
            } catch (e: Exception) {
                Log.e("viewModel", "error refreshing user status. $e")
            }
        }
    }

    fun updateChatFragmentShowing(newStatus: Boolean){
        chatFragmentShowing.value = newStatus
    }












    fun closeSnackBar() {
        _snackBarStateX.value = getNoSnackBarSnackBar()
    }

    init {
        _showConfirmGameRequestDialog.value = false
        _navigateToChatFragment.value = false
        _navigateToChatsFragment.value = false
        _snackBarStateX.value = getNoSnackBarSnackBar()

        viewModelScope.launch {
            combine(messagingRepoX.newGameRequestFlow, chatFragmentShowing){newGameRequestData, chatFragmentShowing ->
                if(newGameRequestData.first == null){
                    return@combine getNoSnackBarSnackBar()
                }

                if(!chatFragmentShowing){
                    snackBarTimer.cancel()
                    snackBarTimer.start()
                    return@combine getPromptSnackBar(
                        "${newGameRequestData.second} has challenged you!",
                        "Open chat"
                    )
                }

                return@combine if (newGameRequestData.third == currentChatChatMateId){
                    getNoSnackBarSnackBar()
                }
                else{
                    snackBarTimer.cancel()
                    snackBarTimer.start()
                    getPromptSnackBar(
                        "${newGameRequestData.second} has challenged you!",
                        "Open chat"
                    )
                }
            }.collect {
                _snackBarStateX.value = it
            }
        }
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