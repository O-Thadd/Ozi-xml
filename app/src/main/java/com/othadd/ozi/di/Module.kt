package com.othadd.ozi.di

import android.content.Context
import com.othadd.ozi.MessagingRepoX
import com.othadd.ozi.OziApplication
import com.othadd.ozi.database.ChatDao
import com.othadd.ozi.database.ChatRoomDatabase
import com.othadd.ozi.gaming.GameManager
import com.othadd.ozi.utils.SettingsRepo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object Module {

    @Provides
    fun provideChatDao(@ApplicationContext context: Context): ChatDao{
        val database: ChatRoomDatabase by lazy { ChatRoomDatabase.getDatabase(context) }
        return database.chatDao()
    }

    @Provides
    fun provideSettingsRepo(@ApplicationContext context: Context): SettingsRepo{
        return SettingsRepo(context)
    }

    @Provides
    fun provideMessagingRepo(@ApplicationContext context: Context): MessagingRepoX{
        return MessagingRepoX(context as OziApplication)
    }

    @Provides
    fun provideOziApp(@ApplicationContext context: Context): OziApplication{
        return context as OziApplication
    }

    @Provides
    fun provideGameManager(@ApplicationContext context: Context, messagingRepoX: MessagingRepoX): GameManager{
        return GameManager((context as OziApplication),messagingRepoX)
    }
}