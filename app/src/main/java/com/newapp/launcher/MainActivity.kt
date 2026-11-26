package com.newapp.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newapp.launcher.ui.LauncherRoot
import com.newapp.launcher.ui.LauncherViewModel
import com.newapp.launcher.ui.theme.LauncherTheme
import com.newapp.launcher.ui.theme.ProvideAppLocale
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * شاشة الهوم الأساسية. أي ضغطة على زر الهوم وهي مفتوحة تزوّد العدّاد،
 * واللي بدوره يقفل الدرج/القوائم ويرجّع المستخدم لأول صفحة.
 */
class MainActivity : ComponentActivity() {

    private var homePresses by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LauncherTheme {
                val vm: LauncherViewModel = viewModel()
                val prefs by vm.prefs.collectAsStateWithLifecycle()
                ProvideAppLocale(prefs.locale) {
                    LauncherRoot(vm = vm, homePresses = homePresses)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.categories?.contains(Intent.CATEGORY_HOME) == true) {
            homePresses++
        }
    }
}
