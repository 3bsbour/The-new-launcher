package com.newapp.launcher.ui.drawer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import com.newapp.launcher.R
import com.newapp.launcher.data.Alphabet
import com.newapp.launcher.data.AppInfo
import com.newapp.launcher.data.ContactHit
import com.newapp.launcher.data.LauncherPrefs
import com.newapp.launcher.ui.IconStore
import com.newapp.launcher.ui.LauncherViewModel
import com.newapp.launcher.ui.common.ActionSheet
import com.newapp.launcher.ui.common.ActionSpec
import com.newapp.launcher.ui.common.AppIconImage
import com.newapp.launcher.ui.common.SectionLabel
import com.newapp.launcher.ui.common.Slot
import com.newapp.launcher.ui.common.TextInputDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt

/** نوع التاب في درج التطبيقات */
enum class TabKind { LETTER, ALL, CUSTOM }

data class DrawerTabItem(val key: String, val kind: TabKind)

/** يبني تابات الدرج: حروف (حسب لغة النظام) ثم «كل التطبيقات» ثم التابات المخصصة */
fun buildDrawerTabs(
    apps: List<AppInfo>,
    prefs: LauncherPrefs,
    arabicFirst: Boolean
): List<DrawerTabItem> {
    val all = DrawerTabItem(LauncherPrefs.TAB_ALL, TabKind.ALL)
    val customs = prefs.drawerTabs.map { DrawerTabItem("tab:${it.id}", TabKind.CUSTOM) }
    return listOf(all) + customs
}

@Composable
fun AppDrawer(
    prefs: LauncherPrefs,
    apps: List<AppInfo>,
    icons: IconStore,
    requestIcon: (AppInfo) -> Unit,
    contacts: List<ContactHit>,
    focusSearch: Boolean,
    vm: LauncherViewModel,
    onLaunch: (AppInfo) -> Unit,
    onItemLongPress: (AppInfo, Int, Slot, Offset) -> Unit,
    onQueryChange: (String) -> Unit,
    onRequestContacts: () -> Unit,
    onOpenWeb: (String) -> Unit,
    onCallContact: (ContactHit) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    onDragProgress: (Float) -> Unit,
    onDragSettle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val arabicFirst = remember { Locale.getDefault().language.startsWith("ar") }
    val tabs = remember(apps, prefs.drawerTabs, prefs.renames, prefs.hidden, arabicFirst) {
        buildDrawerTabs(apps, prefs, arabicFirst)
    }
    val allLabel = stringResource(R.string.tab_all)
    val labels = remember(tabs, allLabel, prefs.drawerTabs) {
        tabs.map { item ->
            when (item.kind) {
                TabKind.LETTER -> item.key
                TabKind.ALL -> allLabel
                TabKind.CUSTOM ->
                    prefs.drawerTabs.firstOrNull { "tab:${it.id}" == item.key }?.name ?: item.key
            }
        }
    }

    val collator = remember(arabicFirst) { Collator.getInstance(Locale.getDefault()) }
    val sortedApps = remember(apps, prefs.renames, collator) {
        apps.sortedWith(compareBy(collator) { prefs.labelOf(it) })
    }
    // تجميع التطبيقات حسب الحرف مرة واحدة بدل الفلترة في كل إعادة رسم
    val appsByLetter = remember(sortedApps, prefs.renames) {
        sortedApps.groupBy { Alphabet.keyOf(prefs.labelOf(it)) }
    }
    val appsByKey = remember(apps) { apps.associateBy { it.key } }

    // فلترة التابات: نخفي "كل التطبيقات" من شريط التابات لأنه سيكون بصفحة مستقلة مع الفاست سكولر
    val pagerTabs = tabs
    val pagerTabLabels = labels
    val pageCount = pagerTabs.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val tabListState = rememberLazyListState()

    var appliedDefault by remember { mutableStateOf(false) }
    LaunchedEffect(pagerTabs, prefs.defaultTab) {
        if (!appliedDefault && pagerTabs.isNotEmpty()) {
            val idx = pagerTabs.indexOfFirst { it.key == prefs.defaultTab }.let { if (it >= 0) it else 0 }
            if (idx != pagerState.currentPage) pagerState.scrollToPage(idx)
            appliedDefault = true
        }
    }
    LaunchedEffect(pagerState.currentPage, pagerTabs.size) {
        if (pagerTabs.isNotEmpty()) tabListState.animateScrollToItem(if (pagerState.currentPage == 0) 0 else pagerState.currentPage + 1)
    }

    var tabMenu by remember { mutableStateOf<Pair<DrawerTabItem, Offset>?>(null) }
    var renameTab by remember { mutableStateOf<DrawerTabItem?>(null) }
    var showNewTab by remember { mutableStateOf(false) }
    var addAppsToTab by remember { mutableStateOf<DrawerTabItem?>(null) }

    val filtered = remember(query, apps, prefs.renames) {
        if (query.isBlank()) apps
        else apps.filter { prefs.labelOf(it).contains(query, ignoreCase = true) }
    }

    // حالة الفاست سكولر (شريط الحروف الجانبي)
    val fastScrollerLetters = remember(appsByLetter, arabicFirst) {
        Alphabet.sortKeys(appsByLetter.keys, arabicFirst)
    }
    val listState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        onRequestContacts()
        if (!focusSearch) keyboard?.hide()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1015).copy(alpha = 0.97f))
    ) {
        // مقبض السحب + العنوان
        Box(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { onDragSettle() },
                        onDragCancel = { onDragSettle() },
                        onVerticalDrag = { _, dragAmount -> onDragProgress(dragAmount) }
                    )
                }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .size(width = 44.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.35f))
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.all_apps),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings),
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
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onQueryChange(it)
            },
            singleLine = true,
            placeholder = {
                Text(stringResource(R.string.search_hint), color = Color.White.copy(alpha = 0.5f))
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        onQueryChange("")
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (query.isNotBlank()) {
            // وضع البحث: شبكة واحدة مع النتائج
            LazyVerticalGrid(
                columns = GridCells.Fixed(prefs.drawerColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (contacts.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionLabel(stringResource(R.string.search_contacts))
                    }
                    items(contacts, key = { "c_${it.number}" }) { contact ->
                        ContactRow(contact = contact, onClick = { onCallContact(contact) })
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    WebRow(query = query, onClick = { onOpenWeb(query) })
                }
                if (filtered.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.no_results),
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
                items(filtered, key = { it.key }) { app ->
                    DrawerAppItem(
                        app = app,
                        label = prefs.labelOf(app),
                        icons = icons,
                        requestIcon = requestIcon,
                        onClick = { onLaunch(app) },
                        onLongClick = { anchor -> onItemLongPress(app, 0, Slot.DRAWER, anchor) }
                    )
                }
            }
        } else {
            // وضع التصفح العادي: تاب "كل التطبيقات" (الوحيد اللي فيه شريط الحروف) + التابات المخصصة
            Column(Modifier.fillMaxSize()) {
                LazyRow(
                    state = tabListState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item(key = "tab_all") {
                        TabChip(
                            label = allLabel,
                            selected = pagerState.currentPage == 0,
                            onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                            onLongClick = { anchor -> pagerTabs.firstOrNull()?.let { tabMenu = it to anchor } }
                        )
                    }
                    item(key = "tab_plus") {
                        TabChip(
                            label = "+",
                            selected = false,
                            onClick = { showNewTab = true },
                            onLongClick = {}
                        )
                    }
                    items(pagerTabs.drop(1), key = { it.key }) { item ->
                        val index = pagerTabs.indexOf(item)
                        TabChip(
                            label = pagerTabLabels.getOrElse(index) { item.key },
                            selected = pagerState.currentPage == index,
                            onClick = { if (index >= 0) scope.launch { pagerState.animateScrollToPage(index) } },
                            onLongClick = { anchor -> tabMenu = item to anchor }
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) { page ->
                    val item = pagerTabs.getOrNull(page)
                    if (item != null && item.kind == TabKind.ALL) {
                        AllAppsPage(
                            prefs = prefs,
                            appsByLetter = appsByLetter,
                            letters = fastScrollerLetters,
                            listState = listState,
                            icons = icons,
                            requestIcon = requestIcon,
                            onLaunch = onLaunch,
                            onItemLongPress = { app, anchor ->
                                onItemLongPress(app, 0, Slot.DRAWER, anchor)
                            },
                            scope = scope,
                            columns = prefs.drawerColumns
                        )
                    } else if (item != null) {
                        val custom = prefs.drawerTabs.firstOrNull { "tab:${it.id}" == item.key }
                        val tabApps = custom?.apps?.mapNotNull { key -> appsByKey[key] } ?: emptyList()
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(prefs.drawerColumns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (tabApps.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = stringResource(R.string.tab_no_apps),
                                        color = Color.White.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            }
                            items(tabApps, key = { it.key }) { app ->
                                DrawerAppItem(
                                    app = app,
                                    label = prefs.labelOf(app),
                                    icons = icons,
                                    requestIcon = requestIcon,
                                    onClick = { onLaunch(app) },
                                    onLongClick = { anchor ->
                                        onItemLongPress(app, 0, Slot.DRAWER, anchor)
                                    }
                                )
                            }
                            item(key = "add_to_${item.key}") {
                                AddAppTile(onClick = { addAppsToTab = item })
                            }
                        }
                    }
                }
            }
        }
    }

    tabMenu?.let { (item, anchor) ->
        val customId = item.key.removePrefix("tab:")
        val secondary = buildList {
            add(ActionSpec(stringResource(R.string.tab_default), R.drawable.ic_settings) {
                vm.setDefaultTab(item.key)
            })
            if (item.kind == TabKind.CUSTOM) {
                add(ActionSpec(stringResource(R.string.tab_rename), R.drawable.ic_edit) {
                    renameTab = item
                })
                add(ActionSpec(stringResource(R.string.tab_delete), R.drawable.ic_uninstall) {
                    vm.deleteTab(customId)
                })
            }
        }
        ActionSheet(
            title = labels.getOrElse(tabs.indexOf(item)) { item.key },
            primary = emptyList(),
            secondary = secondary,
            anchor = anchor,
            onDismiss = { tabMenu = null }
        )
    }

    renameTab?.let { item ->
        val customId = item.key.removePrefix("tab:")
        val current = prefs.drawerTabs.firstOrNull { it.id == customId }?.name.orEmpty()
        TextInputDialog(
            title = stringResource(R.string.tab_rename),
            initial = current,
            onConfirm = {
                vm.renameTab(customId, it)
                renameTab = null
            },
            onDismiss = { renameTab = null }
        )
    }

    if (showNewTab) {
        val fallbackName = stringResource(R.string.tab_new)
        TextInputDialog(
            title = fallbackName,
            placeholder = stringResource(R.string.tab_name_hint),
            onConfirm = { name ->
                vm.createTab(name.ifBlank { fallbackName })
                showNewTab = false
            },
            onDismiss = { showNewTab = false }
        )
    }

    addAppsToTab?.let { item ->
        val customId = item.key.removePrefix("tab:")
        val existing = prefs.drawerTabs.firstOrNull { it.id == customId }?.apps ?: emptyList()
        AddAppsToTabDialog(
            prefs = prefs,
            apps = sortedApps,
            existing = existing,
            onConfirm = { selected ->
                vm.setTabApps(customId, existing + selected)
                addAppsToTab = null
            },
            onDismiss = { addAppsToTab = null }
        )
    }
}

@Composable
private fun TabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit
) {
    // مرجع غير مراقِب: تحديثه أثناء الـlayout لا يسبب أي إعادة رسم
    val posRef = remember { OffsetRef() }
    Box(
        modifier = Modifier
            .onGloballyPositioned { posRef.value = it.positionInRoot() }
            .pointerInput(label) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { local -> onLongClick(posRef.value + local) }
                )
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                else Color.White.copy(alpha = 0.08f)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.8f),
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun AddAppTile(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.tab_add_app),
                tint = Color.White.copy(alpha = 0.85f)
            )
        }
        Text(
            text = stringResource(R.string.tab_add_app),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun DrawerAppItem(
    app: AppInfo,
    label: String,
    icons: IconStore,
    requestIcon: (AppInfo) -> Unit,
    onClick: () -> Unit,
    onLongClick: (Offset) -> Unit
) {
    val posRef = remember { OffsetRef() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { posRef.value = it.positionInRoot() }
            .pointerInput(app.key) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { local -> onLongClick(posRef.value + local) }
                )
            }
            .clip(RoundedCornerShape(14.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIconImage(app = app, icons = icons, requestIcon = requestIcon, size = 54.dp)
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
    }
}

@Composable
private fun AddAppsToTabDialog(
    prefs: LauncherPrefs,
    apps: List<AppInfo>,
    existing: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val candidates = remember(apps, existing) { apps.filter { it.key !in existing } }
    val selected = remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tab_add_app)) },
        text = {
            if (candidates.isEmpty()) {
                Text(stringResource(R.string.no_results))
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                    items(candidates, key = { it.key }) { app ->
                        val checked = selected.value.contains(app.key)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected.value = if (checked) selected.value - app.key
                                    else selected.value + app.key
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
private fun ContactRow(contact: ContactHit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.name.take(1),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Column {
            Text(text = contact.name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = contact.number,
                color = Color.White.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun WebRow(query: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = stringResource(R.string.search_web, query),
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** مرجع لقيمة Offset بدون أي مراقبة من نظام إعادة التركيب */
private class OffsetRef {
    var value: Offset = Offset.Zero
}

private fun fastScrollerItemIndex(letters: List<String>, appsByLetter: Map<String, List<AppInfo>>, target: String): Int {
    var index = 0
    for (letter in letters) {
        if (letter == target) return index
        val count = appsByLetter[letter].orEmpty().size
        if (count > 0) index += count + 1 // header row plus apps
    }
    return index
}

/** تاب "كل التطبيقات": شريط حروف أفقي فوق + شبكة التطبيقات مجمعة بالحرف */
@Composable
private fun AllAppsPage(
    prefs: LauncherPrefs,
    appsByLetter: Map<String, List<AppInfo>>,
    letters: List<String>,
    listState: LazyGridState,
    icons: IconStore,
    requestIcon: (AppInfo) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onItemLongPress: (AppInfo, Offset) -> Unit,
    scope: CoroutineScope,
    columns: Int
) {
    var selected by remember(letters) { mutableStateOf(letters.firstOrNull().orEmpty()) }
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(letters, key = { it }) { letter ->
                val on = letter == selected
                Text(
                    text = letter,
                    color = if (on) Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable {
                            selected = letter
                            val target = fastScrollerItemIndex(letters, appsByLetter, letter)
                            if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        LazyVerticalGrid(
            state = listState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            letters.forEach { letter ->
                val appsForLetter = appsByLetter[letter].orEmpty()
                if (appsForLetter.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = letter,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(appsForLetter, key = { it.key }) { app ->
                        DrawerAppItem(
                            app = app,
                            label = prefs.labelOf(app),
                            icons = icons,
                            requestIcon = requestIcon,
                            onClick = { onLaunch(app) },
                            onLongClick = { anchor -> onItemLongPress(app, anchor) }
                        )
                    }
                }
            }
        }
    }
}
