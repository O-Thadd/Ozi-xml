package com.othadd.ozi.ui

import androidx.room.ColumnInfo
import com.othadd.ozi.Message
import com.othadd.ozi.UIMessage
import com.othadd.ozi.database.DialogState

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

data class UIChat2(
    val chatMateId: String,
    val messages: List<UIMessage>,
    var chatMateUsername: String,
    var chatMateGender: String,
    var dialogState: DialogState,
    var hasUnreadMessage: Boolean,
    var onlineStatus: Boolean,
    var verificationStatus: Boolean
)