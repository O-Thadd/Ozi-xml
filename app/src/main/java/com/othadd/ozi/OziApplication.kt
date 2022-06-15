package com.othadd.ozi

import android.app.Application
import com.othadd.ozi.database.ChatRoomDatabase

class OziApplication : Application(){
    val database: ChatRoomDatabase by lazy { ChatRoomDatabase.getDatabase(this) }

//   private lateinit var instance: OziApplication
//
//    override fun onCreate() {
//        instance = this
//        super.onCreate()
//    }
//
////    fun getInstance(): OziApplication{
////        return instance
////    }
//
//    fun getInstance() = instance
}