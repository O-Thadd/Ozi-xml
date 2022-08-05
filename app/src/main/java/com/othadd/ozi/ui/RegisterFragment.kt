package com.othadd.ozi.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.othadd.ozi.*
import com.othadd.ozi.databinding.FragmentRegisterBinding
import com.othadd.ozi.databinding.RegisterDialogBinding

class RegisterFragment : Fragment() {

    private val sharedViewModel: ChatViewModel by activityViewModels {
        ChatViewModelFactory(
            SettingsRepo(requireContext()),
            MessagingRepo.getInstance((activity?.application as OziApplication)),
            activity?.application as OziApplication
        )
    }

    private lateinit var binding: FragmentRegisterBinding
    private lateinit var includedBinding: RegisterDialogBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentRegisterBinding.inflate(inflater, container, false)
        includedBinding = binding.signUpDialogConstraintLayout

        binding.apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = sharedViewModel
            registerFragment = this@RegisterFragment
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedViewModel.userIsRegistered.observe(viewLifecycleOwner) {
            if (it) {
                findNavController().navigate(RegisterFragmentDirections.actionRegisterFragmentToChatsFragment())
            }
        }

        val checkUsernameImageView = includedBinding.checkUsernameImageView
        val genderTextView = includedBinding.genderTextView
        val genderSelectionPopup = includedBinding.genderSelectionDialogLinearLayout
        val maleOptionTextView = includedBinding.maleTextView
        val femaleOptionTextView = includedBinding.femaleTextView
        val signUpButton = includedBinding.signUpTextView

        val usernameCheckAnimator = ObjectAnimator.ofFloat(checkUsernameImageView, View.ROTATION, -360f, 0f)
        usernameCheckAnimator.repeatCount = ObjectAnimator.INFINITE
        usernameCheckAnimator.duration = 300

        genderTextView.setOnClickListener {
            if (sharedViewModel.genderSelectionPopupIsShowing){
                hideGenderSelectionPopup(genderSelectionPopup)
            }
            else{
                showGenderSelectionPopup(genderSelectionPopup)
            }
        }

        maleOptionTextView.setOnClickListener {
            genderTextView.text = "Male"
            hideGenderSelectionPopup(genderSelectionPopup)
            sharedViewModel.genderHasBeenSelected = true
            sharedViewModel.updateSignUpConditionsStatus()
        }

        femaleOptionTextView.setOnClickListener {
            genderTextView.text = "Female"
            hideGenderSelectionPopup(genderSelectionPopup)
            sharedViewModel.genderHasBeenSelected = true
            sharedViewModel.updateSignUpConditionsStatus()
        }

        sharedViewModel.usernameCheckStatus.observe(viewLifecycleOwner){
            when(it){
                USERNAME_CHECK_CHECKING -> startAnimation(checkUsernameImageView, usernameCheckAnimator)
                USERNAME_CHECK_PASSED -> stopAnimationWithSuccess(checkUsernameImageView, usernameCheckAnimator)
                USERNAME_CHECK_FAILED -> stopAnimationWithFailure(checkUsernameImageView, usernameCheckAnimator)
                USERNAME_CHECK_UNDONE -> resetAnimation(checkUsernameImageView, usernameCheckAnimator)
            }
        }

        includedBinding.usernameEditText.setOnClickListener {
            sharedViewModel.resetUsernameCheck()
        }

        sharedViewModel.signUpConditionsMet.observe(viewLifecycleOwner){
            signUpButton.isEnabled = it
        }
    }

    private fun showGenderSelectionPopup(genderSelectionMiniDialog: LinearLayout) {
        val moveUpPropertyValueHolder = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -200f)
        val appearPropertyValuesHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f)
        val showGenderSelectionAnimator = ObjectAnimator.ofPropertyValuesHolder(
            genderSelectionMiniDialog,
            moveUpPropertyValueHolder,
            appearPropertyValuesHolder
        )
//        showGenderSelectionAnimator.duration = 1000
        showGenderSelectionAnimator.start()
        sharedViewModel.genderSelectionPopupIsShowing = true
    }

    private fun hideGenderSelectionPopup(genderSelectionMiniDialog: LinearLayout) {
        val movePropertyValueHolder = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 200f)
        val alphaPropertyValuesHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 0.0f)
        val animator = ObjectAnimator.ofPropertyValuesHolder(
            genderSelectionMiniDialog,
            movePropertyValueHolder,
            alphaPropertyValuesHolder
        )
        animator.start()
        sharedViewModel.genderSelectionPopupIsShowing = false
    }

    private fun resetAnimation(checkUsernameImageView: ImageView, animator: ObjectAnimator?) {
        checkUsernameImageView.setImageResource(R.drawable.ic_check_username)
        animator?.cancel()
        checkUsernameImageView.rotation = 0f
    }

    private fun stopAnimationWithFailure(
        checkUsernameImageView: ImageView,
        animator: ObjectAnimator?
    ) {
        checkUsernameImageView.setImageResource(R.drawable.ic_username_failed)
        animator?.cancel()
        checkUsernameImageView.rotation = 0f
    }

    private fun stopAnimationWithSuccess(
        checkUsernameImageView: ImageView,
        animator: ObjectAnimator?
    ) {
        checkUsernameImageView.setImageResource(R.drawable.ic_username_checked)
        animator?.cancel()
        checkUsernameImageView.rotation = 0f
    }

    private fun startAnimation(checkUsernameImageView: ImageView, animator: ObjectAnimator?) {
        checkUsernameImageView.setImageResource(R.drawable.ic_checking_username)
        animator?.start()
        checkUsernameImageView.rotation = 0f
    }

    fun registerUser() {
        sharedViewModel.registerUser(includedBinding.usernameEditText.text.toString(), includedBinding.genderTextView.text.toString())
    }

    fun checkUsername(){
        val username = includedBinding.usernameEditText.text.toString()
        if (username.isNotBlank()){
            sharedViewModel.checkUsername(username)
        }
        sharedViewModel.updateSignUpConditionsStatus()
    }
}