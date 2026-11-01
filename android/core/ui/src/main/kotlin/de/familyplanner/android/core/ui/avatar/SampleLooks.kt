package de.familyplanner.android.core.ui.avatar

/** Sample looks for the Compose preview and the renderer tests. */
internal object SampleLooks {
    val free =
        AvatarLook(
            skin = "skin.edb98a",
            top = "top.shortFlat",
            hairColor = "hairColor.724133",
            hatColor = "hatColor.262e33",
            eyes = "eyes.default",
            eyebrows = "eyebrows.default",
            mouth = "mouth.smile",
            clothes = "clothes.hoodie",
            clothesColor = "clothesColor.5199e4",
            background = "background.b6e3f4",
        )
    val hat = free.copy(top = "top.winterHat02", hatColor = "hatColor.ff5c5c", skin = "skin.ae5d29", background = "background.65c9ff")
    val glassesBeard =
        free.copy(
            top = "top.bigHair",
            hairColor = "hairColor.c93305",
            accessories = "accessories.wayfarers",
            facialHair = "facialHair.beardMajestic",
            skin = "skin.614335",
            background = "background.ffd5dc",
        )
    val graphicShirt =
        free.copy(
            top = "top.bun",
            clothes = "clothes.graphicShirt",
            clothesGraphic = "clothesGraphic.pizza",
            clothesColor = "clothesColor.ff488e",
            skin = "skin.ffdbb4",
            eyes = "eyes.hearts",
            mouth = "mouth.tongue",
        )
    val all = listOf(free, hat, glassesBeard, graphicShirt)
}
