package com.newapp.launcher.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newapp.launcher.R
import com.newapp.launcher.data.AppInfo
import com.newapp.launcher.ui.IconStore
import kotlin.math.roundToInt

/** مكان التطبيق: الشاشة الرئيسية، الشريط السفلي، أو الدرج */
enum class Slot { HOME, DOCK, DRAWER }

/** خيار في قائمة الضغط المطوّل */
data class ActionSpec(
    val label: String,
    val iconRes: Int = 0,
    val iconBitmap: ImageBitmap? = null,
    val onClick: () -> Unit
)

@Composable
fun AppIconImage(
    app: AppInfo,
    icons: IconStore,
    requestIcon: (AppInfo) -> Unit,
    size: Dp,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(app.key) { requestIcon(app) }
    val bitmap = icons[app.key]
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val shape = RoundedCornerShape(size * 0.24f)
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }
    }
}

/**
 * قائمة الضغط المطوّل: كارت داكن مدوّر وصغير فوق الواجهة (مش نافذة منفصلة).
 * - primary: صف الأيقونات فوق (سلة المهملات + معلومات...) ثم خط فاصل.
 * - secondary: قائمة الاختصارات/الخيارات تحت.
 * بتتحط جنب مكان الضغط ومتنزلش عن نص الشاشة تقريبًا. لمسة برّه الكارت بتقفلها.
 */
@Composable
fun ActionSheet(
    title: String,
    primary: List<ActionSpec>,
    secondary: List<ActionSpec>,
    anchor: Offset,
    onDismiss: () -> Unit,
    iconOnlyPrimary: Boolean = false
) {
    val density = LocalDensity.current
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }

    val margin = with(density) { 12.dp.toPx() }
    val below = with(density) { 44.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                boxSize = it.size
                origin = it.positionInRoot()
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
    ) {
        Column(
            modifier = Modifier
                .offset {
                    val a = anchor - origin
                    val maxX = (boxSize.width - cardSize.width - margin).coerceAtLeast(margin)
                    val x = (a.x - cardSize.width / 2f).coerceIn(margin, maxX)
                    // لا ينزل أبدًا عن نص الشاشة، ولا يخرج من أسفلها
                    val maxY = minOf(boxSize.height * 0.5f, boxSize.height - cardSize.height - margin)
                        .coerceAtLeast(margin)
                    val y = (a.y + below).coerceIn(margin, maxY)
                    IntOffset(x.roundToInt(), y.roundToInt())
                }
                .alpha(if (cardSize == IntSize.Zero) 0f else 1f)
                .onGloballyPositioned { cardSize = it.size }
                .pointerInput(Unit) { detectTapGestures { } }
                .width(220.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFA202124))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
                )
            }
            if (primary.isNotEmpty()) {
                if (iconOnlyPrimary) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        primary.forEach { action ->
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        action.onClick()
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(action.iconRes),
                                    contentDescription = action.label,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        primary.forEach { action ->
                            IconAction(action = action, onClick = {
                                action.onClick()
                                onDismiss()
                            })
                        }
                    }
                }
            }
            if (primary.isNotEmpty() && secondary.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = Color.White.copy(alpha = 0.18f)
                )
            }
            if (secondary.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    secondary.forEach { action ->
                        ListAction(action = action, onClick = {
                            action.onClick()
                            onDismiss()
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun IconAction(action: ActionSpec, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(action.iconRes),
                contentDescription = action.label,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = action.label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 9.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun ListAction(action: ActionSpec, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (action.iconBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = action.iconBitmap,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        } else if (action.iconRes != 0) {
            Icon(
                painter = painterResource(action.iconRes),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = action.label,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
fun RenameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Text(
                    text = stringResource(R.string.reset_name),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onReset() }
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                keyboard?.hide()
                onConfirm(value)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** حوار إدخال نص بسيط (لأسامي الفولدرات/التابات) */
@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    placeholder: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text(placeholder) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                keyboard?.hide()
                onConfirm(value)
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun PageIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    if (count <= 1) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(if (index == current) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (index == current) 0.95f else 0.4f))
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White.copy(alpha = 0.75f),
        textAlign = TextAlign.Start
    )
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.height(48.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
