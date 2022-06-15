package com.othadd.ozi

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

const val MY_NOTIFICATION_CHANNEL_ID = "My Notification Channel ID"
const val MY_NOTIFICATION_ID = 1

fun NotificationManager.sendNotification(messageBody: String, context: Context){
    val builder = NotificationCompat.Builder(context, MY_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.notification_image)
        .setContentTitle("New Message!")
        .setContentText(messageBody)

    notify(MY_NOTIFICATION_ID, builder.build())
}