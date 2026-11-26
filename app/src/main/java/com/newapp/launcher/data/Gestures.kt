package com.newapp.launcher.data

/** الإيماءات المتاحة والأفعال اللي ممكن تتربط بيها */
object Gestures {

    // الأفعال
    const val NONE = "none"
    const val DRAWER = "drawer"
    const val SEARCH = "search"
    const val CAMERA = "camera"
    const val SETTINGS = "settings"
    const val NEWS = "news"
    const val NOTIFICATIONS = "notifications"

    // الإيماءات
    const val SWIPE_UP = "swipe_up"
    const val SWIPE_DOWN = "swipe_down"
    const val SWIPE_UP_2 = "swipe_up_2"
    const val SWIPE_DOWN_2 = "swipe_down_2"
    const val SWIPE_LEFT_2 = "swipe_left_2"
    const val SWIPE_RIGHT_2 = "swipe_right_2"
    const val PINCH_IN = "pinch_in"
    const val PINCH_OUT = "pinch_out"

    /** كل الإيماءات بالترتيب المعروض في الإعدادات */
    val ids: List<String> = listOf(
        SWIPE_UP,
        SWIPE_DOWN,
        SWIPE_UP_2,
        SWIPE_DOWN_2,
        SWIPE_LEFT_2,
        SWIPE_RIGHT_2,
        PINCH_IN,
        PINCH_OUT
    )

    /** كل الأفعال الممكنة */
    val actions: List<String> = listOf(NONE, DRAWER, SEARCH, CAMERA, SETTINGS, NEWS, NOTIFICATIONS)
}
