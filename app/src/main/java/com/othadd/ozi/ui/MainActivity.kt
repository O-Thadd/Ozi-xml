package com.othadd.ozi.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.othadd.ozi.MyFirebaseMessagingService
import com.othadd.ozi.R
import com.othadd.ozi.pushNotification.FCMRestarter
import com.othadd.ozi.pushNotification.RESTART_FCM_SERVICE


class MainActivity : AppCompatActivity() {
    lateinit var navController: NavController
    private lateinit var myFirebaseMessagingService: MyFirebaseMessagingService
    lateinit var myServiceIntent: Intent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fragmentContainerView = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = fragmentContainerView.navController

        myFirebaseMessagingService = MyFirebaseMessagingService()
        myServiceIntent = Intent(this, MyFirebaseMessagingService::class.java)
        if (!isMyServiceRunning(MyFirebaseMessagingService::class.java)) {
            startService(myServiceIntent)
        }

    }

    override fun onDestroy() {
        val broadcastIntent = Intent();
        broadcastIntent.action = RESTART_FCM_SERVICE
        broadcastIntent.setClass(this, FCMRestarter::class.java)
        this.sendBroadcast(broadcastIntent)
        super.onDestroy()
    }

    private fun isMyServiceRunning(myServiceClass: Class<MyFirebaseMessagingService>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (myServiceClass.name.equals(service.service.className)) {
                return true
            }
        }
        return false
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}