package com.othadd.ozi.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.othadd.ozi.*
import com.othadd.ozi.database.ChatDao
import com.othadd.ozi.database.DialogState
import com.othadd.ozi.database.NOTIFY_DIALOG_TYPE
import com.othadd.ozi.database.PROMPT_DIALOG_TYPE
import com.othadd.ozi.databinding.FragmentChatBinding
import com.othadd.ozi.utils.SettingsRepo
import com.othadd.ozi.utils.showNetworkErrorToast
import com.othadd.ozi.utils.showToast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class ChatFragment : Fragment() {

    private val sharedViewModel: ChatViewModel by activityViewModels()

    private lateinit var binding: FragmentChatBinding
    private lateinit var messagesRecyclerAdapter: MessagesRecyclerAdapter
    private lateinit var confirmSendGameRequestDialog: ConstraintLayout
    private lateinit var promptDialog: ConstraintLayout
    private lateinit var notifyDialog: ConstraintLayout
    private lateinit var screenView: View
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var typeMessageEditTextGroup: ConstraintLayout
    private lateinit var newMessageGameModeEditText: EditText
    private lateinit var newMessageEditText: EditText

    private var chatSwitchBySnackBar = false
    private var chatFragmentWasClosed = true
    private lateinit var backPressedCallback: OnBackPressedCallback
    private lateinit var chatMateUserId: String

    private var gameModeKeyboardEnabled = false

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

        messagesRecyclerAdapter = MessagesRecyclerAdapter()

        binding.apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = sharedViewModel
            messagesRecyclerView.adapter = messagesRecyclerAdapter
            chatFragment = this@ChatFragment
        }

        return binding.root
    }

    override fun onStop() {
        super.onStop()
        sharedViewModel.updateChatFragmentShowing(false)
    }

    override fun onStart() {
        super.onStart()
        sharedViewModel.updateChatFragmentShowing(true)
    }

    override fun onResume() {
        super.onResume()
        sharedViewModel.refreshMessages()
        sharedViewModel.resetNavigateChatsToChatFragment()
        sharedViewModel.markMessagesRead()
        sharedViewModel.resetChatStartedByActivity()
        sharedViewModel.refreshUserStatus()

        if (chatFragmentWasClosed) {
            chatFragmentWasClosed = false
            scrollToRecyclerViewBottom()
        }
    }

    override fun onPause() {
        super.onPause()
        sharedViewModel.markMessagesRead()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            confirmSendGameRequestDialog = confirmSendGameDialogConstraintLayout
            promptDialog = promptDialogConstraintLayout
            notifyDialog = notifyDialogConstraintLayout
            screenView = dialogOverlayScreenView
            this@ChatFragment.messagesRecyclerView = messagesRecyclerView
            typeMessageEditTextGroup = bottomConstraintLayout
            this@ChatFragment.newMessageGameModeEditText = newMessageGameModeEditText
            this@ChatFragment.newMessageEditText = newMessageEditText
        }

        sharedViewModel.chat.observe(viewLifecycleOwner) { chat ->
            chatMateUserId = chat.chatMateId

            handleVerificationStatus(chat.verificationStatus)

            handleEmptyState(chat.messages.isEmpty())

            handleDialog(chat.dialogState)
        }

        sharedViewModel.markAllMessagesSent.observe(viewLifecycleOwner) {
            if (it) sharedViewModel.markMessagesSent()
        }

        sharedViewModel.shouldScrollChat.observe(viewLifecycleOwner) {
            if (it) { smoothScrollToRecyclerBottom() }
        }


        observeDataForDialog(
            sharedViewModel.showConfirmGameRequestDialog,
            confirmSendGameRequestDialog
        )


        sharedViewModel.navigateToChatsFragment.observe(viewLifecycleOwner) {
            if (it && chatSwitchBySnackBar) {
                chatSwitchBySnackBar = false
                findNavController().popBackStack()
            }
        }

        sharedViewModel.shouldEnableGameModeKeyboard.observe(viewLifecycleOwner){
            if (it) enableGameModeKeyboard() else disableGameModeKeyboard()
        }

        sharedViewModel.refreshError.observe(viewLifecycleOwner){
            if (it) {
                showToast(requireContext(), "could not refresh messages")
            }
        }

    }

    private fun handleDialog(dialog: DialogState) {
        when (dialog.dialogType) {
            NOTIFY_DIALOG_TYPE -> {
                if (dialog.showOkayButton) {
                    binding.notifyDialogOkButtonTextView.visibility = View.VISIBLE
                } else {
                    binding.notifyDialogOkButtonTextView.visibility = View.GONE
                }
                lifecycleScope.launch { showDialog(notifyDialog) }
            }

            PROMPT_DIALOG_TYPE -> {
                lifecycleScope.launch { showDialog(promptDialog) }
            }

    //                no dialog state
            else -> {
                hideAllDialogs()
            }
        }
    }

    private fun handleEmptyState(chatIsEmpty: Boolean) {
        if (chatIsEmpty) {
            messagesRecyclerView.visibility = View.GONE
            binding.emptyStateLinearLayout.visibility = View.VISIBLE
        } else {
            messagesRecyclerView.visibility = View.VISIBLE
            binding.emptyStateLinearLayout.visibility = View.GONE
        }
    }

    private fun handleVerificationStatus(status: Boolean) {
        binding.verificationStatusIndicatorImageView.visibility =
            if (status) View.VISIBLE else View.GONE
        binding.onlineTextTextView.visibility =
            if (status) View.VISIBLE else View.GONE
    }

    private fun viewIsVisible(view: View?): Boolean {
        return view?.alpha != 0.0f
    }

    private fun observeDataForDialog(data: LiveData<Boolean>, dialog: View) {
        data.observe(viewLifecycleOwner) {
            if (it) {
                lifecycleScope.launch { showDialog(dialog) }
            } else {
                hideDialog(dialog)
            }
        }
    }

    fun sendMessage() {
        if (!gameModeKeyboardEnabled){
            if (!binding.newMessageEditText.text.isNullOrBlank()) {
                sharedViewModel.sendMessage(binding.newMessageEditText.text.toString())
                binding.newMessageEditText.text?.clear()
            }
        }

        if (gameModeKeyboardEnabled){
            if (!binding.newMessageGameModeEditText.text.isNullOrBlank()) {
                sharedViewModel.sendMessage(binding.newMessageGameModeEditText.text.toString())
                binding.newMessageGameModeEditText.text?.clear()
            }
        }
    }

    private fun scrollToRecyclerViewBottom() {
        Handler(Looper.getMainLooper()).postDelayed({
            val listSize = binding.messagesRecyclerView.layoutManager?.itemCount
            if (listSize != null && listSize != 0)
                binding.messagesRecyclerView.scrollToPosition(listSize - 1)
        }, 50)
    }

    private fun smoothScrollToRecyclerBottom() {
        Handler(Looper.getMainLooper()).postDelayed({
            val listSize = binding.messagesRecyclerView.layoutManager?.itemCount
            if (listSize != null && listSize != 0)
                binding.messagesRecyclerView.smoothScrollToPosition(listSize - 1)
        }, 50)
    }

    fun cancelSendGameRequest() {
        sharedViewModel.cancelSendGameRequest()
    }

    private suspend fun showDialog(dialog: View) {

        if (viewIsVisible(dialog)) {
            return
        }

        hideAllDialogs()
        backPressedCallback.isEnabled = true

        hideKeyboard()

        //without this delay, the dialog is not centered if it is shown while the keyboard is visible
        delay(200L)

        screenView.visibility = View.VISIBLE
        val parent = dialog.parent as ViewGroup

        val movePropertyValueHolder = PropertyValuesHolder.ofFloat(
            View.TRANSLATION_Y,
            0f,
            -(parent.height / 2 + dialog.height / 2).toFloat()
        )
        val transparencyValueHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f)
        val animator = ObjectAnimator.ofPropertyValuesHolder(
            dialog,
            movePropertyValueHolder,
            transparencyValueHolder
        )
        animator.duration = 500
        animator.interpolator = OvershootInterpolator()
        animator.start()
    }

    private fun hideDialog(dialog: View) {

        if (!viewIsVisible(dialog)) {
            return
        }

        backPressedCallback.isEnabled = false
        screenView.visibility = View.GONE

        val movePropertyValueHolder = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f)
        val transparencyValueHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 0.0f)
        val animator = ObjectAnimator.ofPropertyValuesHolder(
            dialog,
            movePropertyValueHolder,
            transparencyValueHolder
        )
        animator.start()
    }

    private fun hideAllDialogs() {
        hideDialog(confirmSendGameRequestDialog)
        hideDialog(promptDialog)
        hideDialog(notifyDialog)
    }

    private fun hideKeyboard() {
        val inputMethodManager =
            requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.newMessageEditText.windowToken, 0)
    }

    fun goBack() {
        findNavController().popBackStack()
    }

    fun doNothing() {
        //do nothing
    }

    fun resetDialog() {
        hideAllDialogs()
    }

    private fun enableGameModeKeyboard(){
        if (gameModeKeyboardEnabled){
            return
        }

        clearTexts()
        hideKeyboard()
        newMessageGameModeEditText.visibility = View.VISIBLE
        newMessageEditText.visibility = View.GONE
        gameModeKeyboardEnabled = true
    }

    private fun disableGameModeKeyboard(){
        if (!gameModeKeyboardEnabled){
            return
        }

        clearTexts()
        hideKeyboard()
        newMessageEditText.visibility = View.VISIBLE
        newMessageGameModeEditText.visibility = View.GONE
        gameModeKeyboardEnabled = false
    }

    private fun clearTexts(){
        newMessageEditText.text.clear()
        newMessageGameModeEditText.text.clear()
    }
}