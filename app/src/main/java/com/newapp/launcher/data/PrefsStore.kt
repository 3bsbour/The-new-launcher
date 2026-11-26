package com.newapp.launcher.data

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "launcher_prefs")

/** تاب مخصص في درج التطبيقات باسم يحدده المستخدم */
@Immutable
data class DrawerTab(
    val id: String,
    val name: String,
    val apps: List<String> = emptyList()
)

/** طريقة عرض محتوى الفولدر */
enum class FolderStyle { VERTICAL, HORIZONTAL }

/** فولدر على الشاشة الرئيسية */
@Immutable
data class Folder(
    val id: String,
    val name: String,
    val apps: List<String> = emptyList(),
    val style: FolderStyle = FolderStyle.VERTICAL,
    val imageUri: String? = null,
    val columns: Int = 3,
    val iconScale: Float = 1f,
    val windowScale: Float = 1f
)

/** ويدجت مدمج على الشاشة الرئيسية */
@Immutable
data class WidgetSpec(
    val id: String,
    val type: String,
    val spanX: Int = 2,
    val spanY: Int = 1,
    val color: Int = 0xFF202634.toInt(),
    val alpha: Float = 0.85f
)

/** عنصر على الشاشة الرئيسية: تطبيق أو فولدر أو ويدجت */
sealed interface HomeEntry {
    val id: String

    data class App(val key: String) : HomeEntry {
        override val id: String get() = "a:$key"
    }

    data class FolderEntry(val folderId: String) : HomeEntry {
        override val id: String get() = "f:$folderId"
    }

    data class Widget(val widgetId: String) : HomeEntry {
        override val id: String get() = "w:$widgetId"
    }

    companion object {
        /** يقبل المفاتيح القديمة (بدون بادئة) كتطبيق */
        fun parse(raw: String): HomeEntry = when {
            raw.startsWith("f:") -> FolderEntry(raw.removePrefix("f:"))
            raw.startsWith("w:") -> Widget(raw.removePrefix("w:"))
            raw.startsWith("a:") -> App(raw.removePrefix("a:"))
            else -> App(raw)
        }
    }
}

/** كل إعدادات اللانشر محفوظة محليًا */
@Immutable
data class LauncherPrefs(
    val home: List<String> = emptyList(),
    val dock: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val renames: Map<String, String> = emptyMap(),
    val columns: Int = 5,
    val rows: Int = 5,
    val drawerColumns: Int = 5,
    val iconScale: Float = 1f,
    val showLabels: Boolean = true,
    val wallpaperUri: String? = null,
    val wallpaperDim: Int = 25,
    val initialized: Boolean = false,
    val drawerTabs: List<DrawerTab> = emptyList(),
    val defaultTab: String = TAB_ALL,
    val folders: List<Folder> = emptyList(),
    val widgets: List<WidgetSpec> = emptyList(),
    val gestures: Map<String, String> = emptyMap(),
    val showNewsPage: Boolean = false,
    val defaultPage: Int = 0,
    val locale: String? = null
) {
    /** الأسماء المعدّلة تُطبّق على أي مكان */
    fun labelOf(app: AppInfo): String = renames[app.key] ?: app.label

    fun folderOf(id: String): Folder? = folders.firstOrNull { it.id == id }

    fun widgetOf(id: String): WidgetSpec? = widgets.firstOrNull { it.id == id }

    /** عناصر الشاشة الرئيسية بعد التحويل */
    val homeEntries: List<HomeEntry> get() = home.map { HomeEntry.parse(it) }

    companion object {
        const val TAB_ALL = "all"
        const val TAB_LETTER_PREFIX = "letter:"
        const val TAB_CUSTOM_PREFIX = "tab:"
    }
}

class PrefsStore(private val context: Context) {

    private object Keys {
        val home = stringPreferencesKey("home")
        val dock = stringPreferencesKey("dock")
        val hidden = stringPreferencesKey("hidden")
        val renames = stringPreferencesKey("renames")
        val columns = intPreferencesKey("columns")
        val rows = intPreferencesKey("rows")
        val drawerColumns = intPreferencesKey("drawer_columns")
        val iconScale = floatPreferencesKey("icon_scale")
        val showLabels = booleanPreferencesKey("show_labels")
        val wallpaperUri = stringPreferencesKey("wallpaper_uri")
        val wallpaperDim = intPreferencesKey("wallpaper_dim")
        val initialized = booleanPreferencesKey("initialized")
        val drawerTabs = stringPreferencesKey("drawer_tabs")
        val defaultTab = stringPreferencesKey("default_tab")
        val folders = stringPreferencesKey("folders")
        val widgets = stringPreferencesKey("widgets")
        val gestures = stringPreferencesKey("gestures")
        val showNewsPage = booleanPreferencesKey("show_news_page")
        val defaultPage = intPreferencesKey("default_page")
        val locale = stringPreferencesKey("locale")
    }

    val state: Flow<LauncherPrefs> = context.dataStore.data.map { p ->
        LauncherPrefs(
            home = decodeList(p[Keys.home]),
            dock = decodeList(p[Keys.dock]),
            hidden = decodeSet(p[Keys.hidden]),
            renames = decodeMap(p[Keys.renames]),
            columns = (p[Keys.columns] ?: 5).coerceIn(3, 6),
            rows = (p[Keys.rows] ?: 5).coerceIn(3, 7),
            drawerColumns = (p[Keys.drawerColumns] ?: 5).coerceIn(3, 6),
            iconScale = (p[Keys.iconScale] ?: 1f).coerceIn(0.7f, 1.4f),
            showLabels = p[Keys.showLabels] ?: true,
            wallpaperUri = p[Keys.wallpaperUri]?.takeIf { it.isNotBlank() },
            wallpaperDim = (p[Keys.wallpaperDim] ?: 25).coerceIn(0, 80),
            initialized = p[Keys.initialized] ?: false,
            drawerTabs = decodeTabs(p[Keys.drawerTabs]),
            defaultTab = p[Keys.defaultTab]?.takeIf { it.isNotBlank() } ?: LauncherPrefs.TAB_ALL,
            folders = decodeFolders(p[Keys.folders]),
            widgets = decodeWidgets(p[Keys.widgets]),
            gestures = decodeMap(p[Keys.gestures]),
            showNewsPage = p[Keys.showNewsPage] ?: false,
            defaultPage = (p[Keys.defaultPage] ?: 0).coerceAtLeast(0),
            locale = p[Keys.locale]?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun setHome(keys: List<String>) = edit { it[Keys.home] = encodeList(keys) }

    suspend fun setDock(keys: List<String>) = edit { it[Keys.dock] = encodeList(keys) }

    suspend fun setHidden(keys: Set<String>) = edit { it[Keys.hidden] = encodeList(keys.toList()) }

    suspend fun setRenames(map: Map<String, String>) = edit { it[Keys.renames] = encodeMap(map) }

    suspend fun setColumns(value: Int) = edit { it[Keys.columns] = value }

    suspend fun setRows(value: Int) = edit { it[Keys.rows] = value }

    suspend fun setDrawerColumns(value: Int) = edit { it[Keys.drawerColumns] = value }

    suspend fun setIconScale(value: Float) = edit { it[Keys.iconScale] = value }

    suspend fun setShowLabels(value: Boolean) = edit { it[Keys.showLabels] = value }

    suspend fun setWallpaperUri(value: String?) = edit { it[Keys.wallpaperUri] = value.orEmpty() }

    suspend fun setWallpaperDim(value: Int) = edit { it[Keys.wallpaperDim] = value }

    suspend fun setInitialized(value: Boolean) = edit { it[Keys.initialized] = value }

    suspend fun setDrawerTabs(tabs: List<DrawerTab>) = edit { it[Keys.drawerTabs] = encodeTabs(tabs) }

    suspend fun setDefaultTab(value: String) = edit { it[Keys.defaultTab] = value }

    suspend fun setFolders(folders: List<Folder>) = edit { it[Keys.folders] = encodeFolders(folders) }

    suspend fun setWidgets(widgets: List<WidgetSpec>) = edit { it[Keys.widgets] = encodeWidgets(widgets) }

    suspend fun setGestures(map: Map<String, String>) = edit { it[Keys.gestures] = encodeMap(map) }

    suspend fun setShowNewsPage(value: Boolean) = edit { it[Keys.showNewsPage] = value }

    suspend fun setDefaultPage(value: Int) = edit { it[Keys.defaultPage] = value }

    suspend fun setLocale(value: String?) = edit { it[Keys.locale] = value.orEmpty() }

    suspend fun resetLayout() = edit {
        it.remove(Keys.home)
        it.remove(Keys.dock)
        it.remove(Keys.folders)
        it.remove(Keys.widgets)
        it[Keys.initialized] = false
        it[Keys.defaultPage] = 0
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    // ---------- ترميز ----------

    private fun encodeList(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun decodeList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { s -> s.isNotBlank() } }
        }.getOrDefault(emptyList())
    }

    private fun decodeSet(raw: String?) = decodeList(raw).toSet()

    private fun encodeMap(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun decodeMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap { obj.keys().forEach { k -> put(k, obj.optString(k)) } }
        }.getOrDefault(emptyMap())
    }

    private fun encodeTabs(tabs: List<DrawerTab>): String {
        val arr = JSONArray()
        tabs.forEach { tab ->
            arr.put(JSONObject().apply {
                put("id", tab.id)
                put("name", tab.name)
                put("apps", JSONArray(tab.apps))
            })
        }
        return arr.toString()
    }

    private fun decodeTabs(raw: String?): List<DrawerTab> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                DrawerTab(
                    id = id,
                    name = obj.optString("name").ifBlank { id },
                    apps = obj.optJSONArray("apps").toStringList()
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeFolders(folders: List<Folder>): String {
        val arr = JSONArray()
        folders.forEach { f ->
            arr.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("apps", JSONArray(f.apps))
                put("style", f.style.name)
                put("image", f.imageUri ?: "")
                put("columns", f.columns)
                put("iconScale", f.iconScale.toDouble())
                put("windowScale", f.windowScale.toDouble())
            })
        }
        return arr.toString()
    }

    private fun decodeFolders(raw: String?): List<Folder> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Folder(
                    id = id,
                    name = obj.optString("name").ifBlank { id },
                    apps = obj.optJSONArray("apps").toStringList(),
                    style = runCatching { FolderStyle.valueOf(obj.optString("style")) }
                        .getOrDefault(FolderStyle.VERTICAL),
                    imageUri = obj.optString("image").takeIf { it.isNotBlank() },
                    columns = obj.optInt("columns", 3).coerceIn(2, 6),
                    iconScale = obj.optDouble("iconScale", 1.0).toFloat().coerceIn(0.7f, 1.5f),
                    windowScale = obj.optDouble("windowScale", 1.0).toFloat().coerceIn(0.6f, 1.6f)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeWidgets(widgets: List<WidgetSpec>): String {
        val arr = JSONArray()
        widgets.forEach { w ->
            arr.put(JSONObject().apply {
                put("id", w.id)
                put("type", w.type)
                put("spanX", w.spanX)
                put("spanY", w.spanY)
                put("color", w.color)
                put("alpha", w.alpha.toDouble())
            })
        }
        return arr.toString()
    }

    private fun decodeWidgets(raw: String?): List<WidgetSpec> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                WidgetSpec(
                    id = id,
                    type = obj.optString("type").ifBlank { "clock" },
                    spanX = obj.optInt("spanX", 2).coerceIn(1, 6),
                    spanY = obj.optInt("spanY", 1).coerceIn(1, 4),
                    color = obj.optInt("color", 0xFF202634.toInt()),
                    alpha = obj.optDouble("alpha", 0.85).toFloat().coerceIn(0.2f, 1f)
                )
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString().substring(0, 8)
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> optString(i).takeIf { it.isNotBlank() } }
}
