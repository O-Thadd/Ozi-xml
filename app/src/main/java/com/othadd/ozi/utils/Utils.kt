package com.othadd.ozi.utils

import android.content.Context
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.othadd.ozi.Message
import com.othadd.ozi.NWMessage
import com.othadd.ozi.OziApplication
import com.othadd.ozi.ui.SnackBarState
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

fun snackBarToString(snackBarState: SnackBarState): String{
    val gson = Gson()
    return gson.toJson(snackBarState)
}

fun stringToSnackBar(snackBarString: String?): SnackBarState?{
    val gson = Gson()
    return if (snackBarString != null){
        gson.fromJson(snackBarString, SnackBarState::class.java)
    }
    else{
        null
    }
}

const val defaultNetworkErrorMessage = "Network Error. Please check your internet connection and try again."
fun showNetworkErrorToast(context: Context, message: String = defaultNetworkErrorMessage){
    if (OziApplication.inForeGround){
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
