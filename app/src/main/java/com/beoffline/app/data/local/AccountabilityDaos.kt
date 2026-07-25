package com.beoffline.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.beoffline.app.data.model.CachedUnlockRequest
import com.beoffline.app.data.model.ChatMessageCache
import com.beoffline.app.data.model.GroupCache
import com.beoffline.app.data.model.OutboxItem
import com.beoffline.app.data.model.Partner
import kotlinx.coroutines.flow.Flow

@Dao
interface PartnerDao {
    @Query("SELECT * FROM partners WHERE status = 'Active'")
    fun getActive(): Flow<List<Partner>>

    @Query("SELECT * FROM partners WHERE status = 'Active'")
    suspend fun getActiveOnce(): List<Partner>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(partners: List<Partner>)

    @Query("DELETE FROM partners")
    suspend fun clear()
}

@Dao
interface UnlockRequestCacheDao {
    @Query("SELECT * FROM unlock_request_cache ORDER BY requestedAtUtc DESC")
    fun getAll(): Flow<List<CachedUnlockRequest>>

    @Query("SELECT * FROM unlock_request_cache WHERE direction = 'INCOMING' AND status = 'Pending' ORDER BY requestedAtUtc DESC")
    fun getIncomingPending(): Flow<List<CachedUnlockRequest>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(request: CachedUnlockRequest)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(requests: List<CachedUnlockRequest>)

    @Query("DELETE FROM unlock_request_cache WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM unlock_request_cache WHERE requestedAtUtc < :beforeMillis")
    suspend fun pruneOlderThan(beforeMillis: Long)

    /** Clears resolved history but keeps anything still in-flight (incoming to act on, or queued outgoing). */
    @Query("DELETE FROM unlock_request_cache WHERE status NOT IN ('Pending', 'Queued')")
    suspend fun clearResolved()

    @Query("DELETE FROM unlock_request_cache")
    suspend fun clearAll()
}

@Dao
interface GroupCacheDao {
    @Query("SELECT * FROM group_cache ORDER BY syncedAt ASC")
    fun getAll(): Flow<List<GroupCache>>

    @Query("SELECT * FROM group_cache ORDER BY syncedAt ASC")
    suspend fun getAllOnce(): List<GroupCache>

    @Query("SELECT * FROM group_cache WHERE groupId = :groupId")
    suspend fun getById(groupId: String): GroupCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(groups: List<GroupCache>)

    @Query("DELETE FROM group_cache")
    suspend fun clear()
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE conversationKey = :key ORDER BY sentAtUtc ASC")
    fun forConversation(key: String): Flow<List<ChatMessageCache>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: ChatMessageCache)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<ChatMessageCache>)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM chat_messages WHERE sentAtUtc < :beforeMillis AND pending = 0")
    suspend fun pruneOlderThan(beforeMillis: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox_items ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<OutboxItem>

    @Insert
    suspend fun insert(item: OutboxItem): Long

    @Query("DELETE FROM outbox_items WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("UPDATE outbox_items SET attempts = attempts + 1 WHERE id = :id")
    suspend fun incrementAttempts(id: Int)

    @Query("DELETE FROM outbox_items")
    suspend fun clearAll()
}
