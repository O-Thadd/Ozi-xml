package com.othadd.ozi.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.othadd.ozi.MessagingRepoX
import com.othadd.ozi.OziApplication
import com.othadd.ozi.database.ChatDao
import com.othadd.ozi.utils.SettingsRepo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {


    private val sharedViewModel: ChatViewModel by viewModels()

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