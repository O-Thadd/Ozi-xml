package com.othadd.ozi.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.othadd.ozi.R
import com.othadd.ozi.databinding.UsersListItemBinding
import com.othadd.ozi.network.FEMALE
import com.othadd.ozi.network.MALE
import com.othadd.ozi.network.User

class UsersRecyclerAdapter(private val onItemClick: (String) -> Unit) :
    ListAdapter<User, UsersRecyclerAdapter.UserViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<User>() {
            override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
                return oldItem.userId == newItem.userId
            }

            override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
                return oldItem == newItem
            }

        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): UserViewHolder {
        return UserViewHolder(
            UsersListItemBinding.inflate(
                LayoutInflater.from(parent.context)
            )
        )
    }

    override fun onBindViewHolder(
        holder: UserViewHolder,
        position: Int
    ) {
        val user = getItem(position)
        holder.bind(user)
        holder.itemView.setOnClickListener{onItemClick(user.userId)}
    }

    class UserViewHolder(val binding: UsersListItemBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User){
            binding.apply {
                chatMateUsernameTextView.text = user.username
                aviImageView.setImageResource(when(user.gender){
                    MALE -> R.drawable.male_profile_black
                    FEMALE -> R.drawable.female_profile_black
                    else -> R.drawable.ic_person_dark
                })
                onlineIndicatorImageView.visibility = if (user.onlineStatus) View.VISIBLE else View.GONE
                verificationStatusIndicatorImageView.visibility = if (user.verificationStatus) View.VISIBLE else View.GONE
            }
        }
    }
}