package com.othadd.ozi

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.othadd.ozi.data.database.ChatRoomDatabase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OziApplication : Application(), Configuration.Provider{
    val database: ChatRoomDatabase by lazy { ChatRoomDatabase.getDatabase(this) }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    companion object{
        var inForeGround = false
    }

    override fun getWorkManagerConfiguration(): Configuration = Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .build()
}