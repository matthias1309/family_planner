package de.familyplanner.core.error

const val MEMBER_NAME_MAX = 40
const val DEVICE_NAME_MAX = 60

/** Trims [value] and requires 1..[max] characters; [field] is used in the German user-facing message. */
fun validatedName(
    value: String,
    field: String,
    max: Int,
): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.length > max) {
        throw AppException.Validation("$field muss zwischen 1 und $max Zeichen lang sein")
    }
    return trimmed
}
