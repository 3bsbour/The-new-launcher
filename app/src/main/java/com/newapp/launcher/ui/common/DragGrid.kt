package com.newapp.launcher.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newapp.launcher.data.AppInfo
import com.newapp.launcher.data.LauncherPrefs
import com.newapp.launcher.ui.IconStore
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * شبكة أيقونات فيها:
 * - نقرة = فتح (تطبيق/فولدر)
 * - ضغط مطوّل = قائمة الخيارات عند مكان الضغط
 * - ضغط مطوّل + تحريك = إعادة ترتيب بالسحب والإفلات
 *
 * @param cells خلايا ثابتة الحجم، الفاضي = null، وكل عنصر بيحمل رقمه في القائمة الكاملة
 * @param indexOffset رقم أول خانة في القائمة الكاملة (للتنقل بين الصفحات)
 */
@Composable
fun DragGrid(
    cells: List<Pair<Int, CellItem>?>,
    columns: Int,
    rows: Int,
    prefs: LauncherPrefs,
    appsByKey: Map<String, AppInfo>,
    icons: IconStore,
    requestIcon: (AppInfo) -> Unit,
    onLaunch: (CellItem) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onItemLongPress: (item: CellItem, index: Int, anchor: Offset) -> Unit,
    onEmptyLongPress: (Offset) -> Unit,
    onEmptyDoubleTap: () -> Unit = {},
    modifier: Modifier = Modifier,
    indexOffset: Int = 0,
    iconBase: Dp = 52.dp,
    labelFontSize: Int = 12,
    showLabels: Boolean = prefs.showLabels,
    outerPadding: Dp = 4.dp
) {
    val density = LocalDensity.current
    // خريطة غير مراقِبة: تُكتب في كل layout بدون إحداث أي إعادة تركيب
    val boundsMap = remember { HashMap<Int, Rect>() }
    var containerPos by remember { mutableStateOf(Offset.Zero) }
    var dragFromCell by remember { mutableStateOf(-1) }
    var dragItem by remember { mutableStateOf<CellItem?>(null) }
    var pointerInContainer by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var targetCell by remember { mutableStateOf(-1) }
    var lastEmptyTapAt by remember { mutableLongStateOf(0L) }
    var dragNotified by remember { mutableStateOf(false) }

    val iconSize = iconBase * prefs.iconScale

    fun cellAt(containerPoint: Offset): Int? {
        if (boundsMap.isEmpty()) return null
        val root = containerPos + containerPoint
        boundsMap.entries.firstOrNull { it.value.contains(root) }?.let { return it.key }
        return boundsMap.entries.minByOrNull { (it.value.center - root).getDistanceSquared() }?.key
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { containerPos = it.positionInRoot() }
            .pointerInput(columns, rows, cells) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startCell = cellAt(down.position)
                    val startEntry = startCell?.let { cells.getOrNull(it) }
                    val startItem = startEntry?.second

                    // نستنى: نقرة، أو تمرير، أو ضغط مطوّل
                    val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var result: String? = null
                        while (result == null) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            result = when {
                                change == null || !change.pressed -> "up"
                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> "move"
                                else -> null
                            }
                        }
                        result
                    }

                    if (outcome == "up") {
                        val moved = (pointerInContainer - down.position).getDistance()
                        // لو فيه سحب سابق، متفتحش التطبيق
                        if (!dragNotified) {
                            if (startItem != null && moved < 10f) {
                                onLaunch(startItem)
                            } else if (startItem == null && moved < 10f) {
                                // نقرتين على مساحة فاضية = فعل مخصص (مثلاً قفل الشاشة)
                                val now = System.currentTimeMillis()
                                val delta = now - lastEmptyTapAt
                                if (delta in 1..viewConfiguration.doubleTapTimeoutMillis) {
                                    lastEmptyTapAt = 0L
                                    onEmptyDoubleTap()
                                } else {
                                    lastEmptyTapAt = now
                                }
                            }
                        }
                        dragNotified = false
                        return@awaitEachGesture
                    }
                    if (outcome == "move") return@awaitEachGesture

                    val startBounds = startCell?.let { boundsMap[it] }
                    if (startItem == null || startCell == null || startEntry == null || startBounds == null) {
                        onEmptyLongPress(containerPos + down.position)
                        return@awaitEachGesture
                    }

                    dragFromCell = startCell
                    dragItem = startItem
                    targetCell = startCell
                    pointerInContainer = down.position
                    grabOffset = down.position - (startBounds.topLeft - containerPos)

                    val anchor = containerPos + down.position

                    var moved = 0f
                    var canceled = false
                    dragNotified = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            canceled = true
                            break
                        }
                        change.consume()
                        if (!change.pressed) break
                        moved += (change.position - change.previousPosition).getDistance()
                        pointerInContainer = change.position
                        cellAt(change.position)?.let { targetCell = it }
                    }

                    val from = dragFromCell
                    val to = targetCell
                    if (!canceled) {
                        if (moved < 16f) {
                            onItemLongPress(startItem, startEntry.first, anchor)
                        } else if (from >= 0 && to >= 0) {
                            val fromGlobal = cells.getOrNull(from)?.first ?: -1
                            if (fromGlobal >= 0) onMove(fromGlobal, indexOffset + to)
                        }
                    }
                    dragItem = null
                    dragFromCell = -1
                    targetCell = -1
                    dragNotified = false
                }
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(outerPadding),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            repeat(rows) { row ->
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    repeat(columns) { col ->
                        val cellIndex = row * columns + col
                        val entry = cells.getOrNull(cellIndex)
                        val visible = entry != null && dragItem?.key != entry.second.key
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onGloballyPositioned { boundsMap[cellIndex] = it.boundsInRoot() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (visible && entry != null) {
                                val item = entry.second
                                CellContent(
                                    item = item,
                                    prefs = prefs,
                                    appsByKey = appsByKey,
                                    icons = icons,
                                    requestIcon = requestIcon,
                                    iconSize = iconSize,
                                    showLabels = showLabels,
                                    labelFontSize = labelFontSize,
                                    modifier = if (item is CellItem.WidgetItem) {
                                        Modifier.fillMaxSize().padding(4.dp)
                                    } else {
                                        Modifier
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // الخانة الهدف
        val dragging = dragItem
        if (dragging != null && targetCell >= 0 && targetCell != dragFromCell) {
            boundsMap[targetCell]?.let { rect ->
                Box(
                    Modifier
                        .offset {
                            IntOffset(
                                (rect.left - containerPos.x).roundToInt(),
                                (rect.top - containerPos.y).roundToInt()
                            )
                        }
                        .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                        .padding(8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.20f))
                )
            }
        }

        // العنصر المسحوب
        if (dragging != null && dragFromCell >= 0) {
            boundsMap[dragFromCell]?.let { rect ->
                val topLeft = pointerInContainer - grabOffset
                Box(
                    Modifier
                        .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                        .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() }),
                    contentAlignment = Alignment.Center
                ) {
                    CellContent(
                        item = dragging,
                        prefs = prefs,
                        appsByKey = appsByKey,
                        icons = icons,
                        requestIcon = requestIcon,
                        iconSize = iconSize * 1.1f,
                        showLabels = showLabels,
                        labelFontSize = labelFontSize,
                        modifier = if (dragging is CellItem.WidgetItem) {
                            Modifier.fillMaxSize().padding(4.dp)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CellContent(
    item: CellItem,
    prefs: LauncherPrefs,
    appsByKey: Map<String, AppInfo>,
    icons: IconStore,
    requestIcon: (AppInfo) -> Unit,
    iconSize: Dp,
    showLabels: Boolean,
    labelFontSize: Int,
    modifier: Modifier = Modifier
) {
    when (item) {
        is CellItem.WidgetItem -> WidgetCard(item.spec, modifier)
        is CellItem.AppItem -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIconImage(item.app, icons, requestIcon, iconSize)
            if (showLabels) LabelText(prefs.labelOf(item.app), labelFontSize)
        }
        is CellItem.FolderItem -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FolderCellIcon(item.folder, appsByKey, icons, requestIcon, iconSize)
            if (showLabels) LabelText(item.folder.name, labelFontSize)
        }
    }
}

@Composable
private fun LabelText(text: String, fontSize: Int) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontSize = fontSize.sp,
        color = Color.White,
        style = TextStyle(
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.85f),
                offset = Offset(0f, 1f),
                blurRadius = 3f
            )
        ),
        modifier = Modifier.padding(top = 5.dp, start = 3.dp, end = 3.dp)
    )
}
