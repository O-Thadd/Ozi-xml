package com.othadd.ozi.models

import com.othadd.ozi.UIMessage
import com.othadd.ozi.database.DialogState
import com.othadd.ozi.network.User
import com.othadd.ozi.ui.UIChat
import com.othadd.ozi.ui.UIChat2

data class FindUsersFragmentUIState(
    val searchStatus: Int,
    val fetchStatus: Int,
    val users: List<User>
)

data class ProfileFragmentUIState(val fetchStatus: Int, val profile: User?)

data class ChatsFragmentUIState(val darkModeSet: Boolean, val chats: List<UIChat>)

data class RegisterFragmentUIState(
    val userIsRegistered: Boolean,
    val usernameCheckStatus: Int,
    val signUpConditionsMet: Boolean,
    val registrationStatus: Int,
    val genderPopupShowing: Boolean
)

//data class ChatFragmentUIState(val chat: UIChat2)