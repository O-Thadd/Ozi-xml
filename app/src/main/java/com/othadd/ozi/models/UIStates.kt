package com.othadd.ozi.models

import com.othadd.ozi.network.User
import com.othadd.ozi.ui.UIChat

data class FindUsersFragmentUIState(val searchStatus: Int, val fetchStatus: Int, val users: List<User>)

data class ProfileFragmentUIState(val fetchStatus: Int, val profile: User?)

data class ChatFragmentUIState(val darkModeSet: Boolean, val chats: List<UIChat>)