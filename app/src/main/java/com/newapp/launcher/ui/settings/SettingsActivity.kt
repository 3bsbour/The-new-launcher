package com.newapp.launcher.ui.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newapp.launcher.R
import com.newapp.launcher.LauncherAccessibilityService
import com.newapp.launcher.data.Gestures
import com.newapp.launcher.ui.LauncherViewModel
import com.newapp.launcher.ui.common.TextInputDialog
import com.newapp.launcher.ui.drawer.TabKind
import com.newapp.launcher.ui.drawer.buildDrawerTabs
import com.newapp.launcher.ui.theme.LauncherTheme
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LauncherTheme {
                val vm: LauncherViewModel = viewModel()
                SettingsScreen(vm = vm, onBack = { finish() })
            }
        }
    }
}

/** أقسام شاشة الإعدادات */
private enum class SettingsPage { ROOT, HOME, DRAWER, WIDGETS, GESTURES, WALLPAPER, HIDDEN, ADVANCED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: LauncherViewModel, onBack: () -> Unit) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appsByKey = remember(apps) { apps.associateBy { it.key } }

    var page by remember { mutableStateOf(SettingsPage.ROOT) }
    var showNewTab by remember { mutableStateOf(false) }
    var renameTabId by remember { mutableStateOf<String?>(null) }
    var showDefaultTab by remember { mutableStateOf(false) }
    var showWidgetPicker by remember { mutableStateOf(false) }
    var gestureDialog by remember { mutableStateOf<String?>(null) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    val arabicFirst = remember { java.util.Locale.getDefault().language.startsWith("ar") }
    val drawerTabItems = remember(apps, prefs.drawerTabs, prefs.renames, arabicFirst) {
        buildDrawerTabs(apps, prefs, arabicFirst)
    }
    val allTabLabel = stringResource(R.string.tab_all)
    val drawerTabOptions = remember(drawerTabItems, allTabLabel, prefs.drawerTabs) {
        drawerTabItems.map { item ->
            item.key to when (item.kind) {
                TabKind.LETTER -> item.key
                TabKind.ALL -> allTabLabel
                TabKind.CUSTOM ->
                    prefs.drawerTabs.firstOrNull { "tab:${it.id}" == item.key }?.name ?: item.key
            }
        }
    }
    val pageSize = (prefs.columns * prefs.rows).coerceAtLeast(1)
    val homePages = ((prefs.home.size + pageSize - 1) / pageSize).coerceAtLeast(1)

    val actionOptions = listOf(
        Gestures.NONE to stringResource(R.string.action_none),
        Gestures.DRAWER to stringResource(R.string.action_open_drawer),
        Gestures.SEARCH to stringResource(R.string.action_open_search),
        Gestures.CAMERA to stringResource(R.string.action_open_camera),
        Gestures.SETTINGS to stringResource(R.string.action_open_settings),
        Gestures.NEWS to stringResource(R.string.action_open_news),
        Gestures.NOTIFICATIONS to stringResource(R.string.action_open_notifications)
    )
    val gestureRows = listOf(
        Gestures.SWIPE_UP to R.string.gesture_swipe_up,
        Gestures.SWIPE_DOWN to R.string.gesture_swipe_down,
        Gestures.SWIPE_UP_2 to R.string.gesture_swipe_up_two,
        Gestures.SWIPE_DOWN_2 to R.string.gesture_swipe_down_two,
        Gestures.SWIPE_LEFT_2 to R.string.gesture_swipe_left_two,
        Gestures.SWIPE_RIGHT_2 to R.string.gesture_swipe_right_two,
        Gestures.PINCH_IN to R.string.gesture_pinch_in,
        Gestures.PINCH_OUT to R.string.gesture_pinch_out
    )
    val widgetTypeOptions = listOf(
        "clock" to stringResource(R.string.widget_clock),
        "battery" to stringResource(R.string.widget_battery),
        "calendar" to stringResource(R.string.widget_calendar),
        "weather" to stringResource(R.string.widget_weather)
    )

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            vm.setWallpaper(uri)
        }
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    BackHandler(enabled = page != SettingsPage.ROOT) { page = SettingsPage.ROOT }

    val pageTitle = when (page) {
        SettingsPage.ROOT -> stringResource(R.string.settings_title)
        SettingsPage.HOME -> stringResource(R.string.home_page_section)
        SettingsPage.DRAWER -> stringResource(R.string.drawer_section)
        SettingsPage.WIDGETS -> stringResource(R.string.widgets_section)
        SettingsPage.GESTURES -> stringResource(R.string.gestures_section)
        SettingsPage.WALLPAPER -> stringResource(R.string.wallpaper_title)
        SettingsPage.HIDDEN -> stringResource(R.string.hidden_apps)
        SettingsPage.ADVANCED -> stringResource(R.string.settings_advanced)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pageTitle) },
                navigationIcon = {
                    IconButton(
                        onClick = { if (page == SettingsPage.ROOT) onBack() else page = SettingsPage.ROOT }
                    ) {
                        Icon(
                            painter = painterResource(
                                if (page == SettingsPage.ROOT) R.drawable.ic_close else R.drawable.ic_chevron_left
                            ),
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }
            )
        }
    ) { padding ->
        when (page) {
            // ---------- قائمة الأقسام ----------
            SettingsPage.ROOT -> SettingsList(padding) {
                item {
                    NavRow(
                        title = stringResource(R.string.home_page_section),
                        subtitle = stringResource(R.string.settings_home_sub)
                    ) { page = SettingsPage.HOME }
                }
                item {
                    NavRow(
                        title = stringResource(R.string.drawer_section),
                        subtitle = stringResource(R.string.settings_drawer_sub)
                    ) { page = SettingsPage.DRAWER }
                }
                item {
                    NavRow(
                        title = stringResource(R.string.widgets_section),
                        subtitle = stringResource(R.string.settings_widgets_sub)
                    ) { page = SettingsPage.WIDGETS }
                }
                item {
                    NavRow(
                        title = stringResource(R.string.gestures_section),
                        subtitle = stringResource(R.string.settings_gestures_sub)
                    ) { page = SettingsPage.GESTURES }
                }
                item {
                    NavRow(
                        title = stringResource(R.string.wallpaper_title),
                        subtitle = stringResource(R.string.settings_wallpaper_sub)
                    ) { page = SettingsPage.WALLPAPER }
                }
                item {
                    NavRow(
                        title = stringResource(R.string.hidden_apps),
                        subtitle = stringResource(R.string.settings_hidden_sub)
                    ) { page = SettingsPage.HIDDEN }
                }
                item {
                    NavRow(
                        title = stringResource(R.string.settings_advanced),
                        subtitle = stringResource(R.string.settings_advanced_sub)
                    ) { page = SettingsPage.ADVANCED }
                }
            }

            // ---------- الصفحة الرئيسية ----------
            SettingsPage.HOME -> SettingsList(padding) {
                item { SectionTitle(stringResource(R.string.layout_section)) }
                item {
                    StepperRow(
                        label = stringResource(R.string.columns),
                        value = prefs.columns,
                        min = 3,
                        max = 6,
                        onChange = { vm.setColumns(it) }
                    )
                }
                item {
                    StepperRow(
                        label = stringResource(R.string.rows),
                        value = prefs.rows,
                        min = 3,
                        max = 7,
                        onChange = { vm.setRows(it) }
                    )
                }
                item {
                    SliderRow(
                        label = stringResource(R.string.icon_size),
                        value = prefs.iconScale,
                        valueRange = 0.7f..1.4f,
                        onChange = { vm.setIconScale(it) }
                    )
                }
                item {
                    SwitchRow(
                        label = stringResource(R.string.show_labels),
                        checked = prefs.showLabels,
                        onCheckedChange = { vm.setShowLabels(it) }
                    )
                }
                item { HorizontalDivider(Modifier.padding(vertical = 10.dp)) }
                item { SectionTitle(stringResource(R.string.home_page_section)) }
                item {
                    StepperRow(
                        label = stringResource(R.string.default_page),
                        value = prefs.defaultPage.coerceIn(0, homePages - 1) + 1,
                        min = 1,
                        max = homePages,
                        onChange = { vm.setDefaultPage(it - 1) }
                    )
                }
                item {
                    SwitchRow(
                        label = stringResource(R.string.news_show),
                        checked = prefs.showNewsPage,
                        onCheckedChange = { vm.setShowNewsPage(it) }
                    )
                }
            }

            // ---------- درج التطبيقات ----------
            SettingsPage.DRAWER -> SettingsList(padding) {
                item { SectionTitle(stringResource(R.string.drawer_section)) }
                item {
                    StepperRow(
                        label = stringResource(R.string.drawer_columns),
                        value = prefs.drawerColumns,
                        min = 3,
                        max = 6,
                        onChange = { vm.setDrawerColumns(it) }
                    )
                }
                item { HorizontalDivider(Modifier.padding(vertical = 10.dp)) }
                item { SectionTitle(stringResource(R.string.tabs_section)) }
                item {
                    val defaultLabel = drawerTabOptions
                        .firstOrNull { it.first == prefs.defaultTab }?.second ?: allTabLabel
                    ActionRow(
                        label = stringResource(R.string.tab_default) + " · " + defaultLabel,
                        onClick = { showDefaultTab = true }
                    )
                }
                items(prefs.drawerTabs, key = { it.id }) { tab ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tab.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { renameTabId = tab.id }) {
                            Text(stringResource(R.string.tab_rename))
                        }
                        TextButton(onClick = { vm.deleteTab(tab.id) }) {
                            Text(stringResource(R.string.tab_delete))
                        }
                    }
                }
                item {
                    ActionRow(
                        label = stringResource(R.string.tab_new),
                        onClick = { showNewTab = true }
                    )
                }
            }

            // ---------- الويدجت ----------
            SettingsPage.WIDGETS -> SettingsList(padding) {
                item { SectionTitle(stringResource(R.string.widgets_section)) }
                if (prefs.widgets.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.widgets_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(prefs.widgets, key = { it.id }) { widget ->
                        val typeLabel = widgetTypeOptions
                            .firstOrNull { it.first == widget.type }?.second ?: widget.type
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { vm.deleteWidget(widget.id) }) {
                                Text(stringResource(R.string.widget_delete))
                            }
                        }
                    }
                }
                item {
                    ActionRow(
                        label = stringResource(R.string.widget_add),
                        onClick = { showWidgetPicker = true }
                    )
                }
            }

            // ---------- الإيماءات ----------
            SettingsPage.GESTURES -> SettingsList(padding) {
                item { SectionTitle(stringResource(R.string.gestures_section)) }
                item {
                    Text(
                        text = stringResource(R.string.gestures_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(gestureRows, key = { it.first }) { (id, labelRes) ->
                    val action = prefs.gestures[id] ?: Gestures.NONE
                    val actionLabel = actionOptions
                        .firstOrNull { it.first == action }?.second
                        ?: stringResource(R.string.action_none)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { gestureDialog = id }
                            .padding(vertical = 14.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // ---------- الخلفية ----------
            SettingsPage.WALLPAPER -> SettingsList(padding) {
                item { SectionTitle(stringResource(R.string.wallpaper_title)) }
                item {
                    ActionRow(
                        label = stringResource(R.string.wallpaper_pick),
                        onClick = { pickImage.launch(arrayOf("image/*")) }
                    )
                }
                item {
                    ActionRow(
                        label = stringResource(R.string.wallpaper_system),
                        onClick = { vm.setWallpaper(null) }
                    )
                }
                item {
                    SliderRow(
                        label = stringResource(R.string.wallpaper_dim),
                        value = prefs.wallpaperDim.toFloat(),
                        valueRange = 0f..80f,
                        onChange = { vm.setWallpaperDim(it.roundToInt()) }
                    )
                }
            }

            // ---------- التطبيقات المخفية ----------
            SettingsPage.HIDDEN -> SettingsList(padding) {
                item { SectionTitle(stringResource(R.string.hidden_apps)) }
                if (prefs.hidden.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.hidden_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(prefs.hidden.toList(), key = { it }) { key ->
                        val label = appsByKey[key]?.let { prefs.labelOf(it) } ?: key
                        HiddenAppRow(label = label, onUnhide = { vm.unhideApp(key) })
                    }
                }
            }

            // ---------- عام ----------
            SettingsPage.ADVANCED -> SettingsList(padding) {
                item { SectionTitle(stringResource(R.string.language_section)) }
                item {
                    ActionRow(
                        label = stringResource(R.string.language_label) + " · " + when (prefs.locale) {
                            "ar" -> stringResource(R.string.language_arabic)
                            "en" -> stringResource(R.string.language_english)
                            else -> stringResource(R.string.language_system)
                        },
                        onClick = { showLanguagePicker = true }
                    )
                }
                item { SectionTitle(stringResource(R.string.permissions_section)) }
                item {
                    ActionRow(
                        label = stringResource(R.string.accessibility_enable),
                        onClick = {
                            runCatching { context.startActivity(LauncherAccessibilityService.settingsIntent()) }
                        }
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.accessibility_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.double_tap_lock),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                item {
                    ActionRow(
                        label = stringResource(R.string.notifications_permission),
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                Toast.makeText(context, R.string.notifications_permission, Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                item { HorizontalDivider(Modifier.padding(vertical = 10.dp)) }
                item { SectionTitle(stringResource(R.string.settings_advanced)) }
                item {
                    OutlinedButton(
                        onClick = {
                            vm.resetLayout()
                            Toast.makeText(context, R.string.reset_done, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text(stringResource(R.string.reset_layout))
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.open_default_settings))
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.set_default_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                item { HorizontalDivider(Modifier.padding(vertical = 10.dp)) }
                item { SectionTitle(stringResource(R.string.about_section)) }
                item {
                    Text(
                        text = stringResource(R.string.about_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showLanguagePicker) {
        val languageOptions = listOf(
            "" to stringResource(R.string.language_system),
            "ar" to stringResource(R.string.language_arabic),
            "en" to stringResource(R.string.language_english)
        )
        OptionPickerDialog(
            title = stringResource(R.string.language_label),
            options = languageOptions,
            selected = prefs.locale.orEmpty(),
            onPick = { value ->
                val language = value.ifBlank { null }
                vm.setLocale(language)
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(language.orEmpty())
                )
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false }
        )
    }

    if (showDefaultTab) {
        OptionPickerDialog(
            title = stringResource(R.string.tab_default),
            options = drawerTabOptions,
            selected = prefs.defaultTab,
            onPick = {
                vm.setDefaultTab(it)
                showDefaultTab = false
            },
            onDismiss = { showDefaultTab = false }
        )
    }

    renameTabId?.let { id ->
        val current = prefs.drawerTabs.firstOrNull { it.id == id }?.name.orEmpty()
        TextInputDialog(
            title = stringResource(R.string.tab_rename),
            initial = current,
            onConfirm = {
                vm.renameTab(id, it)
                renameTabId = null
            },
            onDismiss = { renameTabId = null }
        )
    }

    if (showNewTab) {
        TextInputDialog(
            title = stringResource(R.string.tab_new),
            placeholder = stringResource(R.string.tab_name_hint),
            onConfirm = { name ->
                if (name.isNotBlank()) vm.createTab(name)
                showNewTab = false
            },
            onDismiss = { showNewTab = false }
        )
    }

    if (showWidgetPicker) {
        OptionPickerDialog(
            title = stringResource(R.string.widget_pick_type),
            options = widgetTypeOptions,
            selected = null,
            onPick = { type ->
                vm.addWidget(type = type, spanX = 2, spanY = 1, color = DEFAULT_WIDGET_COLOR, alpha = 0.85f)
                showWidgetPicker = false
            },
            onDismiss = { showWidgetPicker = false }
        )
    }

    gestureDialog?.let { id ->
        val label = gestureRows.firstOrNull { it.first == id }?.second
        OptionPickerDialog(
            title = if (label != null) stringResource(label) else id,
            options = actionOptions,
            selected = prefs.gestures[id] ?: Gestures.NONE,
            onPick = {
                vm.setGesture(id, it)
                gestureDialog = null
            },
            onDismiss = { gestureDialog = null }
        )
    }
}

private val DEFAULT_WIDGET_COLOR = 0xFF202634.toInt()

/** قائمة إعدادات بنفس التنسيق لكل الأقسام */
@Composable
private fun SettingsList(
    padding: PaddingValues,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content
    )
}

/** صف ينقلك لقسم فرعي */
@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OptionPickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.fillMaxWidth()) {
                items(options, key = { it.first }) { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(value) }
                            .padding(vertical = 14.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (value == selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
    )
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { if (value > min) onChange(value - 1) }) { Text("−") }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        TextButton(onClick = { if (value < max) onChange(value + 1) }) { Text("+") }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            valueRange = valueRange,
            onValueChange = onChange
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp)
    )
}

@Composable
private fun HiddenAppRow(label: String, onUnhide: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onUnhide) { Text(stringResource(R.string.menu_unhide)) }
    }
}
