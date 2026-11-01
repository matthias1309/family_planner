package de.familyplanner.android.feature.gamification.domain.avatar

import de.familyplanner.android.feature.gamification.fakes.freeLook
import de.familyplanner.android.feature.gamification.fakes.goal
import de.familyplanner.android.feature.gamification.fakes.reward
import de.familyplanner.android.feature.gamification.fakes.sampleCatalog
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvatarRulesTest {
    private val catalog = sampleCatalog()

    private fun item(id: String) = catalog.slots.flatMap { it.items }.first { it.id == id }

    @Test
    fun `REQ-GAME-030 default look takes first free item per required slot`() {
        assertEquals(freeLook, defaultLook(catalog))
    }

    @Test
    fun `REQ-GAME-030 default look is null when a required slot has no free item`() {
        val broken = AvatarCatalog(catalog.slots.map { if (it.slot == AvatarSlot.EYES) it.copy(items = emptyList()) else it })

        assertNull(defaultLook(broken))
    }

    @Test
    fun `REQ-GAME-030 rubrics split top into hair and headwear`() {
        val rubrics = catalog.rubrics()

        val tops = rubrics.filter { it.slot == AvatarSlot.TOP }
        assertEquals(listOf(ShopCategory.HAIR, ShopCategory.HEADWEAR), tops.map { it.category })
        assertEquals(AvatarSlot.SKIN, rubrics.first().slot)
    }

    @Test
    fun `REQ-GAME-031 balance 10 item 8 is buyable`() {
        assertEquals(PurchaseOption.Buyable(8), purchaseOption(item("top.bigHair").copy(price = 8), emptySet(), 10))
    }

    @Test
    fun `REQ-GAME-031 balance 5 item 8 shows 3 missing`() {
        assertEquals(PurchaseOption.Missing(3), purchaseOption(item("top.bigHair").copy(price = 8), emptySet(), 5))
    }

    @Test
    fun `REQ-GAME-031 balance equal to price is buyable`() {
        assertEquals(PurchaseOption.Buyable(20), purchaseOption(item("top.bigHair"), emptySet(), 20))
    }

    @Test
    fun `REQ-GAME-031 owned or free item is wearable not buyable`() {
        assertEquals(PurchaseOption.Wearable, purchaseOption(item("top.bigHair"), setOf("top.bigHair"), 0))
        assertEquals(PurchaseOption.Wearable, purchaseOption(item("top.shortFlat"), emptySet(), 0))
    }

    @Test
    fun `REQ-GAME-041 warning when purchase lowers goal progress`() {
        val goal = goal(reward("r-1", price = 200), balance = 120)

        assertTrue(goalWarning(goal, 120, 30))
        assertFalse(goalWarning(goal, 260, 30))
        assertFalse(goalWarning(null, 120, 30))
        assertFalse(goalWarning(goal(reward = null), 120, 30))
        assertFalse(goalWarning(goal(reward("r-1", 200, archived = true)), 120, 30))
    }

    @Test
    fun `REQ-GAME-031 cannot save draft with unowned item`() {
        val draft = freeLook.wearing(AvatarSlot.TOP, "top.bigHair")

        assertFalse(canSave(draft, null, catalog, emptySet()))
        assertTrue(canSave(draft, null, catalog, setOf("top.bigHair")))
    }

    @Test
    fun `REQ-GAME-031 cannot save draft with unknown item`() {
        assertFalse(canSave(freeLook.wearing(AvatarSlot.TOP, "top.gone"), null, catalog, emptySet()))
    }

    @Test
    fun `REQ-GAME-030 first save allowed with default look and unchanged saved look not`() {
        assertTrue(canSave(freeLook, null, catalog, emptySet()))
        assertFalse(canSave(freeLook, freeLook, catalog, emptySet()))
    }

    @Test
    fun `wearing and itemIn cover all slots`() {
        AvatarSlot.entries.forEach { slot ->
            val id =
                catalog.slots
                    .first { it.slot == slot }
                    .items
                    .firstOrNull()
                    ?.id ?: "facialHair.beardLight"
            assertEquals(id, freeLook.wearing(slot, id).itemIn(slot))
        }
        assertNull(freeLook.wearing(AvatarSlot.ACCESSORIES, null).itemIn(AvatarSlot.ACCESSORIES))
    }
}
