package com.othadd.ozi.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.othadd.ozi.OziApplication
import com.othadd.ozi.R
import com.othadd.ozi.databinding.ActivityMainBinding
import com.othadd.ozi.data.network.USER_OFFLINE
import com.othadd.ozi.data.network.USER_ONLINE
import com.othadd.ozi.utils.WORKER_STATUS_UPDATE_KEY
import com.othadd.ozi.workers.UpdateStatusWorker
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController
    private val sharedViewModel: ChatViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding

    private lateinit var snackBarActionButton: TextView
    private lateinit var snackBarCloseButton: ImageView
    private lateinit var snackBar: LinearLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apply {
            lifecycleOwner = this@MainActivity
            viewModel = sharedViewModel
            snackBarActionButton = snackBarActionButtonTextView
            snackBarCloseButton = closeSnackBarButtonImageView
            snackBar = snackbarLinearLayout
        }

        sharedViewModel.darkMode.observe(this){
                AppCompatDelegate.setDefaultNightMode(if (it) MODE_NIGHT_YES else MODE_NIGHT_NO)
        }

        val fragmentContainerView = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = fragmentContainerView.navController

        val senderId = intent.getStringExtra("senderId")
        if (senderId != null){
            sharedViewModel.startChatFromActivity(senderId)
        }

        setUpSnackBar()
    }

    private fun setUpSnackBar() {
        sharedViewModel.snackBarStateX.observe(this) {
            when {
                it.showActionButton -> {
                    snackBarActionButton.visibility = View.VISIBLE
                    snackBarCloseButton.visibility = View.VISIBLE
                    showSnackBar()
                }

                !it.showActionButton && it.message != "" -> {
                    snackBarActionButton.visibility = View.GONE
                    snackBarCloseButton.visibility = View.GONE
                    showSnackBar()
                }

                it.message == "" -> {
                    hideSnackBar()
                }
            }
        }
    }

    private fun hideSnackBar() {
        val moveSnackBarUpAnimator =
            ObjectAnimator.ofFloat(snackBar, View.TRANSLATION_Y, -((2.5 * snackBar.height).toFloat()))
        val decreaseSnackBarAlphaAnimator = ObjectAnimator.ofFloat(snackBar, View.ALPHA, 1.0f, 0.0f)

        val generalAnimatorSet = AnimatorSet()
        generalAnimatorSet.playTogether(moveSnackBarUpAnimator, decreaseSnackBarAlphaAnimator)
        generalAnimatorSet.start()
    }

    private fun showSnackBar() {
        val moveSnackBarDownAnimator =
            ObjectAnimator.ofFloat(snackBar, View.TRANSLATION_Y, ((2.5 * snackBar.height).toFloat()))
        val increaseSnackBarAlphaAnimator = ObjectAnimator.ofFloat(snackBar, View.ALPHA, 0.0f, 1.0f)

        val generalAnimatorSet = AnimatorSet()
        generalAnimatorSet.playTogether(moveSnackBarDownAnimator, increaseSnackBarAlphaAnimator)
        generalAnimatorSet.start()
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