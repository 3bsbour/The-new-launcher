package com.newapp.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * خدمة إمكانية الوصول: بتسمح بفتح درج الإشعارات وقفل الشاشة من أي مكان.
 * الخدمة مش بتقرأ محتوى الشاشة ولا بتتعامل مع أي تطبيق تاني — بتستخدم
 * أوامر النظام العامة (GLOBAL_ACTION_*) بس.
 */
class LauncherAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun lockScreen() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    fun openNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    companion object {
        @Volatile
        var instance: LauncherAccessibilityService? = null
            private set

        /** هل الخدمة مفعّلة حاليًا؟ */
        fun isEnabled(): Boolean = instance != null

        /** يبني Intent لإعدادات إمكانية الوصول في النظام */
        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** هل الخدمة مفعّلة حسب إعدادات النظام (بدون الاعتماد على الاتصال الحالي)؟ */
        fun isEnabledInSettings(context: Context): Boolean {
            val expected = "${context.packageName}/${LauncherAccessibilityService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
