package com.othadd.ozi.workers

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.othadd.ozi.MessagingRepo
import com.othadd.ozi.OziApplication
import com.othadd.ozi.sendNotification
import kotlinx.coroutines.*

class GetMessagesWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {

        return try {
            val appContext = applicationContext
            val messagingRepo = MessagingRepo.getInstance(appContext as OziApplication)
            messagingRepo.getMessages(appContext)

            Result.success()
        } catch (throwable: Throwable) {
            Log.e("worker", throwable.message.toString())
            Result.failure()
        }
    }


}