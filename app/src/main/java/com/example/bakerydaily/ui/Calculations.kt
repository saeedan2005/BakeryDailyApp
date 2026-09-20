package com.example.bakerydaily.ui

import com.example.bakerydaily.data.DailyRecord

data class RecordCalculations(
    val cashSold: Double,
    val difference: Double,
    val bagRate: Double?
)

fun calculateRecord(flourKg: Long, loaves: Long, workersMeal: Long, waste: Long, creditSold: Long, revenue: Long, baseRevenue: Long, cashFactor: Long): RecordCalculations {
    val cash = if (revenue == 0L) 0.0 else revenue.toDouble() / baseRevenue.toDouble() * cashFactor.toDouble()
    val difference = loaves.toDouble() - workersMeal - waste - creditSold - cash
    val rate = if (flourKg == 0L) null else loaves.toDouble() / flourKg.toDouble() * 50.0
    return RecordCalculations(cash, difference, rate)
}

data class StatsResult(
    val from: String,
    val to: String,
    val count: Int,
    val flour: Long,
    val loaves: Long,
    val meal: Long,
    val waste: Long,
    val credit: Long,
    val cash: Double,
    val revenue: Long,
    val difference: Double,
    val rate: Double?
)

fun calculateStats(rows: List<DailyRecord>, from: String, to: String): StatsResult {
    val flour = rows.sumOf { it.flourKg }
    val loaves = rows.sumOf { it.loaves }
    val meal = rows.sumOf { it.workersMeal }
    val waste = rows.sumOf { it.waste }
    val credit = rows.sumOf { it.creditSold }
    val revenue = rows.sumOf { it.revenue }
    val cash = rows.sumOf { record -> calculateRecord(record.flourKg, record.loaves, record.workersMeal, record.waste, record.creditSold, record.revenue, record.baseRevenueUsed, record.cashFactorUsed).cashSold }
    val difference = rows.sumOf { record -> calculateRecord(record.flourKg, record.loaves, record.workersMeal, record.waste, record.creditSold, record.revenue, record.baseRevenueUsed, record.cashFactorUsed).difference }
    val rate = if (flour == 0L) null else loaves.toDouble() / flour.toDouble() * 50.0
    return StatsResult(from, to, rows.size, flour, loaves, meal, waste, credit, cash, revenue, difference, rate)
}
