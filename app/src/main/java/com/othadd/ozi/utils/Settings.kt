package com.othadd.ozi.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.othadd.ozi.ui.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.*

const val TEMP_USER_ID = "temporary user ID"
const val NO_USER_ID = "no user ID set"
const val NO_USERNAME = "no username"

class SettingsRepo(private val context: Context) {

    private val USER_ID_KEY = stringPreferencesKey("userIdKey")
    private val USERNAME_KEY = stringPreferencesKey("usernameKey")

    fun getUserId(): String {

        var userId = TEMP_USER_ID

        runBlocking {
            userId = context.dataStore.data.map {
                it[USER_ID_KEY] ?: NO_USER_ID
            }.first()

            if (userId == NO_USER_ID) {
                val newUserId = UUID.randomUUID().toString()
                context.dataStore.edit {
                    it[USER_ID_KEY] = newUserId
                }
                userId = newUserId
            }
        }
        return userId
    }

    fun username(): Flow<String> {
            return context.dataStore.data.map {
                it[USERNAME_KEY] ?: NO_USERNAME
            }
    }

    fun storeUsername(username: String) {
        runBlocking {
            context.dataStore.edit {
                it[USERNAME_KEY] = username
            }
        }
    }

}