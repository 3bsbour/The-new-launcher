package com.newapp.launcher

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.newapp.launcher.data.PrefsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class LauncherApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            PrefsStore(this@LauncherApplication).state
                .distinctUntilChanged { old, new -> old.locale == new.locale }
                .collect { prefs ->
                    val tags = prefs.locale.orEmpty()
                    val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                    if (current != tags) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
                    }
                }
        }
    }
}
