package com.othadd.ozi.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.othadd.ozi.Message
import com.othadd.ozi.ui.UIChat
import java.text.DateFormat
import java.util.*

@Entity(tableName = "chat")
data class DBChat(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo val chatMateId: String,
    @ColumnInfo val messages: MutableList<Message>,
    @ColumnInfo var chatMateUsername: String,
    @ColumnInfo var chatMateGender: String,
    @ColumnInfo var dialogState: DialogState,
) {
    fun addMessages(newMessages: List<Message>) {
        messages.addAll(newMessages)
    }
    fun addMessage(newMessage: Message){
        messages.add(newMessage)
    }

    fun markAllMessagesSent(){
        for (message: Message in messages){
            message.sent = true
        }
    }

    fun markAllMessagesRead(){
        for(message: Message in messages){
            message.read = true
        }
    }
}

fun List<DBChat>.toUIChat(): List<UIChat>{
    return this.map { dbChat ->

        if (dbChat.messages.isNotEmpty()){
            val lastMessage = dbChat.messages.maxByOrNull { it.dateTime }!!
            val read = lastMessage.read

            val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            val calendarObject = Calendar.getInstance()
            calendarObject.timeInMillis = lastMessage.dateTime
            val date = calendarObject.time

            return@map UIChat(dbChat.chatMateId, dbChat.chatMateUsername, lastMessage.body, format.format(date), dbChat.chatMateGender, read)
        }

        else return@map UIChat(
            dbChat.chatMateId,
            dbChat.chatMateUsername,
            "",
            "",
            dbChat.chatMateGender,
            true
        )

    }
}