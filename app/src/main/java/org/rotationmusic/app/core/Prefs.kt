package org.rotationmusic.app.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Device-local settings. The password lands in an encrypted store – it exists
 * only so the app can sign in again silently once the server forgets us,
 * which it does on every restart.
 */
class Prefs(context: Context) {

    private val plain: SharedPreferences =
        context.getSharedPreferences("rotation", Context.MODE_PRIVATE)

    private val secret: SharedPreferences = runCatching {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "rotation-secret", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as SharedPreferences
    }.getOrElse {
        // A device with a broken keystore should still be usable – it just has
        // to be signed in by hand now and then.
        context.getSharedPreferences("rotation-secret-plain", Context.MODE_PRIVATE)
    }

    var serverAddress: String
        get() = plain.getString("server", "") ?: ""
        set(value) = plain.edit().putString("server", value).apply()

    var username: String?
        get() = plain.getString("username", null)
        set(value) = plain.edit().putString("username", value).apply()

    fun password(account: String): String? = secret.getString("password:$account", null)

    fun savePassword(account: String, password: String) {
        secret.edit().putString("password:$account", password).apply()
    }

    /** Which palette the app wears – and the recap's own two switches. */
    var accent: String
        get() = plain.getString("accent", "sunset") ?: "sunset"
        set(value) = plain.edit().putString("accent", value).apply()

    var storyAllYear: Boolean
        get() = plain.getBoolean("season.always", false)
        set(value) = plain.edit().putBoolean("season.always", value).apply()

    var seasonReminder: Boolean
        get() = plain.getBoolean("season.remind", true)
        set(value) = plain.edit().putBoolean("season.remind", value).apply()

    var storyMuted: Boolean
        get() = plain.getBoolean("story.muted", false)
        set(value) = plain.edit().putBoolean("story.muted", value).apply()

    fun clearCredentials() {
        username?.let { secret.edit().remove("password:$it").apply() }
        plain.edit().remove("username").apply()
    }
}
