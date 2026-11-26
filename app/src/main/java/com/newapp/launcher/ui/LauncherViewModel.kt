package com.newapp.launcher.ui

import android.app.Application
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.newapp.launcher.data.AppInfo
import com.newapp.launcher.data.AppRepository
import com.newapp.launcher.data.ContactHit
import com.newapp.launcher.data.ContactsReader
import com.newapp.launcher.data.DrawerTab
import com.newapp.launcher.data.Folder
import com.newapp.launcher.data.FolderStyle
import com.newapp.launcher.data.LauncherPrefs
import com.newapp.launcher.data.PrefsStore
import com.newapp.launcher.data.WidgetSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)
    private val prefsStore = PrefsStore(app)
    private val contacts = ContactsReader(app)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    val prefs: StateFlow<LauncherPrefs> =
        prefsStore.state.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherPrefs())

    /** أيقونات التطبيقات (حالة مستقلة لكل أيقونة) */
    val icons = IconStore()

    private val _wallpaper = MutableStateFlow<ImageBitmap?>(null)
    val wallpaper: StateFlow<ImageBitmap?> = _wallpaper.asStateFlow()

    private val _contactHits = MutableStateFlow<List<ContactHit>>(emptyList())
    val contactHits: StateFlow<List<ContactHit>> = _contactHits.asStateFlow()

    private var currentPrefs = LauncherPrefs()
    private var seeded = false
    private var wallpaperKey: String? = null
    private var preloadDone = false

    /** مفاتيح الأيقونات الجاري تحميلها الآن (لمنع تكرار نفس الطلب) */
    private val inFlight: MutableSet<String> =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    init {
        ContextCompat.registerReceiver(
            app,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        viewModelScope.launch {
            prefs.collect { p ->
                currentPrefs = p
                seedIfNeeded()
                syncWallpaper(p)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val list = repo.loadApps()
            _apps.value = list
            icons.retainAll(list.mapTo(HashSet()) { it.key })
            preloadIcons(list)
        }
    }

    /** تحميل أيقونة تطبيق واحد لو مش موجودة في الذاكرة */
    fun ensureIcon(app: AppInfo) {
        if (icons.contains(app.key)) return
        if (!inFlight.add(app.key)) return
        viewModelScope.launch {
            val bitmap = repo.iconOf(app)
            inFlight.remove(app.key)
            if (bitmap != null) icons.put(app.key, bitmap)
        }
    }

    /**
     * تحميل مسبق لأيقونات أول N تطبيق في الخلفية بتوازي محدود،
     * عشان فتح الدرج يبقى فوري بدل ما كل أيقونة تتحمّل وقت العرض.
     */
    private fun preloadIcons(list: List<AppInfo>) {
        if (preloadDone) return
        preloadDone = true
        viewModelScope.launch {
            val gate = Semaphore(PRELOAD_CONCURRENCY)
            coroutineScope {
                list.take(PRELOAD_COUNT).forEach { app ->
                    launch {
                        gate.withPermit {
                            if (icons.contains(app.key) || !inFlight.add(app.key)) return@launch
                            val bitmap = repo.iconOf(app)
                            inFlight.remove(app.key)
                            if (bitmap != null) icons.put(app.key, bitmap)
                        }
                    }
                }
            }
        }
    }

    /** أول تشغيل: نملأ الشاشة الرئيسية والشريط السفلي بتطبيقات المستخدم */
    private fun seedIfNeeded() {
        if (seeded || currentPrefs.initialized) return
        val all = _apps.value
        if (all.isEmpty()) return
        seeded = true
        val perPage = currentPrefs.columns * currentPrefs.rows
        val dockKeys = all.take(DOCK_SIZE).map { it.key }
        // نستبعد تطبيقات الشريط السفلي من الرئيسية لمنع التكرار
        val homeKeys = all.map { it.key }.filter { it !in dockKeys }.take(perPage)
        viewModelScope.launch {
            prefsStore.setHome(homeKeys.map { "a:$it" })
            prefsStore.setDock(dockKeys)
            prefsStore.setInitialized(true)
        }
    }

    // ---------- التشغيل والتطبيقات ----------

    fun launch(app: AppInfo) = repo.launch(app)

    fun launchKey(key: String) {
        _apps.value.firstOrNull { it.key == key }?.let { repo.launch(it) }
    }

    fun openAppInfo(app: AppInfo) = repo.openAppInfo(app)

    fun uninstall(app: AppInfo) = repo.uninstall(app)

    fun openCamera() {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    fun labelOf(app: AppInfo): String = currentPrefs.labelOf(app)

    fun visibleApps(): List<AppInfo> = _apps.value.filter { it.key !in currentPrefs.hidden }

    // ---------- الشاشة الرئيسية ----------

    fun addEntry(entryId: String) {
        val list = currentPrefs.home
        if (list.contains(entryId)) return
        viewModelScope.launch { prefsStore.setHome((list + entryId).distinct()) }
    }

    fun removeEntry(entryId: String) {
        viewModelScope.launch { prefsStore.setHome(currentPrefs.home - entryId) }
    }

    fun addToHome(app: AppInfo) = addEntry("a:${app.key}")

    fun removeFromHome(app: AppInfo) = removeEntry("a:${app.key}")

    fun moveEntry(from: Int, to: Int) {
        val list = currentPrefs.home.toMutableList()
        if (from !in list.indices) return
        val item = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), item)
        viewModelScope.launch { prefsStore.setHome(list) }
    }

    fun moveDock(from: Int, to: Int) {
        val list = currentPrefs.dock.toMutableList()
        if (from !in list.indices) return
        val item = list.removeAt(from)
        list.add(to.coerceIn(0, list.size), item)
        viewModelScope.launch { prefsStore.setDock(list) }
    }

    fun addToDock(app: AppInfo) {
        val list = currentPrefs.dock.toMutableList()
        list.remove(app.key)
        list.add(app.key)
        viewModelScope.launch { prefsStore.setDock(list.takeLast(DOCK_SIZE)) }
    }

    fun removeFromDock(app: AppInfo) {
        viewModelScope.launch { prefsStore.setDock(currentPrefs.dock - app.key) }
    }

    fun setDefaultPage(page: Int) = viewModelScope.launch { prefsStore.setDefaultPage(page) }

    fun setShowNewsPage(value: Boolean) = viewModelScope.launch { prefsStore.setShowNewsPage(value) }

    // ---------- الفولدرات ----------

    fun createFolder(name: String, apps: List<String> = emptyList()) {
        val id = PrefsStore.newId()
        val folder = Folder(id = id, name = name, apps = apps)
        viewModelScope.launch {
            prefsStore.setFolders(currentPrefs.folders + folder)
            addEntry("f:$id")
        }
    }

    fun renameFolder(id: String, name: String) {
        updateFolder(id) { it.copy(name = name.trim().ifBlank { it.name }) }
    }

    fun deleteFolder(id: String) {
        viewModelScope.launch {
            prefsStore.setFolders(currentPrefs.folders.filterNot { it.id == id })
            prefsStore.setHome(currentPrefs.home.filterNot { it == "f:$id" })
        }
    }

    fun setFolderApps(id: String, apps: List<String>) {
        updateFolder(id) { it.copy(apps = apps.distinct()) }
    }

    fun addToFolder(id: String, appKey: String) {
        val folder = currentPrefs.folderOf(id) ?: return
        if (folder.apps.contains(appKey)) return
        updateFolder(id) { it.copy(apps = folder.apps + appKey) }
        removeFromHomeKey(appKey)
    }

    private fun removeFromHomeKey(appKey: String) {
        val entry = "a:$appKey"
        if (currentPrefs.home.contains(entry)) {
            viewModelScope.launch { prefsStore.setHome(currentPrefs.home - entry) }
        }
    }

    fun removeFromFolder(id: String, appKey: String) {
        val folder = currentPrefs.folderOf(id) ?: return
        updateFolder(id) { it.copy(apps = folder.apps - appKey) }
        addEntry("a:$appKey")
    }

    fun setFolderStyle(id: String, style: FolderStyle) = updateFolder(id) { it.copy(style = style) }

    fun setFolderImage(id: String, uri: Uri?) = updateFolder(id) { it.copy(imageUri = uri?.toString()) }

    fun setFolderColumns(id: String, columns: Int) = updateFolder(id) { it.copy(columns = columns) }

    fun setFolderIconScale(id: String, scale: Float) = updateFolder(id) { it.copy(iconScale = scale) }

    fun setFolderWindowScale(id: String, scale: Float) = updateFolder(id) { it.copy(windowScale = scale) }

    private fun updateFolder(id: String, block: (Folder) -> Folder) {
        viewModelScope.launch {
            prefsStore.setFolders(currentPrefs.folders.map { if (it.id == id) block(it) else it })
        }
    }

    // ---------- الويدجت ----------

    fun addWidget(type: String, spanX: Int, spanY: Int, color: Int, alpha: Float) {
        val spec = WidgetSpec(
            id = PrefsStore.newId(),
            type = type,
            spanX = spanX,
            spanY = spanY,
            color = color,
            alpha = alpha
        )
        viewModelScope.launch {
            prefsStore.setWidgets(currentPrefs.widgets + spec)
            addEntry("w:${spec.id}")
        }
    }

    fun updateWidget(id: String, block: (WidgetSpec) -> WidgetSpec) {
        viewModelScope.launch {
            prefsStore.setWidgets(currentPrefs.widgets.map { if (it.id == id) block(it) else it })
        }
    }

    fun deleteWidget(id: String) {
        viewModelScope.launch {
            prefsStore.setWidgets(currentPrefs.widgets.filterNot { it.id == id })
            prefsStore.setHome(currentPrefs.home.filterNot { it == "w:$id" })
        }
    }

    // ---------- درج التطبيقات ----------

    fun createTab(name: String, apps: List<String> = emptyList()): String {
        val id = PrefsStore.newId()
        viewModelScope.launch {
            prefsStore.setDrawerTabs(currentPrefs.drawerTabs + DrawerTab(id, name.trim().ifBlank { id }, apps))
        }
        return id
    }

    fun renameTab(id: String, name: String) {
        viewModelScope.launch {
            prefsStore.setDrawerTabs(
                currentPrefs.drawerTabs.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it }
            )
        }
    }

    fun deleteTab(id: String) {
        viewModelScope.launch {
            prefsStore.setDrawerTabs(currentPrefs.drawerTabs.filterNot { it.id == id })
            if (currentPrefs.defaultTab == "tab:$id") prefsStore.setDefaultTab(LauncherPrefs.TAB_ALL)
        }
    }

    fun setTabApps(id: String, apps: List<String>) {
        viewModelScope.launch {
            prefsStore.setDrawerTabs(
                currentPrefs.drawerTabs.map { if (it.id == id) it.copy(apps = apps.distinct()) else it }
            )
        }
    }

    fun addAppToTab(tabId: String, appKey: String) {
        val tab = currentPrefs.drawerTabs.firstOrNull { it.id == tabId } ?: return
        if (tab.apps.contains(appKey)) return
        setTabApps(tabId, tab.apps + appKey)
    }

    fun setDefaultTab(value: String) = viewModelScope.launch { prefsStore.setDefaultTab(value) }

    // ---------- الإيماءات ----------

    fun setGesture(gestureId: String, action: String) {
        val map = currentPrefs.gestures.toMutableMap()
        if (action == ACTION_NONE) map.remove(gestureId) else map[gestureId] = action
        viewModelScope.launch { prefsStore.setGestures(map) }
    }

    fun gestureAction(gestureId: String): String = currentPrefs.gestures[gestureId] ?: ACTION_NONE

    // ---------- إخفاء / تسمية ----------

    fun hideApp(app: AppInfo) {
        viewModelScope.launch { prefsStore.setHidden(currentPrefs.hidden + app.key) }
    }

    fun unhideApp(key: String) {
        viewModelScope.launch { prefsStore.setHidden(currentPrefs.hidden - key) }
    }

    fun renameApp(app: AppInfo, newName: String?) {
        val map = currentPrefs.renames.toMutableMap()
        val clean = newName?.trim().orEmpty()
        if (clean.isEmpty() || clean == app.label) map.remove(app.key) else map[app.key] = clean
        viewModelScope.launch { prefsStore.setRenames(map) }
    }

    // ---------- الإعدادات ----------

    fun setColumns(value: Int) = viewModelScope.launch { prefsStore.setColumns(value) }
    fun setRows(value: Int) = viewModelScope.launch { prefsStore.setRows(value) }
    fun setDrawerColumns(value: Int) = viewModelScope.launch { prefsStore.setDrawerColumns(value) }
    fun setIconScale(value: Float) = viewModelScope.launch { prefsStore.setIconScale(value) }
    fun setShowLabels(value: Boolean) = viewModelScope.launch { prefsStore.setShowLabels(value) }
    fun setWallpaperDim(value: Int) = viewModelScope.launch { prefsStore.setWallpaperDim(value) }
    fun setLocale(value: String?) = viewModelScope.launch { prefsStore.setLocale(value) }

    fun setWallpaper(uri: Uri?) = viewModelScope.launch {
        prefsStore.setWallpaperUri(uri?.toString())
        wallpaperKey = null
        syncWallpaper(currentPrefs)
    }

    fun resetLayout() {
        seeded = false
        viewModelScope.launch { prefsStore.resetLayout() }
    }

    // ---------- البحث ----------

    fun searchContacts(query: String) = viewModelScope.launch {
        _contactHits.value = contacts.search(query)
    }

    fun clearContacts() {
        _contactHits.value = emptyList()
    }

    fun hasContactsPermission(): Boolean = contacts.hasPermission()

    fun webSearchUrl(query: String) = "https://www.google.com/search?q=" + Uri.encode(query)

    fun openUrl(url: String) {
        runCatching {
            getApplication<Application>().startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openAppByPackage(pkg: String) {
        runCatching {
            getApplication<Application>().startActivity(
                getApplication<Application>().packageManager
                    .getLaunchIntentForPackage(pkg)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?: Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // ---------- الخلفية ----------

    private fun syncWallpaper(p: LauncherPrefs) {
        val key = p.wallpaperUri ?: SYSTEM_WALLPAPER
        if (key == wallpaperKey) return
        wallpaperKey = key
        viewModelScope.launch {
            _wallpaper.value = loadWallpaper(p.wallpaperUri)
        }
    }

    private suspend fun loadWallpaper(uriString: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (uriString == null) {
                val manager = getApplication<Application>()
                    .getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
                manager.drawable?.let { drawable -> drawable.toScaledBitmap(MAX_WALLPAPER_WIDTH)?.asImageBitmap() }
            } else {
                decodeSampled(getApplication(), Uri.parse(uriString))?.asImageBitmap()
            }
        }.getOrNull()
    }

    companion object {
        const val DOCK_SIZE = 5
        const val ACTION_NONE = "none"
        private const val SYSTEM_WALLPAPER = "system"
        private const val MAX_WALLPAPER_WIDTH = 1080

        /** عدد الأيقونات اللي بنحمّلها مسبقًا في الخلفية */
        private const val PRELOAD_COUNT = 180
        private const val PRELOAD_CONCURRENCY = 3

        private fun Drawable.toScaledBitmap(maxWidth: Int): Bitmap? {
            val w = intrinsicWidth.takeIf { it > 0 } ?: maxWidth
            val h = intrinsicHeight.takeIf { it > 0 } ?: maxWidth
            val scale = maxWidth.toFloat() / w
            val outW = (w * scale).toInt().coerceAtLeast(1)
            val outH = (h * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            setBounds(0, 0, outW, outH)
            draw(canvas)
            return bmp
        }

        private fun decodeSampled(context: Context, uri: Uri): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val (w, h) = bounds.outWidth to bounds.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            while (w / sample > 1440 || h / sample > 1440) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    }
}
