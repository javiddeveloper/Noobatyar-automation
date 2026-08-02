package xyz.sattar.javid.proqueue.core.utils

/**
 * Converts Persian and Arabic digits in a string to English digits.
 */
fun String.toEnglishDigits(): String {
    var result = this
    val persianDigits = arrayOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")
    val arabicDigits = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
    val englishDigits = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

    for (i in 0..9) {
        result = result.replace(persianDigits[i], englishDigits[i])
        result = result.replace(arabicDigits[i], englishDigits[i])
    }
    return result
}

/**
 * Converts English digits to Persian ones. For display only — never feed the
 * result back into anything that parses numbers.
 */
fun String.toPersianDigits(): String {
    val persianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    return map { ch -> if (ch in '0'..'9') persianDigits[ch - '0'] else ch }.joinToString("")
}
