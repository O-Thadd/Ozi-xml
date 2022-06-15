package com.othadd.ozi.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.othadd.ozi.UIMessage
import com.othadd.ozi.databinding.MessagesListItemReceivedBinding
import com.othadd.ozi.databinding.MessagesListItemSentBinding

const val SENT_TYPE = 0
const val RECEIVED_TYPE = 1

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
            else -> MessageViewHolder.ReceivedType(MessagesListItemReceivedBinding.inflate(LayoutInflater.from(parent.context)))
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = getItem(position)
        holder.bind(message)
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).sentByMe) SENT_TYPE else RECEIVED_TYPE
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

    }

}


