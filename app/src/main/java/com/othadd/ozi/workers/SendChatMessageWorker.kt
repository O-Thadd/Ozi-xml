package com.othadd.ozi.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.othadd.ozi.OziApplication
import com.othadd.ozi.Service
import com.othadd.ozi.data.network.NetworkApi
import com.othadd.ozi.data.repos.MessageRepo
import com.othadd.ozi.utils.WORKER_MESSAGE_KEY
import com.othadd.ozi.utils.stringToMessage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*

@HiltWorker
class SendChatMessageWorker @AssistedInject constructor(
    @Assisted appContext: Context, @Assisted workerParams: WorkerParameters, val service: Service, val messageRepo: MessageRepo) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val scope = CoroutineScope(Dispatchers.Main + Job())
        var result: Result? = null
        scope.launch {
            result = try {
                val message = stringToMessage(inputData.getString(WORKER_MESSAGE_KEY)!!)
                messageRepo.sendMessageToServer(message)
                service.markMessagesSent(message.receiverId)
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