package com.othadd.ozi.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.othadd.ozi.*
import com.othadd.ozi.utils.SettingsRepo

class MainActivity : AppCompatActivity() {

    private val sharedViewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(
            SettingsRepo(applicationContext),
            MessagingRepo.getInstance((application as OziApplication)),
            application as OziApplication
        )
    }

    lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val fragmentContainerView = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = fragmentContainerView.navController

        val messageSenderId = intent.getStringExtra("senderId")
        if (messageSenderId != null) {
            sharedViewModel.setChatFromActivityIntent(messageSenderId)
        }

    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}