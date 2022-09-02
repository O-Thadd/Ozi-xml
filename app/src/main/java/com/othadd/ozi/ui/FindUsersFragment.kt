package com.othadd.ozi.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.othadd.ozi.*
import com.othadd.ozi.databinding.FragmentFindUsersBinding

class FindUsersFragment : Fragment() {
    private val sharedViewModel: ChatViewModel by activityViewModels {
        ChatViewModelFactory(
            activity?.application as OziApplication
        )
    }

    private lateinit var binding: FragmentFindUsersBinding

    private lateinit var usersRecyclerAdapter: UsersRecyclerAdapter
    private lateinit var loadingComponents: LinearLayout
    private lateinit var loadingIcon: ImageView
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var couldNotFetchTextView: TextView
    private lateinit var tryAgainTextViewButton: TextView
    private lateinit var snackBar: LinearLayout
    private lateinit var snackBarActionButton: TextView
    private lateinit var snackBarCloseButton: ImageView

    private lateinit var animator: ObjectAnimator
    private var snackBarIsShowing = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentFindUsersBinding.inflate(inflater, container, false)
        usersRecyclerAdapter = UsersRecyclerAdapter {
            val userId = it
            sharedViewModel.startChat(userId)
        }

        binding.apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = sharedViewModel
            findUsersFragment = this@FindUsersFragment
            usersRecyclerView.adapter = usersRecyclerAdapter
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadingComponents = binding.loadingElementsLinearLayout
        loadingIcon = binding.loadingIconImageView
        usersRecyclerView = binding.usersRecyclerView
        couldNotFetchTextView = binding.couldNotFetchUsersTextView
        tryAgainTextViewButton = binding.tryAgainTextView
        snackBar = binding.snackBarLinearLayout
        snackBarActionButton = binding.snackBarActionButtonTextView
        snackBarCloseButton = binding.closeSnackBarButtonImageView

        animator = ObjectAnimator.ofFloat(loadingIcon, View.ROTATION, -360f, 0f)
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.duration = 300

        sharedViewModel.navigateToChatFragment.observe(viewLifecycleOwner) {
            if (it) {
                findNavController().navigate(FindUsersFragmentDirections.actionFindUsersFragmentToChatFragment())
            }
        }

        sharedViewModel.usersFetchStatus.observe(viewLifecycleOwner) {
            when (it) {
                BUSY -> startAnimation()
                PASSED -> stopAnimationWithSuccess()
                FAILED -> stopAnimationWithFailure()
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
    }

    private fun showSnackBar() {
        if (snackBarIsShowing) {
            hideSnackBar()
        }
        val moveBottomComponentsUpAnimator =
            ObjectAnimator.ofFloat(snackBar, View.TRANSLATION_Y, -200f)
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

        val moveBottomComponentsDownAnimator =
            ObjectAnimator.ofFloat(snackBar, View.TRANSLATION_Y, 160f)
        val hideSnackBarAnimator = ObjectAnimator.ofFloat(snackBar, View.ALPHA, 1.0f, 0.0f)

        val generalAnimatorSet = AnimatorSet()
        generalAnimatorSet.playSequentially(hideSnackBarAnimator, moveBottomComponentsDownAnimator)
        generalAnimatorSet.start()

        snackBarIsShowing = false
    }

    private fun stopAnimationWithFailure() {
        usersRecyclerView.visibility = View.GONE
        loadingComponents.visibility = View.GONE
        couldNotFetchTextView.visibility = View.VISIBLE
        tryAgainTextViewButton.visibility = View.VISIBLE
        animator.cancel()
    }

    private fun stopAnimationWithSuccess() {
        usersRecyclerView.visibility = View.VISIBLE
        loadingComponents.visibility = View.GONE
        couldNotFetchTextView.visibility = View.GONE
        tryAgainTextViewButton.visibility = View.GONE
        animator.cancel()
    }

    private fun startAnimation() {
        usersRecyclerView.visibility = View.GONE
        loadingComponents.visibility = View.VISIBLE
        couldNotFetchTextView.visibility = View.GONE
        tryAgainTextViewButton.visibility = View.GONE
        animator.start()
    }

    override fun onResume() {
        super.onResume()
        sharedViewModel.getLatestUsers()
    }
}