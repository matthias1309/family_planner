package de.familyplanner.gamification.avatar

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val HEX_BODY = "^[0-9a-f]{6}$"
private const val SOURCE_KEY_ITEM_ID_MAX = 40

class AvatarCatalogTest {
    private val definition: JsonObject =
        Json.parseToJsonElement(File(checkNotNull(System.getProperty("avatar.definition"))).readText()).jsonObject

    @Test
    fun `REQ-GAME-031 catalog item ids never change`() {
        val expected = EXPECTED_IDS.lines().filter { it.isNotBlank() }

        assertEquals(expected, AvatarCatalog.items.map { it.id }.sorted())
        assertEquals(157, AvatarCatalog.items.size)
        assertEquals(60, AvatarCatalog.items.count { !it.isFree })
    }

    @Test
    fun `REQ-GAME-030 every item exists in the avataaars definition`() {
        AvatarCatalog.items.forEach { item ->
            val value = item.id.substringAfter('.')
            when (item.slot) {
                AvatarSlot.BACKGROUND -> assertTrue(Regex(HEX_BODY).matches(value), item.id)
                in COLOR_PALETTES -> assertTrue(paletteValues(COLOR_PALETTES.getValue(item.slot)).contains(value), item.id)
                else -> assertTrue(variantsOf(item.slot.key).containsKey(value), item.id)
            }
        }
    }

    @Test
    fun `REQ-GAME-030 every required slot has a free default`() {
        AvatarSlot.entries.filter { !it.optional }.forEach { assertEquals(0, AvatarCatalog.defaultOf(it).price, it.key) }
    }

    @Test
    fun `REQ-GAME-031 items respect id title and price limits`() {
        val items = AvatarCatalog.items

        assertEquals(items.size, items.map { it.id }.toSet().size)
        items.forEach {
            assertTrue(ITEM_ID_PATTERN.matches(it.id), it.id)
            assertTrue(it.id.startsWith("${it.slot.key}."), it.id)
            assertTrue(it.id.length <= SOURCE_KEY_ITEM_ID_MAX, it.id)
            assertTrue(it.title.length in 1..AVATAR_ITEM_TITLE_MAX, it.title)
            assertTrue(it.price in 0..AVATAR_PRICE_MAX, it.id)
        }
    }

    @Test
    fun `REQ-GAME-030 every slot has items`() {
        AvatarSlot.entries.forEach { assertTrue(AvatarCatalog.itemsOf(it).isNotEmpty(), it.key) }
    }

    private fun variantsOf(component: String): JsonObject =
        definition
            .getValue("components")
            .jsonObject
            .getValue(component)
            .jsonObject
            .getValue("variants")
            .jsonObject

    private fun paletteValues(name: String): Set<String> =
        definition
            .getValue("colors")
            .jsonObject
            .getValue(name)
            .jsonObject
            .getValue("values")
            .jsonArray
            .map {
                it.jsonPrimitive.content
                    .removePrefix("#")
                    .lowercase()
            }.toSet()

    private companion object {
        val COLOR_PALETTES =
            mapOf(
                AvatarSlot.SKIN to "skin",
                AvatarSlot.HAIR_COLOR to "hair",
                AvatarSlot.HAT_COLOR to "hat",
                AvatarSlot.CLOTHES_COLOR to "clothes",
            )

        val EXPECTED_IDS = AvatarCatalogIds.ALL
    }
}
