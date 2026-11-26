package com.newapp.launcher.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.newapp.launcher.LauncherAccessibilityService
import com.newapp.launcher.R
import com.newapp.launcher.data.AppInfo
import com.newapp.launcher.data.ContactHit
import com.newapp.launcher.data.Gestures
import com.newapp.launcher.data.LauncherPrefs
import com.newapp.launcher.ui.common.ActionSheet
import com.newapp.launcher.ui.common.ActionSpec
import com.newapp.launcher.ui.common.CellItem
import com.newapp.launcher.ui.common.RenameDialog
import com.newapp.launcher.ui.common.Slot
import com.newapp.launcher.ui.common.TextInputDialog
import com.newapp.launcher.ui.common.rememberHomeGestures
import com.newapp.launcher.ui.drawer.AppDrawer
import com.newapp.launcher.ui.home.FolderWindow
import com.newapp.launcher.ui.home.HomeScreen
import com.newapp.launcher.ui.home.WallpaperLayer
import com.newapp.launcher.ui.settings.SettingsActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LauncherRoot(vm: LauncherViewModel, homePresses: Int, modifier: Modifier = Modifier) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val wallpaper by vm.wallpaper.collectAsStateWithLifecycle()
    val contactHits by vm.contactHits.collectAsStateWithLifecycle()

    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    val visibleApps = remember(apps, prefs.hidden) { apps.filter { it.key !in prefs.hidden } }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    var drawerOpen by remember { mutableStateOf(false) }
    // 0 = الدرج مفتوح تمامًا، screenHeightPx = مختفي أسفل الشاشة
    var drawerOffset by remember { mutableFloatStateOf(screenHeightPx) }
    var drawerAnim by remember { mutableStateOf<Job?>(null) }

    var focusSearch by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<Target?>(null) }
    var renameApp by remember { mutableStateOf<AppInfo?>(null) }
    var renameFolderId by remember { mutableStateOf<String?>(null) }
    var homeMenu by remember { mutableStateOf<Offset?>(null) }
    var folderId by remember { mutableStateOf<String?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var askedContacts by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* لو وافق المستخدم، البحث هيرجع نتائج المرة الجاية */ }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    // نطلب صلاحية الإشعارات أول تشغيل (أندرويد 13+)
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // رجوع لأول صفحة/إغلاق الدروج عند ضغط زر الهوم
    LaunchedEffect(homePresses) {
        if (homePresses > 0) {
            drawerAnim?.cancel()
            drawerOpen = false
            drawerOffset = screenHeightPx
            menuTarget = null
            homeMenu = null
            folderId = null
        }
    }

    val requestContacts = {
        if (!askedContacts) {
            askedContacts = true
            if (!vm.hasContactsPermission()) {
                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    // ---------- الدرج التفاعلي ----------
    fun animateDrawerTo(target: Float) {
        drawerAnim?.cancel()
        val start = drawerOffset
        drawerAnim = scope.launch {
            animate(start, target, animationSpec = tween(220)) { value, _ -> drawerOffset = value }
        }
    }

    fun openDrawer(search: Boolean) {
        focusSearch = search
        drawerAnim?.cancel()
        drawerOffset = screenHeightPx
        drawerOpen = true
        animateDrawerTo(0f)
    }

    fun closeDrawer() {
        drawerAnim?.cancel()
        val start = drawerOffset
        drawerAnim = scope.launch {
            animate(start, screenHeightPx, animationSpec = tween(200)) { value, _ -> drawerOffset = value }
            drawerOpen = false
        }
    }

    /**
     * بيثبّت الدرج: يفتح لو الإصبع عدّى النص أو اتحرك لأعلى بسرعة،
     * ويقفل لو عدّى النص للأسفل أو اتحرك لأسفل بسرعة.
     * @param velocity سرعة الإصبع (px/ث)، سالب = لأعلى
     */
    fun settleDrawer(velocity: Float = 0f) {
        drawerAnim?.cancel()
        val start = drawerOffset
        val open = when {
            velocity < -800f -> true
            velocity > 800f -> false
            else -> start < screenHeightPx * 0.5f
        }
        val target = if (open) 0f else screenHeightPx
        drawerAnim = scope.launch {
            animate(start, target, animationSpec = tween(180)) { value, _ -> drawerOffset = value }
            if (!open) drawerOpen = false
        }
    }

    // ---------- الأفعال + إمكانية الوصول ----------
    fun requireAccessibility(): Boolean {
        if (LauncherAccessibilityService.isEnabled()) return true
        Toast.makeText(context, R.string.need_accessibility, Toast.LENGTH_LONG).show()
        runCatching { context.startActivity(LauncherAccessibilityService.settingsIntent()) }
        return false
    }

    fun lockScreen() {
        if (requireAccessibility()) LauncherAccessibilityService.instance?.lockScreen()
    }

    fun openNotifications() {
        if (requireAccessibility()) LauncherAccessibilityService.instance?.openNotifications()
    }

    fun runAction(action: String) {
        when (action) {
            Gestures.DRAWER -> openDrawer(false)
            Gestures.SEARCH -> openDrawer(true)
            Gestures.CAMERA -> vm.openCamera()
            Gestures.SETTINGS -> context.startActivity(Intent(context, SettingsActivity::class.java))
            Gestures.NEWS -> vm.setShowNewsPage(true)
            Gestures.NOTIFICATIONS -> openNotifications()
        }
    }

    BackHandler(enabled = drawerOpen || menuTarget != null || homeMenu != null || folderId != null) {
        when {
            folderId != null -> folderId = null
            menuTarget != null -> menuTarget = null
            homeMenu != null -> homeMenu = null
            drawerOpen -> closeDrawer()
        }
    }

    // لما السحب لأعلى مربوط بفتح الدرج، نخليه يتبع الإصبع بدل ما يستنى
    val followDrawer = actionOf(prefs, Gestures.SWIPE_UP) == Gestures.DRAWER

    val gestureModifier = rememberHomeGestures(
        hasAction = { id ->
            if (id == Gestures.SWIPE_UP && followDrawer) false
            else actionOf(prefs, id) != Gestures.NONE
        },
        onGesture = { id -> runAction(actionOf(prefs, id)) },
        onVerticalDragStart = {
            if (followDrawer && !drawerOpen) {
                drawerAnim?.cancel()
                drawerOffset = screenHeightPx
                drawerOpen = true
            }
        },
        onVerticalDragProgress = { dy ->
            if (followDrawer && drawerOpen) {
                drawerAnim?.cancel()
                drawerOffset = (screenHeightPx + dy).coerceIn(0f, screenHeightPx)
            }
        },
        onVerticalDragEnd = { _, velocity ->
            if (followDrawer && drawerOpen) settleDrawer(velocity)
        }
    )

    BoxWithWallpaper(modifier, wallpaper, prefs.wallpaperDim) {
        HomeScreen(
            prefs = prefs,
            appsByKey = appsByKey,
            icons = vm.icons,
            requestIcon = { vm.icons[it.key] ?: vm.ensureIcon(it) },
            onLaunchApp = { vm.launch(it) },
            onOpenFolder = { folderId = it.id },
            onMoveHome = { from, to -> vm.moveEntry(from, to) },
            onMoveDock = { from, to -> vm.moveDock(from, to) },
            onItemLongPress = { item, index, slot, anchor ->
                menuTarget = Target(item, index, slot, anchor)
            },
            onOpenDrawer = { openDrawer(false) },
            onOpenSearch = { openDrawer(true) },
            onEmptyLongPress = { homeMenu = it },
            onEmptyDoubleTap = { lockScreen() },
            modifier = Modifier.fillMaxSize(),
            gestureModifier = gestureModifier
        )

        if (drawerOpen) {
            AppDrawer(
                prefs = prefs,
                apps = visibleApps,
                icons = vm.icons,
                requestIcon = { vm.ensureIcon(it) },
                contacts = contactHits,
                focusSearch = focusSearch,
                vm = vm,
                onLaunch = {
                    vm.launch(it)
                    closeDrawer()
                },
                onItemLongPress = { app, index, slot, anchor ->
                    menuTarget = Target(CellItem.AppItem(app), index, slot, anchor)
                },
                onQueryChange = { q ->
                    if (q.isBlank()) {
                        vm.clearContacts()
                    } else {
                        requestContacts()
                        vm.searchContacts(q)
                    }
                },
                onRequestContacts = { requestContacts() },
                onOpenWeb = { q ->
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(vm.webSearchUrl(q)))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    closeDrawer()
                },
                onCallContact = { contact: ContactHit ->
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.number}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    closeDrawer()
                },
                onOpenSettings = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                },
                onClose = { closeDrawer() },
                onDragProgress = { dy ->
                    drawerAnim?.cancel()
                    drawerOffset = (drawerOffset + dy).coerceIn(0f, screenHeightPx)
                },
                onDragSettle = { settleDrawer() },
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, drawerOffset.roundToInt()) }
            )
        }

        folderId?.let { id ->
            FolderWindow(
                prefs = prefs,
                folderId = id,
                appsByKey = appsByKey,
                icons = vm.icons,
                requestIcon = { vm.ensureIcon(it) },
                vm = vm,
                onLaunch = { vm.launch(it) },
                onClose = { folderId = null }
            )
        }
    }

    // قائمة خيارات العنصر
    menuTarget?.let { target ->
        when (val item = target.item) {
            is CellItem.AppItem -> AppMenu(
                prefs = prefs,
                app = item.app,
                slot = target.slot,
                anchor = target.anchor,
                vm = vm,
                onRename = { renameApp = item.app },
                onDismiss = { menuTarget = null }
            )
            is CellItem.FolderItem -> ActionSheet(
                title = item.folder.name,
                primary = listOf(
                    ActionSpec(stringResource(R.string.folder_settings), R.drawable.ic_settings) {
                        folderId = item.folder.id
                    },
                    ActionSpec(stringResource(R.string.folder_rename), R.drawable.ic_edit) {
                        renameFolderId = item.folder.id
                    },
                    ActionSpec(stringResource(R.string.folder_delete), R.drawable.ic_uninstall) {
                        vm.deleteFolder(item.folder.id)
                    }
                ),
                secondary = emptyList(),
                anchor = target.anchor,
                onDismiss = { menuTarget = null }
            )
            is CellItem.WidgetItem -> ActionSheet(
                title = stringResource(R.string.widget_settings),
                primary = listOf(
                    ActionSpec(stringResource(R.string.widget_delete), R.drawable.ic_uninstall) {
                        vm.deleteWidget(item.spec.id)
                    }
                ),
                secondary = listOf(
                    ActionSpec(stringResource(R.string.widget_size_small), R.drawable.ic_widget) {
                        vm.updateWidget(item.spec.id) { it.copy(spanX = 1) }
                    },
                    ActionSpec(stringResource(R.string.widget_size_medium), R.drawable.ic_widget) {
                        vm.updateWidget(item.spec.id) { it.copy(spanX = 2) }
                    },
                    ActionSpec(stringResource(R.string.widget_size_large), R.drawable.ic_widget) {
                        vm.updateWidget(item.spec.id) { it.copy(spanX = 3) }
                    }
                ),
                anchor = target.anchor,
                onDismiss = { menuTarget = null }
            )
        }
    }

    // إعادة تسمية تطبيق
    renameApp?.let { app ->
        RenameDialog(
            title = stringResource(R.string.rename_title),
            initial = prefs.labelOf(app),
            onConfirm = { newName ->
                vm.renameApp(app, newName)
                renameApp = null
            },
            onReset = {
                vm.renameApp(app, null)
                renameApp = null
            },
            onDismiss = { renameApp = null }
        )
    }

    // إعادة تسمية فولدر
    renameFolderId?.let { id ->
        val folder = prefs.folderOf(id)
        if (folder != null) {
            TextInputDialog(
                title = stringResource(R.string.folder_rename),
                initial = folder.name,
                onConfirm = {
                    vm.renameFolder(id, it)
                    renameFolderId = null
                },
                onDismiss = { renameFolderId = null }
            )
        }
    }

    // قائمة الضغط المطوّل على مساحة فاضية
    homeMenu?.let { anchor ->
        ActionSheet(
            title = stringResource(R.string.home_menu_title),
            primary = listOf(
                ActionSpec(stringResource(R.string.home_new_folder), R.drawable.ic_folder) {
                    showNewFolder = true
                },
                ActionSpec(stringResource(R.string.home_add_widget), R.drawable.ic_widget) {
                    showWidgetPicker = true
                }
            ),
            secondary = listOf(
                ActionSpec(stringResource(R.string.open_drawer), R.drawable.ic_apps) {
                    openDrawer(true)
                },
                ActionSpec(stringResource(R.string.wallpaper), R.drawable.ic_palette) {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                },
                ActionSpec(stringResource(R.string.settings), R.drawable.ic_settings) {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }
            ),
            anchor = anchor,
            onDismiss = { homeMenu = null }
        )
    }

    if (showNewFolder) {
        val defaultFolderName = stringResource(R.string.folder_new)
        TextInputDialog(
            title = stringResource(R.string.folder_new),
            placeholder = stringResource(R.string.folder_name),
            onConfirm = { name ->
                vm.createFolder(name.ifBlank { defaultFolderName })
                showNewFolder = false
            },
            onDismiss = { showNewFolder = false }
        )
    }

    if (showWidgetPicker) {
        WidgetTypeDialog(
            onPick = { type ->
                vm.addWidget(type, 2, 1, DEFAULT_WIDGET_COLOR, 0.85f)
                showWidgetPicker = false
            },
            onDismiss = { showWidgetPicker = false }
        )
    }
}

@Composable
private fun BoxWithWallpaper(
    modifier: Modifier,
    wallpaper: androidx.compose.ui.graphics.ImageBitmap?,
    dim: Int,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(modifier.fillMaxSize()) {
        WallpaperLayer(bitmap = wallpaper, dimPercent = dim)
        content()
    }
}

@Composable
private fun AppMenu(
    prefs: LauncherPrefs,
    app: AppInfo,
    slot: Slot,
    anchor: Offset,
    vm: LauncherViewModel,
    onRename: () -> Unit,
    onDismiss: () -> Unit
) {
    val primary = buildList {
        add(ActionSpec(stringResource(R.string.menu_app_info), R.drawable.ic_info) {
            vm.openAppInfo(app)
        })
        add(ActionSpec(stringResource(R.string.menu_hide), R.drawable.ic_hide) { vm.hideApp(app) })
        when (slot) {
            Slot.DRAWER ->
                add(ActionSpec(stringResource(R.string.menu_add_home), R.drawable.ic_add) {
                    vm.addToHome(app)
                })
            else ->
                add(ActionSpec(stringResource(R.string.menu_remove_home), R.drawable.ic_remove) {
                    if (slot == Slot.DOCK) vm.removeFromDock(app) else vm.removeFromHome(app)
                })
        }
        add(ActionSpec(stringResource(R.string.menu_uninstall), R.drawable.ic_uninstall) {
            vm.uninstall(app)
        })
    }
    val secondary = buildList {
        add(ActionSpec(stringResource(R.string.menu_rename), R.drawable.ic_edit) { onRename() })
        if (slot != Slot.DOCK) {
            add(ActionSpec(stringResource(R.string.menu_add_dock), R.drawable.ic_add) {
                vm.addToDock(app)
            })
        }
        if (slot != Slot.HOME) {
            add(ActionSpec(stringResource(R.string.menu_add_home), R.drawable.ic_add) {
                vm.addToHome(app)
            })
        }
    }
    ActionSheet(
        title = prefs.labelOf(app),
        primary = primary,
        secondary = secondary,
        anchor = anchor,
        onDismiss = onDismiss
    )
}

@Composable
private fun WidgetTypeDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        "clock" to stringResource(R.string.widget_clock),
        "battery" to stringResource(R.string.widget_battery),
        "calendar" to stringResource(R.string.widget_calendar),
        "weather" to stringResource(R.string.widget_weather)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_pick_type)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                options.forEach { (type, label) ->
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(type) }
                            .padding(vertical = 14.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun actionOf(prefs: LauncherPrefs, id: String): String =
    prefs.gestures[id] ?: if (id == Gestures.SWIPE_UP) Gestures.DRAWER else Gestures.NONE

private val DEFAULT_WIDGET_COLOR = 0xFF202634.toInt()

data class Target(val item: CellItem, val index: Int, val slot: Slot, val anchor: Offset)
