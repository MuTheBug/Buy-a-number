package com.buyanumber.app.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedOrderDao {

    @Query("SELECT * FROM tracked_orders ORDER BY createdAtEpochMillis DESC, id DESC")
    fun observeAll(): Flow<List<TrackedOrder>>

    @Query("SELECT * FROM tracked_orders WHERE status IN ('PENDING', 'RECEIVED') ORDER BY createdAtEpochMillis DESC, id DESC")
    fun observeActive(): Flow<List<TrackedOrder>>

    @Query("SELECT * FROM tracked_orders WHERE status IN ('PENDING', 'RECEIVED')")
    suspend fun activeOrders(): List<TrackedOrder>

    @Query("SELECT * FROM tracked_orders WHERE id = :id")
    suspend fun byId(id: Long): TrackedOrder?

    @Upsert
    suspend fun upsert(order: TrackedOrder)

    @Upsert
    suspend fun upsertAll(orders: List<TrackedOrder>)

    @Query("UPDATE tracked_orders SET notifiedSmsCount = :count WHERE id = :id")
    suspend fun markNotified(id: Long, count: Int)

    @Query("DELETE FROM tracked_orders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tracked_orders")
    suspend fun clear()
}
