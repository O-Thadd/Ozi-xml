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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.othadd.ozi.*
import com.othadd.ozi.database.NOTIFY_DIALOG_TYPE
import com.othadd.ozi.database.PROMPT_DIALOG_TYPE
import com.othadd.ozi.databinding.FragmentChatBinding

class ChatFragment : Fragment() {

    private val sharedViewModel: ChatViewModel by activityViewModels {
        ChatViewModelFactory(
            activity?.application as OziApplication
        )
    }

    private lateinit var binding: FragmentChatBinding
    private lateinit var messagesRecyclerAdapter: MessagesRecyclerAdapter
    private lateinit var confirmSendGameRequestDialog: ConstraintLayout
    private lateinit var promptDialog: ConstraintLayout
    private lateinit var notifyDialog: ConstraintLayout
    private lateinit var screenView: View
    private lateinit var bottomComponents: LinearLayout
    private lateinit var snackBar: LinearLayout
    private lateinit var snackBarActionButton: TextView
    private lateinit var snackBarCloseButton: ImageView
    private lateinit var messagesRecyclerView: RecyclerView

    private var snackBarIsShowing = false
    private var chatSwitchBySnackBar = false
    private lateinit var backPressedCallback: OnBackPressedCallback
    private lateinit var chatMateUserId: String

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

    override fun onResume() {
        super.onResume()
        if (sharedViewModel.scrollToBottomOfChat) {
            scrollToRecyclerViewBottom()
            sharedViewModel.scrollToBottomOfChat = false
        }
        sharedViewModel.refreshMessages("Could not refresh messages")
        sharedViewModel.resetNavigateChatsToChatFragment()
        sharedViewModel.markMessagesRead()
        sharedViewModel.resetChatStartedByActivity()
    }

    override fun onPause() {
        super.onPause()
        sharedViewModel.markMessagesRead()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        confirmSendGameRequestDialog = binding.confirmSendGameDialogConstraintLayout
        promptDialog = binding.promptDialogConstraintLayout
        notifyDialog = binding.notifyDialogConstraintLayout
        screenView = binding.dialogOverlayScreenView
        messagesRecyclerView = binding.messagesRecyclerView
        bottomComponents = binding.chatFragmentBottomComponentsLinearLayout
        snackBar = binding.snackBarLinearLayout
        snackBarActionButton = binding.snackBarActionButtonTextView
        snackBarCloseButton = binding.closeSnackBarButtonImageView

        sharedViewModel.chat.observe(viewLifecycleOwner) { dbChat ->
            val messages = dbChat.messages.sortedBy { it.dateTime }
            val uiMessages = messages.map { it.toUIMessage(sharedViewModel.thisUserId) }
            messagesRecyclerAdapter.submitList(uiMessages)

            binding.verificationStatusIndicatorImageView.visibility = if (dbChat.verificationStatus) View.VISIBLE else View.GONE
            binding.onlineTextTextView.visibility = if (dbChat.onlineStatus) View.VISIBLE else View.GONE

            if (dbChat.messages.isEmpty()) {
                messagesRecyclerView.visibility = View.GONE
                binding.emptyStateLinearLayout.visibility = View.VISIBLE
            } else {
                messagesRecyclerView.visibility = View.VISIBLE
                binding.emptyStateLinearLayout.visibility = View.GONE
            }

            Handler(Looper.getMainLooper()).postDelayed({
                scrollToRecyclerViewBottom()
            }, 50)
        }

        sharedViewModel.allMessagesSentForChat.observe(viewLifecycleOwner) {
            if (it) sharedViewModel.markMessagesSent()
        }

        observeDataForDialog(
            sharedViewModel.showConfirmGameRequestDialog,
            confirmSendGameRequestDialog
        )

        sharedViewModel.chat.observe(viewLifecycleOwner) {

            chatMateUserId = it.chatMateId

            when (it.dialogState.dialogType) {

                NOTIFY_DIALOG_TYPE -> {
                    if (it.dialogState.showOkayButton) {
                        binding.notifyDialogOkButtonTextView.visibility = View.VISIBLE
                    } else {
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

        sharedViewModel.snackBarState.observe(viewLifecycleOwner) {
            when {

//                promptSnackBar case
                it.showActionButton -> {
//                    the aim for the conditional is to verify that the game request sender isn't the one whose chat is currently open.
//                    this check is happening within the promptSnackbar case on the assumption that all promptSnackbars are new game requests.
//                    that is currently true, but that may change in the future and inwhich case this check will have to be reimplemented.
//                    consider adding a field to the snackbarstate class to indicate the nature of info such as 'network', 'game request', etc.
                    if (chatMateUserId != sharedViewModel.getGameRequestSenderId()) {
                        snackBar.visibility = View.VISIBLE
                        snackBarActionButton.visibility = View.VISIBLE
                        snackBarCloseButton.visibility = View.VISIBLE
                        showSnackBar()
                    }
                }

//                updateSnackBar case
                !it.showActionButton && it.message != "" -> {
                    snackBar.visibility = View.VISIBLE
                    snackBarActionButton.visibility = View.GONE
                    snackBarCloseButton.visibility = View.GONE
                    showSnackBar()
                }

//                no snackBar case
                it.message == "" -> {
                    hideSnackBar()
                }
            }
        }

        sharedViewModel.navigateToChatsFragment.observe(viewLifecycleOwner){
            if (it && chatSwitchBySnackBar){
                chatSwitchBySnackBar = false
                findNavController().popBackStack()
            }
        }
    }

    private fun showSnackBar() {
        if (snackBarIsShowing) {
            hideSnackBar()
        }
        val moveBottomComponentsUpAnimator = ObjectAnimator.ofFloat(bottomComponents, View.TRANSLATION_Y, -30f)
        val showSnackBarAnimator = ObjectAnimator.ofFloat(snackBar, View.ALPHA, 0.0f, 1.0f)

        val generalAnimatorSet = AnimatorSet()
        generalAnimatorSet.playSequentially(moveBottomComponentsUpAnimator, showSnackBarAnimator)
        generalAnimatorSet.start()

        snackBarIsShowing = true
    }

    private fun hideSnackBar() {
        if (!snackBarIsShowing) {
            return
        }

        val moveBottomComponentsDownAnimator = ObjectAnimator.ofFloat(bottomComponents, View.TRANSLATION_Y, 180f)
        val hideSnackBarAnimator = ObjectAnimator.ofFloat(snackBar, View.ALPHA, 1.0f, 0.0f)

        val generalAnimatorSet = AnimatorSet()
        generalAnimatorSet.playSequentially(hideSnackBarAnimator, moveBottomComponentsDownAnimator)
        generalAnimatorSet.start()

        snackBarIsShowing = false
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

    private fun observeDataForDialog(data: LiveData<Boolean>, dialog: View) {
        data.observe(viewLifecycleOwner) {
            if (it) {
                showDialog(dialog)
            } else {
                hideDialog(dialog)
            }
        }
    }

    fun sendMessage() {
        if (!binding.newMessageEditText.text.isNullOrBlank()) {
            sharedViewModel.sendMessage(binding.newMessageEditText.text.toString(), chatMateUserId)
            binding.newMessageEditText.text?.clear()
        }
    }

    private fun scrollToRecyclerViewBottom() {
        val listSize = binding.messagesRecyclerView.layoutManager?.itemCount
        if (listSize != null && listSize != 0)
            binding.messagesRecyclerView.smoothScrollToPosition(listSize - 1)
    }

    fun cancelSendGameRequest() {
        sharedViewModel.cancelSendGameRequest()
    }

    fun sendGameRequest() {
//        sharedViewModel.sendGameRequest()
    }

    fun okayAfterContDownEnded() {
//        sharedViewModel.okayAfterCountdownEnded()
    }

    private fun showDialog(dialog: View) {

        if (viewIsVisible(dialog)) {
            return
        }

        hideAllDialogs()
        backPressedCallback.isEnabled = true
        screenView.visibility = View.VISIBLE
        hideKeyboard()

        val movePropertyValueHolder = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -1200f)
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

        val movePropertyValueHolder = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 1200f)
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

    fun snackBarSwitchToGameRequestSenderChat(){
        chatSwitchBySnackBar = true
        sharedViewModel.snackBarNavigateToChatFromChatFragment()
    }

//    private fun getHideAnimationDistance(): Float{
//        return 170f - sharedViewModel.snackBarHideAnimationDistanceOffset
//    }

}