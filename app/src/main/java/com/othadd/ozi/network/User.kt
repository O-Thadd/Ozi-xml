package com.othadd.ozi.network

const val MALE = "Male"
const val FEMALE = "Female"
const val USER_ONLINE = "online"
const val USER_OFFLINE = "offline"
const val USER_VERIFIED = "verified"
const val USER_UNVERIFIED = "unverified"
const val NOT_PLAYING_GAME_STATE = "not playing game state"
const val CURRENTLY_PLAYING_GAME_STATE = "currently playing game state"
const val REQUEST_PENDING_GAME_STATE = "request pending game state"

data class User(
    val username: String,
    val userId: String,
    val gender: String,
    val onlineStatus: Boolean,
    val verificationStatus: Boolean,
    val gameState: String
)