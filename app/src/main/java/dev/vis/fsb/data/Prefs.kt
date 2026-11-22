package dev.vis.fsb.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.UUID

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("fsb", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private inline fun <reified T : Enum<T>> enumOf(key: String, default: T): T =
        sp.getString(key, null)?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

    private fun loadSettings() = Settings(
        theme = enumOf("theme", ThemeMode.SYSTEM),
        oled = sp.getBoolean("oled", false),
        dynamicColor = sp.getBoolean("dynamic", true),
        blurSensitive = sp.getBoolean("blur", false),
        autoplayVideos = sp.getBoolean("autoplay", false),
        postLayout = enumOf("layout", PostLayout.FEED),
        highQuality = sp.getBoolean("hq", true),
        autoUpdate = sp.getBoolean("auto_update", true),
    )

    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        _settings.value = s
        sp.edit {
            putString("theme", s.theme.name)
            putBoolean("oled", s.oled)
            putBoolean("dynamic", s.dynamicColor)
            putBoolean("blur", s.blurSensitive)
            putBoolean("autoplay", s.autoplayVideos)
            putString("layout", s.postLayout.name)
            putBoolean("hq", s.highQuality)
            putBoolean("auto_update", s.autoUpdate)
        }
    }

    private val _recent = MutableStateFlow(loadRecent())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    private fun loadRecent(): List<String> = runCatching {
        val a = JSONArray(sp.getString("recent", "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    fun addRecent(handle: String) {
        val list = (listOf(handle) + _recent.value.filterNot { it.equals(handle, true) }).take(12)
        _recent.value = list
        sp.edit { putString("recent", JSONArray(list).toString()) }
    }

    fun removeRecent(handle: String) {
        val list = _recent.value.filterNot { it.equals(handle, true) }
        _recent.value = list
        sp.edit { putString("recent", JSONArray(list).toString()) }
    }

    fun clearRecent() {
        _recent.value = emptyList()
        sp.edit { remove("recent") }
    }

    /** Stable random device id, same role as the website's `fsb_did`. */
    val deviceId: String
        get() = sp.getString("did", null) ?: UUID.randomUUID().toString().replace("-", "").also { sp.edit { putString("did", it) } }

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0)
        set(v) = sp.edit { putLong("last_update_check", v) }

    var downloadCount: Int
        get() = sp.getInt("dl_count", 0)
        set(v) = sp.edit { putInt("dl_count", v) }
}
