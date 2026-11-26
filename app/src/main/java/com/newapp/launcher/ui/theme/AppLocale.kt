package com.newapp.launcher.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.text.TextUtils
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

private class LocaleContext(base: Context, private val res: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = res
}

/**
 * يطبّق لغة التطبيق فورًا على كل الواجهة (نصوص + اتجاه RTL) من غير ما نستنى تغيير لغة التليفون.
 * tag فاضي = لغة النظام.
 */
@Composable
fun ProvideAppLocale(tag: String?, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val language = tag.orEmpty()
    if (language.isBlank()) {
        content()
        return
    }
    val locale = remember(language) { Locale.forLanguageTag(language) }
    val ctx = remember(language, base, baseConfig) {
        val cfg = Configuration(baseConfig)
        cfg.setLocale(locale)
        cfg.setLayoutDirection(locale)
        LocaleContext(base, base.createConfigurationContext(cfg).resources)
    }
    val dir = if (TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL)
        LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(
        LocalContext provides ctx,
        LocalConfiguration provides ctx.resources.configuration,
        LocalLayoutDirection provides dir
    ) { content() }
}
