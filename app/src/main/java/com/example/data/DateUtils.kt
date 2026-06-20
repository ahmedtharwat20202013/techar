package com.example.data

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    val CAIRO_ZONE: TimeZone = TimeZone.getTimeZone("Africa/Cairo")
    val EGYPT_LOCALE: Locale = Locale("ar", "EG")

    // Retrieve a Calendar instance pre-configured with Africa/Cairo timezone and ar-EG locale
    fun getCairoCalendar(): Calendar {
        return Calendar.getInstance(CAIRO_ZONE, EGYPT_LOCALE)
    }

    // Helper to format Date with Egypt locale and Cairo timezone
    fun formatWithCairo(pattern: String, date: Date = Date()): String {
        val sdf = SimpleDateFormat(pattern, EGYPT_LOCALE)
        sdf.timeZone = CAIRO_ZONE
        return sdf.format(date)
    }

    // Standard format converter with English locale but Cairo timezone to prevent numbering issues in keys
    fun formatStandard(pattern: String, date: Date = Date()): String {
        val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
        sdf.timeZone = CAIRO_ZONE
        return sdf.format(date)
    }

    // Convert date string from yyyy-MM-dd / yyyy/MM/dd to visual display format dd-MM-yyyy
    fun formatDateForDisplay(dateStr: String): String {
        if (dateStr.isBlank()) return "تاريخ غير صالح"
        return try {
            val normalized = dateStr.replace("/", "-").trim()
            val cleanStr = if (normalized.contains("T")) normalized.split("T")[0] else if (normalized.contains(" ")) normalized.split(" ")[0] else normalized
            val parts = cleanStr.split("-")
            if (parts.size >= 3) {
                val p0 = parts[0].toIntOrNull() ?: return "تاريخ غير صالح"
                val p1 = parts[1].toIntOrNull() ?: return "تاريخ غير صالح"
                val p2 = parts[2].toIntOrNull() ?: return "تاريخ غير صالح"
                
                if (parts[0].length == 4) {
                    val year = parts[0]
                    val month = parts[1]
                    val day = parts[2]
                    "${day.padStart(2, '0')}-${month.padStart(2, '0')}-${year}"
                } else if (parts[2].length == 4) {
                    val day = parts[0]
                    val month = parts[1]
                    val year = parts[2]
                    "${day.padStart(2, '0')}-${month.padStart(2, '0')}-${year}"
                } else {
                    "تاريخ غير صالح"
                }
            } else {
                "تاريخ غير صالح"
            }
        } catch (e: Exception) {
            "تاريخ غير صالح"
        }
    }

    fun getArabicDayName(dateStr: String): String {
        if (dateStr.isBlank()) return "تاريخ غير صالح"
        return try {
            val normalized = dateStr.replace("/", "-").trim()
            val cleanStr = if (normalized.contains("T")) normalized.split("T")[0] else if (normalized.contains(" ")) normalized.split(" ")[0] else normalized
            val parts = cleanStr.split("-")
            if (parts.size != 3) return "تاريخ غير صالح"
            
            // Determine if the first part is year or day
            val part0 = parts[0].trim()
            val part1 = parts[1].trim()
            val part2 = parts[2].trim()
            
            val calendar = Calendar.getInstance(CAIRO_ZONE, EGYPT_LOCALE)
            if (part0.length == 4) {
                // yyyy-MM-dd
                val year = part0.toIntOrNull() ?: return "تاريخ غير صالح"
                val month = part1.toIntOrNull() ?: return "تاريخ غير صالح"
                val day = part2.toIntOrNull() ?: return "تاريخ غير صالح"
                calendar.set(year, month - 1, day)
            } else if (part2.length == 4) {
                // dd-MM-yyyy
                val day = part0.toIntOrNull() ?: return "تاريخ غير صالح"
                val month = part1.toIntOrNull() ?: return "تاريخ غير صالح"
                val year = part2.toIntOrNull() ?: return "تاريخ غير صالح"
                calendar.set(year, month - 1, day)
            } else {
                return "تاريخ غير صالح"
            }
            
            when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SATURDAY -> "السبت"
                Calendar.SUNDAY -> "الأحد"
                Calendar.MONDAY -> "الإثنين"
                Calendar.TUESDAY -> "الثلاثاء"
                Calendar.WEDNESDAY -> "الأربعاء"
                Calendar.THURSDAY -> "الخميس"
                Calendar.FRIDAY -> "الجمعة"
                else -> "تاريخ غير صالح"
            }
        } catch (e: Exception) {
            "تاريخ غير صالح"
        }
    }

    fun formatDateWithArabicDay(dateStr: String): String {
        val dayName = getArabicDayName(dateStr)
        if (dayName == "تاريخ غير صالح") return "تاريخ غير صالح"
        val displayDate = formatDateForDisplay(dateStr)
        if (displayDate == "تاريخ غير صالح" || displayDate.isBlank()) return "تاريخ غير صالح"
        return "$displayDate ($dayName)"
    }

    // Pro-rata helper: calculates amount due based on the exact formula:
    // Amount Due = Math.round((Full Monthly Fee / Total Days in Current Month) * Remaining Days in Month)
    fun calculateProRata(fullFee: Double, joinDateStr: String): Double {
        val normalized = joinDateStr.replace("/", "-")
        val parts = normalized.split("-")
        if (parts.size != 3) return fullFee

        val year = parts[0].toIntOrNull() ?: return fullFee
        val month = parts[1].toIntOrNull() ?: return fullFee // 1-indexed
        val day = parts[2].toIntOrNull() ?: return fullFee

        val calendar = Calendar.getInstance(CAIRO_ZONE, Locale.ENGLISH)
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1) // 0-indexed in Calendar
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        val totalDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        // Remaining days includes the joining day itself
        val remainingDays = (totalDaysInMonth - day + 1).coerceIn(1, totalDaysInMonth)

        val amountDue = Math.round((fullFee / totalDaysInMonth.toDouble()) * remainingDays.toDouble())
        return amountDue.toDouble()
    }

    fun parseScheduleToDaysOfWeek(scheduleDays: String): List<String> {
        val days = mutableListOf<String>()
        val lowercaseSchedule = scheduleDays.lowercase()
        
        val satRegex = Regex("\\b(السبت|saturday|sat)\\b")
        val sunRegex = Regex("\\b(الأحد|الاحد|sunday|sun)\\b")
        val monRegex = Regex("\\b(الإثنين|الاثنين|monday|mon)\\b")
        val tueRegex = Regex("\\b(الثلاثاء|tuesday|tue)\\b")
        val wedRegex = Regex("\\b(الأربعاء|الاربعاء|wednesday|wed)\\b")
        val thuRegex = Regex("\\b(الخميس|thursday|thu)\\b")
        val friRegex = Regex("\\b(الجمعة|friday|fri)\\b")

        if (satRegex.containsMatchIn(lowercaseSchedule)) days.add("SATURDAY")
        if (sunRegex.containsMatchIn(lowercaseSchedule)) days.add("SUNDAY")
        if (monRegex.containsMatchIn(lowercaseSchedule)) days.add("MONDAY")
        if (tueRegex.containsMatchIn(lowercaseSchedule)) days.add("TUESDAY")
        if (wedRegex.containsMatchIn(lowercaseSchedule)) days.add("WEDNESDAY")
        if (thuRegex.containsMatchIn(lowercaseSchedule)) days.add("THURSDAY")
        if (friRegex.containsMatchIn(lowercaseSchedule)) days.add("FRIDAY")
        return days
    }
}
