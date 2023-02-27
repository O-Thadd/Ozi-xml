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
import androidx.navigation.fragment.findNavController
import com.othadd.ozi.MessagingRepoX
import com.othadd.ozi.OziApplication
import com.othadd.ozi.database.ChatDao
import com.othadd.ozi.databinding.FragmentProfileBinding
import com.othadd.ozi.network.MALE
import com.othadd.ozi.network.User
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
    private lateinit var profileDetailsGroup: ConstraintLayout

    private lateinit var animator: ObjectAnimator

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

//        loadingComponents = binding.loadingElementsLinearLayout
//        loadingIcon = binding.loadingIconImageView
//        couldNotFetchTextView = binding.couldNotFetchProfileTextView
//        tryAgainTextViewButton = binding.tryAgainButtonTextView
//        profileDetailsGroup = binding.profileDetailsGroupConstraintLayout

        binding.apply {
            loadingComponents = loadingElementsLinearLayout
            loadingIcon = loadingIconImageView
            couldNotFetchTextView = couldNotFetchProfileTextView
            tryAgainTextViewButton = tryAgainButtonTextView
            profileDetailsGroup = profileDetailsGroupConstraintLayout
        }

        animator = ObjectAnimator.ofFloat(loadingIcon, View.ROTATION, -360f, 0f)
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.duration = 300

        sharedViewModel.profileFragmentUIState.observe(viewLifecycleOwner){
            handleProfile(it.profile)
            handleProfileFetchStatus(it.fetchStatus)
        }

        sharedViewModel.navigateToChatFragment.observe(viewLifecycleOwner){
            if (it) {
                findNavController().navigate(ProfileFragmentDirections.actionProfileFragmentToChatFragment())
            }
        }
    }

    private fun handleProfileFetchStatus(status: Int) {
        when (status) {
            BUSY -> startAnimation()
            PASSED -> stopAnimationWithSuccess()
            FAILED -> stopAnimationWithFailure()
        }
    }

    private fun handleProfile(profile: User?) {
        profile ?: return
        binding.apply {
            usernameTextView.text = profile.username
            genderTextView.text = if (profile.gender == MALE) "Male" else "Female"
            onlineTextView.text = if (profile.onlineStatus) "Online" else "Offline"
            verificationTextView.text =
                if (profile.verificationStatus) "Verified" else "Not Verified"
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