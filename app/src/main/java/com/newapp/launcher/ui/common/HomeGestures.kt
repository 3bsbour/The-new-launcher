package com.newapp.launcher.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.newapp.launcher.data.Gestures
import kotlin.math.abs

/**
 * يكتشف إيماءات الشاشة الرئيسية بدون ما يعطّل النقرات أو تمرير الصفحات:
 * - إصبع واحد: سحب لأعلى / لأسفل + بلاغ لحظي بالحركة (عشان العناصر تتبع الإصبع)
 * - إصبعين: سحب لأعلى/أسفل/يمين/شمال + ضم/توسيع الأصابع
 *
 * بنقرأ الأحداث في الـ Initial pass، وبنستهلك بس لما يكون فيه إصبعين
 * (عشان مانخليش الـ pager يتحرك بإصبعين).
 *
 * @param onVerticalDragStart يُنادى أول ما يبدأ سحب بإصبع واحد
 * @param onVerticalDragProgress يُنادى بكل حركة ومعاه إجمالي الإزاحة الرأسية (سالب = لأعلى)
 * @param onVerticalDragEnd يُنادى عند رفع الإصبع ومعاه إجمالي الإزاحة وسرعة الإصبع (px/ث)
 */
@Composable
fun rememberHomeGestures(
    hasAction: (String) -> Boolean,
    onGesture: (String) -> Unit,
    onVerticalDragStart: () -> Unit = {},
    onVerticalDragProgress: (Float) -> Unit = {},
    onVerticalDragEnd: (dy: Float, velocity: Float) -> Unit = { _, _ -> }
): Modifier {
    val threshold = with(LocalDensity.current) { 60.dp.toPx() }
    val has = rememberUpdatedState(hasAction)
    val act = rememberUpdatedState(onGesture)
    val dragStart = rememberUpdatedState(onVerticalDragStart)
    val dragProgress = rememberUpdatedState(onVerticalDragProgress)
    val dragEnd = rememberUpdatedState(onVerticalDragEnd)

    return Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var maxPointers = 1
            var prevCentroid = down.position
            var totalDx = 0f
            var totalDy = 0f
            var startDist = 0f
            var endDist = 0f
            var twoFingerSeen = false
            var dragNotified = false
            var prevTime = down.uptimeMillis
            var velocity = 0f
            var abandoned = false
            val longPressMs = viewConfiguration.longPressTimeoutMillis

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                // لو الإصبع ثابت لحد الضغط المطوّل، يبقى ده سحب وإفلات لأيقونة: الإيماءات متتداخلش
                if (!abandoned && !dragNotified && pressed.size == 1 &&
                    pressed[0].uptimeMillis - down.uptimeMillis >= longPressMs
                ) {
                    abandoned = true
                }
                if (abandoned) continue
                maxPointers = maxOf(maxPointers, pressed.size)

                var cx = 0f
                var cy = 0f
                pressed.forEach { cx += it.position.x; cy += it.position.y }
                val centroid = Offset(cx / pressed.size, cy / pressed.size)
                val ddy = centroid.y - prevCentroid.y
                totalDx += centroid.x - prevCentroid.x
                totalDy += ddy
                prevCentroid = centroid

                // إصبع واحد: نبلّغ بالحركة لحظيًا (الأب هو اللي يقرر يستخدمها ولا لأ)
                // بنبدأ نبلّغ بس بعد ما نتخطى الـ touch slop ونتأكد إنها حركة رأسية
                if (pressed.size == 1 && maxPointers == 1 && !twoFingerSeen) {
                    val now = pressed[0].uptimeMillis
                    val dt = (now - prevTime).coerceAtLeast(8L)
                    velocity = ddy / dt * 1000f
                    prevTime = now

                    if (dragNotified) {
                        dragProgress.value(totalDy)
                        event.changes.forEach { it.consume() } // استهلاك الحدث عشان ما يوصلش للأطفال كـ tap
                    } else if (abs(totalDy) > viewConfiguration.touchSlop &&
                        abs(totalDy) > abs(totalDx)
                    ) {
                        dragNotified = true
                        dragStart.value()
                        dragProgress.value(totalDy)
                        event.changes.forEach { it.consume() }
                    }
                }

                if (pressed.size >= 2) {
                    val dist = (pressed[0].position - pressed[1].position).getDistance()
                    if (!twoFingerSeen) {
                        startDist = dist
                        twoFingerSeen = true
                    }
                    endDist = dist
                    event.changes.forEach { it.consume() }
                }
            }

            if (abandoned) return@awaitEachGesture
            if (dragNotified) dragEnd.value(totalDy, velocity)

            if (maxPointers >= 2) {
                val pinch = endDist - startDist
                val move = maxOf(abs(totalDx), abs(totalDy))
                val id = when {
                    abs(pinch) > threshold && abs(pinch) > move ->
                        if (pinch < 0) Gestures.PINCH_IN else Gestures.PINCH_OUT
                    move > threshold -> {
                        if (abs(totalDx) > abs(totalDy)) {
                            if (totalDx > 0) Gestures.SWIPE_RIGHT_2 else Gestures.SWIPE_LEFT_2
                        } else {
                            if (totalDy > 0) Gestures.SWIPE_DOWN_2 else Gestures.SWIPE_UP_2
                        }
                    }
                    else -> null
                }
                if (id != null && has.value(id)) act.value(id)
            } else {
                if (abs(totalDy) > threshold && abs(totalDy) > abs(totalDx)) {
                    val id = if (totalDy < 0) Gestures.SWIPE_UP else Gestures.SWIPE_DOWN
                    if (has.value(id)) act.value(id)
                }
            }
        }
    }
}
