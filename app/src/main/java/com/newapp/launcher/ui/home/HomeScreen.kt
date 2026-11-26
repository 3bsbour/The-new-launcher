package com.newapp.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newapp.launcher.R
import com.newapp.launcher.data.AppInfo
import com.newapp.launcher.data.Folder
import com.newapp.launcher.data.HomeEntry
import com.newapp.launcher.data.LauncherPrefs
import com.newapp.launcher.ui.IconStore
import com.newapp.launcher.ui.LauncherViewModel
import com.newapp.launcher.ui.common.CellItem
import com.newapp.launcher.ui.common.DragGrid
import com.newapp.launcher.ui.common.PageIndicator
import com.newapp.launcher.ui.common.Slot

@Composable
fun HomeScreen(
    prefs: LauncherPrefs,
    appsByKey: Map<String, AppInfo>,
    icons: IconStore,
    requestIcon: (AppInfo) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onOpenFolder: (Folder) -> Unit,
    onMoveHome: (Int, Int) -> Unit,
    onMoveDock: (Int, Int) -> Unit,
    onItemLongPress: (CellItem, Int, Slot, Offset) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onEmptyLongPress: (Offset) -> Unit,
    onEmptyDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
    gestureModifier: Modifier = Modifier
) {
    val entries = remember(prefs.home, prefs.folders, prefs.widgets, appsByKey, prefs.hidden) {
        prefs.home.mapIndexedNotNull { index, raw ->
            cellOf(raw, prefs, appsByKey)?.let { index to it }
        }
    }
    val pageSize = (prefs.columns * prefs.rows).coerceAtLeast(1)
    val homePages = ((entries.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    val totalPages = homePages + if (prefs.showNewsPage) 1 else 0
    val pagerState = rememberPagerState(pageCount = { totalPages })

    // نفتح على الصفحة الافتراضية التي اختارها المستخدم (مرة واحدة بعد تحميل الإعدادات)
    var appliedDefault by remember { mutableStateOf(false) }
    LaunchedEffect(prefs.defaultPage, homePages, prefs.initialized) {
        if (!appliedDefault && prefs.initialized) {
            val target = prefs.defaultPage.coerceIn(0, homePages - 1)
            if (target != pagerState.currentPage) pagerState.scrollToPage(target)
            appliedDefault = true
        }
    }

    val dockCells = remember(prefs.dock, appsByKey, prefs.hidden) {
        List(LauncherViewModel.DOCK_SIZE) { i ->
            prefs.dock.getOrNull(i)
                ?.let { key -> appsByKey[key]?.takeIf { it.key !in prefs.hidden } }
                ?.let { i to CellItem.AppItem(it) }
        }
    }

    Column(modifier = modifier.fillMaxSize().then(gestureModifier)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                if (page >= homePages) {
                    NewsPage()
                } else {
                    val cells = remember(entries, page, pageSize) {
                        List(pageSize) { i -> entries.getOrNull(page * pageSize + i) }
                    }
                    DragGrid(
                        cells = cells,
                        columns = prefs.columns,
                        rows = prefs.rows,
                        prefs = prefs,
                        appsByKey = appsByKey,
                        icons = icons,
                        requestIcon = requestIcon,
                        onLaunch = { item ->
                            when (item) {
                                is CellItem.AppItem -> onLaunchApp(item.app)
                                is CellItem.FolderItem -> onOpenFolder(item.folder)
                                is CellItem.WidgetItem -> Unit
                            }
                        },
                        onMove = onMoveHome,
                        onItemLongPress = { item, index, anchor ->
                            onItemLongPress(item, index, Slot.HOME, anchor)
                        },
                        onEmptyLongPress = onEmptyLongPress,
                        onEmptyDoubleTap = onEmptyDoubleTap,
                        indexOffset = page * pageSize,
                        outerPadding = 6.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            if (entries.isEmpty() && !prefs.showNewsPage) {
                Button(
                    onClick = onOpenDrawer,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(stringResource(R.string.open_drawer))
                }
            }
        }

        PageIndicator(
            count = totalPages,
            current = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp)
        )

        SearchPill(onClick = onOpenSearch)

        DragGrid(
            cells = dockCells,
            columns = LauncherViewModel.DOCK_SIZE,
            rows = 1,
            prefs = prefs,
            appsByKey = appsByKey,
            icons = icons,
            requestIcon = requestIcon,
            onLaunch = { item -> if (item is CellItem.AppItem) onLaunchApp(item.app) },
            onMove = onMoveDock,
            onItemLongPress = { item, index, anchor ->
                onItemLongPress(item, index, Slot.DOCK, anchor)
            },
            onEmptyLongPress = onEmptyLongPress,
            onEmptyDoubleTap = onEmptyDoubleTap,
            indexOffset = 0,
            iconBase = 48.dp,
            showLabels = false,
            outerPadding = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .padding(bottom = 12.dp)
        )
    }
}

/** يحوّل مفتاح العنصر المحفوظ إلى عنصر قابل للعرض */
private fun cellOf(raw: String, prefs: LauncherPrefs, appsByKey: Map<String, AppInfo>): CellItem? =
    when (val entry = HomeEntry.parse(raw)) {
        is HomeEntry.App ->
            appsByKey[entry.key]?.takeIf { it.key !in prefs.hidden }?.let { CellItem.AppItem(it) }
        is HomeEntry.FolderEntry ->
            prefs.folderOf(entry.folderId)?.let { CellItem.FolderItem(it) }
        is HomeEntry.Widget ->
            prefs.widgetOf(entry.widgetId)?.let { CellItem.WidgetItem(it) }
    }

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = stringResource(R.string.search_hint),
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** خلفية اللانشر: صورة المستخدم أو خلفية النظام مع تعتيم اختياري */
@Composable
fun WallpaperLayer(bitmap: ImageBitmap?, dimPercent: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color(0xFF0B0E13))) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (dimPercent > 0) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimPercent / 100f))
            )
        }
    }
}

@Composable
fun CircleDot(modifier: Modifier = Modifier) {
    Box(modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
}
