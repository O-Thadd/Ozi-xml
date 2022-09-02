package com.othadd.ozi

import android.app.Application
import com.othadd.ozi.database.ChatRoomDatabase

class OziApplication : Application(){
    val database: ChatRoomDatabase by lazy { ChatRoomDatabase.getDatabase(this) }

    companion object{
        var inForeGround = false
    }
}