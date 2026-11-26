package com.newapp.launcher.ui.common

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newapp.launcher.R
import com.newapp.launcher.data.WidgetSpec
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** رسم الويدجت المدمج داخل خلية واحدة على الشاشة الرئيسية */
@Composable
fun WidgetCard(spec: WidgetSpec, modifier: Modifier = Modifier) {
    val scale = when (spec.spanX) {
        1 -> 0.82f
        3 -> 1.2f
        else -> 1f
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(spec.color).copy(alpha = spec.alpha)),
        contentAlignment = Alignment.Center
    ) {
        when (spec.type) {
            "battery" -> BatteryWidget(scale)
            "calendar" -> CalendarWidget(scale)
            "weather" -> WeatherWidget(scale)
            else -> ClockWidget(scale)
        }
    }
}

@Composable
private fun ClockWidget(scale: Float) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    // نحدّث مرة كل دقيقة (الوقت معروض بالساعات والدقائق) بدل كل ثانية
    LaunchedEffect(Unit) {
        while (true) {
            val current = LocalDateTime.now()
            delay((60_000L - current.second * 1000L - current.nano / 1_000_000L).coerceAtLeast(1_000L))
            now = LocalDateTime.now()
        }
    }
    val time = remember(now.hour, now.minute) { "%02d:%02d".format(now.hour, now.minute) }
    val date = remember(now.toLocalDate()) {
        now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = time,
            color = Color.White,
            fontSize = (22 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = date,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = (10 * scale).sp,
            maxLines = 1
        )
    }
}

@Composable
private fun BatteryWidget(scale: Float) {
    val level = rememberBatteryLevel()
    val accent = when {
        level <= 20 -> Color(0xFFE57373)
        level <= 50 -> Color(0xFFFFB74D)
        else -> Color(0xFF81C784)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$level%",
            color = Color.White,
            fontSize = (20 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = stringResource(R.string.widget_battery),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = (9 * scale).sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp)
        )
        Box(
            Modifier
                .padding(top = 6.dp)
                .width((56 * scale).dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(level / 100f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent)
            )
        }
    }
}

@Composable
private fun CalendarWidget(scale: Float) {
    val today = LocalDate.now()
    val weekday = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
    }
    val month = remember(today) {
        today.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = today.dayOfMonth.toString(),
            color = Color.White,
            fontSize = (24 * scale).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = "$weekday · $month",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = (9 * scale).sp,
            maxLines = 1
        )
    }
}

@Composable
private fun WeatherWidget(scale: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size((30 * scale).dp)) {
            val c = center
            val r = size.minDimension * 0.26f
            drawCircle(color = Color(0xFFFFC107), radius = r, center = c)
            val rayStart = r * 1.35f
            val rayEnd = r * 1.9f
            repeat(8) { i ->
                val angle = Math.toRadians(i * 45.0)
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                drawLine(
                    color = Color(0xFFFFC107),
                    start = androidx.compose.ui.geometry.Offset(c.x + dx * rayStart, c.y + dy * rayStart),
                    end = androidx.compose.ui.geometry.Offset(c.x + dx * rayEnd, c.y + dy * rayEnd),
                    strokeWidth = size.minDimension * 0.07f,
                    cap = StrokeCap.Round
                )
            }
        }
        Text(
            text = stringResource(R.string.widget_weather),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = (10 * scale).sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun rememberBatteryLevel(): Int {
    val context = LocalContext.current
    var level by remember { mutableStateOf(readBatteryLevel(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                level = readBatteryLevel(context)
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return level
}

private fun readBatteryLevel(context: Context): Int {
    val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    return manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 0
}
