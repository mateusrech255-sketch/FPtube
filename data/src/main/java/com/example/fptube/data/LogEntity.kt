package br.com.fpagency.fptube.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["level"])
    ]
)
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val level: String, // INFO, WARNING, ERROR
    val tag: String,
    val message: String,
    val throwable: String? = null,
    val className: String? = null,
    val methodName: String? = null,
    val threadName: String = Thread.currentThread().name
)
