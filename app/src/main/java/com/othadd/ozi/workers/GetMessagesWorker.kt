package com.othadd.ozi.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.othadd.ozi.OziApplication
import com.othadd.ozi.Service
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*

@HiltWorker
class GetMessagesWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters, val service: Service) :
    Worker(context, params) {


    override fun doWork(): Result {
        val scope = CoroutineScope(Dispatchers.Main + Job())
        var result: Result? = null
        scope.launch {
            result = try {
                service.refreshMessages()
                Result.success()
            } catch (throwable: Throwable) {
                Log.e("getWorker", throwable.message.toString())
                Result.failure()
            }
        }
        return result!!
    }
}