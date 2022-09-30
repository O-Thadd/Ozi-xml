package com.othadd.ozi.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.othadd.ozi.OziApplication

class SplashActivity : AppCompatActivity() {

    private val sharedViewModel: ChatViewModel by viewModels {
        ChatViewModelFactory(
            application as OziApplication
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        sharedViewModel.darkMode.observe(this){
            AppCompatDelegate.setDefaultNightMode(if (it) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}