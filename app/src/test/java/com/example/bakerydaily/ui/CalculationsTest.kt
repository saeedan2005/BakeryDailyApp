package com.example.bakerydaily.ui

import com.example.bakerydaily.data.DailyRecord
import org.junit.Assert.*
import org.junit.Test

class CalculationsTest {
    @Test fun cashSoldUsesStoredSettings() {
        val c = calculateRecord(50, 1000, 70, 20, 30, 56000, 500, 9)
        assertEquals(1008.0, c.cashSold, 0.0001)
        assertEquals(-128.0, c.difference, 0.0001)
    }

    @Test fun bagRateUsesFiftyKgBag() {
        val c = calculateRecord(50, 1000, 70, 20, 30, 0, 500, 9)
        assertEquals(1000.0, c.bagRate!!, 0.0001)
    }

    @Test fun zeroFlourMakesRateUnavailable() {
        val c = calculateRecord(0, 1000, 70, 20, 30, 0, 500, 9)
        assertNull(c.bagRate)
    }

    @Test fun statsCashAndDifferenceUseEachRecordSnapshot() {
        val rows = listOf(
            DailyRecord(1, 1, "2026-09-15", 50, 1000, 70, 20, 30, 56000, 500, 9),
            DailyRecord(2, 1, "2026-09-16", 50, 900, 70, 10, 20, 56000, 1000, 10)
        )
        val result = calculateStats(rows, "15/09/2026", "16/09/2026")
        assertEquals(2, result.count)
        assertEquals(1568.0, result.cash, 0.0001)
        assertEquals(112.0, result.difference, 0.0001)
        assertEquals(950.0, result.rate!!, 0.0001)
    }
}
