package com.othadd.ozi.utils

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.othadd.ozi.Message
import com.othadd.ozi.NWMessage
import java.lang.reflect.Type

class MessagesHolder(val chatMateId: String){
    val messages = mutableListOf<Message>()
}

fun List<NWMessage>.toMessages(): MutableList<Message> {
    val messages = mutableListOf<Message>()
    for (networkMessage in this){
        messages.add(networkMessage.toMessage())
    }
    return messages
}

fun List<NWMessage>.sortIntoGroupsByChatMate(): List<MessagesHolder> {
    val messagesHolders = mutableListOf<MessagesHolder>()
    val messages = this.toMessages()

    while (messages.isNotEmpty()){
        val message = messages[0]
        var messagesHolder = messagesHolders.find { it.chatMateId == message.senderId}
        if (messagesHolder == null){
            messagesHolder = MessagesHolder(message.senderId)
            messagesHolders.add(messagesHolder)
        }
        messagesHolder.messages.add(message)
        messages.remove(message)
    }
    return messagesHolders
}

fun messageToString(message: Message): String{
    val gson = Gson()
    return gson.toJson(message)
}

fun stringToMessage(jsonString: String): Message {
    val gson = Gson()
    val collectionType: Type = object : TypeToken<Message?>() {}.type
    return gson.fromJson(jsonString, collectionType)
}