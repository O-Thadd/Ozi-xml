package com.othadd.ozi.network

const val MALE = "Male"
const val FEMALE = "Female"
const val USER_ONLINE = "online"
const val USER_OFFLINE = "offline"
const val USER_VERIFIED = "verified"
const val USER_UNVERIFIED = "unverified"

data class User(val username: String, val userId: String, val gender: String, val onlineStatus: Boolean, val verificationStatus: Boolean)