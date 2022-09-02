package com.othadd.ozi.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.othadd.ozi.MessagingRepoX
import com.othadd.ozi.OziApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class
GetMessagesWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    override fun doWork(): Result {
        val appContext = applicationContext as OziApplication
        return runBlocking(Dispatchers.Main) {
            try {
                MessagingRepoX.refreshMessages(appContext)
                Result.success()
            } catch (throwable: Throwable) {
                Log.e("getWorker", throwable.message.toString())
                Result.failure()
            }
        }
    }
}