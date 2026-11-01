package br.com.fpagency.fptube.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideDownloadDao(database: AppDatabase) = database.downloadDao()

    @Provides
    fun provideLogDao(database: AppDatabase) = database.logDao()

    @Provides
    @Singleton
    fun provideSettingsManager(@ApplicationContext context: Context) = SettingsManager(context)
}
