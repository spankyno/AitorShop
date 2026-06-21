package com.example.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Gestiona la sesión del usuario usando EncryptedSharedPreferences.
 *
 * Datos sensibles (token JWT, email) se cifran con AES-256-GCM en el
 * Android Keystore antes de escribirse en disco. El resto de preferencias
 * no sensibles (tema, lista activa) siguen en el SharedPreferences normal
 * para no añadir overhead innecesario.
 */
class SecureSessionManager(context: Context) {

    // -----------------------------------------------------------------------
    // Preferencias cifradas  →  token + email
    // -----------------------------------------------------------------------
    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_ENCRYPTED,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // -----------------------------------------------------------------------
    // Preferencias normales  →  datos no sensibles
    // -----------------------------------------------------------------------
    private val plainPrefs: SharedPreferences =
        context.getSharedPreferences(FILE_PLAIN, Context.MODE_PRIVATE)

    // -----------------------------------------------------------------------
    // API pública — sesión
    // -----------------------------------------------------------------------

    /** Guarda la sesión tras login/registro. Token cifrado en Keystore. */
    fun saveSession(email: String, accessToken: String, refreshToken: String = "") {
        encryptedPrefs.edit()
            .putString(KEY_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_EMAIL, email)
            .apply()

        plainPrefs.edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putBoolean(KEY_GUEST, false)
            .apply()
    }

    /** Elimina todos los datos de sesión (logout). */
    fun clearSession() {
        encryptedPrefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EMAIL)
            .apply()

        plainPrefs.edit()
            .putBoolean(KEY_LOGGED_IN, false)
            .putBoolean(KEY_GUEST, false)
            .apply()
    }

    /** Activa el modo invitado. */
    fun setGuestMode(enabled: Boolean) {
        plainPrefs.edit().putBoolean(KEY_GUEST, enabled).apply()
    }

    /** Actualiza solo los tokens (para cuando el authenticator refresca la sesión). */
    fun updateTokens(accessToken: String, refreshToken: String) {
        encryptedPrefs.edit()
            .putString(KEY_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun registerListeners(
        onEncryptedChanged: SharedPreferences.OnSharedPreferenceChangeListener,
        onPlainChanged: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        encryptedPrefs.registerOnSharedPreferenceChangeListener(onEncryptedChanged)
        plainPrefs.registerOnSharedPreferenceChangeListener(onPlainChanged)
    }

    fun unregisterListeners(
        onEncryptedChanged: SharedPreferences.OnSharedPreferenceChangeListener,
        onPlainChanged: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        encryptedPrefs.unregisterOnSharedPreferenceChangeListener(onEncryptedChanged)
        plainPrefs.unregisterOnSharedPreferenceChangeListener(onPlainChanged)
    }

    // -----------------------------------------------------------------------
    // API pública — lecturas
    // -----------------------------------------------------------------------

    val accessToken: String? get() = encryptedPrefs.getString(KEY_TOKEN, null)
    val refreshToken: String? get() = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
    val userEmail: String? get() = encryptedPrefs.getString(KEY_EMAIL, null)
    val isLoggedIn: Boolean get() = plainPrefs.getBoolean(KEY_LOGGED_IN, false)
    val isGuestMode: Boolean get() = plainPrefs.getBoolean(KEY_GUEST, false)

    // -----------------------------------------------------------------------
    // API pública — preferencias no sensibles
    // -----------------------------------------------------------------------

    var activeListId: String
        get() = plainPrefs.getString(KEY_ACTIVE_LIST, "CASA_FAMILIA") ?: "CASA_FAMILIA"
        set(value) { plainPrefs.edit().putString(KEY_ACTIVE_LIST, value).apply() }

    var isDarkMode: Boolean
        get() = plainPrefs.getBoolean(KEY_DARK_MODE, true)
        set(value) { plainPrefs.edit().putBoolean(KEY_DARK_MODE, value).apply() }

    companion object {
        private const val FILE_ENCRYPTED = "supercompra_secure_prefs"
        private const val FILE_PLAIN     = "supercompra_prefs"

        // Claves cifradas
        const val KEY_TOKEN         = "user_token"
        const val KEY_REFRESH_TOKEN = "user_refresh_token"
        const val KEY_EMAIL         = "user_email"

        // Claves en plano
        const val KEY_LOGGED_IN   = "is_logged_in"
        const val KEY_GUEST       = "is_guest_mode"
        const val KEY_ACTIVE_LIST = "active_list_id"
        const val KEY_DARK_MODE   = "dark_mode"
    }
}
