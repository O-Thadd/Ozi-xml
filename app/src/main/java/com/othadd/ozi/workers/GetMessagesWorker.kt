package com.othadd.ozi.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.othadd.ozi.MessagingRepoX
import com.othadd.ozi.OziApplication
import kotlinx.coroutines.*

class
GetMessagesWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    override fun doWork(): Result {
        val scope = CoroutineScope(Dispatchers.Main + Job())
        var result: Result? = null
        val appContext = applicationContext as OziApplication
        scope.launch {
            result = try {
                MessagingRepoX(appContext).refreshMessages()
                Result.success()
            } catch (throwable: Throwable) {
                Log.e("getWorker", throwable.message.toString())
                Result.failure()
            }
        }
        return result!!
    }
}