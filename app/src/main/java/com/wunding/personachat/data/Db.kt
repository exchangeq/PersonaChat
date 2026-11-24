package com.wunding.personachat.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val personaName: String,
    val position: String,
    val personaFull: String,
    val fullMode: Boolean,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,
    val content: String,
    val ts: Long,
)

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 0 永久 / 1 长期 / 2 短期 */
    val level: Int,
    val content: String,
    val sourceSession: String,
    val ts: Long,
    val locked: Boolean = false,
)

@Dao
interface PersonaDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun sessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE personaName = :name ORDER BY updatedAt DESC LIMIT 1")
    suspend fun sessionForPersona(name: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSession(s: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM messages WHERE sessionId = :sid")
    suspend fun deleteMessages(sid: String)

    @Query("SELECT * FROM messages WHERE sessionId = :sid ORDER BY id ASC")
    fun messages(sid: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sid ORDER BY id DESC LIMIT 1")
    suspend fun lastMessage(sid: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE sessionId = :sid ORDER BY id DESC LIMIT :n")
    suspend fun recent(sid: String, n: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sid")
    suspend fun countMessages(sid: String): Int

    @Insert
    suspend fun addMessage(m: MessageEntity): Long

    @Query("DELETE FROM messages WHERE sessionId = :sid AND id > :id")
    suspend fun deleteAfter(sid: String, id: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateMessage(id: Long, content: String)

    @Query("SELECT * FROM memories ORDER BY level ASC, ts DESC")
    fun memories(): Flow<List<MemoryEntity>>

    @Insert
    suspend fun addMemory(m: MemoryEntity): Long

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemory(id: Long)

    @Query("UPDATE memories SET content = :content WHERE id = :id")
    suspend fun updateMemory(id: Long, content: String)

    @Query("UPDATE memories SET level = :level WHERE id = :id")
    suspend fun setMemoryLevel(id: Long, level: Int)

    @Query("SELECT * FROM memories WHERE level = 2 AND sourceSession = :sid ORDER BY ts ASC")
    suspend fun shortOf(sid: String): List<MemoryEntity>

    @Query("DELETE FROM memories WHERE level = 2 AND sourceSession = :sid")
    suspend fun clearShort(sid: String)

    @Query("SELECT * FROM memories WHERE level <= :maxLevel ORDER BY ts DESC")
    suspend fun recallPool(maxLevel: Int): List<MemoryEntity>

    @Query("SELECT COUNT(*) FROM memories WHERE level = :level")
    suspend fun count(level: Int): Int
}

@Database(entities = [SessionEntity::class, MessageEntity::class, MemoryEntity::class], version = 1, exportSchema = false)
abstract class PersonaDb : RoomDatabase() {
    abstract fun dao(): PersonaDao
}

fun openDb(context: Context): PersonaDb =
    Room.databaseBuilder(context, PersonaDb::class.java, "persona.db").fallbackToDestructiveMigration().build()
