package org.mobuntu.chroot.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mobuntu_settings")

data class AppSettings(
    val tarballUrl: String  = DEFAULT_TARBALL_URL,
    val ui: String          = "phosh",
    val display: String     = "termux-x11",
    val resolution: String  = "1280x720",
    val dpi: String         = "160",
    val extraArgs: String   = "",
    val vncPort: Int        = 5900,
    val autoStartOnBoot: Boolean = false,
) {
    companion object {
        const val DEFAULT_TARBALL_URL =
            "https://releases.mobuntu.org/base-resolute.tar.gz"
    }
}

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            tarballUrl      = prefs[Keys.TARBALL_URL]  ?: AppSettings.DEFAULT_TARBALL_URL,
            ui              = prefs[Keys.UI]            ?: "phosh",
            display         = prefs[Keys.DISPLAY]       ?: "termux-x11",
            resolution      = prefs[Keys.RESOLUTION]    ?: "1280x720",
            dpi             = prefs[Keys.DPI]           ?: "160",
            extraArgs       = prefs[Keys.EXTRA_ARGS]    ?: "",
            vncPort         = prefs[Keys.VNC_PORT]      ?: 5900,
            autoStartOnBoot = prefs[Keys.AUTO_START]    ?: false,
        )
    }

    suspend fun update(block: AppSettings.() -> AppSettings) {
        val current = mutableMapOf<String, Any>()
        context.dataStore.edit { prefs ->
            val updated = AppSettings(
                tarballUrl      = prefs[Keys.TARBALL_URL]  ?: AppSettings.DEFAULT_TARBALL_URL,
                ui              = prefs[Keys.UI]            ?: "phosh",
                display         = prefs[Keys.DISPLAY]       ?: "termux-x11",
                resolution      = prefs[Keys.RESOLUTION]    ?: "1280x720",
                dpi             = prefs[Keys.DPI]           ?: "160",
                extraArgs       = prefs[Keys.EXTRA_ARGS]    ?: "",
                vncPort         = prefs[Keys.VNC_PORT]      ?: 5900,
                autoStartOnBoot = prefs[Keys.AUTO_START]    ?: false,
            ).block()
            prefs[Keys.TARBALL_URL]  = updated.tarballUrl
            prefs[Keys.UI]           = updated.ui
            prefs[Keys.DISPLAY]      = updated.display
            prefs[Keys.RESOLUTION]   = updated.resolution
            prefs[Keys.DPI]          = updated.dpi
            prefs[Keys.EXTRA_ARGS]   = updated.extraArgs
            prefs[Keys.VNC_PORT]     = updated.vncPort
            prefs[Keys.AUTO_START]   = updated.autoStartOnBoot
        }
    }

    private object Keys {
        val TARBALL_URL  = stringPreferencesKey("tarball_url")
        val UI           = stringPreferencesKey("ui")
        val DISPLAY      = stringPreferencesKey("display")
        val RESOLUTION   = stringPreferencesKey("resolution")
        val DPI          = stringPreferencesKey("dpi")
        val EXTRA_ARGS   = stringPreferencesKey("extra_args")
        val VNC_PORT     = intPreferencesKey("vnc_port")
        val AUTO_START   = booleanPreferencesKey("auto_start")
    }
}
