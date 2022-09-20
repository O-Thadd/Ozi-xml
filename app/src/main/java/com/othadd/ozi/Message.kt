package com.othadd.ozi

import com.othadd.ozi.gaming.DUMMY_STRING
import java.text.DateFormat
import java.util.*


//message types
const val CHAT_MESSAGE_TYPE = "Chat Message"
const val GAME_REQUEST_MESSAGE_TYPE = "Game Request Message"
const val GAME_REQUEST_RESPONSE_MESSAGE_TYPE = "Game Request Response Message"
const val STATUS_UPDATE_MESSAGE_TYPE = "Status Update Message"
const val GAME_MANAGER_MESSAGE_TYPE = "Game Manager Message"

const val SERVER_SENDER_TYPE = "Server Sender Type"
const val CHAT_MATE_SENDER_TYPE = "ChatMate Sender Type"
const val GAME_MODERATOR_SENDER_TYPE = "Game Moderator Sender Type"

const val USER_ALREADY_PLAYING_MESSAGE_BODY = "The user is already playing"
const val USER_HAS_PENDING_REQUEST_MESSAGE_BODY = "The user has a pending request"
const val GAME_REQUEST_DECLINED_MESSAGE_BODY = "Game request declined"
const val GAME_REQUEST_ACCEPTED_MESSAGE_BODY = "Game request accepted"


const val MESSAGE_SENT_BY_ME = "message sent by me"
const val MESSAGE_SENT_BY_CHAT_MATE = "message sent by chatmate"
const val MESSAGE_SENT_BY_SERVER = "message sent by server"
const val MESSAGE_SENT_BY_MODERATOR = "message sent by moderator"

data class Message(
    val senderId: String,
    val receiverId: String,
    var body: String,
    var dateTime: Long
) {
    var id: String = UUID.randomUUID().toString()
    var type: String = CHAT_MESSAGE_TYPE
    var senderType: String = CHAT_MATE_SENDER_TYPE
    var messagePackage: String = "null" // if message with a package has to be sent. then the package field must be updated immediately after message construction


    //this field is only relevant for sent messages. completely irrelevant for received messages.
    // also not accounted for in conversion to NWMessage.
    var sent: Boolean = false

    constructor(senderId: String, receiverId: String, type: String) : this(
        senderId,
        receiverId,
        "",
        Calendar.getInstance().timeInMillis
    ) {
        this.type = type
    }

    constructor(senderId: String, receiverId: String, type: String, body: String) : this(
        senderId,
        receiverId,
        type
    ) {
        this.body = body
        this.senderType = SERVER_SENDER_TYPE
    }

    constructor(
        senderId: String,
        receiverId: String,
        body: String,
        type: String,
        senderType: String,
        dateTime: Long = Calendar.getInstance().timeInMillis
    ) : this(senderId, receiverId, body, dateTime) {
        this.type = type
        this.senderType = senderType
    }

    fun toNWMessage(): NWMessage {
        val nWMessage = NWMessage(senderId, receiverId, body, dateTime, id)
        nWMessage.type = type
        nWMessage.senderType = senderType
        nWMessage.messagePackage = messagePackage
        return nWMessage
    }

    fun toUIMessage(myId: String): UIMessage {
        val format = DateFormat.getTimeInstance(DateFormat.SHORT)
        val calendarObject = Calendar.getInstance()
        calendarObject.timeInMillis = dateTime
        val date = calendarObject.time

        val sender = when {
            senderId == myId -> {
                MESSAGE_SENT_BY_ME
            }

            senderType == SERVER_SENDER_TYPE -> {
                MESSAGE_SENT_BY_SERVER
            }

            senderType == GAME_MODERATOR_SENDER_TYPE -> {
                MESSAGE_SENT_BY_MODERATOR
            }

            else -> {
                MESSAGE_SENT_BY_CHAT_MATE
            }
        }

        return UIMessage(id, sender, body, format.format(date), sent)
    }
}

data class NWMessage(
    val senderId: String,
    val receiverId: String,
    val body: String,
    var dateTime: Long,
    val id: String
) {

    lateinit var type: String
    var senderType = CHAT_MATE_SENDER_TYPE
    var messagePackage: String = DUMMY_STRING

    fun toMessage(): Message {
        val message = Message(senderId, receiverId, body, dateTime)
        message.apply {
            id = this@NWMessage.id
            type = this@NWMessage.type
            senderType = this@NWMessage.senderType
            messagePackage = this@NWMessage.messagePackage
        }
        return message
    }
}

data class UIMessage(val id: String, val sender: String, val body: String, val dateTime: String, var sent: Boolean)