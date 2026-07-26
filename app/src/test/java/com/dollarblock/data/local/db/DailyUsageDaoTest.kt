package com.dollarblock.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Testes de DAO do [DailyUsageDao] rodando na JVM (Robolectric + Room in-memory),
 * sem emulador. Cobrem a semântica de `upsertUsage` (update-senão-insert) e a
 * garantia do índice único `(packageName, epochDay)` — a base para o uso do dia
 * nunca duplicar linhas por app/dia (o que inflaria o tempo medido).
 */
@RunWith(RobolectricTestRunner::class)
class DailyUsageDaoTest {

    private lateinit var db: DollarBlockDatabase
    private lateinit var dao: DailyUsageDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DollarBlockDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.dailyUsageDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert insere quando nao existe`() = runTest {
        dao.upsertUsage("com.app", epochDay = 100, usedMillis = 5_000, updatedAt = 1)

        val row = dao.getForDay("com.app", 100)
        assertEquals(5_000L, row?.usedMillis)
    }

    @Test
    fun `upsert atualiza a linha existente em vez de duplicar`() = runTest {
        dao.upsertUsage("com.app", epochDay = 100, usedMillis = 5_000, updatedAt = 1)
        dao.upsertUsage("com.app", epochDay = 100, usedMillis = 9_000, updatedAt = 2)

        val rows = dao.observeForDay(100).first()
        assertEquals(1, rows.size)             // não duplicou
        assertEquals(9_000L, rows.first().usedMillis) // valor atualizado
        assertEquals(2L, rows.first().updatedAt)
    }

    @Test
    fun `mesmo app em dias diferentes gera linhas distintas`() = runTest {
        dao.upsertUsage("com.app", epochDay = 100, usedMillis = 1_000, updatedAt = 1)
        dao.upsertUsage("com.app", epochDay = 101, usedMillis = 2_000, updatedAt = 1)

        assertEquals(1_000L, dao.getForDay("com.app", 100)?.usedMillis)
        assertEquals(2_000L, dao.getForDay("com.app", 101)?.usedMillis)
    }

    @Test
    fun `apps diferentes no mesmo dia coexistem`() = runTest {
        dao.upsertUsage("com.a", epochDay = 100, usedMillis = 1_000, updatedAt = 1)
        dao.upsertUsage("com.b", epochDay = 100, usedMillis = 2_000, updatedAt = 1)

        val rows = dao.observeForDay(100).first()
        assertEquals(2, rows.size)
    }

    @Test
    fun `getForDay retorna null quando nao ha registro`() = runTest {
        assertNull(dao.getForDay("com.missing", 100))
    }

    @Test
    fun `observeRange filtra pelo intervalo de dias inclusivo`() = runTest {
        dao.upsertUsage("com.app", epochDay = 99, usedMillis = 1, updatedAt = 1)
        dao.upsertUsage("com.app", epochDay = 100, usedMillis = 2, updatedAt = 1)
        dao.upsertUsage("com.app", epochDay = 101, usedMillis = 3, updatedAt = 1)
        dao.upsertUsage("com.app", epochDay = 105, usedMillis = 4, updatedAt = 1)

        val rows = dao.observeRange(100, 101).first()
        assertEquals(setOf(100L, 101L), rows.map { it.epochDay }.toSet())
    }
}
