package de.familyplanner.android.core.ui.avatar

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns a DiceBear style definition plus a selection into an SVG string (ADR-0017).
 * Pure JVM code so it can be unit tested without Android.
 */
object AvatarSvg {
    fun render(
        definition: JsonObject,
        selection: AvatarSelection,
    ): String {
        val canvas = definition.getValue("canvas").jsonObject
        val width = canvas.number("width")
        val height = canvas.number("height")
        val radius = width / 2
        return buildString {
            append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" width="$width" height="$height"""")
            definition["attributes"]?.jsonObject?.forEach { (name, value) -> append(""" $name="${escape(value.jsonPrimitive.content)}"""") }
            append(">")
            append("""<circle cx="$radius" cy="${height / 2}" r="$radius" fill="${escape(selection.background)}"/>""")
            Renderer(definition, selection, this).elements(canvas["elements"])
            append("</svg>")
        }
    }

    private class Renderer(
        private val definition: JsonObject,
        private val selection: AvatarSelection,
        private val out: StringBuilder,
    ) {
        fun elements(elements: JsonElement?) {
            elements?.jsonArray?.forEach { element(it.jsonObject) }
        }

        private fun element(node: JsonObject) {
            val name = node.getValue("name").jsonPrimitive.content
            if (node.getValue("type").jsonPrimitive.content == "component") component(name, node) else plain(name, node)
        }

        private fun plain(
            name: String,
            node: JsonObject,
        ) {
            out.append("<").append(name)
            attributes(node)
            val children = node["children"] as? JsonArray
            if (children == null) {
                out.append("/>")
                return
            }
            out.append(">")
            children.forEach { element(it.jsonObject) }
            out.append("</").append(name).append(">")
        }

        private fun component(
            name: String,
            node: JsonObject,
        ) {
            val chosen = selection.components[name] ?: return
            val variants =
                definition["components"]
                    ?.jsonObject
                    ?.get(name)
                    ?.jsonObject
                    ?.get("variants")
                    ?.jsonObject ?: return
            val variant = (variants[chosen] ?: variants.values.firstOrNull())?.jsonObject ?: return
            out.append("<g")
            attributes(node)
            out.append(">")
            elements(variant["elements"])
            out.append("</g>")
        }

        private fun attributes(node: JsonObject) {
            node["attributes"]?.jsonObject?.forEach { (key, value) ->
                out
                    .append(' ')
                    .append(key)
                    .append("=\"")
                    .append(escape(attributeValue(value)))
                    .append('"')
            }
        }

        private fun attributeValue(value: JsonElement): String {
            if (value is JsonPrimitive) return value.content
            val colorName =
                value.jsonObject
                    .getValue("name")
                    .jsonPrimitive.content
            return selection.colors[colorName] ?: firstColor(colorName)
        }

        private fun firstColor(colorName: String): String =
            definition["colors"]
                ?.jsonObject
                ?.get(colorName)
                ?.jsonObject
                ?.get("values")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonPrimitive
                ?.contentOrNull ?: "#000000"
    }

    private fun JsonObject.number(key: String): Int = getValue(key).jsonPrimitive.doubleOrNull?.toInt() ?: error("Missing $key")

    private fun escape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
