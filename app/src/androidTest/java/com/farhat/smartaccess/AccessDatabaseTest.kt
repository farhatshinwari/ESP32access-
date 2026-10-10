package com.farhat.smartaccess

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.farhat.smartaccess.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AccessDatabaseTest {
    @Test fun logsPersistDeduplicateSortAndClear() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${System.nanoTime()}.db"
        var db = Room.databaseBuilder(context, AccessDatabase::class.java, name).build()
        try {
            db.logs().insert(listOf(AccessLog("a", "ABC", true, 1, "RFID"), AccessLog("b", "DEF", false, 2, "RFID")))
            db.close()
            db = Room.databaseBuilder(context, AccessDatabase::class.java, name).build()
            db.logs().insert(listOf(AccessLog("a", "ABC", true, 1, "RFID")))
            assertEquals(listOf("b", "a"), db.logs().observe().first().map { it.eventId })
            db.logs().clear(); assertTrue(db.logs().observe().first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
