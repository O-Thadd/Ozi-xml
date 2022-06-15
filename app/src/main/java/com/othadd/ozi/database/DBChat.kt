package com.othadd.ozi.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.othadd.ozi.Message
import com.othadd.ozi.UIChat
import java.text.DateFormat
import java.util.*

@Entity(tableName = "chat")
data class DBChat(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo val chatMateId: String,
    @ColumnInfo val messages: MutableList<Message>,
    @ColumnInfo var chatMateUsername: String,
    @ColumnInfo var chatMateGender: String
) {
    fun addMessages(newMessages: List<Message>) {
        messages.addAll(newMessages)
    }
    fun addMessage(newMessage: Message){
        messages.add(newMessage)
    }
}

fun List<DBChat>.toUIChat(): List<UIChat>{
    return this.map { dbChat ->

        if (dbChat.messages.isNotEmpty()){
            val lastMessage = dbChat.messages.maxByOrNull { it.dateTime }!!

            val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            val calendarObject = Calendar.getInstance()
            calendarObject.timeInMillis = lastMessage.dateTime
            val date = calendarObject.time

            return@map UIChat(dbChat.id, dbChat.chatMateUsername, lastMessage.body, format.format(date), "male", dbChat.chatMateGender)
        }

        else return@map UIChat(
            dbChat.id,
            dbChat.chatMateUsername,
            "",
            "",
            "male",
            dbChat.chatMateGender
        )

    }
}