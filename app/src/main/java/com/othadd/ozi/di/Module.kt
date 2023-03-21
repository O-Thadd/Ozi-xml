package com.othadd.ozi.di

import android.content.Context
import com.othadd.ozi.OziApplication
import com.othadd.ozi.Service
import com.othadd.ozi.data.database.ChatDao
import com.othadd.ozi.data.database.ChatRoomDatabase
import com.othadd.ozi.data.repos.MessageRepo
import com.othadd.ozi.data.repos.UtilRepo
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

//    @Provides
//    fun provideChatDao(@ApplicationContext context: Context): ChatDao {
//        val database: ChatRoomDatabase by lazy { ChatRoomDatabase.getDatabase(context) }
//        return database.chatDao()
//    }
//
//    @Provides
//    fun provideSettingsRepo(@ApplicationContext context: Context): SettingsRepo{
//        return SettingsRepo(context)
//    }

//    @Singleton
//    @Provides
//    fun provideMessagingRepo(@ApplicationContext context: Context): MessagingRepoX{
//        return MessagingRepoX.getInstance(context as OziApplication)
//    }

    @Provides
    fun provideOziApp(@ApplicationContext context: Context): OziApplication{
        return context as OziApplication
    }

    @Singleton
    @Provides
    fun provideGameManager(messageRepo: MessageRepo, utilRepo: UtilRepo): GameManager{
        return GameManager(messageRepo, utilRepo)
    }

    @Singleton
    @Provides
    fun provideMessageRepo(oziApp: OziApplication): MessageRepo{
        return MessageRepo(oziApp)
    }

    @Singleton
    @Provides
    fun provideUtilRepo(oziApp: OziApplication): UtilRepo{
        return UtilRepo(oziApp)
    }

    @Singleton
    @Provides
    fun provideService(messageRepo: MessageRepo, utilRepo: UtilRepo, gameManager: GameManager): Service{
        return Service(messageRepo, utilRepo, gameManager)
    }
}