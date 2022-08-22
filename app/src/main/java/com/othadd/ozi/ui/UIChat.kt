package com.othadd.ozi.ui

import java.util.*

data class UIChat(
    val chatMateId: String,
    val chatMateUsername: String,
    val lastMessage: String,
    val lastMessageDateTime: String,
    val chatMateGender: String,
    var allMessagesRead: Boolean
)