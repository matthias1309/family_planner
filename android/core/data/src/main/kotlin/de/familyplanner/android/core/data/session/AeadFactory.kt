package de.familyplanner.android.core.data.session

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

private const val KEYSET_NAME = "fp_session_keyset"
private const val KEYSET_PREFS = "fp_session_keyset_prefs"
private const val MASTER_KEY_URI = "android-keystore://fp_session_key"
private const val KEY_TEMPLATE = "AES256_GCM"

/** Tink-Schlüssel liegt verschlüsselt in SharedPreferences, der Master-Key im Android Keystore. */
object AeadFactory {
    fun create(context: Context): Aead {
        AeadConfig.register()
        val keysetHandle =
            AndroidKeysetManager
                .Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
                .withKeyTemplate(KeyTemplates.get(KEY_TEMPLATE))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
        return keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }
}
