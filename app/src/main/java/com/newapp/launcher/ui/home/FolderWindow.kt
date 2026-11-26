package com.newapp.launcher.ui.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newapp.launcher.R
import com.newapp.launcher.data.AppInfo
import com.newapp.launcher.data.Folder
import com.newapp.launcher.data.FolderStyle
import com.newapp.launcher.data.LauncherPrefs
import com.newapp.launcher.ui.IconStore
import com.newapp.launcher.ui.LauncherViewModel
import com.newapp.launcher.ui.common.ActionSheet
import com.newapp.launcher.ui.common.ActionSpec
import com.newapp.launcher.ui.common.AppIconImage
import com.newapp.launcher.ui.common.CellItem
import com.newapp.launcher.ui.common.DragGrid
import com.newapp.launcher.ui.common.EmptyHint
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * نافذة الفولدر: عرض التطبيقات + إعادة الترتيب + الإعدادات + التنقل بين الفولدرات.
 */
@Composable
fun FolderWindow(
    prefs: LauncherPrefs,
    folderId: String,
    appsByKey: Map<String, AppInfo>,
    icons: IconStore,
    requestIcon: (AppInfo) -> Unit,
    vm: LauncherViewModel,
    onLaunch: (AppInfo) -> Unit,
    onClose: () -> Unit
) {
    var currentId by remember(folderId) { mutableStateOf(folderId) }
    val folder = prefs.folderOf(currentId)
    if (folder == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var numbersMode by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Pair<AppInfo, Offset>?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            vm.setFolderImage(folder.id, uri)
        }
    }

    val index = prefs.folders.indexOfFirst { it.id == folder.id }
    val prev = prefs.folders.getOrNull(index - 1)
    val next = prefs.folders.getOrNull(index + 1)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { onClose() } }
    ) {
        val fraction = (0.88f * folder.windowScale).coerceIn(0.6f, 0.98f)
        Box(
            Modifier
                .align(Alignment.Center)
                .width(maxWidth * fraction)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xF21B2029))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = folder.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showAdd = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = stringResource(R.string.folder_add_apps),
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.folder_settings),
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.cancel),
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                HorizontalDivider(
                    Modifier.padding(vertical = 6.dp),
                    color = Color.White.copy(alpha = 0.12f)
                )

                FolderContent(
                    folder = folder,
                    appsByKey = appsByKey,
                    icons = icons,
                    requestIcon = requestIcon,
                    prefs = prefs,
                    numbersMode = numbersMode,
                    onToggleMode = { numbersMode = !numbersMode },
                    onLaunch = onLaunch,
                    onMenu = { app, anchor -> menu = app to anchor },
                    onReorder = { list -> vm.setFolderApps(folder.id, list) },
                    onSaveNumbers = { list ->
                        vm.setFolderApps(folder.id, list)
                        numbersMode = false
                    },
                    onAdd = { showAdd = true }
                )
            }

            if (prev != null) {
                IconButton(
                    onClick = { currentId = prev.id },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            if (next != null) {
                IconButton(
                    onClick = { currentId = next.id },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }

    // قائمة خيارات التطبيق داخل الفولدر
    menu?.let { (app, anchor) ->
        ActionSheet(
            title = prefs.labelOf(app),
            primary = listOf(
                ActionSpec(stringResource(R.string.folder_remove_app), R.drawable.ic_remove) {
                    vm.removeFromFolder(folder.id, app.key)
                },
                ActionSpec(stringResource(R.string.menu_uninstall), R.drawable.ic_uninstall) {
                    vm.uninstall(app)
                }
            ),
            secondary = listOf(
                ActionSpec(stringResource(R.string.menu_app_info), R.drawable.ic_info) {
                    vm.openAppInfo(app)
                }
            ),
            anchor = anchor,
            onDismiss = { menu = null }
        )
    }

    if (showAdd) {
        AddAppsDialog(
            prefs = prefs,
            apps = appsByKey.values.toList(),
            existing = folder.apps,
            onConfirm = { selected ->
                vm.setFolderApps(folder.id, folder.apps + selected)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    if (showSettings) {
        FolderSettingsDialog(
            folder = folder,
            onRename = { vm.renameFolder(folder.id, it) },
            onStyle = { vm.setFolderStyle(folder.id, it) },
            onColumns = { vm.setFolderColumns(folder.id, it) },
            onIconScale = { vm.setFolderIconScale(folder.id, it) },
            onWindowScale = { vm.setFolderWindowScale(folder.id, it) },
            onPickImage = { pickImage.launch(arrayOf("image/*")) },
            onClearImage = { vm.setFolderImage(folder.id, null) },
            onDelete = {
                vm.deleteFolder(folder.id)
                showSettings = false
                onClose()
            },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun FolderContent(
    folder: Folder,
    appsByKey: Map<String, AppInfo>,
    icons: IconStore,
    requestIcon: (AppInfo) -> Unit,
    prefs: LauncherPrefs,
    numbersMode: Boolean,
    onToggleMode: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onMenu: (AppInfo, Offset) -> Unit,
    onReorder: (List<String>) -> Unit,
    onSaveNumbers: (List<String>) -> Unit,
    onAdd: () -> Unit
) {
    val apps = folder.apps.mapNotNull { appsByKey[it] }
    val iconSize = 54.dp * folder.iconScale

    Column {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.folder_apps_count, apps.size),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onToggleMode) {
                Icon(
                    painter = painterResource(
                        if (numbersMode) R.drawable.ic_drag else R.drawable.ic_numbers
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (numbersMode) stringResource(R.string.reorder_drag)
                    else stringResource(R.string.reorder_numbers),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }

        if (apps.isEmpty()) {
            EmptyHint(stringResource(R.string.folder_empty))
        } else if (numbersMode) {
            ReorderNumbers(
                apps = apps,
                prefs = prefs,
                icons = icons,
                requestIcon = requestIcon,
                onSave = onSaveNumbers
            )
        } else if (folder.style == FolderStyle.HORIZONTAL) {
            val rowsPerPage = 3
            val perPage = (folder.columns * rowsPerPage).coerceAtLeast(1)
            val pages = ceil(apps.size / perPage.toFloat()).toInt().coerceAtLeast(1)
            val pagerState = rememberPagerState(pageCount = { pages })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(340.dp)
            ) { page ->
                val pageApps = apps.subList(
                    (page * perPage).coerceAtMost(apps.size),
                    ((page + 1) * perPage).coerceAtMost(apps.size)
                )
                AppGrid(
                    apps = pageApps,
                    prefs = prefs,
                    icons = icons,
                    requestIcon = requestIcon,
                    columns = folder.columns,
                    iconSize = iconSize,
                    onLaunch = onLaunch,
                    onMenu = onMenu
                )
            }
        } else {
            AppGrid(
                apps = apps,
                prefs = prefs,
                icons = icons,
                requestIcon = requestIcon,
                columns = folder.columns,
                iconSize = iconSize,
                onLaunch = onLaunch,
                onMenu = onMenu,
                modifier = Modifier.fillMaxWidth().height(340.dp)
            )
        }
    }
}

@Composable
private fun AppGrid(
    apps: List<AppInfo>,
    prefs: LauncherPrefs,
    icons: IconStore,
    requestIcon: (AppInfo) -> Unit,
    columns: Int,
    iconSize: androidx.compose.ui.unit.Dp,
    onLaunch: (AppInfo) -> Unit,
    onMenu: (AppInfo, Offset) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(apps, key = { it.key }) { app ->
            FolderAppCell(
                app = app,
                label = prefs.labelOf(app),
                icons = icons,
                requestIcon = requestIcon,
                iconSize = iconSize,
                onClick = { onLaunch(app) },
                onLongClick = { anchor -> onMenu(app, anchor) }
            )
        }
    }
}

@Composable
private fun FolderAppCell(
    app: AppInfo,
    label: String,
    icons: IconStore,
    requestIcon: (AppInfo) -> Unit,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit
) {
    var pos by remember { mutableStateOf(Offset.Zero) }
    Column(
        modifier = Modifier
            .onGloballyPositioned { pos = it.positionInRoot() }
            .pointerInput(app.key) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { local -> onLongClick(pos + local) }
                )
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconImage(app = app, icons = icons, requestIcon = requestIcon, size = iconSize)
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
        )
    }
}

/** إعادة الترتيب عبر أرقام ثم حفظ */
@Composable
private fun ReorderNumbers(
    apps: List<AppInfo>,
    prefs: LauncherPrefs,
    icons: IconStore,
    requestIcon: (AppInfo) -> Unit,
    onSave: (List<String>) -> Unit
) {
    var order by remember(apps.map { it.key }) {
        mutableStateOf(apps.mapIndexed { i, a -> a.key to (i + 1).toString() }.toMap())
    }

    Column(Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(apps, key = { it.key }) { app ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppIconImage(app, icons, requestIcon, 36.dp)
                    Text(
                        text = prefs.labelOf(app),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = order[app.key].orEmpty(),
                        onValueChange = { v ->
                            val clean = v.filter { it.isDigit() }.take(3)
                            order = order.toMutableMap().apply { put(app.key, clean) }
                        },
                        singleLine = true,
                        modifier = Modifier.width(72.dp)
                    )
                }
            }
        }
        OutlinedButton(
            onClick = {
                val sorted = order.entries
                    .sortedBy { it.value.toIntOrNull() ?: Int.MAX_VALUE }
                    .map { it.key }
                onSave(sorted)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.folder_save))
        }
    }
}

@Composable
private fun AddAppsDialog(
    prefs: LauncherPrefs,
    apps: List<AppInfo>,
    existing: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val candidates = remember(apps, existing) {
        apps.filter { it.key !in existing }.sortedBy { prefs.labelOf(it) }
    }
    val selected = remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_add_apps)) },
        text = {
            if (candidates.isEmpty()) {
                Text(stringResource(R.string.no_results))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(candidates, key = { it.key }) { app ->
                        val checked = selected.value.contains(app.key)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected.value = if (checked) {
                                        selected.value - app.key
                                    } else {
                                        selected.value + app.key
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { on ->
                                    selected.value = if (on) selected.value + app.key
                                    else selected.value - app.key
                                }
                            )
                            Text(
                                text = prefs.labelOf(app),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selected.value.toList()) },
                enabled = candidates.isNotEmpty()
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun FolderSettingsDialog(
    folder: Folder,
    onRename: (String) -> Unit,
    onStyle: (FolderStyle) -> Unit,
    onColumns: (Int) -> Unit,
    onIconScale: (Float) -> Unit,
    onWindowScale: (Float) -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(folder.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_settings)) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.folder_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { onRename(name) }) { Text(stringResource(R.string.save)) }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                Text(
                    stringResource(R.string.folder_style),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StyleButton(
                        label = stringResource(R.string.folder_style_vertical),
                        selected = folder.style == FolderStyle.VERTICAL,
                        onClick = { onStyle(FolderStyle.VERTICAL) },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    StyleButton(
                        label = stringResource(R.string.folder_style_horizontal),
                        selected = folder.style == FolderStyle.HORIZONTAL,
                        onClick = { onStyle(FolderStyle.HORIZONTAL) },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                SliderRow(
                    label = stringResource(R.string.folder_columns) + ": ${folder.columns}",
                    value = folder.columns.toFloat(),
                    range = 2f..6f,
                    onChange = { onColumns(it.roundToInt()) },
                    steps = 3
                )
                SliderRow(
                    label = stringResource(R.string.folder_icon_size),
                    value = folder.iconScale,
                    range = 0.7f..1.5f,
                    onChange = onIconScale
                )
                SliderRow(
                    label = stringResource(R.string.folder_window_size),
                    value = folder.windowScale,
                    range = 0.6f..1.6f,
                    onChange = onWindowScale
                )

                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                Text(
                    stringResource(R.string.folder_image),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickImage) {
                        Text(stringResource(R.string.folder_image_pick))
                    }
                    OutlinedButton(onClick = onClearImage) {
                        Text(stringResource(R.string.folder_image_clear))
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.folder_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        }
    )
}

@Composable
private fun StyleButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    steps: Int = 0
) {
    Column(Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            valueRange = range,
            steps = steps,
            onValueChange = onChange
        )
    }
}
