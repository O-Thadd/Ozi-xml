package com.othadd.ozi.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.othadd.ozi.*
import com.othadd.ozi.databinding.FragmentRegisterBinding
import com.othadd.ozi.databinding.RegisterDialogBinding

class RegisterFragment : Fragment() {

    private val sharedViewModel: ChatViewModel by activityViewModels {
        ChatViewModelFactory(
            activity?.application as OziApplication
        )
    }

    private lateinit var binding: FragmentRegisterBinding
    private lateinit var includedBinding: RegisterDialogBinding

    private lateinit var usernameEditText: TextView
    private lateinit var fragmentMotionLayout: MotionLayout
    private lateinit var genderTextview: TextView
    private lateinit var genderSelectionPopup: LinearLayout
    private lateinit var signupTextView: TextView
    private lateinit var registerLoadingIconImageView: ImageView

    private lateinit var registerIconAnimator: ObjectAnimator

    private var triggerCount = 0


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
        val maleOptionTextView = includedBinding.maleTextView
        val femaleOptionTextView = includedBinding.femaleTextView

        genderSelectionPopup = includedBinding.genderSelectionDialogLinearLayout
        usernameEditText = includedBinding.usernameEditText
        fragmentMotionLayout = binding.constraintLayout
        genderTextview = includedBinding.genderTextView
        signupTextView = includedBinding.signUpTextView
        registerLoadingIconImageView = includedBinding.registerLoadingIconImageView

        registerIconAnimator = ObjectAnimator.ofFloat(registerLoadingIconImageView, View.ROTATION, -360f, 0f)
        registerIconAnimator.repeatCount = ObjectAnimator.INFINITE
        registerIconAnimator.duration = 300

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
                BUSY -> startAnimation(checkUsernameImageView, usernameCheckAnimator)
                PASSED -> stopAnimationWithSuccess(checkUsernameImageView, usernameCheckAnimator)
                FAILED -> stopAnimationWithFailure(checkUsernameImageView, usernameCheckAnimator)
                DEFAULT -> resetAnimation(checkUsernameImageView, usernameCheckAnimator)
            }
        }

        includedBinding.usernameEditText.setOnClickListener {
            sharedViewModel.resetUsernameCheck()
        }

        sharedViewModel.signUpConditionsMet.observe(viewLifecycleOwner){
            signupTextView.isEnabled = it
        }

        usernameEditText.addTextChangedListener{
//            resetAnimation(checkUsernameImageView, usernameCheckAnimator)
//            sharedViewModel.resetSignUpConditionsMet()

            checkUsername()
        }

        sharedViewModel.registrationStatus.observe(viewLifecycleOwner){
            when(it){
                BUSY -> startRegistrationAnimation()
                PASSED -> stopRegistrationAnimationWithSuccess()
                FAILED -> stopRegistrationAnimationWithFailure()
            }
        }
    }

    private fun stopRegistrationAnimationWithSuccess() {
        signupTextView.visibility = View.GONE
        registerIconAnimator.cancel()
        registerLoadingIconImageView.rotation = 0f
        registerLoadingIconImageView.setImageResource(R.drawable.ic_message_sent)
        registerLoadingIconImageView.visibility = View.VISIBLE
    }

    private fun stopRegistrationAnimationWithFailure() {
        signupTextView.visibility = View.VISIBLE
        registerIconAnimator.cancel()
        registerLoadingIconImageView.visibility = View.GONE
    }

    private fun startRegistrationAnimation() {
        signupTextView.visibility = View.GONE
        registerLoadingIconImageView.visibility = View.VISIBLE
        registerIconAnimator.start()
    }

    private fun showGenderSelectionPopup(genderSelectionMiniDialog: LinearLayout) {
        usernameEditText.isEnabled = false
        genderSelectionMiniDialog.visibility = View.VISIBLE

        val moveUpPropertyValueHolder = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 150f)
        val appearPropertyValuesHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f)
        val showGenderSelectionAnimator = ObjectAnimator.ofPropertyValuesHolder(
            genderSelectionMiniDialog,
            moveUpPropertyValueHolder,
            appearPropertyValuesHolder
        )
        showGenderSelectionAnimator.start()
        sharedViewModel.genderSelectionPopupIsShowing = true
    }

    private fun hideGenderSelectionPopup(genderSelectionMiniDialog: LinearLayout) {
        usernameEditText.isEnabled = true
        genderSelectionMiniDialog.visibility = View.GONE

        val movePropertyValueHolder = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -150f)
        val alphaPropertyValuesHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 0.0f)
        val hideGenderSelectionAnimator = ObjectAnimator.ofPropertyValuesHolder(
            genderSelectionMiniDialog,
            movePropertyValueHolder,
            alphaPropertyValuesHolder
        )
        hideGenderSelectionAnimator.start()
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
        checkUsernameImageView.setImageResource(R.drawable.ic_busy_registeration)
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

    fun onCloseButtonPressed(){
        if (sharedViewModel.genderSelectionPopupIsShowing){
            hideGenderSelectionPopup(genderSelectionPopup)
        }
        fragmentMotionLayout.transitionToStart()
    }

    fun onTriggerButtonPressed(){
        triggerCount++
        if (triggerCount >= 15){
            binding.userIdInputEditText.visibility = View.VISIBLE
            binding.storeUserIdButton.visibility = View.VISIBLE
        }
    }

    fun onStoreUserIdButtonPressed(){
        sharedViewModel.saveUserId(binding.userIdInputEditText.text.toString())
    }
}