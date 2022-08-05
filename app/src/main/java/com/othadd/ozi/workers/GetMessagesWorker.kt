package com.othadd.ozi.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.othadd.ozi.MessagingRepo
import com.othadd.ozi.OziApplication
import com.othadd.ozi.SettingsRepo
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.utils.showNetworkErrorToast
import kotlinx.coroutines.*

class GetMessagesWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    override fun doWork(): Result {
        val appContext = applicationContext
        return runBlocking(Dispatchers.Main) {
            try {
                val messagingRepo = MessagingRepo.getInstance(appContext as OziApplication)
                messagingRepo.getMessages(appContext)
                Result.success()
            } catch (throwable: Throwable) {
                Log.e("getWorker", throwable.message.toString())
                Result.failure()
            }
        }
    }
}