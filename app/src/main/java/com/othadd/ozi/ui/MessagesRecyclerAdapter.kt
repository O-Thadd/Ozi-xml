package com.othadd.ozi.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.othadd.ozi.MESSAGE_SENT_BY_ME
import com.othadd.ozi.MESSAGE_SENT_BY_MODERATOR
import com.othadd.ozi.MESSAGE_SENT_BY_SERVER
import com.othadd.ozi.UIMessage
import com.othadd.ozi.databinding.MessageListItemModeratorBinding
import com.othadd.ozi.databinding.MessageListItemServerBinding
import com.othadd.ozi.databinding.MessagesListItemReceivedBinding
import com.othadd.ozi.databinding.MessagesListItemSentBinding

const val SENT_TYPE = 0
const val RECEIVED_TYPE = 1
const val SERVER_TYPE = 2
const val MODERATOR_TYPE = 3

class MessagesRecyclerAdapter :
    ListAdapter<UIMessage, MessagesRecyclerAdapter.MessageViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<UIMessage>() {
            override fun areItemsTheSame(oldItem: UIMessage, newItem: UIMessage): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: UIMessage, newItem: UIMessage): Boolean {
                return oldItem == newItem
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return when(viewType) {
            SENT_TYPE -> MessageViewHolder.SentType(MessagesListItemSentBinding.inflate(LayoutInflater.from(parent.context)))
            RECEIVED_TYPE -> MessageViewHolder.ReceivedType(MessagesListItemReceivedBinding.inflate(LayoutInflater.from(parent.context)))
            MODERATOR_TYPE -> MessageViewHolder.ModeratorType(MessageListItemModeratorBinding.inflate(LayoutInflater.from(parent.context)))
            else -> MessageViewHolder.ServerType(MessageListItemServerBinding.inflate(LayoutInflater.from(parent.context)))
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        holder.bind(message)
    }

    override fun getItemViewType(position: Int): Int {
        return when(getItem(position).sender){
            MESSAGE_SENT_BY_ME -> SENT_TYPE
            MESSAGE_SENT_BY_SERVER -> SERVER_TYPE
            MESSAGE_SENT_BY_MODERATOR -> MODERATOR_TYPE
            else -> RECEIVED_TYPE
        }
    }

    abstract class MessageViewHolder(val view: View) :
        RecyclerView.ViewHolder(view) {

        abstract fun bind(message: UIMessage)

        class SentType(val binding: MessagesListItemSentBinding): MessageViewHolder(binding.root) {
            override fun bind(message: UIMessage) {
                binding.apply {
                    messageBodyTextview.text = message.body
                    dateTimeTextview.text = message.dateTime
                }
            }
        }

        class ReceivedType(val binding: MessagesListItemReceivedBinding): MessageViewHolder(binding.root) {
            override fun bind(message: UIMessage) {
                binding.apply {
                    messageBodyTextview.text = message.body
                    dateTimeTextview.text = message.dateTime
                }
            }
        }

        class ServerType(val binding: MessageListItemServerBinding): MessageViewHolder(binding.root){
            override fun bind(message: UIMessage) {
                binding.apply {
                    messageTextview.text = message.body
                }
            }
        }

        class ModeratorType(val binding: MessageListItemModeratorBinding): MessageViewHolder(binding.root){
            override fun bind(message: UIMessage) {
                binding.apply {
                    messageTextview.text = message.body
                }
            }
        }

    }

}


