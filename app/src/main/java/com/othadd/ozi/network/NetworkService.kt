package com.othadd.ozi.network

import com.othadd.ozi.NWMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

private const val BASE_URL = "https://coastal-haven-309701.ew.r.appspot.com"

//"https://coastal-haven-309701.ew.r.appspot.com"
//"http://192.168.43.107:8080"

private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()

private val retrofit = Retrofit.Builder()
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .baseUrl(BASE_URL)
    .build()

interface NetworkService{
    @GET("getMessages/{userId}")
    suspend fun getMessages(@Path("userId")userId: String): List<NWMessage>

    @POST("sendMessage")
    suspend fun sendMessage(@Body message: NWMessage)

    @GET("getUser/{userId}")
    suspend fun getUser(@Path("userId")userId: String): User

    @POST("registerNewUserWithToken/{userId}/{username}/{gender}/{token}")
    suspend fun registerNewUserWithToken(
        @Path("userId") userId: String,
        @Path("username") username: String,
        @Path("gender")gender: String,
        @Path("token")token: String
    )

    @GET("getUsers/{userId}")
    suspend fun getUsers(@Path("userId")userId: String): List<User>

    @GET("checkUsername/{username}")
    suspend fun checkUsername(@Path("username")username: String): Boolean

    @POST("updateStatus/{userId}/{update}")
    suspend fun updateStatus(
        @Path("userId") userId: String,
        @Path("update") update: String
    )

    @GET("getUsersWithMatch/{userId}/{usernameSubString}")
    suspend fun getUsersWithMatch(@Path("userId")userId: String, @Path("usernameSubString")usernameSubString: String): List<User>

    @POST("resolveGameConflict/{gameModeratorId}/{roundSummary}")
    suspend fun resolveGameConflict(
        @Path("gameModeratorId") gameModeratorId: String,
        @Path("roundSummary") roundSummaryString: String
    )

    @GET("ping")
    suspend fun ping(): Boolean
}

object NetworkApi{
    val retrofitService: NetworkService by lazy {
        retrofit.create(NetworkService::class.java)
    }
}