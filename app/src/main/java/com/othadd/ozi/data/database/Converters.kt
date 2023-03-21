package com.othadd.ozi.data.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.othadd.ozi.Message
import java.lang.reflect.Type

class Converters {

    @TypeConverter
    fun messagesToString(messages: List<Message>): String{
        val gson = Gson()
        return gson.toJson(messages)
    }

    @TypeConverter
    fun stringToMessages(jsonString: String): List<Message> {
        val gson = Gson()
        val collectionType: Type = object : TypeToken<List<Message?>?>() {}.type
        return gson.fromJson(jsonString, collectionType)
    }

    @TypeConverter
    fun dialogStateToString(dialogState: DialogState): String{
        val gson = Gson()
        return gson.toJson(dialogState)
    }

    @TypeConverter
    fun stringToDialogState(jsonString: String): DialogState {
        val gson = Gson()
        return gson.fromJson(jsonString, DialogState::class.java)
    }
}