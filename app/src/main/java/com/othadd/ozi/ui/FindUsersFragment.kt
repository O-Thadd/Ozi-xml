package com.othadd.ozi.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.othadd.ozi.*
import com.othadd.ozi.databinding.FragmentFindUsersBinding

class FindUsersFragment : Fragment() {
    private val sharedViewModel: ChatViewModel by activityViewModels {
        ChatViewModelFactory(
            SettingsRepo(requireContext()),
            MessagingRepo((activity?.application as OziApplication)),
            activity?.application as OziApplication
        )
    }

    private lateinit var binding: FragmentFindUsersBinding
    private lateinit var usersRecyclerAdapter: UsersRecyclerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentFindUsersBinding.inflate(inflater, container, false)
        usersRecyclerAdapter = UsersRecyclerAdapter {
            val username = it
            sharedViewModel.startChat(username)
            sharedViewModel.setChat(username)
            val action = FindUsersFragmentDirections.actionFindUsersFragmentToChatFragment()
            findNavController().navigate(action)
        }

        binding.apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = sharedViewModel
            findUsersFragment = this@FindUsersFragment
            usersRecyclerView.adapter = usersRecyclerAdapter
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        sharedViewModel.getLatestUsers()
    }
}