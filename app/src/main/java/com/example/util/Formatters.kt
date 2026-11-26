package com.example.util

import com.example.data.model.StockItem
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Formatters {
    private val enInLocale = Locale("en", "IN")
    private val isoSdf = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    private val displaySdf = ThreadLocal.withInitial { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    private val shortWeekdaySdf = ThreadLocal.withInitial { SimpleDateFormat("EEE, dd MMM", Locale.US) }
    private val timeSdf = ThreadLocal.withInitial { SimpleDateFormat("h:mm a", Locale.US) }

    fun formatInr(amount: Double, showDecimals: Boolean = false): String {
        val safe = if (amount.isNaN() || amount.isInfinite()) 0.0 else amount
        val neg = safe < 0
        val absVal = abs(safe)
        val formatted = if (showDecimals && absVal % 1.0 != 0.0) {
            val whole = absVal.toLong()
            val frac = ((absVal - whole) * 100).roundToLong().toString().padStart(2, '0')
            "${groupIndian(whole)}.$frac"
        } else {
            groupIndian(absVal.roundToLong())
        }
        return if (neg) "-₹$formatted" else "₹$formatted"
    }

    private fun groupIndian(number: Long): String {
        val s = number.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val chunks = mutableListOf<String>()
        while (rest.length > 2) {
            chunks.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) chunks.add(0, rest)
        return chunks.joinToString(",") + "," + last3
    }

    fun todayIso(): String = isoSdf.get()!!.format(Date())

    fun formatIsoDate(date: Date): String = isoSdf.get()!!.format(date)

    fun formatDisplayDate(isoDate: String): String {
        if (isoDate.isBlank()) return ""
        return try {
            val d = isoSdf.get()!!.parse(isoDate.take(10)) ?: return isoDate
            displaySdf.get()!!.format(d)
        } catch (_: Exception) {
            isoDate
        }
    }

    fun formatShortWeekday(isoDate: String): String {
        if (isoDate.isBlank()) return ""
        return try {
            val d = isoSdf.get()!!.parse(isoDate.take(10)) ?: return isoDate
            shortWeekdaySdf.get()!!.format(d)
        } catch (_: Exception) {
            isoDate
        }
    }

    fun formatTime(millis: Long): String {
        return timeSdf.get()!!.format(Date(millis))
    }

    fun formatDurationHoursMins(startMs: Long, endMs: Long): String {
        val diffMins = ((endMs - startMs) / 60000L).coerceAtLeast(0L)
        val h = diffMins / 60
        val m = diffMins % 60
        return "${h}h ${m}m"
    }

    fun daysBetweenIso(fromIso: String, toIso: String = todayIso()): Int {
        return try {
            val parser = isoSdf.get()!!
            val d1 = parser.parse(fromIso.take(10)) ?: return 0
            val d2 = parser.parse(toIso.take(10)) ?: return 0
            ((d2.time - d1.time) / 86_400_000L).toInt()
        } catch (_: Exception) {
            0
        }
    }

    fun isDateInPeriod(isoDate: String, period: String, customFrom: String = "", customTo: String = ""): Boolean {
        val d = isoDate.take(10)
        if (d.isBlank()) return false
        val today = todayIso()
        return when (period) {
            "Today" -> d == today
            "This Week" -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val weekStart = isoSdf.get()!!.format(cal.time)
                d >= weekStart && d <= today
            }
            "This Month" -> d.take(7) == today.take(7)
            "This Year" -> d.take(4) == today.take(4)
            "Custom" -> {
                val okFrom = customFrom.isBlank() || d >= customFrom.take(10)
                val okTo = customTo.isBlank() || d <= customTo.take(10)
                okFrom && okTo
            }
            else -> true
        }
    }

    // Field clamping limits matching the web app
    fun clampName(v: String): String = v.trim().take(120)
    fun clampPhone(v: String): String = v.trim().take(30)
    fun clampNotes(v: String): String = v.trim().take(500)
    fun clampAddress(v: String): String = v.trim().take(300)
    fun clampImei(v: String): String = v.filter { it.isDigit() }.take(15)
    fun clampAmount(v: Double): Double = if (v.isNaN() || v.isInfinite()) 0.0 else v.coerceIn(0.0, 100_000_000.0)

    fun normalizeWhatsAppPhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "91$digits"
            digits.startsWith("0") && digits.length == 11 -> "91${digits.drop(1)}"
            else -> digits
        }
    }

    fun extractInstagramHandle(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val afterSlash = trimmed.substringAfter("instagram.com/", trimmed).substringBefore("?").trim('/')
        return afterSlash.removePrefix("@")
    }

    fun tokenizeQuery(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val tokens = ArrayList<String>(4)
        var start = -1
        for (i in trimmed.indices) {
            if (trimmed[i].isWhitespace()) {
                if (start >= 0) {
                    tokens.add(trimmed.substring(start, i))
                    start = -1
                }
            } else if (start < 0) {
                start = i
            }
        }
        if (start >= 0) {
            tokens.add(trimmed.substring(start))
        }
        return tokens
    }

    /**
     * Multi-word search: every whitespace-separated token must match across
     * brand, model, color, category, alsoFits, barcode, or imei.
     */
    fun matchesStockQuery(item: StockItem, query: String): Boolean {
        val tokens = tokenizeQuery(query)
        if (tokens.isEmpty()) return true
        return matchesStockQueryTokens(item, tokens)
    }

    fun matchesStockQueryTokens(item: StockItem, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return true
        for (i in tokens.indices) {
            val token = tokens[i]
            val found = item.brand.contains(token, ignoreCase = true) ||
                item.model.contains(token, ignoreCase = true) ||
                item.color.contains(token, ignoreCase = true) ||
                item.category.contains(token, ignoreCase = true) ||
                item.alsoFits.contains(token, ignoreCase = true) ||
                item.barcode.contains(token, ignoreCase = true) ||
                item.imei.contains(token, ignoreCase = true)
            if (!found) return false
        }
        return true
    }

    fun formatSeqNumber(prefix: String, seq: Int): String {
        val num = seq.coerceAtLeast(1).toString().padStart(4, '0')
        return "$prefix$num"
    }

    fun parseCsvRows(csvText: String): List<List<String>> {
        val lines = csvText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return lines.map { line ->
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            for (ch in line) {
                when {
                    ch == '"' -> inQuotes = !inQuotes
                    ch == ',' && !inQuotes -> {
                        result.add(current.toString().trim())
                        current.clear()
                    }
                    else -> current.append(ch)
                }
            }
            result.add(current.toString().trim())
            result
        }
    }

    fun formatDecimal(v: Double): String {
        val df = DecimalFormat("0.##", DecimalFormatSymbols(enInLocale))
        return df.format(v)
    }
}
