package br.com.fpagency.fptube.`data`

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.RoomRawQuery
import androidx.room.RoomSQLiteQuery
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.db.SupportSQLiteQuery
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class LogDao_Impl(
  __db: RoomDatabase,
) : LogDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfLogEntity: EntityInsertAdapter<LogEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfLogEntity = object : EntityInsertAdapter<LogEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `logs` (`id`,`timestamp`,`level`,`tag`,`message`,`throwable`,`className`,`methodName`,`threadName`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: LogEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.timestamp)
        statement.bindText(3, entity.level)
        statement.bindText(4, entity.tag)
        statement.bindText(5, entity.message)
        val _tmpThrowable: String? = entity.throwable
        if (_tmpThrowable == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpThrowable)
        }
        val _tmpClassName: String? = entity.className
        if (_tmpClassName == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpClassName)
        }
        val _tmpMethodName: String? = entity.methodName
        if (_tmpMethodName == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpMethodName)
        }
        statement.bindText(9, entity.threadName)
      }
    }
  }

  public override suspend fun insert(log: LogEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfLogEntity.insert(_connection, log)
  }

  public override fun getAllLogs(): Flow<List<LogEntity>> {
    val _sql: String = "SELECT * FROM logs ORDER BY timestamp DESC"
    return createFlow(__db, false, arrayOf("logs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfLevel: Int = getColumnIndexOrThrow(_stmt, "level")
        val _columnIndexOfTag: Int = getColumnIndexOrThrow(_stmt, "tag")
        val _columnIndexOfMessage: Int = getColumnIndexOrThrow(_stmt, "message")
        val _columnIndexOfThrowable: Int = getColumnIndexOrThrow(_stmt, "throwable")
        val _columnIndexOfClassName: Int = getColumnIndexOrThrow(_stmt, "className")
        val _columnIndexOfMethodName: Int = getColumnIndexOrThrow(_stmt, "methodName")
        val _columnIndexOfThreadName: Int = getColumnIndexOrThrow(_stmt, "threadName")
        val _result: MutableList<LogEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: LogEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpLevel: String
          _tmpLevel = _stmt.getText(_columnIndexOfLevel)
          val _tmpTag: String
          _tmpTag = _stmt.getText(_columnIndexOfTag)
          val _tmpMessage: String
          _tmpMessage = _stmt.getText(_columnIndexOfMessage)
          val _tmpThrowable: String?
          if (_stmt.isNull(_columnIndexOfThrowable)) {
            _tmpThrowable = null
          } else {
            _tmpThrowable = _stmt.getText(_columnIndexOfThrowable)
          }
          val _tmpClassName: String?
          if (_stmt.isNull(_columnIndexOfClassName)) {
            _tmpClassName = null
          } else {
            _tmpClassName = _stmt.getText(_columnIndexOfClassName)
          }
          val _tmpMethodName: String?
          if (_stmt.isNull(_columnIndexOfMethodName)) {
            _tmpMethodName = null
          } else {
            _tmpMethodName = _stmt.getText(_columnIndexOfMethodName)
          }
          val _tmpThreadName: String
          _tmpThreadName = _stmt.getText(_columnIndexOfThreadName)
          _item = LogEntity(_tmpId,_tmpTimestamp,_tmpLevel,_tmpTag,_tmpMessage,_tmpThrowable,_tmpClassName,_tmpMethodName,_tmpThreadName)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getLatest1000Logs(): List<LogEntity> {
    val _sql: String = "SELECT * FROM logs ORDER BY timestamp DESC LIMIT 1000"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfLevel: Int = getColumnIndexOrThrow(_stmt, "level")
        val _columnIndexOfTag: Int = getColumnIndexOrThrow(_stmt, "tag")
        val _columnIndexOfMessage: Int = getColumnIndexOrThrow(_stmt, "message")
        val _columnIndexOfThrowable: Int = getColumnIndexOrThrow(_stmt, "throwable")
        val _columnIndexOfClassName: Int = getColumnIndexOrThrow(_stmt, "className")
        val _columnIndexOfMethodName: Int = getColumnIndexOrThrow(_stmt, "methodName")
        val _columnIndexOfThreadName: Int = getColumnIndexOrThrow(_stmt, "threadName")
        val _result: MutableList<LogEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: LogEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpLevel: String
          _tmpLevel = _stmt.getText(_columnIndexOfLevel)
          val _tmpTag: String
          _tmpTag = _stmt.getText(_columnIndexOfTag)
          val _tmpMessage: String
          _tmpMessage = _stmt.getText(_columnIndexOfMessage)
          val _tmpThrowable: String?
          if (_stmt.isNull(_columnIndexOfThrowable)) {
            _tmpThrowable = null
          } else {
            _tmpThrowable = _stmt.getText(_columnIndexOfThrowable)
          }
          val _tmpClassName: String?
          if (_stmt.isNull(_columnIndexOfClassName)) {
            _tmpClassName = null
          } else {
            _tmpClassName = _stmt.getText(_columnIndexOfClassName)
          }
          val _tmpMethodName: String?
          if (_stmt.isNull(_columnIndexOfMethodName)) {
            _tmpMethodName = null
          } else {
            _tmpMethodName = _stmt.getText(_columnIndexOfMethodName)
          }
          val _tmpThreadName: String
          _tmpThreadName = _stmt.getText(_columnIndexOfThreadName)
          _item = LogEntity(_tmpId,_tmpTimestamp,_tmpLevel,_tmpTag,_tmpMessage,_tmpThrowable,_tmpClassName,_tmpMethodName,_tmpThreadName)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getCount(): Int {
    val _sql: String = "SELECT COUNT(*) FROM logs"
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

  public override suspend fun deleteOldest(count: Int) {
    val _sql: String = "DELETE FROM logs WHERE id IN (SELECT id FROM logs ORDER BY timestamp ASC LIMIT ?)"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, count.toLong())
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clearAll() {
    val _sql: String = "DELETE FROM logs"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun checkpoint(supportSQLiteQuery: SupportSQLiteQuery): Int {
    val _rawQuery: RoomRawQuery = RoomSQLiteQuery.copyFrom(supportSQLiteQuery).toRoomRawQuery()
    val _sql: String = _rawQuery.sql
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _rawQuery.getBindingFunction().invoke(_stmt)
        val _result: Int
        if (_stmt.step()) {
          _result = _stmt.getLong(0).toInt()
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
