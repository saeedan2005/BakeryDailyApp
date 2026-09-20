package com.example.bakerydaily.data

import androidx.room.*

@Entity(
    tableName = "bakeries",
    indices = [
        Index(value = ["number"], unique = true),
        Index(value = ["normalizedName"], unique = true)
    ]
)
data class Bakery(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val name: String,
    val normalizedName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val baseRevenue: Long = 500,
    val cashFactor: Long = 9
)

@Entity(
    tableName = "daily_records",
    foreignKeys = [ForeignKey(
        entity = Bakery::class,
        parentColumns = ["id"],
        childColumns = ["bakeryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["bakeryId", "date"], unique = true)]
)
data class DailyRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bakeryId: Long,
    val date: String,
    val flourKg: Long,
    val loaves: Long,
    val workersMeal: Long,
    val waste: Long,
    val creditSold: Long,
    val revenue: Long,
    val baseRevenueUsed: Long,
    val cashFactorUsed: Long
)
