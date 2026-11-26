package com.newapp.launcher.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.newapp.launcher.data.AppInfo
import com.newapp.launcher.data.Folder
import com.newapp.launcher.ui.IconStore

/** أيقونة فولدر: صورة لو موجودة، وإلا معاينة مصغّرة لأول ٤ تطبيقات */
@Composable
fun FolderCellIcon(
    folder: Folder,
    appsByKey: Map<String, AppInfo>,
    icons: IconStore,
    requestIcon: (AppInfo) -> Unit,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val image = rememberUriBitmap(folder.imageUri)
    val shape = RoundedCornerShape(size * 0.24f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = folder.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
        } else {
            val previews = folder.apps.take(4).mapNotNull { appsByKey[it] }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(size * 0.10f),
                verticalArrangement = Arrangement.spacedBy(size * 0.05f)
            ) {
                repeat(2) { row ->
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(size * 0.05f)
                    ) {
                        repeat(2) { col ->
                            val app = previews.getOrNull(row * 2 + col)
                            Box(
                                Modifier.fillMaxHeight().weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                if (app != null) {
                                    AppIconImage(
                                        app = app,
                                        icons = icons,
                                        requestIcon = requestIcon,
                                        size = size * 0.34f
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
