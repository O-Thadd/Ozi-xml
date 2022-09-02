package com.othadd.ozi.utils

import android.widget.ImageView
import androidx.databinding.BindingAdapter
import androidx.recyclerview.widget.RecyclerView
import com.othadd.ozi.R
import com.othadd.ozi.network.FEMALE
import com.othadd.ozi.network.MALE
import com.othadd.ozi.network.User
import com.othadd.ozi.ui.ChatsRecyclerAdapter
import com.othadd.ozi.ui.UIChat
import com.othadd.ozi.ui.UsersRecyclerAdapter

@BindingAdapter("listData")
fun bindChatsListData(recyclerView: RecyclerView, chatsList: List<UIChat>?) {
    val adapter = recyclerView.adapter as ChatsRecyclerAdapter
    adapter.submitList(chatsList)
}

@BindingAdapter("listData")
fun bindUsersListData(recyclerView: RecyclerView, usersList: List<User>?) {
    val adapter = recyclerView.adapter as UsersRecyclerAdapter
    adapter.submitList(usersList)
}

@BindingAdapter("lightGender")
fun bindLightGender(imageView: ImageView, gender: String?) {
    imageView.setImageResource(
        when (gender) {
            MALE -> R.drawable.male_profile
            FEMALE -> R.drawable.female_profile
            else -> R.drawable.ic_person_light
        }
    )
}

@BindingAdapter("darkGender")
fun bindDarkGender(imageView: ImageView, gender: String?) {
    imageView.setImageResource(
        when (gender) {
            MALE -> R.drawable.male_profile_black
            FEMALE -> R.drawable.female_profile_black
            else -> R.drawable.ic_person_dark
        }
    )
}