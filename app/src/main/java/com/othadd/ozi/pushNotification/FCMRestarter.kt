package com.othadd.ozi.pushNotification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.othadd.ozi.MyFirebaseMessagingService


const val RESTART_FCM_SERVICE = "restartFCM"

class FCMRestarter: BroadcastReceiver() {
    override fun onReceive(context: Context?, p1: Intent?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context?.startForegroundService(Intent(context, MyFirebaseMessagingService::class.java))
        } else {
            context?.startService(Intent(context, MyFirebaseMessagingService::class.java))
        }
    }


}