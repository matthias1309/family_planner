package de.familyplanner.android.feature.meals.domain.cache

import de.familyplanner.android.core.testing.InMemorySessionStore
import de.familyplanner.android.feature.meals.fakes.childSession
import de.familyplanner.android.feature.meals.fakes.parentSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LastLoadedCacheTest {
    private val sessions = InMemorySessionStore(parentSession)
    private val cache = LastLoadedCache<Int, String>(sessions, maxKeys = 2)

    private fun store(
        key: Int,
        value: String,
    ) = cache.write(cache.ticket(), key, value)

    @Test
    fun `keys are kept independently`() {
        store(1, "a")
        store(2, "b")

        assertEquals("a", cache.read(1))
        assertEquals("b", cache.read(2))
    }

    @Test
    fun `oldest key is dropped beyond the limit`() {
        store(1, "a")
        store(2, "b")
        store(3, "c")

        assertNull(cache.read(1))
        assertEquals("c", cache.read(3))
    }

    @Test
    fun `writing a key again makes it the newest`() {
        store(1, "a")
        store(2, "b")
        store(1, "a2")
        store(3, "c")

        assertEquals("a2", cache.read(1))
        assertNull(cache.read(2))
    }

    @Test
    fun `values of another member are not shown`() {
        store(1, "a")

        sessions.setCurrent(childSession)

        assertNull(cache.read(1))
    }

    @Test
    fun `response for one member replaces the values of another`() {
        store(1, "a")
        sessions.setCurrent(childSession)

        store(2, "b")
        sessions.setCurrent(parentSession)

        assertNull(cache.read(1))
    }

    @Test
    fun `response requested before an own change is dropped`() {
        store(1, "a")
        val ticket = cache.ticket()

        cache.update(1) { "$it!" }
        cache.write(ticket, 1, "old")

        assertEquals("a!", cache.read(1))
    }

    @Test
    fun `update without stored value changes nothing`() {
        cache.update(1) { "x" }

        assertNull(cache.read(1))
    }

    @Test
    fun `response without member forgets everything`() {
        store(1, "a")
        sessions.setCurrent(null)
        store(2, "b")

        sessions.setCurrent(parentSession)

        assertNull(cache.read(1))
        assertNull(cache.read(2))
    }
}
