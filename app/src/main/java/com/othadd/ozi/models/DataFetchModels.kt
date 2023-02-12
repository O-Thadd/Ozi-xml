package com.othadd.ozi.models

import com.othadd.ozi.network.User
import com.othadd.ozi.ui.DEFAULT
import java.util.Calendar

class UsersFetchResponse(val users: List<User>, override val status: Int, val timeStamp: Long = Calendar.getInstance().timeInMillis): DataFetchResponse{
    companion object{
        fun getDefault() = UsersFetchResponse(emptyList(), DEFAULT)
    }
}

class ProfileFetchResponse(val user: User?, override val status: Int): DataFetchResponse{
    companion object{
        fun getDefault() = ProfileFetchResponse(null, DEFAULT)
    }
}

class UsernameCheckResponse(val checkPassed: Boolean, override val status: Int): DataFetchResponse{
    companion object{
        fun getDefault() = UsernameCheckResponse(false, DEFAULT)
    }
}
