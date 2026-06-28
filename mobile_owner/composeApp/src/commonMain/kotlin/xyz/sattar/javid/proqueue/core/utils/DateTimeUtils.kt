package xyz.sattar.javid.proqueue.core.utils

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
object DateTimeUtils {

    @OptIn(ExperimentalTime::class)
    fun endOfTodayOfMonthMillis(timeZone: TimeZone = TimeZone.currentSystemDefault()): Long {
        val now = Clock.System.now()
        val todayOfMonth = now.toLocalDateTime(timeZone).date
        val tomorrowStart = todayOfMonth.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        return tomorrowStart.toEpochMilliseconds() - 1
    }

    fun getNextDays(count: Int): List<Long> {
        // Return days of current month
        val now = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(timeZone)
        
        val jalaliToday = gregorianToJalali(today.year, today.monthNumber, today.dayOfMonth)
        val daysInMonth = if (jalaliToday.month <= 6) 31 else if (jalaliToday.month < 12) 30 else 29 // Leap year check needed for Esfand but 29 is safe minimum
        
        val days = mutableListOf<Long>()
        
        // Find start of month in Gregorian
        val startOfMonthGregorian = jalaliToGregorian(jalaliToday.year, jalaliToday.month, 1)
        val startInstant = Instant.fromEpochMilliseconds(startOfMonthGregorian)
        
        for (i in 0 until daysInMonth) {
            days.add(startInstant.plus(i, DateTimeUnit.DAY, timeZone).toEpochMilliseconds())
        }
        
        return days
    }

    @OptIn(ExperimentalTime::class)
    fun systemCurrentMilliseconds(): Long =
        Clock.System.now().toEpochMilliseconds()


    @OptIn(ExperimentalTime::class)
    fun formatMillisDateOnly(millis: Long): String {
        val dateTime =
            Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())

        val dayOfMonth = dateTime.dayOfMonth.toString().padStart(2, '0')
        val month = dateTime.month.name.lowercase()
            .replaceFirstChar { it.titlecase() } // "January"
            .take(3) // → "Jan"
        val year = dateTime.year

        return "$dayOfMonth-$month-$year"
    }

    @OptIn(ExperimentalTime::class)
    fun formatTimeNow(): String {
        val currentMillis: Long = Clock.System.now().toEpochMilliseconds()
        val dateTime = Instant.fromEpochMilliseconds(currentMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = dateTime.hour.toString().padStart(2, '0')
        val minute = dateTime.minute.toString().padStart(2, '0')
        return "$hour:$minute" // 00:00 (24h)
    }

    @OptIn(ExperimentalTime::class)
    fun formatMillisWithTimeNow(): String {
        val currentMillis: Long = Clock.System.now().toEpochMilliseconds()
        val dateTime = Instant.fromEpochMilliseconds(currentMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        val hour = if (dateTime.hour % 12 == 0) 12 else dateTime.hour % 12
        val minute = dateTime.minute.toString().padStart(2, '0')
        val amPm = if (dateTime.hour < 12) "AM" else "PM"

        val dayOfMonth = dateTime.dayOfMonth.toString().padStart(2, '0')
        val month = dateTime.month.name.lowercase()
            .replaceFirstChar { it.titlecase() } // "January"
            .take(3) // → "Jan"
        val year = dateTime.year

        return "$hour:$minute $amPm $dayOfMonth-$month-$year" // 00:00 AM 00-Jan-0000
    }

    fun calculateWaitingTime(appointmentDate: Long): String {
        val now = systemCurrentMilliseconds()
        val diff = appointmentDate - now
        if (diff <= 0) return "زمان نوبت فرا رسیده"

        val duration = diff.milliseconds
        val hours = duration.inWholeHours
        val minutes = duration.inWholeMinutes % 60

        if (hours == 0L && minutes == 0L) {
            return "کمتر از یک دقیقه تا نوبت"
        }

        val hoursPart = if (hours > 0) "$hours ساعت" else ""
        val minutesPart = if (minutes > 0) "$minutes دقیقه" else ""
        val separator = if (hours > 0 && minutes > 0) " و " else ""
        
        return "$hoursPart$separator$minutesPart زمان انتظار"
    }

    fun calculateWaitingOrOverdueText(
        appointmentDate: Long,
        serviceDurationMinutes: Int,
        status: String
    ): String {
        val endTime = appointmentDate + serviceDurationMinutes * 60 * 1000L
        val overdue = systemCurrentMilliseconds() > endTime && status == "WAITING"
        return if (overdue) "زمان رد شده" else calculateWaitingTime(appointmentDate)
    }

    fun formatDate(timestamp: Long): String {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

        val persianDate = gregorianToJalali(
            localDateTime.year,
            localDateTime.monthNumber,
            localDateTime.dayOfMonth
        )

        return "${persianDate.year}/${
            persianDate.month.toString().padStart(2, '0')
        }/${persianDate.dayOfMonth.toString().padStart(2, '0')}"
    }

    fun formatDateTime(timestamp: Long): String {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

        val persianDate = gregorianToJalali(
            localDateTime.year,
            localDateTime.monthNumber,
            localDateTime.dayOfMonth
        )

        val hour = localDateTime.hour.toString().padStart(2, '0')
        val minute = localDateTime.minute.toString().padStart(2, '0')

        return "$hour:$minute  ${persianDate.year}/${
            persianDate.month.toString().padStart(2, '0')
        }/${persianDate.dayOfMonth.toString().padStart(2, '0')}"
    }

    fun formatTime(timestamp: Long): String {
        val instant = Instant.fromEpochMilliseconds(timestamp)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = localDateTime.hour.toString().padStart(2, '0')
        val minute = localDateTime.minute.toString().padStart(2, '0')
        return "$hour:$minute"
    }

    fun getDayOfWeekName(millis: Long): String {
        val instant = Instant.fromEpochMilliseconds(millis)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return when (localDateTime.dayOfWeek.isoDayNumber) {
            1 -> "دوشنبه"
            2 -> "سه‌شنبه"
            3 -> "چهارشنبه"
            4 -> "پنج‌شنبه"
            5 -> "جمعه"
            6 -> "شنبه"
            7 -> "یکشنبه"
            else -> ""
        }
    }

    data class PersianDate(val year: Int, val month: Int, val dayOfMonth: Int)

    fun getJalaliDate(millis: Long): String {
        val instant = Instant.fromEpochMilliseconds(millis)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val persianDate = gregorianToJalali(
            localDateTime.year,
            localDateTime.monthNumber,
            localDateTime.dayOfMonth
        )
        return "${persianDate.year}/${persianDate.month.toString().padStart(2, '0')}/${
            persianDate.dayOfMonth.toString().padStart(2, '0')
        }"
    }

    fun getJalaliDateParts(millis: Long): PersianDate {
        val instant = Instant.fromEpochMilliseconds(millis)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return gregorianToJalali(
            localDateTime.year,
            localDateTime.monthNumber,
            localDateTime.dayOfMonth
        )
    }

    fun getJalaliTime(millis: Long): String {
        val instant = Instant.fromEpochMilliseconds(millis)
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = localDateTime.hour.toString().padStart(2, '0')
        val minute = localDateTime.minute.toString().padStart(2, '0')
        return "$hour:$minute"
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Long {
        var jy = jy - 979
        var j_days = (jy * 365) + (jy / 33) * 8 + ((jy % 33) + 3) / 4
        for (i in 0 until jm - 1) {
            j_days += if (i < 6) 31 else 30
        }
        j_days += jd - 1

        var g_days = j_days + 79
        var gy = 1600 + 400 * (g_days / 146097)
        g_days %= 146097

        var leap = true
        if (g_days >= 36525) {
            g_days--
            gy += 100 * (g_days / 36524)
            g_days %= 36524
            if (g_days >= 365) {
                g_days++
            } else {
                leap = false
            }
        }

        gy += 4 * (g_days / 1461)
        g_days %= 1461

        if (g_days >= 366) {
            leap = false
            g_days--
            gy += g_days / 365
            g_days %= 365
        }

        var gm = 0
        var gd = 0
        val g_d_m = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var i = 1
        while (i <= 12) {
            if (g_days < g_d_m[i]) {
                gm = i
                gd = g_days + 1
                break
            }
            g_days -= g_d_m[i]
            i++
        }

        // Create LocalDateTime
        val localDateTime = kotlinx.datetime.LocalDateTime(
            year = gy,
            monthNumber = gm,
            dayOfMonth = gd,
            hour = 0,
            minute = 0,
            second = 0,
            nanosecond = 0
        )
        return localDateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): PersianDate {
        val g_d_m = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        var gy2 = if (gm > 2) gy + 1 else gy
        var dayOfMonths =
            355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + g_d_m[gm - 1]
        var jy = -1595 + (33 * (dayOfMonths / 12053))
        dayOfMonths %= 12053
        jy += 4 * (dayOfMonths / 1461)
        dayOfMonths %= 1461
        if (dayOfMonths > 365) {
            jy += (dayOfMonths - 1) / 365
            dayOfMonths = (dayOfMonths - 1) % 365
        }
        val jm = if (dayOfMonths < 186) 1 + (dayOfMonths / 31) else 7 + ((dayOfMonths - 186) / 30)
        val jd = 1 + if (dayOfMonths < 186) (dayOfMonths % 31) else ((dayOfMonths - 186) % 30)
        return PersianDate(jy, jm, jd)
    }

    fun combineDateAndTime(dateMillis: Long, timeString: String): Long {
        val timeParts = timeString.split(":")
        val hour = timeParts[0].toIntOrNull() ?: 0
        val minute = timeParts[1].toIntOrNull() ?: 0

        val instant = Instant.fromEpochMilliseconds(dateMillis)
        val timeZone = TimeZone.currentSystemDefault()
        val localDate = instant.toLocalDateTime(timeZone)

        // Create new LocalDateTime with the same date but new time
        val newLocalDateTime = kotlinx.datetime.LocalDateTime(
            year = localDate.year,
            monthNumber = localDate.month.number,
            dayOfMonth = localDate.dayOfMonth,
            hour = hour,
            minute = minute,
            second = 0,
            nanosecond = 0
        )
        return newLocalDateTime.toInstant(timeZone).toEpochMilliseconds()
    }

    fun parseIsoToEpochMillis(isoString: String): Long {
        return try {
            kotlinx.datetime.Instant.parse(isoString).toEpochMilliseconds()
        } catch (e: Exception) {
            0L
        }
    }

    fun isSameDay(millis1: Long, millis2: Long): Boolean {
        val timeZone = TimeZone.currentSystemDefault()
        val dt1 = Instant.fromEpochMilliseconds(millis1).toLocalDateTime(timeZone)
        val dt2 = Instant.fromEpochMilliseconds(millis2).toLocalDateTime(timeZone)
        return dt1.year == dt2.year && dt1.monthNumber == dt2.monthNumber && dt1.dayOfMonth == dt2.dayOfMonth
    }
}
