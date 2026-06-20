package com.example.data

import java.util.Locale

data class BillingPeriod(
    val month: Int,
    val year: Int
) : Comparable<BillingPeriod> {

    fun nextMonth(): BillingPeriod {
        return if (month == 12) {
            BillingPeriod(1, year + 1)
        } else {
            BillingPeriod(month + 1, year)
        }
    }

    fun previousMonth(): BillingPeriod {
        return if (month == 1) {
            BillingPeriod(12, year - 1)
        } else {
            BillingPeriod(month - 1, year)
        }
    }

    fun formatArabicMonth(): String {
        val monthNames = arrayOf(
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
        )
        val mName = if (month in 1..12) monthNames[month - 1] else ""
        return "$mName $year"
    }

    fun toLegacyString(): String {
        return String.format(Locale.ENGLISH, "%04d-%02d", year, month)
    }

    override fun compareTo(other: BillingPeriod): Int {
        return comparePeriods(this, other)
    }

    companion object {
        fun comparePeriods(p1: BillingPeriod, p2: BillingPeriod): Int {
            return if (p1.year != p2.year) {
                p1.year.compareTo(p2.year)
            } else {
                p1.month.compareTo(p2.month)
            }
        }

        fun sortPeriods(periods: List<BillingPeriod>): List<BillingPeriod> {
            return periods.sortedWith { p1, p2 -> comparePeriods(p1, p2) }
        }

        fun parseBillingPeriod(str: String): BillingPeriod {
            val trimmed = str.trim()
            // Try YYYY-MM
            val parts = trimmed.split("-")
            if (parts.size == 2) {
                val y = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                if (y != null && m != null) {
                    return BillingPeriod(m, y)
                }
            }
            
            // Try matching Arabic or English month name
            val englishMonths = listOf(
                "january", "february", "march", "april", "may", "june",
                "july", "august", "september", "october", "november", "december"
            )
            val arabicMonths = listOf(
                "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
            )
            
            val lower = trimmed.lowercase(Locale.ROOT)
            var foundMonth = 6 // default June
            var foundYear = 2026 // default 2026
            
            // Extract trailing or leading year (4 digits)
            val yearFinder = "\\b(20\\d{2})\\b".toRegex()
            val match = yearFinder.find(trimmed)
            if (match != null) {
                foundYear = match.groupValues[1].toInt()
            }
            
            for ((index, mName) in englishMonths.withIndex()) {
                if (lower.contains(mName)) {
                    foundMonth = index + 1
                    break
                }
            }
            for ((index, mName) in arabicMonths.withIndex()) {
                if (trimmed.contains(mName)) {
                    foundMonth = index + 1
                    break
                }
            }
            return BillingPeriod(foundMonth, foundYear)
        }
    }
}

fun Payment.getBillingPeriod(): BillingPeriod {
    return if (monthVal in 1..12 && yearVal > 1000) {
        BillingPeriod(monthVal, yearVal)
    } else {
        BillingPeriod.parseBillingPeriod(month)
    }
}
