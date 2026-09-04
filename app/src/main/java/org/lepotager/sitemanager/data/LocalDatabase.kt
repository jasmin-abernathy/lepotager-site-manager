package org.lepotager.sitemanager.data

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

@Entity(tableName = "sites")
data class SiteEntity(
    @PrimaryKey val siteId: String,
    val origin: String,
    val displayName: String,
    val apiBaseUrl: String,
    val manifestJson: String,
    val configJson: String,
    val configVersion: Long,
    val snapshotJson: String,
    val snapshotRevision: Long,
    val updatedAt: Long,
)

@Entity(tableName = "queued_changes")
data class QueuedChangeEntity(
    @PrimaryKey val clientRequestId: String,
    val siteId: String,
    val moduleId: String,
    val action: String,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
    val lastError: String = "",
)

@Dao
interface SiteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(site: SiteEntity)

    @Query("SELECT * FROM sites WHERE siteId = :siteId LIMIT 1")
    suspend fun get(siteId: String): SiteEntity?

    @Query("SELECT * FROM sites ORDER BY updatedAt DESC")
    suspend fun all(): List<SiteEntity>

    @Query("DELETE FROM sites WHERE siteId = :siteId")
    suspend fun delete(siteId: String)
}

@Dao
interface QueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(change: QueuedChangeEntity)

    @Query("SELECT * FROM queued_changes ORDER BY createdAt ASC")
    suspend fun all(): List<QueuedChangeEntity>

    @Query("DELETE FROM queued_changes WHERE clientRequestId = :id")
    suspend fun delete(id: String)

    @Query("UPDATE queued_changes SET attempts = attempts + 1, lastError = :error WHERE clientRequestId = :id")
    suspend fun fail(id: String, error: String)

    @Query("SELECT COUNT(*) FROM queued_changes")
    suspend fun count(): Int
}

@Database(
    entities = [SiteEntity::class, QueuedChangeEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun sites(): SiteDao
    abstract fun queue(): QueueDao

    companion object {
        @Volatile private var instance: LocalDatabase? = null

        fun get(context: Context): LocalDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LocalDatabase::class.java,
                "lepotager-site-manager.db",
            ).build().also { instance = it }
        }
    }
}
