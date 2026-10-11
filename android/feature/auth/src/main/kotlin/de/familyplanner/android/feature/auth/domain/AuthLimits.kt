package de.familyplanner.android.feature.auth.domain

object AuthLimits {
    const val NAME_MAX = 40
    const val DEVICE_NAME_MAX = 60
    const val SETUP_TOKEN_MIN = 16
    const val PAIRING_CODE_LENGTH = 6
    val PAIRING_CODE_REGEX = Regex("^[0-9]{$PAIRING_CODE_LENGTH}$")
}
