package br.com.fpagency.fptube.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [DownloadEntity::class, LogEntity::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun logDao(): LogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_status` ON `downloads` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_url` ON `downloads` (`url`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_timestamp` ON `downloads` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_platform` ON `downloads` (`platform`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_logs_timestamp` ON `logs` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_logs_level` ON `logs` (`level`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fptube_database"
                )
                .addMigrations(MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): DownloadStatus = try {
        DownloadStatus.valueOf(value)
    } catch (_: IllegalArgumentException) {
        DownloadStatus.FAILED
    }
}
