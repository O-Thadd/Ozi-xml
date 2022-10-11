package com.othadd.ozi.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.utils.SettingsRepo
import com.othadd.ozi.utils.WORKER_STATUS_UPDATE_KEY

class UpdateStatusWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val update = inputData.getString(WORKER_STATUS_UPDATE_KEY)
            sendUpdate(update!!)
            Result.success()
        } catch (throwable: Throwable) {
            Log.e("Worker update status", throwable.message.toString())
            val serverIsReachable: Boolean = try { NetworkApi.retrofitService.ping() } catch (e: Exception) { false }
            if (serverIsReachable){ Result.failure() }
            else{ Result.retry() }
        }
    }

    private suspend fun sendUpdate(update: String){
        val thisUserId = SettingsRepo(applicationContext).getUserId()
        NetworkApi.retrofitService.updateStatus(thisUserId, update)
    }
}