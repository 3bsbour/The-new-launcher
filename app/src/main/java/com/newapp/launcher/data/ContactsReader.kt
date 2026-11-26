package com.newapp.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** بحث بسيط في جهات الاتصال (اسم + رقم) */
class ContactsReader(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun search(query: String, limit: Int = 8): List<ContactHit> = withContext(Dispatchers.IO) {
        if (query.isBlank() || !hasPermission()) return@withContext emptyList()
        val out = ArrayList<ContactHit>()
        runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
                Uri.encode(query)
            )
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext() && out.size < limit) {
                    val name = cursor.getString(0) ?: continue
                    val number = (cursor.getString(1) ?: continue).replace(" ", "")
                    out.add(ContactHit(name, number))
                }
            }
        }
        out
    }
}
