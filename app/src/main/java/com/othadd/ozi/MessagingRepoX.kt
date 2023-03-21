package com.othadd.ozi

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.*
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.messaging.FirebaseMessaging
import com.othadd.ozi.data.database.DBChat
import com.othadd.ozi.data.database.DialogState
import com.othadd.ozi.data.database.getNoDialogDialogType
import com.othadd.ozi.data.network.*
import com.othadd.ozi.data.repos.MessageRepo
import com.othadd.ozi.data.repos.UtilRepo
import com.othadd.ozi.gaming.DUMMY_STRING
import com.othadd.ozi.gaming.GameManager
import com.othadd.ozi.models.ProfileFetchResponse
import com.othadd.ozi.models.UsersFetchResponse
import com.othadd.ozi.ui.*
import com.othadd.ozi.utils.*
import com.othadd.ozi.workers.SendChatMessageWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

const val RESPOND_TO_GAME_REQUEST_PROMPT_TYPE = "respond to game request"

