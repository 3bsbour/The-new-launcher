package com.newapp.launcher.ui.common

import com.newapp.launcher.data.AppInfo
import com.newapp.launcher.data.Folder
import com.newapp.launcher.data.WidgetSpec

/** عنصر معروض في شبكة الشاشة الرئيسية */
sealed interface CellItem {
    val key: String

    data class AppItem(val app: AppInfo) : CellItem {
        override val key: String get() = app.key
    }

    data class FolderItem(val folder: Folder) : CellItem {
        override val key: String get() = "f:${folder.id}"
    }

    data class WidgetItem(val spec: WidgetSpec) : CellItem {
        override val key: String get() = "w:${spec.id}"
    }
}
