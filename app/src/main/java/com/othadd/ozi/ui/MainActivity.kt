package com.othadd.ozi.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.othadd.ozi.MessagingRepoX
import com.othadd.ozi.OziApplication
import com.othadd.ozi.R
import com.othadd.ozi.database.ChatDao
import com.othadd.ozi.network.NetworkApi
import com.othadd.ozi.network.USER_OFFLINE
import com.othadd.ozi.network.USER_ONLINE
import com.othadd.ozi.utils.SettingsRepo
import com.othadd.ozi.utils.WORKER_STATUS_UPDATE_KEY
import com.othadd.ozi.workers.UpdateStatusWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController

    private val sharedViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        sharedViewModel.darkMode.observe(this){
                AppCompatDelegate.setDefaultNightMode(if (it) MODE_NIGHT_YES else MODE_NIGHT_NO)
        }

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
        scheduleStatusUpdate(USER_ONLINE)
    }

    override fun onStop() {
        super.onStop()
        scheduleStatusUpdate(USER_OFFLINE)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun scheduleStatusUpdate(update: String){
        val workManager = WorkManager.getInstance(this)
        val workRequest = OneTimeWorkRequestBuilder<UpdateStatusWorker>()
            .setInputData(workDataOf(WORKER_STATUS_UPDATE_KEY to update))
            .build()

        workManager.enqueue(workRequest)
    }
}