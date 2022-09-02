package com.othadd.ozi

import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.othadd.ozi.network.sendFCMToken
import com.othadd.ozi.workers.GetMessagesWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val workManager = WorkManager.getInstance(this)
        workManager.enqueue(OneTimeWorkRequest.from(GetMessagesWorker::class.java))
    }

    override fun onNewToken(token: String) {
//        val scope = CoroutineScope(Job() + Dispatchers.Main)
    }
}