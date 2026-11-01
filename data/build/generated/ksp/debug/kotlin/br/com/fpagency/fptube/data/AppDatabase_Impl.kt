package br.com.fpagency.fptube.`data`

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _downloadDao: Lazy<DownloadDao> = lazy {
    DownloadDao_Impl(this)
  }

  private val _logDao: Lazy<LogDao> = lazy {
    LogDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(5, "af81ac3416387afa81b47d6843bd6b6c", "59542abb94ab1df6313d0a4ef2d166fe") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `downloads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `thumbnail` TEXT, `channel` TEXT, `resolution` TEXT, `totalSize` INTEGER NOT NULL, `downloadedSize` INTEGER NOT NULL, `progress` INTEGER NOT NULL, `status` TEXT NOT NULL, `speed` TEXT, `eta` TEXT, `timestamp` INTEGER NOT NULL, `filePath` TEXT, `codec` TEXT, `fps` INTEGER, `duration` TEXT, `mimeType` TEXT, `formatId` TEXT, `downloadType` TEXT, `playerClient` TEXT, `platform` TEXT, `lastError` TEXT, `stackTrace` TEXT)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `level` TEXT NOT NULL, `tag` TEXT NOT NULL, `message` TEXT NOT NULL, `throwable` TEXT, `className` TEXT, `methodName` TEXT, `threadName` TEXT NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'af81ac3416387afa81b47d6843bd6b6c')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `downloads`")
        connection.execSQL("DROP TABLE IF EXISTS `logs`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsDownloads: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsDownloads.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("url", TableInfo.Column("url", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("thumbnail", TableInfo.Column("thumbnail", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("channel", TableInfo.Column("channel", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("resolution", TableInfo.Column("resolution", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("totalSize", TableInfo.Column("totalSize", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("downloadedSize", TableInfo.Column("downloadedSize", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("progress", TableInfo.Column("progress", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("status", TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("speed", TableInfo.Column("speed", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("eta", TableInfo.Column("eta", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("filePath", TableInfo.Column("filePath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("codec", TableInfo.Column("codec", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("fps", TableInfo.Column("fps", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("duration", TableInfo.Column("duration", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("mimeType", TableInfo.Column("mimeType", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("formatId", TableInfo.Column("formatId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("downloadType", TableInfo.Column("downloadType", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("playerClient", TableInfo.Column("playerClient", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("platform", TableInfo.Column("platform", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("lastError", TableInfo.Column("lastError", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloads.put("stackTrace", TableInfo.Column("stackTrace", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysDownloads: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesDownloads: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoDownloads: TableInfo = TableInfo("downloads", _columnsDownloads, _foreignKeysDownloads, _indicesDownloads)
        val _existingDownloads: TableInfo = read(connection, "downloads")
        if (!_infoDownloads.equals(_existingDownloads)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |downloads(br.com.fpagency.fptube.data.DownloadEntity).
              | Expected:
              |""".trimMargin() + _infoDownloads + """
              |
              | Found:
              |""".trimMargin() + _existingDownloads)
        }
        val _columnsLogs: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsLogs.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLogs.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLogs.put("level", TableInfo.Column("level", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLogs.put("tag", TableInfo.Column("tag", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLogs.put("message", TableInfo.Column("message", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLogs.put("throwable", TableInfo.Column("throwable", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLogs.put("className", TableInfo.Column("className", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLogs.put("methodName", TableInfo.Column("methodName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLogs.put("threadName", TableInfo.Column("threadName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysLogs: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesLogs: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoLogs: TableInfo = TableInfo("logs", _columnsLogs, _foreignKeysLogs, _indicesLogs)
        val _existingLogs: TableInfo = read(connection, "logs")
        if (!_infoLogs.equals(_existingLogs)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |logs(br.com.fpagency.fptube.data.LogEntity).
              | Expected:
              |""".trimMargin() + _infoLogs + """
              |
              | Found:
              |""".trimMargin() + _existingLogs)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "downloads", "logs")
  }

  public override fun clearAllTables() {
    super.performClear(false, "downloads", "logs")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(DownloadDao::class, DownloadDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(LogDao::class, LogDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun downloadDao(): DownloadDao = _downloadDao.value

  public override fun logDao(): LogDao = _logDao.value
}
