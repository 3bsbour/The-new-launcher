package com.newapp.launcher.data

/** ترتيب الحروف وتصنيف التطبيقات حسب أول حرف في الاسم */
object Alphabet {

    /** ترتيب الحروف العربية (الهجائي) */
    private const val ARABIC_ORDER = "ابتثجحخدذرزسشصضطظعغفقكلمنهوي"

    /** حرف تجميعي لما لا يبدأ بحرف (أرقام/رموز) */
    const val OTHER = "#"

    /**
     * يرجّع حرف التصنيف: أي شكل من أشكال الحرف يُرجع لأصله
     * (أ إ آ ← ا) و(چ ← ج) و(ة ← ه) وهكذا.
     */
    fun keyOf(label: String): String {
        var text = label.trim()
        // تجاهل «ال» التعريف في بداية الاسم
        if (text.length > 2 && text.startsWith("ال")) text = text.substring(2)
        val ch = text.firstOrNull { it.isLetter() } ?: return OTHER
        return normalize(ch)
    }

    /** يحوّل الحرف لشكله الأساسي */
    fun normalize(ch: Char): String = when (ch) {
        'أ', 'إ', 'آ', 'ٱ', 'ٲ', 'ٳ', 'ٵ' -> "ا"
        'ة' -> "ه"
        'ى', 'ئ', 'ی' -> "ي"
        'ؤ' -> "و"
        'چ' -> "ج"
        'پ' -> "ب"
        'ڤ' -> "ف"
        'گ', 'ک' -> "ك"
        'ھ' -> "ه"
        'ڭ' -> "ك"
        else -> {
            if (ch in 'a'..'z' || ch in 'A'..'Z') ch.uppercaseChar().toString()
            else if (isArabic(ch)) ch.toString()
            else OTHER
        }
    }

    fun isArabic(ch: Char): Boolean {
        val c = ch.code
        return c in 0x0600..0x06FF || c in 0x0750..0x077F || c in 0xFB50..0xFEFF
    }

    fun isArabicKey(key: String): Boolean = key.length == 1 && isArabic(key[0])

    /** رتبة الحرف العربي حسب الترتيب الهجائي */
    fun arabicRank(key: String): Int {
        val index = ARABIC_ORDER.indexOf(key)
        return if (index >= 0) index else ARABIC_ORDER.length
    }

    /**
     * يرتب مفاتيح الحروف: حسب اللغة الأولى (عربي أو إنجليزي),
     * والأرقام/الرموز في الآخر.
     */
    fun sortKeys(keys: Collection<String>, arabicFirst: Boolean): List<String> {
        val arabic = keys.filter { isArabicKey(it) }.distinct().sortedWith(
            compareBy({ arabicRank(it) }, { it })
        )
        val latin = keys.filter { it.length == 1 && it[0] in 'A'..'Z' }.distinct().sorted()
        val other = keys.filter { it == OTHER }.distinct()
        return (if (arabicFirst) arabic + latin else latin + arabic) + other
    }
}
