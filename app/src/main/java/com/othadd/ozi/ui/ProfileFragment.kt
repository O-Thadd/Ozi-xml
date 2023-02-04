package com.othadd.ozi.ui

import android.animation.ObjectAnimator
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.activityViewModels
import com.othadd.ozi.MessagingRepoX
import com.othadd.ozi.OziApplication
import com.othadd.ozi.database.ChatDao
import com.othadd.ozi.databinding.FragmentProfileBinding
import com.othadd.ozi.network.MALE
import com.othadd.ozi.utils.SettingsRepo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private val sharedViewModel: ChatViewModel by activityViewModels()

    private lateinit var binding: FragmentProfileBinding


    private lateinit var loadingComponents: LinearLayout
    private lateinit var loadingIcon: ImageView
    private lateinit var couldNotFetchTextView: TextView
    private lateinit var tryAgainTextViewButton: TextView
    private lateinit var snackBar: LinearLayout
    private lateinit var snackBarActionButton: TextView
    private lateinit var snackBarCloseButton: ImageView
    private lateinit var profileDetailsGroup: ConstraintLayout

    private lateinit var animator: ObjectAnimator
    private var snackBarIsShowing = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentProfileBinding.inflate(inflater, container, false)

        binding.apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = sharedViewModel
            profileFragment = this@ProfileFragment
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadingComponents = binding.loadingElementsLinearLayout
        loadingIcon = binding.loadingIconImageView
        couldNotFetchTextView = binding.couldNotFetchProfileTextView
        tryAgainTextViewButton = binding.tryAgainButtonTextView
        profileDetailsGroup = binding.profileDetailsGroupConstraintLayout
//        snackBar = binding.snackBarLinearLayout
//        snackBarActionButton = binding.snackBarActionButtonTextView
//        snackBarCloseButton = binding.closeSnackBarButtonImageView

        animator = ObjectAnimator.ofFloat(loadingIcon, View.ROTATION, -360f, 0f)
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.duration = 300

        sharedViewModel.profile.observe(viewLifecycleOwner){
            binding.apply {
                usernameTextView.text = it.username
                genderTextView.text = if(it.gender == MALE) "Male" else "Female"
                onlineTextView.text = if (it.onlineStatus) "Online" else "Offline"
                verificationTextView.text = if(it.verificationStatus) "Verified" else "Not Verified"
            }
        }

        sharedViewModel.profileFetchStatus.observe(viewLifecycleOwner) {
            when (it) {
                BUSY -> startAnimation()
                PASSED -> stopAnimationWithSuccess()
                FAILED -> stopAnimationWithFailure()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sharedViewModel.getProfile()
    }

    private fun stopAnimationWithFailure() {
        profileDetailsGroup.visibility = View.GONE
        loadingComponents.visibility = View.GONE
        couldNotFetchTextView.visibility = View.VISIBLE
        tryAgainTextViewButton.visibility = View.VISIBLE
        animator.cancel()
    }

    private fun stopAnimationWithSuccess() {
        profileDetailsGroup.visibility = View.VISIBLE
        loadingComponents.visibility = View.GONE
        couldNotFetchTextView.visibility = View.GONE
        tryAgainTextViewButton.visibility = View.GONE
        animator.cancel()
    }

    private fun startAnimation() {
        profileDetailsGroup.visibility = View.GONE
        loadingComponents.visibility = View.VISIBLE
        couldNotFetchTextView.visibility = View.GONE
        tryAgainTextViewButton.visibility = View.GONE
        animator.start()
    }
}