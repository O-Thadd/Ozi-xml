package com.othadd.ozi.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.othadd.ozi.MessagingRepoX
import com.othadd.ozi.utils.WORKER_MESSAGE_KEY
import com.othadd.ozi.utils.stringToMessage

class SendChatMessageWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val message = stringToMessage(inputData.getString(WORKER_MESSAGE_KEY)!!)
            MessagingRepoX.sendMessageToServer(message)
            Result.success()
        } catch (throwable: Throwable) {
            Log.e("Worker send message", throwable.message.toString())
            Result.retry()
        }
    }
}