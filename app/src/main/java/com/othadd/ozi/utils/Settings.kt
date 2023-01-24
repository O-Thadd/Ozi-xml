package com.othadd.ozi.utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.othadd.ozi.gaming.DUMMY_STRING
import com.othadd.ozi.ui.SnackBarState
import com.othadd.ozi.ui.dataStore
import com.othadd.ozi.ui.getNoSnackBarSnackBar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.util.*

const val TEMP_USER_ID = "temporary user ID"
const val NO_USER_ID = "no user ID set"
const val NO_USERNAME = "no username"

class SettingsRepo(private val context: Context) {

    private val userIdKey = stringPreferencesKey("userIdKey")
    private val usernameKey = stringPreferencesKey("usernameKey")
    private val snackBarKey = stringPreferencesKey("snackBarKey")
    private val scrollKey = booleanPreferencesKey("scrollKey")
    private val markSentKey = booleanPreferencesKey("markSentKey")
    private val darkModeKey = booleanPreferencesKey("darkModeKey")
    private val gameModeKeyboardKey = booleanPreferencesKey("gameModeKeyboardKey")
    private val gameModeratorIdKey = stringPreferencesKey("gameModeratorIdKey")

    fun getUserId(): String {

        var userId = TEMP_USER_ID

        runBlocking {
            userId = context.dataStore.data.map {
                it[userIdKey] ?: NO_USER_ID
            }.first()

            if (userId == NO_USER_ID) {
                val newUserId = UUID.randomUUID().toString()
                context.dataStore.edit {
                    it[userIdKey] = newUserId
                }
                userId = newUserId
            }
        }
        return userId
    }

    fun updateUserId(userId: String) {
        runBlocking {
            context.dataStore.edit {
                it[userIdKey] = userId
            }
        }
    }

    fun username(): Flow<String> {
        return context.dataStore.data.map {
            it[usernameKey] ?: NO_USERNAME
        }
    }

    fun storeUsername(username: String) {
        runBlocking {
            context.dataStore.edit {
                it[usernameKey] = username
            }
        }
    }

    fun updateSnackBarState(snackBarState: SnackBarState) {
        runBlocking {
            val snackBarString = snackBarToString(snackBarState)
            context.dataStore.edit {
                it[snackBarKey] = snackBarString
            }
        }
    }

    fun snackBarStateFlow(): Flow<SnackBarState> {
        return context.dataStore.data.map {
            stringToSnackBar(it[snackBarKey]) ?: getNoSnackBarSnackBar()
        }
    }

    fun updateScroll(scroll: Boolean) {
        runBlocking {
            context.dataStore.edit {
                it[scrollKey] = scroll
            }
        }
    }

    fun scrollFlow(): Flow<Boolean> {
        return context.dataStore.data.map {
            it[scrollKey] ?: false
        }

    }

    fun updateMarkSent(update: Boolean) {
        runBlocking {
            context.dataStore.edit {
                it[markSentKey] = update
            }
        }
    }

    fun markSentFlow(): Flow<Boolean> {
        return context.dataStore.data.map {
            it[markSentKey] ?: false
        }
    }

    fun updateDarkMode(update: Boolean) {
        runBlocking {
            context.dataStore.edit {
                it[darkModeKey] = update
            }
        }
    }

    fun darkModeFlow(): Flow<Boolean> {
        return context.dataStore.data.map {
            it[darkModeKey] ?: false
        }
    }

    fun updateKeyboardMode(update: Boolean) {
        runBlocking {
            context.dataStore.edit {
                it[gameModeKeyboardKey] = update
            }
        }
    }

    fun keyBoardModeFlow(): Flow<Boolean> {
        return context.dataStore.data.map {
            it[gameModeKeyboardKey] ?: false
        }
    }

    fun updateGameModeratorId(newId: String) {
        runBlocking {
            context.dataStore.edit {
                it[gameModeratorIdKey] = newId
            }
        }
    }

    fun getGameModeratorId(): String {
        return runBlocking {
            context.dataStore.data.map {
                it[gameModeratorIdKey] ?: DUMMY_STRING
            }.first()
        }
    }
}