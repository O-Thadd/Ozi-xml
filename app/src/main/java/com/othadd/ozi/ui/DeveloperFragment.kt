package com.othadd.ozi.ui

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.othadd.ozi.MessagingRepoX
import com.othadd.ozi.OziApplication
import com.othadd.ozi.database.ChatDao
import com.othadd.ozi.databinding.FragmentDeveloperBinding
import com.othadd.ozi.utils.SettingsRepo
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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