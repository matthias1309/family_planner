package de.familyplanner.gamification.avatar

// Declarative catalog data: one block per slot, so the file is long but flat. Variants/colors must exist in
// api/avatar/avataaars.json (checked by AvatarCatalogTest). `vomit` (mouth) is deliberately left out.

private const val PRICE_HAIR = 20
private const val PRICE_HAIR_COLOR = 15
private const val PRICE_HEADWEAR = 30
private const val PRICE_FACE_PART = 10
private const val PRICE_CLOTHES_PART = 20
private const val PRICE_PREMIUM_CLOTHES = 25
private const val PRICE_GRAPHIC_SHIRT = 15
private const val PRICE_GRAPHIC = 15
private const val PRICE_GLASSES = 25
private const val PRICE_EYEPATCH = 20
private const val PRICE_BEARD = 20
private const val PRICE_COLOR = 10

private typealias Entry = Pair<String, String>

private fun slotItems(
    slot: AvatarSlot,
    category: ShopCategory,
    price: Int,
    vararg entries: Entry,
): List<AvatarItem> = entries.map { (value, title) -> AvatarItem("${slot.key}.$value", slot, category, title, price) }

private val COLOR_TITLES =
    mapOf(
        "262e33" to "Anthrazit",
        "65c9ff" to "Himmelblau",
        "5199e4" to "Blau",
        "25557c" to "Dunkelblau",
        "e6e6e6" to "Hellgrau",
        "929598" to "Grau",
        "3c4f5c" to "Schiefer",
        "b1e2ff" to "Eisblau",
        "a7ffc4" to "Mint",
        "ffdeb5" to "Pfirsich",
        "ffafb9" to "Rosa",
        "ffffb1" to "Hellgelb",
        "ff488e" to "Pink",
        "ff5c5c" to "Rot",
        "ffffff" to "Weiß",
    )

private fun colorItems(
    slot: AvatarSlot,
    price: Int,
    values: List<String>,
): List<AvatarItem> = values.map { AvatarItem("${slot.key}.$it", slot, ShopCategory.COLORS, COLOR_TITLES.getValue(it), price) }

private val SKIN =
    slotItems(
        AvatarSlot.SKIN,
        ShopCategory.COLORS,
        0,
        "614335" to "Dunkelbraun",
        "ae5d29" to "Braun",
        "d08b5b" to "Mittelbraun",
        "edb98a" to "Beige",
        "ffdbb4" to "Hell",
        "fd9841" to "Orange",
        "f8d25c" to "Gelb",
    )

private val TOP_HAIR_FREE =
    slotItems(
        AvatarSlot.TOP,
        ShopCategory.HAIR,
        0,
        "shortFlat" to "Kurz glatt",
        "shortRound" to "Kurz rund",
        "shortWaved" to "Kurz gewellt",
        "shortCurly" to "Kurz lockig",
        "theCaesar" to "Caesar",
        "theCaesarAndSidePart" to "Caesar mit Scheitel",
        "sides" to "Seiten",
        "frizzle" to "Wuschelig",
        "shaggy" to "Zottelig",
        "bob" to "Bob",
        "bun" to "Dutt",
        "curly" to "Lockig",
        "curvy" to "Wellig lang",
        "fro" to "Afro",
        "dreads01" to "Dreads kurz",
        "longButNotTooLong" to "Mittellang",
        "straight01" to "Lang glatt",
        "straight02" to "Lang glatt 2",
    )

private val TOP_HAIR_PAID =
    slotItems(
        AvatarSlot.TOP,
        ShopCategory.HAIR,
        PRICE_HAIR,
        "bigHair" to "Große Mähne",
        "dreads" to "Dreads",
        "dreads02" to "Dreads lang",
        "frida" to "Frida",
        "froBand" to "Afro mit Band",
        "miaWallace" to "Pony",
        "shaggyMullet" to "Vokuhila",
        "shavedSides" to "Undercut",
        "straightAndStrand" to "Lang mit Strähne",
    )

private val TOP_HEADWEAR =
    slotItems(AvatarSlot.TOP, ShopCategory.HEADWEAR, 0, "hijab" to "Hidschab", "turban" to "Turban") +
        slotItems(
            AvatarSlot.TOP,
            ShopCategory.HEADWEAR,
            PRICE_HEADWEAR,
            "hat" to "Hut",
            "winterHat1" to "Wintermütze",
            "winterHat02" to "Bommelmütze",
            "winterHat03" to "Mütze mit Ohren",
            "winterHat04" to "Katzenmütze",
        )

private val HAIR_COLOR =
    slotItems(
        AvatarSlot.HAIR_COLOR,
        ShopCategory.COLORS,
        0,
        "2c1b18" to "Schwarz",
        "4a312c" to "Dunkelbraun",
        "724133" to "Braun",
        "a55728" to "Kastanie",
        "b58143" to "Hellbraun",
        "d6b370" to "Blond",
        "c93305" to "Rot",
        "e8e1e1" to "Grau",
    ) +
        slotItems(AvatarSlot.HAIR_COLOR, ShopCategory.COLORS, PRICE_HAIR_COLOR, "ecdcbf" to "Platinblond", "f59797" to "Rosa")

private val HAT_COLOR =
    colorItems(
        AvatarSlot.HAT_COLOR,
        0,
        listOf(
            "262e33",
            "65c9ff",
            "5199e4",
            "25557c",
            "e6e6e6",
            "929598",
            "3c4f5c",
            "b1e2ff",
            "a7ffc4",
            "ffdeb5",
            "ffafb9",
            "ffffb1",
            "ff488e",
            "ff5c5c",
            "ffffff",
        ),
    )

private val EYES =
    slotItems(
        AvatarSlot.EYES,
        ShopCategory.FACE,
        0,
        "default" to "Normal",
        "happy" to "Fröhlich",
        "side" to "Seitenblick",
        "squint" to "Blinzeln",
        "wink" to "Zwinkern",
        "closed" to "Geschlossen",
        "surprised" to "Überrascht",
    ) +
        slotItems(
            AvatarSlot.EYES,
            ShopCategory.FACE,
            PRICE_FACE_PART,
            "eyeRoll" to "Augenrollen",
            "hearts" to "Herzaugen",
            "winkWacky" to "Verrückt zwinkern",
            "cry" to "Tränen",
            "xDizzy" to "Schwindelig",
        )

private val EYEBROWS =
    slotItems(
        AvatarSlot.EYEBROWS,
        ShopCategory.FACE,
        0,
        "default" to "Normal",
        "defaultNatural" to "Natürlich",
        "flatNatural" to "Gerade",
        "raisedExcited" to "Hochgezogen",
        "raisedExcitedNatural" to "Hochgezogen natürlich",
        "angry" to "Böse",
        "angryNatural" to "Böse natürlich",
        "frownNatural" to "Stirnrunzeln",
        "sadConcerned" to "Besorgt",
        "sadConcernedNatural" to "Besorgt natürlich",
        "unibrowNatural" to "Monobraue",
        "upDown" to "Schief",
        "upDownNatural" to "Schief natürlich",
    )

private val MOUTH =
    slotItems(
        AvatarSlot.MOUTH,
        ShopCategory.FACE,
        0,
        "default" to "Normal",
        "smile" to "Lächeln",
        "twinkle" to "Schmunzeln",
        "serious" to "Ernst",
        "concerned" to "Besorgt",
        "sad" to "Traurig",
        "disbelief" to "Ungläubig",
    ) +
        slotItems(
            AvatarSlot.MOUTH,
            ShopCategory.FACE,
            PRICE_FACE_PART,
            "eating" to "Mampfen",
            "grimace" to "Grimasse",
            "screamOpen" to "Schrei",
            "tongue" to "Zunge",
        )

private val CLOTHES =
    slotItems(
        AvatarSlot.CLOTHES,
        ShopCategory.CLOTHES,
        0,
        "shirtCrewNeck" to "T-Shirt",
        "shirtVNeck" to "V-Ausschnitt",
        "shirtScoopNeck" to "Rundhals weit",
        "hoodie" to "Hoodie",
    ) +
        slotItems(AvatarSlot.CLOTHES, ShopCategory.CLOTHES, PRICE_GRAPHIC_SHIRT, "graphicShirt" to "Motiv-Shirt") +
        slotItems(
            AvatarSlot.CLOTHES,
            ShopCategory.CLOTHES,
            PRICE_CLOTHES_PART,
            "collarAndSweater" to "Pulli mit Kragen",
            "overall" to "Latzhose",
        ) +
        slotItems(
            AvatarSlot.CLOTHES,
            ShopCategory.CLOTHES,
            PRICE_PREMIUM_CLOTHES,
            "blazerAndShirt" to "Sakko mit Hemd",
            "blazerAndSweater" to "Sakko mit Pulli",
        )

private val CLOTHES_COLOR =
    colorItems(AvatarSlot.CLOTHES_COLOR, 0, listOf("262e33", "65c9ff", "5199e4", "25557c", "e6e6e6", "929598")) +
        colorItems(
            AvatarSlot.CLOTHES_COLOR,
            PRICE_COLOR,
            listOf("3c4f5c", "b1e2ff", "a7ffc4", "ffafb9", "ffffb1", "ff488e", "ff5c5c", "ffffff"),
        )

private val CLOTHES_GRAPHIC =
    slotItems(
        AvatarSlot.CLOTHES_GRAPHIC,
        ShopCategory.CLOTHES,
        PRICE_GRAPHIC,
        "bat" to "Fledermaus",
        "bear" to "Bär",
        "cumbia" to "Cumbia",
        "deer" to "Hirsch",
        "diamond" to "Diamant",
        "hola" to "Hola",
        "pizza" to "Pizza",
        "resist" to "Resist",
        "skull" to "Totenkopf",
        "skullOutline" to "Totenkopf-Umriss",
    )

private val ACCESSORIES =
    slotItems(
        AvatarSlot.ACCESSORIES,
        ShopCategory.ACCESSORIES,
        0,
        "prescription01" to "Brille",
        "prescription02" to "Brille eckig",
        "round" to "Runde Brille",
    ) +
        slotItems(AvatarSlot.ACCESSORIES, ShopCategory.ACCESSORIES, PRICE_EYEPATCH, "eyepatch" to "Augenklappe") +
        slotItems(
            AvatarSlot.ACCESSORIES,
            ShopCategory.ACCESSORIES,
            PRICE_GLASSES,
            "kurt" to "Kurt-Brille",
            "sunglasses" to "Sonnenbrille",
            "wayfarers" to "Wayfarer",
        )

private val FACIAL_HAIR =
    slotItems(
        AvatarSlot.FACIAL_HAIR,
        ShopCategory.FACE,
        0,
        "beardLight" to "Leichter Bart",
        "beardMedium" to "Vollbart",
    ) +
        slotItems(
            AvatarSlot.FACIAL_HAIR,
            ShopCategory.FACE,
            PRICE_BEARD,
            "beardMajestic" to "Prachtbart",
            "moustacheFancy" to "Schnurrbart",
            "moustacheMagnum" to "Magnum-Schnurrbart",
        )

private val BACKGROUND =
    slotItems(
        AvatarSlot.BACKGROUND,
        ShopCategory.COLORS,
        0,
        "b6e3f4" to "Hellblau",
        "c0aede" to "Lavendel",
        "d1d4f9" to "Flieder",
        "ffd5dc" to "Rosé",
        "ffdfbf" to "Apricot",
    ) +
        slotItems(
            AvatarSlot.BACKGROUND,
            ShopCategory.COLORS,
            PRICE_COLOR,
            "65c9ff" to "Himmelblau",
            "a7ffc4" to "Mint",
            "ffffb1" to "Zitrone",
            "ff5c5c" to "Rot",
            "262e33" to "Nacht",
        )

/** In editor order of [AvatarSlot]. */
internal val AVATAR_ITEMS: List<AvatarItem> =
    SKIN + TOP_HAIR_FREE + TOP_HAIR_PAID + TOP_HEADWEAR + HAIR_COLOR + HAT_COLOR + EYES + EYEBROWS + MOUTH +
        CLOTHES + CLOTHES_COLOR + CLOTHES_GRAPHIC + ACCESSORIES + FACIAL_HAIR + BACKGROUND
