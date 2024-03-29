package com.othadd.ozi.utils

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.othadd.ozi.R

const val NEW_MESSAGE_NOTIFICATION_CHANNEL_ID = "New Message Notification Channel ID"
const val NEW_MESSAGE_NOTIFICATION_ID = 1
const val GAME_REQUEST_NOTIFICATION_CHANNEL_ID = "Game Request Notification Channel ID"
const val GAME_REQUEST_NOTIFICATION_ID = 2

fun NotificationManager.sendNewMessageNotification(messageBody: String, context: Context){
    val builder = NotificationCompat.Builder(context, context.getString(R.string.new_message_notification_channel_id))
        .setSmallIcon(R.drawable.ozi_icon_black_thick)
        .setContentTitle("New Message!")
        .setContentText(messageBody)

    notify(NEW_MESSAGE_NOTIFICATION_ID, builder.build())
}

fun NotificationManager.sendGameRequestNotification(messageBody: String, context: Context){
    val builder = NotificationCompat.Builder(context, GAME_REQUEST_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ozi_icon_black_thick)
        .setContentTitle("New Game Request!")
        .setContentText(messageBody)

    notify(GAME_REQUEST_NOTIFICATION_ID, builder.build())
}

fun NotificationManager.cancelAllNotifications(){
    cancelAll()
}

