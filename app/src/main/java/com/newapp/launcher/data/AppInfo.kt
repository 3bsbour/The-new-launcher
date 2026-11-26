package com.newapp.launcher.data

import androidx.compose.runtime.Immutable

/** تطبيق مثبّت على الجهاز وله أيقونة في اللانشر */
@Immutable
data class AppInfo(
    val key: String,
    val packageName: String,
    val activityName: String,
    val label: String
) {
    companion object {
        fun keyOf(packageName: String, activityName: String) = "$packageName/$activityName"
    }
}

/** اختصار تطبيق (App Shortcut) */
@Immutable
data class AppShortcut(
    val id: String,
    val packageName: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap?
)

/** نتيجة بحث في جهات الاتصال */
@Immutable
data class ContactHit(val name: String, val number: String)
