package de.eva.arbeitszeit

import java.util.Calendar
import java.util.Locale

object Stats {
    data class Sum(val ms: Long, val count: Int)

    fun sum(visits: List<Visit>, station: Int, from: Long, to: Long, now: Long): Sum {
        var ms = 0L
        var count = 0
        for (v in visits) {
            if (v.station != station) continue
            val e = v.end ?: now
            val s = maxOf(v.start, from)
            val en = minOf(e, to)
            if (en > s) ms += en - s
            if (v.start >= from && v.start < to) count++
        }
        return Sum(ms, count)
    }

    /** Anzahl unterschiedlicher Tage mit Anwesenheit */
    fun days(visits: List<Visit>, station: Int, from: Long, to: Long): Int =
        visits.filter { it.station == station && it.start >= from && it.start < to }
            .map { startOfDay(it.start) }
            .toSet().size

    fun fmt(ms: Long): String {
        val min = ms / 60_000
        return String.format(Locale.GERMANY, "%d:%02d Std", min / 60, min % 60)
    }

    fun hoursDecimal(ms: Long): String = String.format(Locale.GERMANY, "%.2f", ms / 3_600_000.0)

    fun cal(): Calendar = Calendar.getInstance(Locale.GERMANY).apply {
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
    }

    private fun clearTime(c: Calendar) {
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
    }

    fun startOfDay(t: Long): Long {
        val c = cal()
        c.timeInMillis = t
        clearTime(c)
        return c.timeInMillis
    }

    fun addDays(t: Long, days: Int): Long {
        val c = cal()
        c.timeInMillis = t
        c.add(Calendar.DAY_OF_MONTH, days)
        return c.timeInMillis
    }

    fun startOfWeek(t: Long): Long {
        val c = cal()
        c.timeInMillis = t
        clearTime(c)
        val dow = c.get(Calendar.DAY_OF_WEEK) // So=1, Mo=2 ... Sa=7
        val back = (dow + 5) % 7              // Mo=0 ... So=6
        c.add(Calendar.DAY_OF_MONTH, -back)
        return c.timeInMillis
    }

    /** Monatsanfang, verschoben um offset Monate vom Monat von t */
    fun startOfMonth(t: Long, offset: Int): Long {
        val c = cal()
        c.timeInMillis = t
        clearTime(c)
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.add(Calendar.MONTH, offset)
        return c.timeInMillis
    }

    fun weekNumber(t: Long): Int {
        val c = cal()
        c.timeInMillis = t
        return c.get(Calendar.WEEK_OF_YEAR)
    }
}
