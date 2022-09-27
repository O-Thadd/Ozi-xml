package com.othadd.ozi.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.othadd.ozi.R
import com.othadd.ozi.databinding.ChatsListItemBinding
import com.othadd.ozi.network.FEMALE
import com.othadd.ozi.network.MALE

class ChatsRecyclerAdapter(private val onItemClick: (String) -> Unit) :
    ListAdapter<UIChat, ChatsRecyclerAdapter.ChatViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<UIChat>() {
            override fun areItemsTheSame(oldItem: UIChat, newItem: UIChat): Boolean {
                return oldItem.chatMateId == newItem.chatMateId
            }

            override fun areContentsTheSame(oldItem: UIChat, newItem: UIChat): Boolean {
                return oldItem == newItem
            }

        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ChatViewHolder {
        return ChatViewHolder(
            ChatsListItemBinding.inflate(
                LayoutInflater.from(parent.context)))
    }

    override fun onBindViewHolder(
        holder: ChatViewHolder,
        position: Int
    ) {
        val chat = getItem(position)
        holder.bind(chat)
        holder.itemView.setOnClickListener{onItemClick(chat.chatMateId)}
    }

    class ChatViewHolder(val binding: ChatsListItemBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(chat : UIChat){
            binding.apply {
                chatMateUsernameTextView.text = chat.chatMateUsername
                lastMessageTextView.text = chat.lastMessage
                lastMessageTimeTextView.text = chat.lastMessageDateTime
                aviImageView.setImageResource(when(chat.chatMateGender){
                    MALE -> R.drawable.icon_male_avi
                    FEMALE -> R.drawable.icon_female_avi
                    else -> R.drawable.ic_person_dark
                })
                unreadMessageIndicatorImageView.visibility = if (chat.hasUnreadMessage) View.VISIBLE else View.GONE
                verificationStatusIndicatorImageView.visibility = if (chat.verificationStatus) View.VISIBLE else View.GONE
            }
        }
    }
}