package com.othadd.ozi.network

import android.content.Context
import com.othadd.ozi.NWMessage
import com.othadd.ozi.SettingsRepo
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

private const val BASE_URL = "http://192.168.43.107:8080" // TODO: place base URL here

private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

private val retrofit = Retrofit.Builder()
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .baseUrl(BASE_URL)
    .build()

suspend fun sendFCMToken(context: Context, token: String){
    NetworkApi.retrofitService.sendFCMToken(SettingsRepo(context).getUserId(), token)
}

interface NetworkService{
    @GET("getMessages/{userId}")
    suspend fun getMessages(@Path("userId")userId: String): List<NWMessage>

    @POST("sendMessage")
    suspend fun sendMessage(@Body message: NWMessage)

    @POST("sendFCMToken/{userId}/{FCMToken}")
    suspend fun sendFCMToken(@Path("userId")userId: String, @Path("FCMToken")FCMToken: String)

    @GET("getUser/{userId}")
    suspend fun getUser(@Path("userId")userId: String): User

    @POST("registerUser/{userId}/{username}/{gender}")
    suspend fun registerUser(
        @Path("userId") userId: String,
        @Path("username") username: String,
        @Path("gender")gender: String
    )

    @GET("getUsers")
    suspend fun getUsers(): List<User>

    @GET("checkUsername/{username}")
    suspend fun checkUsername(@Path("username")username: String): Boolean
}

object NetworkApi{
    val retrofitService: NetworkService by lazy {
        retrofit.create(NetworkService::class.java)
    }
}