package com.othadd.ozi

import java.util.*

data class UIChat(
    val id: Int,
    val chatMate: String,
    val lastMessage: String,
    val lastMessageDateTime: String,
    val gender: String,
    val chatMateGender: String
)