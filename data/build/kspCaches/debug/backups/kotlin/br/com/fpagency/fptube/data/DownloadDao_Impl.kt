package br.com.fpagency.fptube.`data`

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.getTotalChangedRows
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class DownloadDao_Impl(
  __db: RoomDatabase,
) : DownloadDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfDownloadEntity: EntityInsertAdapter<DownloadEntity>

  private val __converters: Converters = Converters()

  private val __deleteAdapterOfDownloadEntity: EntityDeleteOrUpdateAdapter<DownloadEntity>

  private val __updateAdapterOfDownloadEntity: EntityDeleteOrUpdateAdapter<DownloadEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfDownloadEntity = object : EntityInsertAdapter<DownloadEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `downloads` (`id`,`url`,`title`,`thumbnail`,`channel`,`resolution`,`totalSize`,`downloadedSize`,`progress`,`status`,`speed`,`eta`,`timestamp`,`filePath`,`codec`,`fps`,`duration`,`mimeType`,`formatId`,`downloadType`,`playerClient`,`platform`,`lastError`,`stackTrace`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: DownloadEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.url)
        statement.bindText(3, entity.title)
        val _tmpThumbnail: String? = entity.thumbnail
        if (_tmpThumbnail == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpThumbnail)
        }
        val _tmpChannel: String? = entity.channel
        if (_tmpChannel == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpChannel)
        }
        val _tmpResolution: String? = entity.resolution
        if (_tmpResolution == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpResolution)
        }
        statement.bindLong(7, entity.totalSize)
        statement.bindLong(8, entity.downloadedSize)
        statement.bindLong(9, entity.progress.toLong())
        val _tmp: String = __converters.fromStatus(entity.status)
        statement.bindText(10, _tmp)
        val _tmpSpeed: String? = entity.speed
        if (_tmpSpeed == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpSpeed)
        }
        val _tmpEta: String? = entity.eta
        if (_tmpEta == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpEta)
        }
        statement.bindLong(13, entity.timestamp)
        val _tmpFilePath: String? = entity.filePath
        if (_tmpFilePath == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpFilePath)
        }
        val _tmpCodec: String? = entity.codec
        if (_tmpCodec == null) {
          statement.bindNull(15)
        } else {
          statement.bindText(15, _tmpCodec)
        }
        val _tmpFps: Int? = entity.fps
        if (_tmpFps == null) {
          statement.bindNull(16)
        } else {
          statement.bindLong(16, _tmpFps.toLong())
        }
        val _tmpDuration: String? = entity.duration
        if (_tmpDuration == null) {
          statement.bindNull(17)
        } else {
          statement.bindText(17, _tmpDuration)
        }
        val _tmpMimeType: String? = entity.mimeType
        if (_tmpMimeType == null) {
          statement.bindNull(18)
        } else {
          statement.bindText(18, _tmpMimeType)
        }
        val _tmpFormatId: String? = entity.formatId
        if (_tmpFormatId == null) {
          statement.bindNull(19)
        } else {
          statement.bindText(19, _tmpFormatId)
        }
        val _tmpDownloadType: String? = entity.downloadType
        if (_tmpDownloadType == null) {
          statement.bindNull(20)
        } else {
          statement.bindText(20, _tmpDownloadType)
        }
        val _tmpPlayerClient: String? = entity.playerClient
        if (_tmpPlayerClient == null) {
          statement.bindNull(21)
        } else {
          statement.bindText(21, _tmpPlayerClient)
        }
        val _tmpPlatform: String? = entity.platform
        if (_tmpPlatform == null) {
          statement.bindNull(22)
        } else {
          statement.bindText(22, _tmpPlatform)
        }
        val _tmpLastError: String? = entity.lastError
        if (_tmpLastError == null) {
          statement.bindNull(23)
        } else {
          statement.bindText(23, _tmpLastError)
        }
        val _tmpStackTrace: String? = entity.stackTrace
        if (_tmpStackTrace == null) {
          statement.bindNull(24)
        } else {
          statement.bindText(24, _tmpStackTrace)
        }
      }
    }
    this.__deleteAdapterOfDownloadEntity = object : EntityDeleteOrUpdateAdapter<DownloadEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `downloads` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: DownloadEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfDownloadEntity = object : EntityDeleteOrUpdateAdapter<DownloadEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `downloads` SET `id` = ?,`url` = ?,`title` = ?,`thumbnail` = ?,`channel` = ?,`resolution` = ?,`totalSize` = ?,`downloadedSize` = ?,`progress` = ?,`status` = ?,`speed` = ?,`eta` = ?,`timestamp` = ?,`filePath` = ?,`codec` = ?,`fps` = ?,`duration` = ?,`mimeType` = ?,`formatId` = ?,`downloadType` = ?,`playerClient` = ?,`platform` = ?,`lastError` = ?,`stackTrace` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: DownloadEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.url)
        statement.bindText(3, entity.title)
        val _tmpThumbnail: String? = entity.thumbnail
        if (_tmpThumbnail == null) {
          statement.bindNull(4)
        } else {
          statement.bindText(4, _tmpThumbnail)
        }
        val _tmpChannel: String? = entity.channel
        if (_tmpChannel == null) {
          statement.bindNull(5)
        } else {
          statement.bindText(5, _tmpChannel)
        }
        val _tmpResolution: String? = entity.resolution
        if (_tmpResolution == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpResolution)
        }
        statement.bindLong(7, entity.totalSize)
        statement.bindLong(8, entity.downloadedSize)
        statement.bindLong(9, entity.progress.toLong())
        val _tmp: String = __converters.fromStatus(entity.status)
        statement.bindText(10, _tmp)
        val _tmpSpeed: String? = entity.speed
        if (_tmpSpeed == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpSpeed)
        }
        val _tmpEta: String? = entity.eta
        if (_tmpEta == null) {
          statement.bindNull(12)
        } else {
          statement.bindText(12, _tmpEta)
        }
        statement.bindLong(13, entity.timestamp)
        val _tmpFilePath: String? = entity.filePath
        if (_tmpFilePath == null) {
          statement.bindNull(14)
        } else {
          statement.bindText(14, _tmpFilePath)
        }
        val _tmpCodec: String? = entity.codec
        if (_tmpCodec == null) {
          statement.bindNull(15)
        } else {
          statement.bindText(15, _tmpCodec)
        }
        val _tmpFps: Int? = entity.fps
        if (_tmpFps == null) {
          statement.bindNull(16)
        } else {
          statement.bindLong(16, _tmpFps.toLong())
        }
        val _tmpDuration: String? = entity.duration
        if (_tmpDuration == null) {
          statement.bindNull(17)
        } else {
          statement.bindText(17, _tmpDuration)
        }
        val _tmpMimeType: String? = entity.mimeType
        if (_tmpMimeType == null) {
          statement.bindNull(18)
        } else {
          statement.bindText(18, _tmpMimeType)
        }
        val _tmpFormatId: String? = entity.formatId
        if (_tmpFormatId == null) {
          statement.bindNull(19)
        } else {
          statement.bindText(19, _tmpFormatId)
        }
        val _tmpDownloadType: String? = entity.downloadType
        if (_tmpDownloadType == null) {
          statement.bindNull(20)
        } else {
          statement.bindText(20, _tmpDownloadType)
        }
        val _tmpPlayerClient: String? = entity.playerClient
        if (_tmpPlayerClient == null) {
          statement.bindNull(21)
        } else {
          statement.bindText(21, _tmpPlayerClient)
        }
        val _tmpPlatform: String? = entity.platform
        if (_tmpPlatform == null) {
          statement.bindNull(22)
        } else {
          statement.bindText(22, _tmpPlatform)
        }
        val _tmpLastError: String? = entity.lastError
        if (_tmpLastError == null) {
          statement.bindNull(23)
        } else {
          statement.bindText(23, _tmpLastError)
        }
        val _tmpStackTrace: String? = entity.stackTrace
        if (_tmpStackTrace == null) {
          statement.bindNull(24)
        } else {
          statement.bindText(24, _tmpStackTrace)
        }
        statement.bindLong(25, entity.id)
      }
    }
  }

  public override suspend fun insert(download: DownloadEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfDownloadEntity.insertAndReturnId(_connection, download)
    _result
  }

  public override suspend fun delete(download: DownloadEntity): Int = performSuspending(__db, false, true) { _connection ->
    var _result: Int = 0
    _result += __deleteAdapterOfDownloadEntity.handle(_connection, download)
    _result
  }

  public override suspend fun update(download: DownloadEntity): Int = performSuspending(__db, false, true) { _connection ->
    var _result: Int = 0
    _result += __updateAdapterOfDownloadEntity.handle(_connection, download)
    _result
  }

  public override fun getAllDownloads(): Flow<List<DownloadEntity>> {
    val _sql: String = "SELECT * FROM downloads ORDER BY timestamp DESC"
    return createFlow(__db, false, arrayOf("downloads")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUrl: Int = getColumnIndexOrThrow(_stmt, "url")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfThumbnail: Int = getColumnIndexOrThrow(_stmt, "thumbnail")
        val _columnIndexOfChannel: Int = getColumnIndexOrThrow(_stmt, "channel")
        val _columnIndexOfResolution: Int = getColumnIndexOrThrow(_stmt, "resolution")
        val _columnIndexOfTotalSize: Int = getColumnIndexOrThrow(_stmt, "totalSize")
        val _columnIndexOfDownloadedSize: Int = getColumnIndexOrThrow(_stmt, "downloadedSize")
        val _columnIndexOfProgress: Int = getColumnIndexOrThrow(_stmt, "progress")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfSpeed: Int = getColumnIndexOrThrow(_stmt, "speed")
        val _columnIndexOfEta: Int = getColumnIndexOrThrow(_stmt, "eta")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfFilePath: Int = getColumnIndexOrThrow(_stmt, "filePath")
        val _columnIndexOfCodec: Int = getColumnIndexOrThrow(_stmt, "codec")
        val _columnIndexOfFps: Int = getColumnIndexOrThrow(_stmt, "fps")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfMimeType: Int = getColumnIndexOrThrow(_stmt, "mimeType")
        val _columnIndexOfFormatId: Int = getColumnIndexOrThrow(_stmt, "formatId")
        val _columnIndexOfDownloadType: Int = getColumnIndexOrThrow(_stmt, "downloadType")
        val _columnIndexOfPlayerClient: Int = getColumnIndexOrThrow(_stmt, "playerClient")
        val _columnIndexOfPlatform: Int = getColumnIndexOrThrow(_stmt, "platform")
        val _columnIndexOfLastError: Int = getColumnIndexOrThrow(_stmt, "lastError")
        val _columnIndexOfStackTrace: Int = getColumnIndexOrThrow(_stmt, "stackTrace")
        val _result: MutableList<DownloadEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: DownloadEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpUrl: String
          _tmpUrl = _stmt.getText(_columnIndexOfUrl)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpThumbnail: String?
          if (_stmt.isNull(_columnIndexOfThumbnail)) {
            _tmpThumbnail = null
          } else {
            _tmpThumbnail = _stmt.getText(_columnIndexOfThumbnail)
          }
          val _tmpChannel: String?
          if (_stmt.isNull(_columnIndexOfChannel)) {
            _tmpChannel = null
          } else {
            _tmpChannel = _stmt.getText(_columnIndexOfChannel)
          }
          val _tmpResolution: String?
          if (_stmt.isNull(_columnIndexOfResolution)) {
            _tmpResolution = null
          } else {
            _tmpResolution = _stmt.getText(_columnIndexOfResolution)
          }
          val _tmpTotalSize: Long
          _tmpTotalSize = _stmt.getLong(_columnIndexOfTotalSize)
          val _tmpDownloadedSize: Long
          _tmpDownloadedSize = _stmt.getLong(_columnIndexOfDownloadedSize)
          val _tmpProgress: Int
          _tmpProgress = _stmt.getLong(_columnIndexOfProgress).toInt()
          val _tmpStatus: DownloadStatus
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfStatus)
          _tmpStatus = __converters.toStatus(_tmp)
          val _tmpSpeed: String?
          if (_stmt.isNull(_columnIndexOfSpeed)) {
            _tmpSpeed = null
          } else {
            _tmpSpeed = _stmt.getText(_columnIndexOfSpeed)
          }
          val _tmpEta: String?
          if (_stmt.isNull(_columnIndexOfEta)) {
            _tmpEta = null
          } else {
            _tmpEta = _stmt.getText(_columnIndexOfEta)
          }
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpFilePath: String?
          if (_stmt.isNull(_columnIndexOfFilePath)) {
            _tmpFilePath = null
          } else {
            _tmpFilePath = _stmt.getText(_columnIndexOfFilePath)
          }
          val _tmpCodec: String?
          if (_stmt.isNull(_columnIndexOfCodec)) {
            _tmpCodec = null
          } else {
            _tmpCodec = _stmt.getText(_columnIndexOfCodec)
          }
          val _tmpFps: Int?
          if (_stmt.isNull(_columnIndexOfFps)) {
            _tmpFps = null
          } else {
            _tmpFps = _stmt.getLong(_columnIndexOfFps).toInt()
          }
          val _tmpDuration: String?
          if (_stmt.isNull(_columnIndexOfDuration)) {
            _tmpDuration = null
          } else {
            _tmpDuration = _stmt.getText(_columnIndexOfDuration)
          }
          val _tmpMimeType: String?
          if (_stmt.isNull(_columnIndexOfMimeType)) {
            _tmpMimeType = null
          } else {
            _tmpMimeType = _stmt.getText(_columnIndexOfMimeType)
          }
          val _tmpFormatId: String?
          if (_stmt.isNull(_columnIndexOfFormatId)) {
            _tmpFormatId = null
          } else {
            _tmpFormatId = _stmt.getText(_columnIndexOfFormatId)
          }
          val _tmpDownloadType: String?
          if (_stmt.isNull(_columnIndexOfDownloadType)) {
            _tmpDownloadType = null
          } else {
            _tmpDownloadType = _stmt.getText(_columnIndexOfDownloadType)
          }
          val _tmpPlayerClient: String?
          if (_stmt.isNull(_columnIndexOfPlayerClient)) {
            _tmpPlayerClient = null
          } else {
            _tmpPlayerClient = _stmt.getText(_columnIndexOfPlayerClient)
          }
          val _tmpPlatform: String?
          if (_stmt.isNull(_columnIndexOfPlatform)) {
            _tmpPlatform = null
          } else {
            _tmpPlatform = _stmt.getText(_columnIndexOfPlatform)
          }
          val _tmpLastError: String?
          if (_stmt.isNull(_columnIndexOfLastError)) {
            _tmpLastError = null
          } else {
            _tmpLastError = _stmt.getText(_columnIndexOfLastError)
          }
          val _tmpStackTrace: String?
          if (_stmt.isNull(_columnIndexOfStackTrace)) {
            _tmpStackTrace = null
          } else {
            _tmpStackTrace = _stmt.getText(_columnIndexOfStackTrace)
          }
          _item = DownloadEntity(_tmpId,_tmpUrl,_tmpTitle,_tmpThumbnail,_tmpChannel,_tmpResolution,_tmpTotalSize,_tmpDownloadedSize,_tmpProgress,_tmpStatus,_tmpSpeed,_tmpEta,_tmpTimestamp,_tmpFilePath,_tmpCodec,_tmpFps,_tmpDuration,_tmpMimeType,_tmpFormatId,_tmpDownloadType,_tmpPlayerClient,_tmpPlatform,_tmpLastError,_tmpStackTrace)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getActiveDownloads(limit: Int): List<DownloadEntity> {
    val _sql: String = "SELECT * FROM downloads WHERE status = 'DOWNLOADING' OR status = 'QUEUED' LIMIT ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUrl: Int = getColumnIndexOrThrow(_stmt, "url")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfThumbnail: Int = getColumnIndexOrThrow(_stmt, "thumbnail")
        val _columnIndexOfChannel: Int = getColumnIndexOrThrow(_stmt, "channel")
        val _columnIndexOfResolution: Int = getColumnIndexOrThrow(_stmt, "resolution")
        val _columnIndexOfTotalSize: Int = getColumnIndexOrThrow(_stmt, "totalSize")
        val _columnIndexOfDownloadedSize: Int = getColumnIndexOrThrow(_stmt, "downloadedSize")
        val _columnIndexOfProgress: Int = getColumnIndexOrThrow(_stmt, "progress")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfSpeed: Int = getColumnIndexOrThrow(_stmt, "speed")
        val _columnIndexOfEta: Int = getColumnIndexOrThrow(_stmt, "eta")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfFilePath: Int = getColumnIndexOrThrow(_stmt, "filePath")
        val _columnIndexOfCodec: Int = getColumnIndexOrThrow(_stmt, "codec")
        val _columnIndexOfFps: Int = getColumnIndexOrThrow(_stmt, "fps")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfMimeType: Int = getColumnIndexOrThrow(_stmt, "mimeType")
        val _columnIndexOfFormatId: Int = getColumnIndexOrThrow(_stmt, "formatId")
        val _columnIndexOfDownloadType: Int = getColumnIndexOrThrow(_stmt, "downloadType")
        val _columnIndexOfPlayerClient: Int = getColumnIndexOrThrow(_stmt, "playerClient")
        val _columnIndexOfPlatform: Int = getColumnIndexOrThrow(_stmt, "platform")
        val _columnIndexOfLastError: Int = getColumnIndexOrThrow(_stmt, "lastError")
        val _columnIndexOfStackTrace: Int = getColumnIndexOrThrow(_stmt, "stackTrace")
        val _result: MutableList<DownloadEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: DownloadEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpUrl: String
          _tmpUrl = _stmt.getText(_columnIndexOfUrl)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpThumbnail: String?
          if (_stmt.isNull(_columnIndexOfThumbnail)) {
            _tmpThumbnail = null
          } else {
            _tmpThumbnail = _stmt.getText(_columnIndexOfThumbnail)
          }
          val _tmpChannel: String?
          if (_stmt.isNull(_columnIndexOfChannel)) {
            _tmpChannel = null
          } else {
            _tmpChannel = _stmt.getText(_columnIndexOfChannel)
          }
          val _tmpResolution: String?
          if (_stmt.isNull(_columnIndexOfResolution)) {
            _tmpResolution = null
          } else {
            _tmpResolution = _stmt.getText(_columnIndexOfResolution)
          }
          val _tmpTotalSize: Long
          _tmpTotalSize = _stmt.getLong(_columnIndexOfTotalSize)
          val _tmpDownloadedSize: Long
          _tmpDownloadedSize = _stmt.getLong(_columnIndexOfDownloadedSize)
          val _tmpProgress: Int
          _tmpProgress = _stmt.getLong(_columnIndexOfProgress).toInt()
          val _tmpStatus: DownloadStatus
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfStatus)
          _tmpStatus = __converters.toStatus(_tmp)
          val _tmpSpeed: String?
          if (_stmt.isNull(_columnIndexOfSpeed)) {
            _tmpSpeed = null
          } else {
            _tmpSpeed = _stmt.getText(_columnIndexOfSpeed)
          }
          val _tmpEta: String?
          if (_stmt.isNull(_columnIndexOfEta)) {
            _tmpEta = null
          } else {
            _tmpEta = _stmt.getText(_columnIndexOfEta)
          }
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpFilePath: String?
          if (_stmt.isNull(_columnIndexOfFilePath)) {
            _tmpFilePath = null
          } else {
            _tmpFilePath = _stmt.getText(_columnIndexOfFilePath)
          }
          val _tmpCodec: String?
          if (_stmt.isNull(_columnIndexOfCodec)) {
            _tmpCodec = null
          } else {
            _tmpCodec = _stmt.getText(_columnIndexOfCodec)
          }
          val _tmpFps: Int?
          if (_stmt.isNull(_columnIndexOfFps)) {
            _tmpFps = null
          } else {
            _tmpFps = _stmt.getLong(_columnIndexOfFps).toInt()
          }
          val _tmpDuration: String?
          if (_stmt.isNull(_columnIndexOfDuration)) {
            _tmpDuration = null
          } else {
            _tmpDuration = _stmt.getText(_columnIndexOfDuration)
          }
          val _tmpMimeType: String?
          if (_stmt.isNull(_columnIndexOfMimeType)) {
            _tmpMimeType = null
          } else {
            _tmpMimeType = _stmt.getText(_columnIndexOfMimeType)
          }
          val _tmpFormatId: String?
          if (_stmt.isNull(_columnIndexOfFormatId)) {
            _tmpFormatId = null
          } else {
            _tmpFormatId = _stmt.getText(_columnIndexOfFormatId)
          }
          val _tmpDownloadType: String?
          if (_stmt.isNull(_columnIndexOfDownloadType)) {
            _tmpDownloadType = null
          } else {
            _tmpDownloadType = _stmt.getText(_columnIndexOfDownloadType)
          }
          val _tmpPlayerClient: String?
          if (_stmt.isNull(_columnIndexOfPlayerClient)) {
            _tmpPlayerClient = null
          } else {
            _tmpPlayerClient = _stmt.getText(_columnIndexOfPlayerClient)
          }
          val _tmpPlatform: String?
          if (_stmt.isNull(_columnIndexOfPlatform)) {
            _tmpPlatform = null
          } else {
            _tmpPlatform = _stmt.getText(_columnIndexOfPlatform)
          }
          val _tmpLastError: String?
          if (_stmt.isNull(_columnIndexOfLastError)) {
            _tmpLastError = null
          } else {
            _tmpLastError = _stmt.getText(_columnIndexOfLastError)
          }
          val _tmpStackTrace: String?
          if (_stmt.isNull(_columnIndexOfStackTrace)) {
            _tmpStackTrace = null
          } else {
            _tmpStackTrace = _stmt.getText(_columnIndexOfStackTrace)
          }
          _item = DownloadEntity(_tmpId,_tmpUrl,_tmpTitle,_tmpThumbnail,_tmpChannel,_tmpResolution,_tmpTotalSize,_tmpDownloadedSize,_tmpProgress,_tmpStatus,_tmpSpeed,_tmpEta,_tmpTimestamp,_tmpFilePath,_tmpCodec,_tmpFps,_tmpDuration,_tmpMimeType,_tmpFormatId,_tmpDownloadType,_tmpPlayerClient,_tmpPlatform,_tmpLastError,_tmpStackTrace)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getDownloadById(id: Long): DownloadEntity? {
    val _sql: String = "SELECT * FROM downloads WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUrl: Int = getColumnIndexOrThrow(_stmt, "url")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfThumbnail: Int = getColumnIndexOrThrow(_stmt, "thumbnail")
        val _columnIndexOfChannel: Int = getColumnIndexOrThrow(_stmt, "channel")
        val _columnIndexOfResolution: Int = getColumnIndexOrThrow(_stmt, "resolution")
        val _columnIndexOfTotalSize: Int = getColumnIndexOrThrow(_stmt, "totalSize")
        val _columnIndexOfDownloadedSize: Int = getColumnIndexOrThrow(_stmt, "downloadedSize")
        val _columnIndexOfProgress: Int = getColumnIndexOrThrow(_stmt, "progress")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfSpeed: Int = getColumnIndexOrThrow(_stmt, "speed")
        val _columnIndexOfEta: Int = getColumnIndexOrThrow(_stmt, "eta")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfFilePath: Int = getColumnIndexOrThrow(_stmt, "filePath")
        val _columnIndexOfCodec: Int = getColumnIndexOrThrow(_stmt, "codec")
        val _columnIndexOfFps: Int = getColumnIndexOrThrow(_stmt, "fps")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfMimeType: Int = getColumnIndexOrThrow(_stmt, "mimeType")
        val _columnIndexOfFormatId: Int = getColumnIndexOrThrow(_stmt, "formatId")
        val _columnIndexOfDownloadType: Int = getColumnIndexOrThrow(_stmt, "downloadType")
        val _columnIndexOfPlayerClient: Int = getColumnIndexOrThrow(_stmt, "playerClient")
        val _columnIndexOfPlatform: Int = getColumnIndexOrThrow(_stmt, "platform")
        val _columnIndexOfLastError: Int = getColumnIndexOrThrow(_stmt, "lastError")
        val _columnIndexOfStackTrace: Int = getColumnIndexOrThrow(_stmt, "stackTrace")
        val _result: DownloadEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpUrl: String
          _tmpUrl = _stmt.getText(_columnIndexOfUrl)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpThumbnail: String?
          if (_stmt.isNull(_columnIndexOfThumbnail)) {
            _tmpThumbnail = null
          } else {
            _tmpThumbnail = _stmt.getText(_columnIndexOfThumbnail)
          }
          val _tmpChannel: String?
          if (_stmt.isNull(_columnIndexOfChannel)) {
            _tmpChannel = null
          } else {
            _tmpChannel = _stmt.getText(_columnIndexOfChannel)
          }
          val _tmpResolution: String?
          if (_stmt.isNull(_columnIndexOfResolution)) {
            _tmpResolution = null
          } else {
            _tmpResolution = _stmt.getText(_columnIndexOfResolution)
          }
          val _tmpTotalSize: Long
          _tmpTotalSize = _stmt.getLong(_columnIndexOfTotalSize)
          val _tmpDownloadedSize: Long
          _tmpDownloadedSize = _stmt.getLong(_columnIndexOfDownloadedSize)
          val _tmpProgress: Int
          _tmpProgress = _stmt.getLong(_columnIndexOfProgress).toInt()
          val _tmpStatus: DownloadStatus
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfStatus)
          _tmpStatus = __converters.toStatus(_tmp)
          val _tmpSpeed: String?
          if (_stmt.isNull(_columnIndexOfSpeed)) {
            _tmpSpeed = null
          } else {
            _tmpSpeed = _stmt.getText(_columnIndexOfSpeed)
          }
          val _tmpEta: String?
          if (_stmt.isNull(_columnIndexOfEta)) {
            _tmpEta = null
          } else {
            _tmpEta = _stmt.getText(_columnIndexOfEta)
          }
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpFilePath: String?
          if (_stmt.isNull(_columnIndexOfFilePath)) {
            _tmpFilePath = null
          } else {
            _tmpFilePath = _stmt.getText(_columnIndexOfFilePath)
          }
          val _tmpCodec: String?
          if (_stmt.isNull(_columnIndexOfCodec)) {
            _tmpCodec = null
          } else {
            _tmpCodec = _stmt.getText(_columnIndexOfCodec)
          }
          val _tmpFps: Int?
          if (_stmt.isNull(_columnIndexOfFps)) {
            _tmpFps = null
          } else {
            _tmpFps = _stmt.getLong(_columnIndexOfFps).toInt()
          }
          val _tmpDuration: String?
          if (_stmt.isNull(_columnIndexOfDuration)) {
            _tmpDuration = null
          } else {
            _tmpDuration = _stmt.getText(_columnIndexOfDuration)
          }
          val _tmpMimeType: String?
          if (_stmt.isNull(_columnIndexOfMimeType)) {
            _tmpMimeType = null
          } else {
            _tmpMimeType = _stmt.getText(_columnIndexOfMimeType)
          }
          val _tmpFormatId: String?
          if (_stmt.isNull(_columnIndexOfFormatId)) {
            _tmpFormatId = null
          } else {
            _tmpFormatId = _stmt.getText(_columnIndexOfFormatId)
          }
          val _tmpDownloadType: String?
          if (_stmt.isNull(_columnIndexOfDownloadType)) {
            _tmpDownloadType = null
          } else {
            _tmpDownloadType = _stmt.getText(_columnIndexOfDownloadType)
          }
          val _tmpPlayerClient: String?
          if (_stmt.isNull(_columnIndexOfPlayerClient)) {
            _tmpPlayerClient = null
          } else {
            _tmpPlayerClient = _stmt.getText(_columnIndexOfPlayerClient)
          }
          val _tmpPlatform: String?
          if (_stmt.isNull(_columnIndexOfPlatform)) {
            _tmpPlatform = null
          } else {
            _tmpPlatform = _stmt.getText(_columnIndexOfPlatform)
          }
          val _tmpLastError: String?
          if (_stmt.isNull(_columnIndexOfLastError)) {
            _tmpLastError = null
          } else {
            _tmpLastError = _stmt.getText(_columnIndexOfLastError)
          }
          val _tmpStackTrace: String?
          if (_stmt.isNull(_columnIndexOfStackTrace)) {
            _tmpStackTrace = null
          } else {
            _tmpStackTrace = _stmt.getText(_columnIndexOfStackTrace)
          }
          _result = DownloadEntity(_tmpId,_tmpUrl,_tmpTitle,_tmpThumbnail,_tmpChannel,_tmpResolution,_tmpTotalSize,_tmpDownloadedSize,_tmpProgress,_tmpStatus,_tmpSpeed,_tmpEta,_tmpTimestamp,_tmpFilePath,_tmpCodec,_tmpFps,_tmpDuration,_tmpMimeType,_tmpFormatId,_tmpDownloadType,_tmpPlayerClient,_tmpPlatform,_tmpLastError,_tmpStackTrace)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getDownloadByUrl(url: String): DownloadEntity? {
    val _sql: String = "SELECT * FROM downloads WHERE url = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, url)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUrl: Int = getColumnIndexOrThrow(_stmt, "url")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfThumbnail: Int = getColumnIndexOrThrow(_stmt, "thumbnail")
        val _columnIndexOfChannel: Int = getColumnIndexOrThrow(_stmt, "channel")
        val _columnIndexOfResolution: Int = getColumnIndexOrThrow(_stmt, "resolution")
        val _columnIndexOfTotalSize: Int = getColumnIndexOrThrow(_stmt, "totalSize")
        val _columnIndexOfDownloadedSize: Int = getColumnIndexOrThrow(_stmt, "downloadedSize")
        val _columnIndexOfProgress: Int = getColumnIndexOrThrow(_stmt, "progress")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfSpeed: Int = getColumnIndexOrThrow(_stmt, "speed")
        val _columnIndexOfEta: Int = getColumnIndexOrThrow(_stmt, "eta")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfFilePath: Int = getColumnIndexOrThrow(_stmt, "filePath")
        val _columnIndexOfCodec: Int = getColumnIndexOrThrow(_stmt, "codec")
        val _columnIndexOfFps: Int = getColumnIndexOrThrow(_stmt, "fps")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfMimeType: Int = getColumnIndexOrThrow(_stmt, "mimeType")
        val _columnIndexOfFormatId: Int = getColumnIndexOrThrow(_stmt, "formatId")
        val _columnIndexOfDownloadType: Int = getColumnIndexOrThrow(_stmt, "downloadType")
        val _columnIndexOfPlayerClient: Int = getColumnIndexOrThrow(_stmt, "playerClient")
        val _columnIndexOfPlatform: Int = getColumnIndexOrThrow(_stmt, "platform")
        val _columnIndexOfLastError: Int = getColumnIndexOrThrow(_stmt, "lastError")
        val _columnIndexOfStackTrace: Int = getColumnIndexOrThrow(_stmt, "stackTrace")
        val _result: DownloadEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpUrl: String
          _tmpUrl = _stmt.getText(_columnIndexOfUrl)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpThumbnail: String?
          if (_stmt.isNull(_columnIndexOfThumbnail)) {
            _tmpThumbnail = null
          } else {
            _tmpThumbnail = _stmt.getText(_columnIndexOfThumbnail)
          }
          val _tmpChannel: String?
          if (_stmt.isNull(_columnIndexOfChannel)) {
            _tmpChannel = null
          } else {
            _tmpChannel = _stmt.getText(_columnIndexOfChannel)
          }
          val _tmpResolution: String?
          if (_stmt.isNull(_columnIndexOfResolution)) {
            _tmpResolution = null
          } else {
            _tmpResolution = _stmt.getText(_columnIndexOfResolution)
          }
          val _tmpTotalSize: Long
          _tmpTotalSize = _stmt.getLong(_columnIndexOfTotalSize)
          val _tmpDownloadedSize: Long
          _tmpDownloadedSize = _stmt.getLong(_columnIndexOfDownloadedSize)
          val _tmpProgress: Int
          _tmpProgress = _stmt.getLong(_columnIndexOfProgress).toInt()
          val _tmpStatus: DownloadStatus
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfStatus)
          _tmpStatus = __converters.toStatus(_tmp)
          val _tmpSpeed: String?
          if (_stmt.isNull(_columnIndexOfSpeed)) {
            _tmpSpeed = null
          } else {
            _tmpSpeed = _stmt.getText(_columnIndexOfSpeed)
          }
          val _tmpEta: String?
          if (_stmt.isNull(_columnIndexOfEta)) {
            _tmpEta = null
          } else {
            _tmpEta = _stmt.getText(_columnIndexOfEta)
          }
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpFilePath: String?
          if (_stmt.isNull(_columnIndexOfFilePath)) {
            _tmpFilePath = null
          } else {
            _tmpFilePath = _stmt.getText(_columnIndexOfFilePath)
          }
          val _tmpCodec: String?
          if (_stmt.isNull(_columnIndexOfCodec)) {
            _tmpCodec = null
          } else {
            _tmpCodec = _stmt.getText(_columnIndexOfCodec)
          }
          val _tmpFps: Int?
          if (_stmt.isNull(_columnIndexOfFps)) {
            _tmpFps = null
          } else {
            _tmpFps = _stmt.getLong(_columnIndexOfFps).toInt()
          }
          val _tmpDuration: String?
          if (_stmt.isNull(_columnIndexOfDuration)) {
            _tmpDuration = null
          } else {
            _tmpDuration = _stmt.getText(_columnIndexOfDuration)
          }
          val _tmpMimeType: String?
          if (_stmt.isNull(_columnIndexOfMimeType)) {
            _tmpMimeType = null
          } else {
            _tmpMimeType = _stmt.getText(_columnIndexOfMimeType)
          }
          val _tmpFormatId: String?
          if (_stmt.isNull(_columnIndexOfFormatId)) {
            _tmpFormatId = null
          } else {
            _tmpFormatId = _stmt.getText(_columnIndexOfFormatId)
          }
          val _tmpDownloadType: String?
          if (_stmt.isNull(_columnIndexOfDownloadType)) {
            _tmpDownloadType = null
          } else {
            _tmpDownloadType = _stmt.getText(_columnIndexOfDownloadType)
          }
          val _tmpPlayerClient: String?
          if (_stmt.isNull(_columnIndexOfPlayerClient)) {
            _tmpPlayerClient = null
          } else {
            _tmpPlayerClient = _stmt.getText(_columnIndexOfPlayerClient)
          }
          val _tmpPlatform: String?
          if (_stmt.isNull(_columnIndexOfPlatform)) {
            _tmpPlatform = null
          } else {
            _tmpPlatform = _stmt.getText(_columnIndexOfPlatform)
          }
          val _tmpLastError: String?
          if (_stmt.isNull(_columnIndexOfLastError)) {
            _tmpLastError = null
          } else {
            _tmpLastError = _stmt.getText(_columnIndexOfLastError)
          }
          val _tmpStackTrace: String?
          if (_stmt.isNull(_columnIndexOfStackTrace)) {
            _tmpStackTrace = null
          } else {
            _tmpStackTrace = _stmt.getText(_columnIndexOfStackTrace)
          }
          _result = DownloadEntity(_tmpId,_tmpUrl,_tmpTitle,_tmpThumbnail,_tmpChannel,_tmpResolution,_tmpTotalSize,_tmpDownloadedSize,_tmpProgress,_tmpStatus,_tmpSpeed,_tmpEta,_tmpTimestamp,_tmpFilePath,_tmpCodec,_tmpFps,_tmpDuration,_tmpMimeType,_tmpFormatId,_tmpDownloadType,_tmpPlayerClient,_tmpPlatform,_tmpLastError,_tmpStackTrace)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTotalCount(): Int {
    val _sql: String = "SELECT COUNT(*) FROM downloads"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getCompletedCount(): Int {
    val _sql: String = "SELECT COUNT(*) FROM downloads WHERE status = 'COMPLETED'"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getFailedCount(): Int {
    val _sql: String = "SELECT COUNT(*) FROM downloads WHERE status = 'FAILED'"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getLastDownload(): DownloadEntity? {
    val _sql: String = "SELECT * FROM downloads ORDER BY timestamp DESC LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUrl: Int = getColumnIndexOrThrow(_stmt, "url")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfThumbnail: Int = getColumnIndexOrThrow(_stmt, "thumbnail")
        val _columnIndexOfChannel: Int = getColumnIndexOrThrow(_stmt, "channel")
        val _columnIndexOfResolution: Int = getColumnIndexOrThrow(_stmt, "resolution")
        val _columnIndexOfTotalSize: Int = getColumnIndexOrThrow(_stmt, "totalSize")
        val _columnIndexOfDownloadedSize: Int = getColumnIndexOrThrow(_stmt, "downloadedSize")
        val _columnIndexOfProgress: Int = getColumnIndexOrThrow(_stmt, "progress")
        val _columnIndexOfStatus: Int = getColumnIndexOrThrow(_stmt, "status")
        val _columnIndexOfSpeed: Int = getColumnIndexOrThrow(_stmt, "speed")
        val _columnIndexOfEta: Int = getColumnIndexOrThrow(_stmt, "eta")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfFilePath: Int = getColumnIndexOrThrow(_stmt, "filePath")
        val _columnIndexOfCodec: Int = getColumnIndexOrThrow(_stmt, "codec")
        val _columnIndexOfFps: Int = getColumnIndexOrThrow(_stmt, "fps")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfMimeType: Int = getColumnIndexOrThrow(_stmt, "mimeType")
        val _columnIndexOfFormatId: Int = getColumnIndexOrThrow(_stmt, "formatId")
        val _columnIndexOfDownloadType: Int = getColumnIndexOrThrow(_stmt, "downloadType")
        val _columnIndexOfPlayerClient: Int = getColumnIndexOrThrow(_stmt, "playerClient")
        val _columnIndexOfPlatform: Int = getColumnIndexOrThrow(_stmt, "platform")
        val _columnIndexOfLastError: Int = getColumnIndexOrThrow(_stmt, "lastError")
        val _columnIndexOfStackTrace: Int = getColumnIndexOrThrow(_stmt, "stackTrace")
        val _result: DownloadEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpUrl: String
          _tmpUrl = _stmt.getText(_columnIndexOfUrl)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpThumbnail: String?
          if (_stmt.isNull(_columnIndexOfThumbnail)) {
            _tmpThumbnail = null
          } else {
            _tmpThumbnail = _stmt.getText(_columnIndexOfThumbnail)
          }
          val _tmpChannel: String?
          if (_stmt.isNull(_columnIndexOfChannel)) {
            _tmpChannel = null
          } else {
            _tmpChannel = _stmt.getText(_columnIndexOfChannel)
          }
          val _tmpResolution: String?
          if (_stmt.isNull(_columnIndexOfResolution)) {
            _tmpResolution = null
          } else {
            _tmpResolution = _stmt.getText(_columnIndexOfResolution)
          }
          val _tmpTotalSize: Long
          _tmpTotalSize = _stmt.getLong(_columnIndexOfTotalSize)
          val _tmpDownloadedSize: Long
          _tmpDownloadedSize = _stmt.getLong(_columnIndexOfDownloadedSize)
          val _tmpProgress: Int
          _tmpProgress = _stmt.getLong(_columnIndexOfProgress).toInt()
          val _tmpStatus: DownloadStatus
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfStatus)
          _tmpStatus = __converters.toStatus(_tmp)
          val _tmpSpeed: String?
          if (_stmt.isNull(_columnIndexOfSpeed)) {
            _tmpSpeed = null
          } else {
            _tmpSpeed = _stmt.getText(_columnIndexOfSpeed)
          }
          val _tmpEta: String?
          if (_stmt.isNull(_columnIndexOfEta)) {
            _tmpEta = null
          } else {
            _tmpEta = _stmt.getText(_columnIndexOfEta)
          }
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpFilePath: String?
          if (_stmt.isNull(_columnIndexOfFilePath)) {
            _tmpFilePath = null
          } else {
            _tmpFilePath = _stmt.getText(_columnIndexOfFilePath)
          }
          val _tmpCodec: String?
          if (_stmt.isNull(_columnIndexOfCodec)) {
            _tmpCodec = null
          } else {
            _tmpCodec = _stmt.getText(_columnIndexOfCodec)
          }
          val _tmpFps: Int?
          if (_stmt.isNull(_columnIndexOfFps)) {
            _tmpFps = null
          } else {
            _tmpFps = _stmt.getLong(_columnIndexOfFps).toInt()
          }
          val _tmpDuration: String?
          if (_stmt.isNull(_columnIndexOfDuration)) {
            _tmpDuration = null
          } else {
            _tmpDuration = _stmt.getText(_columnIndexOfDuration)
          }
          val _tmpMimeType: String?
          if (_stmt.isNull(_columnIndexOfMimeType)) {
            _tmpMimeType = null
          } else {
            _tmpMimeType = _stmt.getText(_columnIndexOfMimeType)
          }
          val _tmpFormatId: String?
          if (_stmt.isNull(_columnIndexOfFormatId)) {
            _tmpFormatId = null
          } else {
            _tmpFormatId = _stmt.getText(_columnIndexOfFormatId)
          }
          val _tmpDownloadType: String?
          if (_stmt.isNull(_columnIndexOfDownloadType)) {
            _tmpDownloadType = null
          } else {
            _tmpDownloadType = _stmt.getText(_columnIndexOfDownloadType)
          }
          val _tmpPlayerClient: String?
          if (_stmt.isNull(_columnIndexOfPlayerClient)) {
            _tmpPlayerClient = null
          } else {
            _tmpPlayerClient = _stmt.getText(_columnIndexOfPlayerClient)
          }
          val _tmpPlatform: String?
          if (_stmt.isNull(_columnIndexOfPlatform)) {
            _tmpPlatform = null
          } else {
            _tmpPlatform = _stmt.getText(_columnIndexOfPlatform)
          }
          val _tmpLastError: String?
          if (_stmt.isNull(_columnIndexOfLastError)) {
            _tmpLastError = null
          } else {
            _tmpLastError = _stmt.getText(_columnIndexOfLastError)
          }
          val _tmpStackTrace: String?
          if (_stmt.isNull(_columnIndexOfStackTrace)) {
            _tmpStackTrace = null
          } else {
            _tmpStackTrace = _stmt.getText(_columnIndexOfStackTrace)
          }
          _result = DownloadEntity(_tmpId,_tmpUrl,_tmpTitle,_tmpThumbnail,_tmpChannel,_tmpResolution,_tmpTotalSize,_tmpDownloadedSize,_tmpProgress,_tmpStatus,_tmpSpeed,_tmpEta,_tmpTimestamp,_tmpFilePath,_tmpCodec,_tmpFps,_tmpDuration,_tmpMimeType,_tmpFormatId,_tmpDownloadType,_tmpPlayerClient,_tmpPlatform,_tmpLastError,_tmpStackTrace)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getActiveCount(): Int {
    val _sql: String = "SELECT COUNT(*) FROM downloads WHERE status = 'DOWNLOADING'"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateStatus(id: Long, status: DownloadStatus): Int {
    val _sql: String = "UPDATE downloads SET status = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: String = __converters.fromStatus(status)
        _stmt.bindText(_argIndex, _tmp)
        _argIndex = 2
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
        getTotalChangedRows(_connection)
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun resetDownloadingToQueued(): Int {
    val _sql: String = "UPDATE downloads SET status = 'QUEUED' WHERE status = 'DOWNLOADING'"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
        getTotalChangedRows(_connection)
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun renameDownload(
    id: Long,
    title: String,
    filePath: String,
  ): Int {
    val _sql: String = "UPDATE downloads SET title = ?, filePath = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, title)
        _argIndex = 2
        _stmt.bindText(_argIndex, filePath)
        _argIndex = 3
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
        getTotalChangedRows(_connection)
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateError(
    id: Long,
    status: DownloadStatus,
    error: String?,
    stackTrace: String?,
  ): Int {
    val _sql: String = "UPDATE downloads SET status = ?, lastError = ?, stackTrace = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        val _tmp: String = __converters.fromStatus(status)
        _stmt.bindText(_argIndex, _tmp)
        _argIndex = 2
        if (error == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, error)
        }
        _argIndex = 3
        if (stackTrace == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, stackTrace)
        }
        _argIndex = 4
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
        getTotalChangedRows(_connection)
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
