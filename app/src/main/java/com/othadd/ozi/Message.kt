package com.othadd.ozi

import java.text.DateFormat
import java.util.*

const val CHAT_MESSAGE = "Chat Message"
const val GAME_REQUEST_MESSAGE = "Game Request Message"
const val USER_ALREADY_PLAYING = "The user is already playing"
const val FROM_SERVER = "From Server"

class Message(
    val senderId: String,
    val receiverId: String,
    val body: String,
    val dateTime: Long
) {
    var id: String = UUID.randomUUID().toString()
    var type: String = CHAT_MESSAGE

    constructor(senderId: String, receiverId: String, type: String) : this(senderId, receiverId, "", Calendar.getInstance().timeInMillis) {
        this.type = type
    }

    fun toNWMessage(): NWMessage {
        val nWMessage = NWMessage(senderId, receiverId, body, dateTime, id)
        nWMessage.type = type
        return nWMessage
    }

    fun toUIMessage(myId: String): UIMessage {
        val format = DateFormat.getTimeInstance(DateFormat.SHORT)
        val calendarObject = Calendar.getInstance()
        calendarObject.timeInMillis = dateTime
        val date = calendarObject.time
        return UIMessage(id, senderId == myId, body, format.format(date))
    }
}

data class NWMessage(
    val senderId: String,
    val receiverId: String,
    val body: String,
    val dateTime: Long,
    val id: String
) {

    lateinit var type: String

    fun toMessage(): Message {
        val message = Message(senderId, receiverId, body, dateTime)
        message.apply {
            id = this@NWMessage.id
            type = this@NWMessage.type
        }
        return message
    }
}

data class UIMessage(val id: String, val sentByMe: Boolean, val body: String, val dateTime: String)