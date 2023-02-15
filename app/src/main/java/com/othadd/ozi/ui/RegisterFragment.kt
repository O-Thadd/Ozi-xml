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
import com.othadd.ozi.utils.showToast
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterFragment : Fragment() {


    private var checkedUsername = ""
    private val sharedViewModel: ChatViewModel by activityViewModels()

    private lateinit var binding: FragmentRegisterBinding
    private lateinit var includedBinding: RegisterDialogBinding

    private lateinit var usernameEditText: TextView
    private lateinit var fragmentMotionLayout: MotionLayout
    private lateinit var genderTextview: TextView
    private lateinit var genderSelectionPopup: LinearLayout
    private lateinit var signupTextView: TextView
    private lateinit var registerLoadingIconImageView: ImageView

    private lateinit var checkUsernameImageView: ImageView
    private lateinit var genderTextView: TextView
    private lateinit var maleOptionTextView: TextView
    private lateinit var femaleOptionTextView: TextView

    private lateinit var registerIconAnimator: ObjectAnimator
    private lateinit var usernameCheckAnimator: ObjectAnimator

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

        fragmentMotionLayout = binding.constraintLayout

        includedBinding.apply {
            this@RegisterFragment.checkUsernameImageView = checkUsernameImageView
            this@RegisterFragment.genderTextView = genderTextView
            maleOptionTextView = maleTextView
            femaleOptionTextView = femaleTextView

            genderSelectionPopup = genderSelectionDialogLinearLayout
            this@RegisterFragment.usernameEditText = usernameEditText
            genderTextview = genderTextView
            signupTextView = signUpTextView
            this@RegisterFragment.registerLoadingIconImageView = registerLoadingIconImageView
        }

        usernameCheckAnimator =
        ObjectAnimator.ofFloat(checkUsernameImageView, View.ROTATION, -360f, 0f).apply {
            repeatCount = ObjectAnimator.INFINITE
            duration = 300
        }

        registerIconAnimator =
        ObjectAnimator.ofFloat(registerLoadingIconImageView, View.ROTATION, -360f, 0f).apply {
            repeatCount = ObjectAnimator.INFINITE
            duration = 300
        }

        sharedViewModel.registerFragmentUIState.observe(viewLifecycleOwner) {
            if (it.userIsRegistered) {
                findNavController().navigate(RegisterFragmentDirections.actionRegisterFragmentToChatsFragment())
            }
            handleUsernameCheckStatus(it.usernameCheckStatus)
            handleRegisteringStatus(it.registrationStatus)
            signupTextView.isEnabled = it.signUpConditionsMet
            if (it.genderPopupShowing) showGenderSelectionPopup() else hideGenderSelectionPopup()
        }

        genderTextView.setOnClickListener {
            sharedViewModel.toggleGenderSelectionPopup()
        }

        maleOptionTextView.setOnClickListener {
            genderTextView.text = "Male"
            sharedViewModel.updateGenderSelected(true)
            sharedViewModel.hideGenderSelectionPopup()
        }

        femaleOptionTextView.setOnClickListener {
            genderTextView.text = "Female"
            sharedViewModel.updateGenderSelected(true)
            sharedViewModel.hideGenderSelectionPopup()
        }

        includedBinding.usernameEditText.setOnClickListener {
            sharedViewModel.resetUsernameCheck()
        }

        usernameEditText.addTextChangedListener{
            checkUsername()
        }
    }

    private fun handleUsernameCheckStatus(status: Int) {
        when (status) {
            BUSY -> startAnimation(checkUsernameImageView, usernameCheckAnimator)
            PASSED -> stopAnimationWithSuccess(checkUsernameImageView, usernameCheckAnimator)
            FAILED -> {
                stopAnimationWithFailure(checkUsernameImageView, usernameCheckAnimator)
                showToast(requireContext(), "'$checkedUsername' has been taken. Try a different username")
            }
            ERROR -> {
                stopAnimationWithFailure(checkUsernameImageView, usernameCheckAnimator)
                showToast(requireContext(), "username verification failed. Check network and try again.")
            }
            DEFAULT -> resetAnimation(checkUsernameImageView, usernameCheckAnimator)
        }
    }

    private fun handleRegisteringStatus(status: Int) {
        when (status) {
            BUSY -> startRegistrationAnimation()
            PASSED -> stopRegistrationAnimationWithSuccess()
            FAILED -> {
                stopRegistrationAnimationWithFailure()
                showToast(requireContext(), "SignUp failed. Check network and try again.")
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

    private fun showGenderSelectionPopup(genderSelectionMiniDialog: LinearLayout = genderSelectionPopup) {
        usernameEditText.isEnabled = false
        genderSelectionMiniDialog.visibility = View.VISIBLE

        val moveUpPropertyValueHolder = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, genderSelectionMiniDialog.height.toFloat(),  0f)
        val appearPropertyValuesHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f)
        val showGenderSelectionAnimator = ObjectAnimator.ofPropertyValuesHolder(
            genderSelectionMiniDialog,
            moveUpPropertyValueHolder,
            appearPropertyValuesHolder
        )
        showGenderSelectionAnimator.start()
    }

    private fun hideGenderSelectionPopup(genderSelectionMiniDialog: LinearLayout = genderSelectionPopup) {
        usernameEditText.isEnabled = true
        genderSelectionMiniDialog.visibility = View.GONE

        val movePropertyValueHolder = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, genderSelectionMiniDialog.height.toFloat())
        val alphaPropertyValuesHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 0.0f)
        val hideGenderSelectionAnimator = ObjectAnimator.ofPropertyValuesHolder(
            genderSelectionMiniDialog,
            movePropertyValueHolder,
            alphaPropertyValuesHolder
        )
        hideGenderSelectionAnimator.start()
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
            checkedUsername = username
            sharedViewModel.checkUsername(username)
        }
    }

    fun onCloseButtonPressed(){
        sharedViewModel.hideGenderSelectionPopup()
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