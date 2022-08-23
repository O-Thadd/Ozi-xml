package com.othadd.ozi

import java.util.*

data class UIChat(
    val chatMateId: String,
    val chatMateUsername: String,
    val lastMessage: String,
    val lastMessageDateTime: String,
    val gender: String,
    val chatMateGender: String
)