package com.example.bakerydaily.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BakeryDao {
    @Query("SELECT * FROM bakeries ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<Bakery>>

    @Query("SELECT * FROM bakeries WHERE id=:id LIMIT 1")
    suspend fun get(id: Long): Bakery?

    @Query("SELECT COUNT(*) FROM bakeries WHERE number=:number AND id != :id")
    suspend fun numberExists(number: String, id: Long): Int

    @Query("SELECT COUNT(*) FROM bakeries WHERE normalizedName=:name AND id != :id")
    suspend fun nameExists(name: String, id: Long): Int

    @Insert suspend fun insert(bakery: Bakery): Long
    @Update suspend fun update(bakery: Bakery)
    @Delete suspend fun delete(bakery: Bakery)
}

@Dao
interface DailyDao {
    @Query("SELECT * FROM daily_records WHERE bakeryId=:bakeryId AND date=:date LIMIT 1")
    suspend fun byDate(bakeryId: Long, date: String): DailyRecord?

    @Query("SELECT * FROM daily_records WHERE bakeryId=:bakeryId AND date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun between(bakeryId: Long, from: String, to: String): List<DailyRecord>

    @Query("SELECT COUNT(*) FROM daily_records WHERE bakeryId=:bakeryId")
    suspend fun countForBakery(bakeryId: Long): Long

    @Insert suspend fun insert(r: DailyRecord): Long
    @Update suspend fun update(r: DailyRecord)
    @Delete suspend fun delete(r: DailyRecord)
}
