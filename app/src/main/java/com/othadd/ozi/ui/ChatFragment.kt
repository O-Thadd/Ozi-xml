package com.othadd.ozi.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import com.othadd.ozi.*
import com.othadd.ozi.database.NOTIFY_DIALOG_TYPE
import com.othadd.ozi.database.PROMPT_DIALOG_TYPE
import com.othadd.ozi.databinding.FragmentChatBinding

class ChatFragment : Fragment() {

    private val sharedViewModel: ChatViewModel by activityViewModels {
        ChatViewModelFactory(
            SettingsRepo(requireContext()),
            MessagingRepo.getInstance((activity?.application as OziApplication)),
            activity?.application as OziApplication
        )
    }

    private lateinit var binding: FragmentChatBinding
    private lateinit var messagesRecyclerAdapter: MessagesRecyclerAdapter
    private lateinit var confirmSendGameRequestDialog: ConstraintLayout
    private lateinit var promptDialog: ConstraintLayout
    private lateinit var notifyDialog: ConstraintLayout
    private lateinit var screenView: View
    private lateinit var backPressedCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This callback will only be called when MyFragment is at least Started.
        backPressedCallback = requireActivity().onBackPressedDispatcher.addCallback(this) {
            doNothing()
        }
        backPressedCallback.isEnabled = false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentChatBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        sharedViewModel.refreshMessages("Could not refresh messages")
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        messagesRecyclerAdapter = MessagesRecyclerAdapter()

        binding.apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = sharedViewModel
            messagesRecyclerView.adapter = messagesRecyclerAdapter
            chatFragment = this@ChatFragment
        }

        confirmSendGameRequestDialog = binding.confirmSendGameDialogConstraintLayout
        promptDialog = binding.promptDialogConstraintLayout
        notifyDialog = binding.notifyDialogConstraintLayout
        screenView = binding.screenView

        sharedViewModel.messages.observe(viewLifecycleOwner){
            messagesRecyclerAdapter.submitList(it)
        }

        observeDataForDialog(sharedViewModel.showConfirmGameRequestDialog, confirmSendGameRequestDialog)

        sharedViewModel.chat.observe(viewLifecycleOwner){
            when(it.dialogState.dialogType){

                NOTIFY_DIALOG_TYPE -> {
                    if (it.dialogState.showOkayButton){
                        binding.notifyDialogOkButtonTextView.visibility = View.VISIBLE
                    }
                    else{
                        binding.notifyDialogOkButtonTextView.visibility = View.GONE
                    }
                    showDialog(notifyDialog)
                }

                PROMPT_DIALOG_TYPE -> {
                    showDialog(promptDialog)
                }

//                no dialog state
                else -> {
                    hideAllDialogs()
                }
            }
        }

    }

    private fun viewIsVisible(view: View?): Boolean {
//        if (view == null) {
//            return false
//        }
//        if (!view.isShown) {
//            return false
//        }
//        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
//        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
//        val actualPosition = Rect()
//        view.getGlobalVisibleRect(actualPosition)
//        val screen = Rect(0, 0, screenWidth, screenHeight)
//        return actualPosition.intersect(screen)

        return view?.alpha != 0.0f
    }

    private fun observeDataForDialog(data: LiveData<Boolean>, dialog: View){
        data.observe(viewLifecycleOwner){
            if (it){
                showDialog(dialog)
            }
            else{
                hideDialog(dialog)
            }
        }
    }

    fun sendMessage() {
        if(!binding.newMessageEditText.text.isNullOrBlank()){
            sharedViewModel.sendMessage(binding.newMessageEditText.text.toString())
            binding.newMessageEditText.text?.clear()
            scrollToRecyclerViewBottom()
        }
    }

    private fun scrollToRecyclerViewBottom() {
        val listSize = binding.messagesRecyclerView.adapter?.itemCount
        if (listSize != null)
            binding.messagesRecyclerView.scrollToPosition(listSize - 1)
    }

    fun cancelSendGameRequest(){
        sharedViewModel.cancelSendGameRequest()
    }

    fun sendGameRequest(){
        sharedViewModel.sendGameRequest()
    }

    fun okayAfterContDownEnded(){
//        sharedViewModel.okayAfterCountdownEnded()
    }

    private fun showDialog(dialog: View){

        if (viewIsVisible(dialog)){
            return
        }

        hideAllDialogs()
        backPressedCallback.isEnabled = true
        screenView.visibility = View.VISIBLE
        hideKeyboard()

        val movePropertyValueHolder = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -1200f)
        val transparencyValueHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f)
        val animator = ObjectAnimator.ofPropertyValuesHolder(dialog, movePropertyValueHolder, transparencyValueHolder)
        animator.duration = 500
        animator.interpolator = OvershootInterpolator()
        animator.start()
    }

    private fun hideDialog(dialog: View){

        if (!viewIsVisible(dialog)){
            return
        }

        backPressedCallback.isEnabled = false
        screenView.visibility = View.GONE

        val movePropertyValueHolder = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 1200f)
        val transparencyValueHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 0.0f)
        val animator = ObjectAnimator.ofPropertyValuesHolder(dialog, movePropertyValueHolder, transparencyValueHolder)
        animator.start()
    }

    private fun hideAllDialogs() {
        hideDialog(confirmSendGameRequestDialog)
        hideDialog(promptDialog)
        hideDialog(notifyDialog)
    }

    private fun hideKeyboard(){
        val inputMethodManager = requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.newMessageEditText.windowToken, 0)
    }

    fun doNothing(){
        //do nothing
    }

}