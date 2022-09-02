package com.othadd.ozi.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.othadd.ozi.OziApplication
import com.othadd.ozi.R
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.network.USER_OFFLINE
import com.othadd.ozi.network.USER_ONLINE
import com.othadd.ozi.utils.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController
    private val sharedViewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(
            application as OziApplication
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val fragmentContainerView = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = fragmentContainerView.navController

        val senderId = intent.getStringExtra("senderId")
        if (senderId != null){
            sharedViewModel.startChatFromActivity(senderId)
        }
    }

    override fun onResume() {
        super.onResume()
        OziApplication.inForeGround = true
    }

    override fun onPause() {
        super.onPause()
        OziApplication.inForeGround = false
    }

    override fun onStart() {
        super.onStart()
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
            try {
                NetworkApi.retrofitService.updateStatus(SettingsRepo(this@MainActivity).getUserId(), USER_ONLINE)
            }
            catch (e: Exception){
                Log.e("mainActivity", "exception updating user online. $e")
            }

        }
    }

    override fun onStop() {
        super.onStop()
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
            try {
                NetworkApi.retrofitService.updateStatus(SettingsRepo(this@MainActivity).getUserId(), USER_OFFLINE)
            }
            catch (e: Exception){
                Log.e("mainActivity", "exception updating user online. $e")
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}