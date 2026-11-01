package de.familyplanner.android.core.ui.avatar

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AvatarSvgTest {
    private val definition: JsonObject = loadDefinition()

    @Test
    fun `REQ-GAME-030 svg has canvas size background and skin color`() {
        val svg = AvatarSvg.render(definition, SampleLooks.free.toSelection())

        assertTrue(svg.contains("""viewBox="0 0 280 280""""))
        assertTrue(svg.contains("""fill="#b6e3f4""""))
        assertTrue(svg.contains("""fill="#edb98a""""))
    }

    @Test
    fun `REQ-GAME-030 svg is well formed and has no unresolved color references`() {
        val svg = AvatarSvg.render(definition, SampleLooks.all.last().toSelection())

        parse(svg)
        assertFalse(svg.contains("\"type\""))
        assertFalse(svg.contains("{"))
    }

    @Test
    fun `REQ-GAME-030 optional slots left out draw nothing`() {
        val without = AvatarSvg.render(definition, SampleLooks.free.toSelection())
        val with = AvatarSvg.render(definition, SampleLooks.free.copy(accessories = "accessories.round").toSelection())

        assertTrue(with.length > without.length)
    }

    @Test
    fun `REQ-GAME-030 unknown variant falls back to the first variant`() {
        val first = AvatarSvg.render(definition, SampleLooks.free.copy(eyes = "eyes.closed").toSelection())
        val svg = AvatarSvg.render(definition, SampleLooks.free.copy(eyes = "eyes.doesNotExist").toSelection())

        parse(svg)
        assertEquals(first, svg)
    }

    @Test
    fun `REQ-GAME-030 graphic shirt nests the clothes graphic and hoodie does not`() {
        val shirt = SampleLooks.free.copy(clothes = "clothes.graphicShirt", clothesGraphic = "clothesGraphic.pizza")
        val withGraphic = AvatarSvg.render(definition, shirt.toSelection())
        val hoodie = AvatarSvg.render(definition, shirt.copy(clothes = "clothes.hoodie").toSelection())
        val shirtWithoutGraphic = AvatarSvg.render(definition, shirt.copy(clothesGraphic = null).toSelection())

        assertTrue(withGraphic.length > shirtWithoutGraphic.length)
        assertTrue(withGraphic.length > hoodie.length)
    }

    @Test
    fun `REQ-GAME-030 attribute values are xml escaped`() {
        val tiny =
            Json
                .parseToJsonElement(
                    """{"canvas":{"width":10,"height":10,"elements":[
                  {"type":"element","name":"path","attributes":{"id":"a&b<c>\"d"}}]},
                  "attributes":{},"components":{},"colors":{}}""",
                ).jsonObject

        val svg = AvatarSvg.render(tiny, SampleLooks.free.toSelection())

        assertTrue(svg.contains("""id="a&amp;b&lt;c&gt;&quot;d""""))
    }

    @Test
    fun `REQ-GAME-030 item id without a dot is rejected`() {
        val broken = SampleLooks.free.copy(skin = "nodot")

        val failure = runCatching { broken.toSelection() }.exceptionOrNull()

        assertEquals(IllegalArgumentException::class, failure?.let { it::class })
    }

    private fun parse(svg: String) {
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(svg.toByteArray()))
    }
}
