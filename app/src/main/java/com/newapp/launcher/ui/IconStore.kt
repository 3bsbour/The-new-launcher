package com.newapp.launcher.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * مخزن أيقونات التطبيقات.
 *
 * الفكرة: لكل مفتاح حالة مراقَبة مستقلة، فقراءة أيقونة معيّنة (get) بتتابع
 * حالة المفتاح ده لوحده. أي تحميل/تبديل لأيقونة تانية **مش** بيعمل إعادة رسم
 * لبقية الأيقونات. مقارنة بـ SnapshotStateMap اللي بتلغي كل القرّاء عند أي تعديل.
 *
 * كل الوصول من الـMain thread (الـcomposition + الـviewModelScope)، فمفيش تزامن.
 */
@Stable
class IconStore {

    private val states = HashMap<String, MutableState<ImageBitmap?>>()
    private val order = ArrayDeque<String>()

    /** لازم ننشئ الحالة حتى لو الأيقونة لسه مش محمّلة، عشان القراءة تتسجل ونعيد الرسم لما توصل */
    private fun stateOf(key: String): MutableState<ImageBitmap?> =
        states.getOrPut(key) { mutableStateOf(null) }

    operator fun get(key: String): ImageBitmap? = stateOf(key).value

    fun contains(key: String): Boolean = states[key]?.value != null

    fun put(key: String, bitmap: ImageBitmap) {
        val state = stateOf(key)
        if (state.value == null) {
            order.addLast(key)
            while (order.size > MAX_ENTRIES) {
                val oldest = order.removeFirst()
                if (oldest != key) states[oldest]?.value = null
            }
        }
        state.value = bitmap
    }

    /** نسيب الحالات مكانها (null) بدل الحذف، عشان القرّاء يفضلوا متسجلين */
    fun retainAll(keys: Set<String>) {
        order.retainAll(keys)
        states.forEach { (key, state) -> if (key !in keys) state.value = null }
    }

    companion object {
        /** أقصى عدد أيقونات محمّلة في الذاكرة */
        const val MAX_ENTRIES = 360
    }
}
