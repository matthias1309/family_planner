package de.familyplanner.android.feature.auth.domain

import de.familyplanner.android.core.data.ApiResult
import de.familyplanner.android.core.data.validationFailure

internal fun validateName(name: String): ApiResult.Failure? =
    when {
        name.isBlank() -> validationFailure("Bitte einen Namen eingeben.")
        name.length > AuthLimits.NAME_MAX -> validationFailure("Der Name darf höchstens ${AuthLimits.NAME_MAX} Zeichen haben.")
        else -> null
    }

internal fun validateDeviceName(name: String): ApiResult.Failure? =
    when {
        name.isBlank() -> validationFailure("Bitte einen Gerätenamen eingeben.")
        name.length > AuthLimits.DEVICE_NAME_MAX ->
            validationFailure("Der Gerätename darf höchstens ${AuthLimits.DEVICE_NAME_MAX} Zeichen haben.")
        else -> null
    }

internal fun validateCode(code: String): ApiResult.Failure? =
    if (AuthLimits.PAIRING_CODE_REGEX.matches(code)) null else validationFailure("Der Code besteht aus 6 Ziffern.")

internal fun validateSetupToken(token: String): ApiResult.Failure? =
    if (token.length >= AuthLimits.SETUP_TOKEN_MIN) {
        null
    } else {
        validationFailure("Das Setup-Token hat mindestens ${AuthLimits.SETUP_TOKEN_MIN} Zeichen.")
    }
