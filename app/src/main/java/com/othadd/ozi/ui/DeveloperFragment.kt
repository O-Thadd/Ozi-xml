package com.othadd.ozi.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.othadd.ozi.databinding.FragmentDeveloperBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DeveloperFragment : Fragment() {


    private val sharedViewModel: ChatViewModel by activityViewModels()

    private lateinit var binding: FragmentDeveloperBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentDeveloperBinding.inflate(inflater, container, false)

        binding.apply {
            lifecycleOwner = viewLifecycleOwner
            viewModel = sharedViewModel
            developerFragment = this@DeveloperFragment
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedViewModel.navigateToChatFragment.observe(viewLifecycleOwner) {
            if (it) {
                findNavController().navigate(DeveloperFragmentDirections.actionDeveloperFragmentToChatFragment())
            }
        }
    }

    fun goBack() {
        findNavController().popBackStack()
    }
}