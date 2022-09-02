package com.othadd.ozi.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.othadd.ozi.*
import com.othadd.ozi.databinding.FragmentChatsBinding
import com.othadd.ozi.utils.GAME_REQUEST_NOTIFICATION_CHANNEL_ID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ChatsFragment : Fragment() {
    private val sharedViewModel: ChatViewModel by activityViewModels {
        ChatViewModelFactory(
            activity?.application as OziApplication
        )
    }

    private lateinit var binding: FragmentChatsBinding
    private lateinit var chatsRecyclerAdapter: ChatsRecyclerAdapter
    private lateinit var menuOverlay: View
    private lateinit var developerMenuItem: TextView
    private lateinit var bottomComponents: LinearLayout
    private lateinit var snackBar: LinearLayout
    private lateinit var snackBarActionButton: TextView
    private lateinit var snackBarCloseButton: ImageView

    private var snackBarIsShowing = false


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentChatsBinding.inflate(inflater, container, false)
        chatsRecyclerAdapter = ChatsRecyclerAdapter {
            sharedViewModel.startChat(it)
//            findNavController().navigate(ChatsFragmentDirections.actionChatsFragmentToChatFragment())
        }

        binding.apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = sharedViewModel
            chatsFragment = this@ChatsFragment
            chatsRecyclerView.adapter = chatsRecyclerAdapter

        }

        createChannel(
            getString(R.string.new_message_notification_channel_id),
            "New Message",
            "Notifies you of new Messages"
        )
        createChannel(
            GAME_REQUEST_NOTIFICATION_CHANNEL_ID,
            "Game Request",
            "Notifies you of Game Requests"
        )

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (!sharedViewModel.chatStartedByActivity) {
            sharedViewModel.refreshMessages("Could not refresh chats")
        }

        (ContextCompat.getSystemService(requireContext(), NotificationManager::class.java) as NotificationManager).cancelAll()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedViewModel.chats.observe(viewLifecycleOwner) {
            chatsRecyclerAdapter.submitList(it)

            if (it.isEmpty()) {
                binding.chatsRecyclerView.visibility = View.GONE
                binding.emptyStateLinearLayout.visibility = View.VISIBLE
            } else {
                binding.chatsRecyclerView.visibility = View.VISIBLE
                binding.emptyStateLinearLayout.visibility = View.GONE
            }
        }

        sharedViewModel.navigateToChatFragment.observe(viewLifecycleOwner) {
            if (it) {
                findNavController().navigate(ChatsFragmentDirections.actionChatsFragmentToChatFragment())
            }
        }

        sharedViewModel.snackBarState.observe(viewLifecycleOwner) {
            when {
                it.showActionButton -> {
                    snackBar.visibility = View.VISIBLE
                    snackBarActionButton.visibility = View.VISIBLE
                    snackBarCloseButton.visibility = View.VISIBLE
                    showSnackBar()
                }

                !it.showActionButton && it.message != "" -> {
                    snackBar.visibility = View.VISIBLE
                    snackBarActionButton.visibility = View.GONE
                    snackBarCloseButton.visibility = View.GONE
                    showSnackBar()
                }

                it.message == "" -> {
                    hideSnackBar()
                }
            }
        }

        menuOverlay = binding.menuOverlayView
        developerMenuItem = binding.developerMenuItemTextView
        bottomComponents = binding.bottomComponentsLinearLayout
        snackBar = binding.snackbarLinearLayout
        snackBarActionButton = binding.snackBarActionButtonTextView
        snackBarCloseButton = binding.closeSnackBarButtonImageView
    }

    fun findUsers() {
        findNavController().navigate(ChatsFragmentDirections.actionChatsFragmentToFindUsersFragment())
    }

    fun showMenu() {
        menuOverlay.visibility = View.VISIBLE
        developerMenuItem.visibility = View.VISIBLE
    }

    fun hideMenu() {
        menuOverlay.visibility = View.GONE
        developerMenuItem.visibility = View.GONE
    }

    fun goToDeveloperFragment() {
        hideMenu()
        findNavController().navigate(ChatsFragmentDirections.actionChatsFragmentToDeveloperFragment())
    }

    private fun showSnackBar() {
        if (snackBarIsShowing) {
            hideSnackBar()
        }
        val moveBottomComponentsUpAnimator =
            ObjectAnimator.ofFloat(bottomComponents, View.TRANSLATION_Y, -50f)
        val showSnackBarAnimator = ObjectAnimator.ofFloat(snackBar, View.ALPHA, 0.0f, 1.0f)

        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(moveBottomComponentsUpAnimator, showSnackBarAnimator)
        animatorSet.start()

        snackBarIsShowing = true
    }

    private fun hideSnackBar() {
        if (!snackBarIsShowing) {
            return
        }

        val moveBottomComponentsDownAnimator =
            ObjectAnimator.ofFloat(bottomComponents, View.TRANSLATION_Y, 180f)
        val hideSnackBarAnimator = ObjectAnimator.ofFloat(snackBar, View.ALPHA, 1.0f, 0.0f)

        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(hideSnackBarAnimator, moveBottomComponentsDownAnimator)
        animatorSet.start()

        snackBarIsShowing = false
    }

//    private fun getHideAnimationDistance(): Float{
//        return 190f - sharedViewModel.snackBarHideAnimationDistanceOffset
//    }


    private fun createChannel(channelId: String, channelName: String, description: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setShowBadge(false)
            }
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.RED
            notificationChannel.enableVibration(true)
            notificationChannel.description = description

            val notificationManager = requireActivity().getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }
}