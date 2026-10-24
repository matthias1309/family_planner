package de.familyplanner.core.events

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DomainEventCodecTest {
    private val accepted = TaskAccepted("c1", "m1", "Bad putzen", 3, "p1", 1_000L)

    @Test
    fun `DomainEventCodec round-trips TaskAccepted`() {
        val (type, payload) = DomainEventCodec.encode(accepted)

        assertEquals("task.accepted.v1", type)
        assertEquals(accepted, DomainEventCodec.decode(type, payload))
    }

    @Test
    fun `DomainEventCodec decodes an unknown type to null`() {
        assertNull(DomainEventCodec.decode("future.thing.v9", "{}"))
    }

    @Test
    fun `DomainEventCodec ignores unknown payload fields`() {
        val payload = """{"cardId":"c","memberId":"m","title":"t","points":1,"acceptedBy":"p","acceptedAt":1,"extra":true}"""

        assertEquals(TaskAccepted("c", "m", "t", 1, "p", 1L), DomainEventCodec.decode("task.accepted.v1", payload))
    }
}
