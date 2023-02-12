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
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.othadd.ozi.MessagingRepoX
import com.othadd.ozi.OziApplication
import com.othadd.ozi.database.ChatDao
import com.othadd.ozi.databinding.FragmentFindUsersBinding
import com.othadd.ozi.utils.SettingsRepo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FindUsersFragment : Fragment() {


    private val sharedViewModel: ChatViewModel by activityViewModels()

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
    private lateinit var searchLoadingIcon: ImageView

    private lateinit var animator: ObjectAnimator
    private lateinit var searchAnimator: ObjectAnimator
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
        tryAgainTextViewButton = binding.tryAgainButtonTextView
        snackBar = binding.snackBarLinearLayout
        snackBarActionButton = binding.snackBarActionButtonTextView
        snackBarCloseButton = binding.closeSnackBarButtonImageView
        searchLoadingIcon = binding.searchLoadingIconImageView

        animator = ObjectAnimator.ofFloat(loadingIcon, View.ROTATION, -360f, 0f)
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.duration = 300

        searchAnimator = ObjectAnimator.ofFloat(searchLoadingIcon, View.ROTATION, -360f, 0f)
        searchAnimator.repeatCount = ObjectAnimator.INFINITE
        searchAnimator.duration = 300

        sharedViewModel.navigateToChatFragment.observe(viewLifecycleOwner) {
            if (it) {
                findNavController().navigate(FindUsersFragmentDirections.actionFindUsersFragmentToChatFragment())
            }
        }

        sharedViewModel.findUsersFragmentUIState.observe(viewLifecycleOwner){
            handleFetchStatus(it.fetchStatus)
            handleSearchStatus(it.searchStatus)
        }

//        sharedViewModel.usersFetchStatus.observe(viewLifecycleOwner) {
//            handleFetchStatus(it)
//        }
//
//        sharedViewModel.searchStatus.observe(viewLifecycleOwner) {
//            handleSearchStatus(it)
//        }

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

        binding.searchEditText.addTextChangedListener {
            sharedViewModel.getMatchingUsers(it.toString())
        }

    }

    private fun handleSearchStatus(status: Int?) {
        when (status) {
            BUSY -> {
                startSearchAnimation()
            }
            else -> {
                stopSearchAnimation()
            }
        }
    }

    private fun handleFetchStatus(status: Int?) {
        when (status) {
            BUSY -> {
                startAnimation()
            }
            PASSED -> {
                stopAnimationWithSuccess()
            }
            FAILED -> {
                stopAnimationWithFailure()
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

    private fun stopSearchAnimationWithFailure() {
        searchLoadingIcon.visibility = View.INVISIBLE
        searchAnimator.cancel()
    }

    private fun stopSearchAnimationWithSuccess() {
        searchLoadingIcon.visibility = View.INVISIBLE
        searchAnimator.cancel()
    }

    private fun stopSearchAnimation(){
        searchLoadingIcon.visibility = View.INVISIBLE
        searchAnimator.cancel()
    }

    private fun startSearchAnimation() {
        searchLoadingIcon.visibility = View.VISIBLE
        searchAnimator.start()
    }

    override fun onResume() {
        super.onResume()
        sharedViewModel.getLatestUsers()
    }
}