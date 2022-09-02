package com.othadd.ozi.ui

data class UIChat(
    val chatMateId: String,
    val chatMateUsername: String,
    val lastMessage: String,
    val lastMessageDateTime: String,
    val chatMateGender: String,
    val hasUnreadMessage: Boolean,
    val onlineStatus: Boolean,
    val verificationStatus: Boolean
)