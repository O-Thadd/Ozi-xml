package com.othadd.ozi.utils

import android.content.Context
import android.widget.Toast
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.othadd.ozi.Message
import com.othadd.ozi.MessagePackage
import com.othadd.ozi.NWMessage
import com.othadd.ozi.OziApplication
import com.othadd.ozi.gaming.RoundSummary
import com.othadd.ozi.ui.SnackBarState
import java.lang.reflect.Type

class MessagesHolder(val chatMateId: String) {
    val messages = mutableListOf<Message>()
}

fun List<NWMessage>.toMessages(): MutableList<Message> {
    val messages = mutableListOf<Message>()
    for (networkMessage in this) {
        messages.add(networkMessage.toMessage())
    }
    return messages
}

fun List<NWMessage>.sortIntoGroupsByChatMate(): List<MessagesHolder> {
    val messagesHolders = mutableListOf<MessagesHolder>()
    val messages = this.toMessages()

    while (messages.isNotEmpty()) {
        val message = messages[0]
        var messagesHolder = messagesHolders.find { it.chatMateId == message.senderId }
        if (messagesHolder == null) {
            messagesHolder = MessagesHolder(message.senderId)
            messagesHolders.add(messagesHolder)
        }
        messagesHolder.messages.add(message)
        messages.remove(message)
    }
    return messagesHolders
}

fun messageToString(message: Message): String {
    val gson = Gson()
    return gson.toJson(message)
}

fun stringToMessage(jsonString: String): Message {
    val gson = Gson()
    val collectionType: Type = object : TypeToken<Message?>() {}.type
    return gson.fromJson(jsonString, collectionType)
}

fun snackBarToString(snackBarState: SnackBarState): String {
    val gson = Gson()
    return gson.toJson(snackBarState)
}

fun stringToSnackBar(snackBarString: String?): SnackBarState? {
    val gson = Gson()
    return if (snackBarString != null) {
        gson.fromJson(snackBarString, SnackBarState::class.java)
    } else {
        null
    }
}

fun messagePackageToString(messagePackage: MessagePackage): String {
    val gson = Gson()
    return gson.toJson(messagePackage)
}

fun stringToMessagePackage(packageString: String): MessagePackage {
    val gson = Gson()
    val messagePackage: MessagePackage
    try {
        messagePackage = gson.fromJson(packageString, MessagePackage::class.java)
    } catch (e: Exception) {
        throw e
    }

    return messagePackage
}

fun roundSummaryToString(roundSummary: RoundSummary): String {
    val gson = Gson()
    return gson.toJson(roundSummary)
}

fun stringToRoundSummary(roundSummaryString: String): RoundSummary {
    val gson = Gson()
    return gson.fromJson(roundSummaryString, RoundSummary::class.java)
}

fun instructionsToString(instructions: List<String>): String{
    val gson = Gson()
    return gson.toJson(instructions)
}

fun stringToInstructions(jsonString: String): List<String> {
    val gson = Gson()
    val collectionType: Type = object : TypeToken<List<String?>?>() {}.type
    return gson.fromJson(jsonString, collectionType)
}

const val defaultNetworkErrorMessage =
    "Network Error. Please check your internet connection and try again."

fun showNetworkErrorToast(context: Context, message: String = defaultNetworkErrorMessage) {
    if (OziApplication.inForeGround) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
