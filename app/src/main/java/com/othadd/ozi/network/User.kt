package com.othadd.ozi.network

const val MALE = "Male"
const val FEMALE = "Female"

data class User(val username: String, val userId: String, val gender: String)