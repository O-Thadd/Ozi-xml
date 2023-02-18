package com.othadd.ozi.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.othadd.ozi.MessagingRepoX
import com.othadd.ozi.OziApplication
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.utils.SettingsRepo
import com.othadd.ozi.utils.WORKER_MESSAGE_KEY
import com.othadd.ozi.utils.stringToMessage
import kotlinx.coroutines.*

class SendChatMessageWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val scope = CoroutineScope(Dispatchers.Main + Job())
        var result: Result? = null
        scope.launch {
            result = try {
                val message = stringToMessage(inputData.getString(WORKER_MESSAGE_KEY)!!)
                MessagingRepoX(applicationContext as OziApplication).sendMessageToServer(message)
                SettingsRepo(applicationContext).updateMarkSent(true)
                SettingsRepo(applicationContext).updateMarkSent(false)
                Result.success()
            } catch (throwable: Throwable) {
                Log.e("Worker send chat message", throwable.message.toString())
                val serverIsReachable: Boolean = try { NetworkApi.retrofitService.ping() } catch (e: Exception) { false }
                if (serverIsReachable){ Result.failure() } else{ Result.retry() }
            }
        }
        return result!!
    }
}