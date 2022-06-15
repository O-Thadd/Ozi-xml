package com.othadd.ozi.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.BounceInterpolator
import android.view.animation.OvershootInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import com.othadd.ozi.*
import com.othadd.ozi.databinding.FragmentChatBinding

class ChatFragment : Fragment() {

    private val sharedViewModel: ChatViewModel by activityViewModels {
        ChatViewModelFactory(
            SettingsRepo(requireContext()),
            MessagingRepo((activity?.application as OziApplication)),
            activity?.application as OziApplication
        )
    }

    private lateinit var binding: FragmentChatBinding
    private lateinit var messagesRecyclerAdapter: MessagesRecyclerAdapter
    private lateinit var confirmSendGameRequestDialog: ConstraintLayout
    private lateinit var countDownTimeDialog: ConstraintLayout
    private lateinit var countDownEndedDialog: ConstraintLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentChatBinding.inflate(inflater, container, false)

        return binding.root
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

        confirmSendGameRequestDialog = binding.confirmGameModeDialogConstraintLayout
        countDownTimeDialog = binding.gameRequestCountdownDialogConstraintLayout
        countDownEndedDialog = binding.countDownEndedDialogConstraintLayout

        sharedViewModel.messages.observe(viewLifecycleOwner){
            messagesRecyclerAdapter.submitList(it)
        }

//        dummy subscription. done just to get code to run in viewModel
        sharedViewModel.dummyLiveData.observe(viewLifecycleOwner){ }


        observeDataForDialog(sharedViewModel.showConfirmGameRequestDialog, confirmSendGameRequestDialog)
        observeDataForDialog(sharedViewModel.showCountDownDialog, countDownTimeDialog)
        observeDataForDialog(sharedViewModel.showCountDownEndedDialog, countDownEndedDialog)
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
            scrollToRecyclerViewBottom()
            sharedViewModel.sendMessage(binding.newMessageEditText.text.toString())
            binding.newMessageEditText.text?.clear()
        }
    }

    private fun scrollToRecyclerViewBottom() {
        val listSize = binding.messagesRecyclerView.adapter?.itemCount
        if (listSize != null)
            binding.messagesRecyclerView.scrollToPosition(listSize - 1)
    }

    fun confirmSendGameRequest(){
        sharedViewModel.confirmSendGameRequest()
    }

    fun cancelSendGameRequest(){
        sharedViewModel.cancelSendGameRequest()
    }

    fun sendGameRequest(){
        sharedViewModel.sendGameRequest()
    }

    fun okayAfterContDownEnded(){
        sharedViewModel.okayAfterCountdownEnded()
    }

    private fun showDialog(dialog: View){
        val movePropertyValueHolder = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -1200f)
        val transparencyValueHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f)
        val animator = ObjectAnimator.ofPropertyValuesHolder(dialog, movePropertyValueHolder, transparencyValueHolder)
        animator.duration = 500
        animator.interpolator = OvershootInterpolator()
        animator.start()
    }

    private fun hideDialog(dialog: View){
        val movePropertyValueHolder = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 1200f)
        val transparencyValueHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 0.0f)
        val animator = ObjectAnimator.ofPropertyValuesHolder(dialog, movePropertyValueHolder, transparencyValueHolder)
        animator.start()
    }

}