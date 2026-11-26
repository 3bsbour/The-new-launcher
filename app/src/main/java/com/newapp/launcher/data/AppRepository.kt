package com.newapp.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/** كل التعامل مع تطبيقات النظام: الجلب، الأيقونات، الفتح، إلغاء التثبيت */
class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        resolved.mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            if (ai.packageName == context.packageName) return@mapNotNull null
            val label = runCatching { ai.loadLabel(pm).toString() }.getOrNull().orEmpty().ifBlank { ai.packageName }
            AppInfo(AppInfo.keyOf(ai.packageName, ai.name), ai.packageName, ai.name, label)
        }.distinctBy { it.key }
            .sortedWith(Comparator { a, b -> collator.compare(a.label, b.label) })
    }

    suspend fun iconOf(app: AppInfo): ImageBitmap? = withContext(Dispatchers.IO) {
        val drawable: Drawable? = runCatching {
            pm.getActivityIcon(ComponentName(app.packageName, app.activityName))
        }.getOrNull() ?: runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
        drawable?.toIconBitmap(ICON_PX)
    }

    fun launch(app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { context.startActivity(intent) }
    }

    fun openAppInfo(app: AppInfo) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** اختصارات التطبيق (زي «الكاميرا» في تطبيق الكاميرا). بتشتغل لما اللانشر يبقى هو الافتراضي */
    fun shortcutsOf(app: AppInfo): List<AppShortcut> {
        val la = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        return runCatching {
            if (!la.hasShortcutHostPermission()) return emptyList()
            val query = LauncherApps.ShortcutQuery()
                .setPackage(app.packageName)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                )
            la.getShortcuts(query, Process.myUserHandle()).orEmpty()
                .filter { it.isEnabled }
                .take(4)
                .map { si ->
                    val label = (si.shortLabel ?: si.longLabel)?.toString().orEmpty()
                    val icon = runCatching {
                        la.getShortcutIconDrawable(si, 0)?.toIconBitmap(96)
                    }.getOrNull()
                    AppShortcut(si.id, si.`package`, label, icon)
                }
                .filter { it.label.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun startShortcut(shortcut: AppShortcut) {
        val la = context.getSystemService(LauncherApps::class.java) ?: return
        runCatching {
            la.startShortcut(shortcut.packageName, shortcut.id, null, null, Process.myUserHandle())
        }
    }

    fun uninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    companion object {
        const val ICON_PX = 144
    }
}

private fun Drawable.toIconBitmap(size: Int): ImageBitmap {
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    if (this is BitmapDrawable && bitmap != null) {
        canvas.drawBitmap(bitmap, null, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
    } else {
        setBounds(0, 0, size, size)
        draw(canvas)
    }
    return out.asImageBitmap()
}
