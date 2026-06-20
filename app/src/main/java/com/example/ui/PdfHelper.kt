package com.example.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfHelper {
    const val FILE_PROVIDER_AUTHORITY = "com.aistudio.teachermanager.qyhwpx.fileprovider"

    fun cleanupOldPdfReports(context: Context, maxAgeDays: Int = 7) {
        try {
            val cachePath = File(context.cacheDir, "reports")
            if (!cachePath.exists()) return

            val cutoffTime = System.currentTimeMillis() - (maxAgeDays.toLong() * 24 * 60 * 60 * 1000)
            cachePath.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoffTime) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isPdfViewerInstalled(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "application/pdf"
            }
            context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Draws a text value wrapped onto multiple lines if it exceedsmaxWidth.
     * Support centering or custom alignment, returns the total vertical space consumed.
     */
    fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float = paint.textSize * 1.3f,
        align: Paint.Align = Paint.Align.CENTER
    ): Float {
        if (text.isEmpty()) return 0f
        
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()
        
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            val width = paint.measureText(testLine)
            if (width <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                } else {
                    lines.add(word)
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        
        var y = startY
        val originalTextAlign = paint.textAlign
        paint.textAlign = align
        
        for (line in lines) {
            canvas.drawText(line, x, y, paint)
            y += lineHeight
        }
        
        paint.textAlign = originalTextAlign
        return lines.size * lineHeight
    }

    /**
     * Calculates the number of lines a text string will generate when wrapped to maxWidth using paint.
     */
    fun getWrappedLinesCount(text: String, maxWidth: Float, paint: Paint): Int {
        if (text.isEmpty()) return 0
        val words = text.split(" ")
        var lineCount = 0
        var currentLine = StringBuilder()
        
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
            val width = paint.measureText(testLine)
            if (width <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                lineCount++
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lineCount++
        }
        return lineCount
    }

    fun generateAndExportStudentProfile(
        context: Context,
        student: Student,
        group: Group?,
        payments: List<Payment>,
        exams: List<ExamScore>,
        attendances: List<AttendanceRecord>,
        sessions: List<Session>,
        viewImmediately: Boolean
    ) {
        val progressDialog = android.app.ProgressDialog(context).apply {
            setMessage("جاري إنشاء تقرير PDF للرصد والمتابعة...")
            setCancelable(false)
        }
        progressDialog.show()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // First, run cleanup on IO thread
                withContext(Dispatchers.IO) {
                    cleanupOldPdfReports(context)
                }

                val timestamp = System.currentTimeMillis()
                val fileName = "report_student_${student.id}_${timestamp}.pdf"

                val resultFile = withContext(Dispatchers.IO) {
                    val pdfDocument = android.graphics.pdf.PdfDocument()
                    
                    // --- DATA CALCULATIONS ---
                    val defaultDate = DateUtils.formatStandard("yyyy-MM-dd")
                    val normJoinDate = student.joinDate.isNotBlank().let { if (it) student.joinDate.replace("-", "/") else defaultDate }
                    
                    // Filter sessions starting on or after Student joinDate
                    val activeSessions = sessions.filter { it.date.replace("-", "/") >= normJoinDate }
                    val activeSessionIds = activeSessions.map { it.id }.toSet()
                    
                    // Filter attendance records to those sessions
                    val activeAttendances = attendances.filter { activeSessionIds.contains(it.sessionId) }
                    
                    val totalSessions = activeAttendances.size
                    val presentCount = activeAttendances.count { it.status == AttendanceStatus.present || it.status == AttendanceStatus.late }
                    val lateCount = activeAttendances.count { it.status == AttendanceStatus.late }
                    val absentCount = activeAttendances.count { it.status == AttendanceStatus.absent }
                    val totalAttendanceCount = presentCount
                    val attendancePercentage = if (totalSessions > 0) (totalAttendanceCount * 100 / totalSessions) else 0
                    
                    var totalRequired = 0.0
                    var totalPaid = 0.0
                    var totalRemaining = 0.0
                    val cleanPayments = payments.map { p ->
                        val req = if (p.amountDue > 0.0) p.amountDue else (group?.monthlyFee ?: 200.0)
                        val paid = p.amountPaid
                        val rem = maxOf(0.0, req - paid)
                        
                        totalRequired += req
                        totalPaid += paid
                        totalRemaining += rem
                        
                        p to (req to rem)
                    }
                    
                    val overallPercentage = if (exams.isNotEmpty()) {
                        (exams.sumOf { it.score } / exams.sumOf { it.maxScore } * 100).toInt()
                    } else {
                        0
                    }
                    
                    // academic level
                    val academicLevel = when {
                        overallPercentage >= 90 -> "ممتاز"
                        overallPercentage >= 80 -> "جيد جداً"
                        overallPercentage >= 65 -> "جيد"
                        else -> "مقبول"
                    }
                    
                    val ratingExplanation = when {
                        overallPercentage >= 90 -> "ممتاز (5/5)"
                        overallPercentage >= 80 -> "جيد جداً (4/5)"
                        overallPercentage >= 65 -> "جيد (3/5)"
                        else -> "مقبول (2/5)"
                    }
                    
                    // --- CALENDAR & EXAM PERFORMANCE CALCULATIONS ---
                    val sortedAttendances = activeAttendances.sortedByDescending { it.attendanceDate.ifBlank { it.timestamp } }
                    val sortedPayments = cleanPayments.sortedByDescending { it.first.month } // e.g. June, May etc.
                    val sortedExams = exams.sortedByDescending { it.date }
                    
                    // --- STRICT GLOBAL COLOR CODES ---
                    val colorPrimaryGreen = 0xFF004420.toInt()
                    val colorLightGreen  = 0xFF2E7D32.toInt()
                    val colorRed         = 0xFFD32F2F.toInt()
                    val colorBlue        = 0xFF1976D2.toInt()
                    val colorOrange      = 0xFFef6c00.toInt()
                    val colorDarkGray    = 0xFF212121.toInt()
                    val colorLightGray   = 0xFFF5F5F5.toInt()
                    val colorWhite       = 0xFFFFFFFF.toInt()
                    val colorGrayBorder  = 0xFFE0E0E0.toInt()
                    val colorTextGray    = 0xFF808080.toInt()
                    
                    // Common Paints
                    val paint = android.graphics.Paint()
                    val textPaint = android.graphics.Paint().apply {
                        color = colorDarkGray
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.SANS_SERIF
                    }
                    
                    val todayStr = DateUtils.formatDateWithArabicDay(SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date()))
                    
                    var currentPageNum = 1
                    
                    val arabicMonths = mapOf(
                        "01" to "يناير",
                        "02" to "فبراير",
                        "03" to "مارس",
                        "04" to "أبريل",
                        "05" to "مايو",
                        "06" to "يونيو",
                        "07" to "يوليو",
                        "08" to "أغسطس",
                        "09" to "سبتمبر",
                        "10" to "أكتوبر",
                        "11" to "نوفمبر",
                        "12" to "ديسمبر"
                    )
                    
                    fun getYearMonthKey(dateStr: String): String {
                        if (dateStr.isBlank()) return "unknown"
                        return try {
                            val normalized = dateStr.replace("/", "-").trim()
                            val cleanStr = if (normalized.contains("T")) normalized.split("T")[0] else if (normalized.contains(" ")) normalized.split(" ")[0] else normalized
                            val parts = cleanStr.split("-")
                            val result = if (parts.size >= 3) {
                                val p1 = parts[1].padStart(2, '0')
                                if (parts[0].length == 4) {
                                    "${parts[0]}-$p1"
                                } else if (parts[2].length == 4) {
                                    "${parts[2]}-$p1"
                                } else {
                                    val fallbackYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                                    "$fallbackYear-$p1"
                                }
                            } else {
                                "unknown"
                            }
                            result.ifBlank { "unknown" }
                        } catch (e: Exception) {
                            "unknown"
                        }
                    }
                    
                    val sessionMap = sessions.associateBy { it.id }
                    val monthKeysSet = mutableSetOf<String>()
                    val monthlyStats = mutableMapOf<String, IntArray>()
                    
                    activeAttendances.forEach { record ->
                        val recordDate = if (record.attendanceDate.isNotBlank()) record.attendanceDate else record.timestamp
                        val key = getYearMonthKey(recordDate)
                        if (key != "unknown") {
                            monthKeysSet.add(key)
                            val stats = monthlyStats.getOrPut(key) { IntArray(4) }
                            stats[0]++
                            when (record.status) {
                                AttendanceStatus.present -> stats[1]++
                                AttendanceStatus.absent -> stats[2]++
                                AttendanceStatus.late -> {
                                    stats[3]++
                                    stats[1]++
                                }
                            }
                        }
                    }
                    
                    val sortedMonthKeys = monthKeysSet.sorted()
                    
                    val maxAbsents = if (sortedMonthKeys.isNotEmpty()) sortedMonthKeys.maxOfOrNull { monthlyStats[it]?.get(2) ?: 0 } ?: 0 else 0
                    val mostAbsentMonthsStr = if (maxAbsents > 0) {
                        sortedMonthKeys.filter { (monthlyStats[it]?.get(2) ?: 0) == maxAbsents }
                            .map { k -> arabicMonths[k.split("-").getOrNull(1)] ?: k }
                            .joinToString(" و ")
                    } else {
                        "لا يوجد"
                    }
                    
                    val attendanceAdvice = when {
                        attendancePercentage >= 90 -> "ممتاز (ملتزم جداً بالحضور والانضباط)"
                        attendancePercentage >= 80 -> "جيد جداً (مواظب على الحضور ويعتمد عليه)"
                        attendancePercentage >= 65 -> "مقبول (يحتاج إلى تحسين الالتزام بالحضور)"
                        else -> "مستواه حرج (غياب متكرر وتأخر مستمر، يتطلب متابعة)"
                    }
                    
                    // ==========================================
                    // PAGE 1 — DASHBOARD (Executive Overview)
                    // ==========================================
                    val pageInfo1 = android.graphics.pdf.PdfDocument.PageInfo.Builder(842, 595, currentPageNum).create()
                    val page1 = pdfDocument.startPage(pageInfo1)
                    val canvas1 = page1.canvas
                    canvas1.drawColor(colorWhite)
                    
                    // 1. Header (Forest Green Rect with Rounded Corners)
                    paint.color = colorPrimaryGreen
                    canvas1.drawRoundRect(30f, 25f, 812f, 105f, 12f, 12f, paint)
                    
                    // Title on Right
                    textPaint.color = colorWhite
                    textPaint.textSize = 22f
                    textPaint.isFakeBoldText = true
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    canvas1.drawText("لوحة تحكم الطالب الشاملة", 782f, 72f, textPaint)
                    
                    // Report Date on Left
                    textPaint.textSize = 12f
                    textPaint.isFakeBoldText = false
                    textPaint.textAlign = android.graphics.Paint.Align.LEFT
                    canvas1.drawText("إصدار التقرير: $todayStr", 60f, 70f, textPaint)
                    
                    // 2. Student Profile Card (Forest Green Rect with Rounded Corners)
                    paint.color = 0xFF004D26.toInt()
                    canvas1.drawRoundRect(345f, 125f, 812f, 415f, 15f, 15f, paint)
                    
                    // Avatar Circle Container on the right
                    paint.color = colorWhite
                    canvas1.drawCircle(745f, 270f, 42f, paint)
                    
                    // Render text centered inside circle, avoiding Emojis
                    textPaint.color = colorPrimaryGreen
                    textPaint.textSize = 16f
                    textPaint.textAlign = android.graphics.Paint.Align.CENTER
                    textPaint.isFakeBoldText = true
                    canvas1.drawText("طالب", 745f, 276f, textPaint)
                    
                    // Student Profile details
                    textPaint.color = colorWhite
                    
                    // Student long name handling
                    val nameText = student.name
                    val namePaint = android.graphics.Paint().apply {
                        color = colorWhite
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.SANS_SERIF
                        isFakeBoldText = true
                        textSize = 20f
                    }
                    var nameSize = 20f
                    while (namePaint.measureText(nameText) > 310f && nameSize > 12f) {
                        nameSize -= 1f
                        namePaint.textSize = nameSize
                    }
                    namePaint.textAlign = android.graphics.Paint.Align.RIGHT
                    canvas1.drawText(nameText, 680f, 205f, namePaint)
                    
                    textPaint.isFakeBoldText = false
                    textPaint.textSize = 11f
                    
                    // Group long name handling
                    val groupText = "الصف والمسار: ${group?.name ?: "غير محدد"}"
                    val groupDescPaint = android.graphics.Paint().apply {
                        color = colorWhite
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.SANS_SERIF
                        textSize = 11f
                    }
                    var groupTextSize = 11f
                    while (groupDescPaint.measureText(groupText) > 310f && groupTextSize > 8f) {
                        groupTextSize -= 0.5f
                        groupDescPaint.textSize = groupTextSize
                    }
                    groupDescPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    canvas1.drawText(groupText, 680f, 240f, groupDescPaint)
                    
                    // Parent Phone
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    canvas1.drawText("هاتف ولي الأمر: ${student.parentPhone}", 680f, 275f, textPaint)
                    
                    // Enrolled Dynamic Academic Year
                    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                    val academicYear = "$currentYear / ${currentYear + 1}"
                    canvas1.drawText("الفترة الدراسية: العام الدراسي $academicYear", 680f, 310f, textPaint)
                    
                    // Dynamic evaluation / Recommendation Text
                    textPaint.color = 0xFFFCD34D.toInt() // gold/light orange text for emphasis
                    textPaint.isFakeBoldText = true
                    textPaint.textSize = 10.5f
                    canvas1.drawText("تقييم عام: $attendanceAdvice", 680f, 355f, textPaint)
                    
                    // 3. 2x2 Grid of Left Side Cards (متبقي, مدفوع, الحضور, الامتحانات)
                    val drawLeftCard: (Float, Float, Float, Float, String, String, Int) -> Unit = { l, t, r, b, labelText, valText, accentColor ->
                        // Card Base
                        paint.color = colorWhite
                        canvas1.drawRoundRect(l, t, r, b, 10f, 10f, paint)
                        
                        // Subtle Gray border
                        paint.color = colorGrayBorder
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.strokeWidth = 1f
                        canvas1.drawRoundRect(l, t, r, b, 10f, 10f, paint)
                        paint.style = android.graphics.Paint.Style.FILL
                        
                        // Accent border on the right
                        paint.color = accentColor
                        canvas1.drawRect(r - 5f, t, r, b, paint) // right Vertical bar
                        
                        // Texts (Centered)
                        textPaint.textAlign = android.graphics.Paint.Align.CENTER
                        textPaint.color = colorTextGray
                        textPaint.textSize = 11f
                        textPaint.isFakeBoldText = false
                        canvas1.drawText(labelText, (l + r) / 2f, t + 35f, textPaint)
                        
                        textPaint.color = colorDarkGray
                        textPaint.textSize = 18f
                        textPaint.isFakeBoldText = true
                        canvas1.drawText(valText, (l + r) / 2f, t + 75f, textPaint)
                    }
                    
                    // Draw Left Cards
                    drawLeftCard(30f, 125f, 170f, 265f, "متبقي", "${totalRemaining.toInt()} ج", colorRed)
                    drawLeftCard(185f, 125f, 325f, 265f, "مدفوع", "${totalPaid.toInt()} ج", colorLightGreen)
                    drawLeftCard(30f, 275f, 170f, 415f, "متوسط الحضور", "$attendancePercentage%", colorOrange)
                    
                    // Rating Stars Card 2x2 Replacement (Without emojis)
                    paint.color = colorWhite
                    canvas1.drawRoundRect(185f, 275f, 325f, 415f, 10f, 10f, paint)
                    paint.color = colorGrayBorder
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.strokeWidth = 1f
                    canvas1.drawRoundRect(185f, 275f, 325f, 415f, 10f, 10f, paint)
                    paint.style = android.graphics.Paint.Style.FILL
                    
                    // Blue Right border
                    paint.color = colorBlue
                    canvas1.drawRect(320f, 275f, 325f, 415f, paint)
                    
                    textPaint.textAlign = android.graphics.Paint.Align.CENTER
                    textPaint.color = colorTextGray
                    textPaint.textSize = 11f
                    textPaint.isFakeBoldText = false
                    canvas1.drawText("مستوى الامتحان", 255f, 310f, textPaint)
                    
                    textPaint.color = colorPrimaryGreen
                    textPaint.textSize = 16f
                    textPaint.isFakeBoldText = true
                    canvas1.drawText(academicLevel, 255f, 355f, textPaint)
                    
                    // 4. Three bottom wide row cards (Perfectly symmetric with width=250 and spacing=16)
                    val drawBottomWideCard: (Float, Float, String, String, Int) -> Unit = { l, r, labelText, valText, bottomBarColor ->
                        val t = 430f
                        val b = 525f
                        paint.color = colorWhite
                        canvas1.drawRoundRect(l, t, r, b, 10f, 10f, paint)
                        
                        // Gray border
                        paint.color = colorGrayBorder
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.strokeWidth = 1f
                        canvas1.drawRoundRect(l, t, r, b, 10f, 10f, paint)
                        paint.style = android.graphics.Paint.Style.FILL
                        
                        // Bottom horizontal bar accent
                        paint.color = bottomBarColor
                        canvas1.drawRect(l, b - 5f, r, b, paint) // bottom bar
                        
                        // Labels
                        textPaint.textAlign = android.graphics.Paint.Align.CENTER
                        textPaint.color = colorTextGray
                        textPaint.textSize = 12f
                        textPaint.isFakeBoldText = false
                        canvas1.drawText(labelText, (l + r) / 2f, t + 35f, textPaint)
                        
                        textPaint.color = colorDarkGray
                        textPaint.textSize = 21f
                        textPaint.isFakeBoldText = true
                        canvas1.drawText(valText, (l + r) / 2f, t + 73f, textPaint)
                    }
                    
                    drawBottomWideCard(30f, 280f, "إجمالي التأخير", "$lateCount مرات", colorOrange)
                    drawBottomWideCard(296f, 546f, "إجمالي الغياب", "$absentCount يوم", colorRed)
                    drawBottomWideCard(562f, 812f, "إجمالي الحضور", "$presentCount يوم", colorLightGreen)
                    
                    // Footer Page 1
                    paint.color = colorGrayBorder
                    canvas1.drawRect(30f, 555f, 812f, 556f, paint)
                    
                    textPaint.textSize = 9f
                    textPaint.isFakeBoldText = false
                    textPaint.color = colorTextGray
                    textPaint.textAlign = android.graphics.Paint.Align.LEFT
                    canvas1.drawText("صفحة 1", 30f, 575f, textPaint)
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    canvas1.drawText("إدارة المركز التعليمي - لوحة المتابعة الشاملة", 812f, 575f, textPaint)
                    
                    pdfDocument.finishPage(page1)
                    
                    // ==========================================
                    // PAGE 2 — MONTHLY ATTENDANCE SUMMARY (Portrait 595 x 842)
                    // ==========================================
                    currentPageNum++
                    val pageInfo2 = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, currentPageNum).create()
                    var page2Obj = pdfDocument.startPage(pageInfo2)
                    var canvas2 = page2Obj.canvas
                    canvas2.drawColor(colorWhite)
                    
                    // 1. Header (Primary Green)
                    paint.color = colorPrimaryGreen
                    canvas2.drawRoundRect(30f, 25f, 565f, 105f, 10f, 10f, paint)
                    
                    textPaint.color = colorWhite
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    textPaint.isFakeBoldText = true
                    textPaint.textSize = 18f
                    canvas2.drawText("الوضع التفصيلي للحضور والغياب (شهري)", 545f, 65f, textPaint)
                    
                    // Long name header scaling
                    val maxHeaderNameWidth = 220f
                    val headerNameText = "الطالب: ${student.name}"
                    val headerNamePaint = android.graphics.Paint().apply {
                        color = colorWhite
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.SANS_SERIF
                        textSize = 10f
                    }
                    var headerNameSize = 10f
                    while (headerNamePaint.measureText(headerNameText) > maxHeaderNameWidth && headerNameSize > 7f) {
                        headerNameSize -= 0.5f
                        headerNamePaint.textSize = headerNameSize
                    }
                    headerNamePaint.textAlign = android.graphics.Paint.Align.LEFT
                    canvas2.drawText(headerNameText, 50f, 62f, headerNamePaint)
                    canvas2.drawText("الصف: ${group?.name ?: "-"}", 50f, 82f, headerNamePaint)
                    
                    // 2. Twin Stats Cards at Top (y: 120f to 185f)
                    val drawPortraitCard: (Float, Float, String, String, Int) -> Unit = { l, r, label, value, bottomColor ->
                        val t = 120f
                        val b = 185f
                        paint.color = colorWhite
                        canvas2.drawRoundRect(l, t, r, b, 8f, 8f, paint)
                        
                        // border
                        paint.color = colorGrayBorder
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.strokeWidth = 1f
                        canvas2.drawRoundRect(l, t, r, b, 8f, 8f, paint)
                        paint.style = android.graphics.Paint.Style.FILL
                        
                        // Bottom bar
                        paint.color = bottomColor
                        canvas2.drawRect(l, b - 4f, r, b, paint)
                        
                        // Texts (Centered)
                        textPaint.textAlign = android.graphics.Paint.Align.CENTER
                        textPaint.color = colorTextGray
                        textPaint.textSize = 10f
                        textPaint.isFakeBoldText = false
                        canvas2.drawText(label, (l + r) / 2f, t + 24f, textPaint)
                        
                        textPaint.color = colorDarkGray
                        textPaint.textSize = 16f
                        textPaint.isFakeBoldText = true
                        canvas2.drawText(value, (l + r) / 2f, t + 50f, textPaint)
                    }
                    
                    drawPortraitCard(307.5f, 565f, "إجمالي الغياب", "$absentCount حصة", colorRed)
                    drawPortraitCard(30f, 287.5f, "إجمالي التأخير", "$lateCount حصة", colorOrange)
                    
                    // 3. Wide banner for "أكثر شهر غياباً" below them
                    paint.color = 0xFFF9FAFB.toInt() // light gray-blue
                    canvas2.drawRoundRect(30f, 195f, 565f, 240f, 6f, 6f, paint)
                    paint.color = colorGrayBorder
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.strokeWidth = 1f
                    canvas2.drawRoundRect(30f, 195f, 565f, 240f, 6f, 6f, paint)
                    paint.style = android.graphics.Paint.Style.FILL
                    
                    // left accent border
                    paint.color = colorBlue
                    canvas2.drawRect(30f, 195f, 34f, 240f, paint)
                    
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    textPaint.color = colorDarkGray
                    textPaint.textSize = 10f
                    textPaint.isFakeBoldText = true
                    canvas2.drawText("تنبيه: أكثر شهر غياباً: $mostAbsentMonthsStr", 545f, 222f, textPaint)
                    
                    // 4. Monthly Attendance Table with dynamic overflow
                    var yPos2 = 255f
                    
                    // Header Row
                    paint.color = 0xFF004D26.toInt()
                    canvas2.drawRoundRect(30f, yPos2, 565f, yPos2 + 28f, 6f, 6f, paint)
                    
                    textPaint.color = colorWhite
                    textPaint.textSize = 10.5f
                    textPaint.isFakeBoldText = true
                    textPaint.textAlign = android.graphics.Paint.Align.CENTER
                    
                    canvas2.drawText("الشهر", 497.5f, yPos2 + 18f, textPaint)
                    canvas2.drawText("إجمالي الحصص", 370f, yPos2 + 18f, textPaint)
                    canvas2.drawText("حضور", 265f, yPos2 + 18f, textPaint)
                    canvas2.drawText("غياب", 175f, yPos2 + 18f, textPaint)
                    canvas2.drawText("تأخير", 80f, yPos2 + 18f, textPaint)
                    
                    yPos2 += 28f
                    
                    if (sortedMonthKeys.isEmpty()) {
                        textPaint.textAlign = android.graphics.Paint.Align.CENTER
                        textPaint.textSize = 12f
                        textPaint.color = android.graphics.Color.GRAY
                        canvas2.drawText("لا توجد مذكرات أو بيانات حضور لهذا الطالب.", 297.5f, yPos2 + 40f, textPaint)
                    } else {
                        sortedMonthKeys.forEachIndexed { index, mKey ->
                            if (yPos2 > 740f) {
                                // Draw Footer before finishing page
                                paint.color = colorGrayBorder
                                canvas2.drawRect(30f, 790f, 565f, 791f, paint)
                                textPaint.textSize = 9f
                                textPaint.isFakeBoldText = false
                                textPaint.color = colorTextGray
                                textPaint.textAlign = android.graphics.Paint.Align.LEFT
                                canvas2.drawText("صفحة $currentPageNum", 30f, 810f, textPaint)
                                textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                                canvas2.drawText("إجمالي تقارير الحضور والغياب", 565f, 810f, textPaint)
                                
                                pdfDocument.finishPage(page2Obj)
                                
                                currentPageNum++
                                val pageInfoNew = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, currentPageNum).create()
                                page2Obj = pdfDocument.startPage(pageInfoNew)
                                canvas2 = page2Obj.canvas
                                canvas2.drawColor(colorWhite)
                                
                                // Header of new page
                                paint.color = colorPrimaryGreen
                                canvas2.drawRoundRect(30f, 25f, 565f, 105f, 10f, 10f, paint)
                                textPaint.color = colorWhite
                                textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                                textPaint.isFakeBoldText = true
                                textPaint.textSize = 18f
                                canvas2.drawText("الوضع التفصيلي للحضور والغياب (تابع)", 545f, 65f, textPaint)
                                
                                textPaint.textSize = 11f
                                textPaint.isFakeBoldText = false
                                textPaint.textAlign = android.graphics.Paint.Align.LEFT
                                canvas2.drawText(headerNameText, 50f, 62f, headerNamePaint)
                                canvas2.drawText("الصف: ${group?.name ?: "-"}", 50f, 82f, headerNamePaint)
                                
                                yPos2 = 130f
                                
                                // Table Header in new page
                                paint.color = 0xFF004D26.toInt()
                                canvas2.drawRoundRect(30f, yPos2, 565f, yPos2 + 28f, 6f, 6f, paint)
                                textPaint.color = colorWhite
                                textPaint.textSize = 10.5f
                                textPaint.isFakeBoldText = true
                                textPaint.textAlign = android.graphics.Paint.Align.CENTER
                                
                                canvas2.drawText("الشهر", 497.5f, yPos2 + 18f, textPaint)
                                canvas2.drawText("إجمالي الحصص", 370f, yPos2 + 18f, textPaint)
                                canvas2.drawText("حضور", 265f, yPos2 + 18f, textPaint)
                                canvas2.drawText("غياب", 175f, yPos2 + 18f, textPaint)
                                canvas2.drawText("تأخير", 80f, yPos2 + 18f, textPaint)
                                
                                yPos2 += 28f
                            }
                            
                            val monthName = arabicMonths[mKey.split("-").getOrNull(1)] ?: mKey
                            val stats = monthlyStats[mKey] ?: intArrayOf(0, 0, 0, 0)
                            
                            yPos2 += 2f
                            if (index % 2 == 1) {
                                paint.color = colorLightGray
                                canvas2.drawRect(30f, yPos2, 565f, yPos2 + 25f, paint)
                            }
                            
                            textPaint.textAlign = android.graphics.Paint.Align.CENTER
                            textPaint.color = colorDarkGray
                            textPaint.isFakeBoldText = false
                            textPaint.textSize = 10f
                            
                            canvas2.drawText(monthName, 497.5f, yPos2 + 17f, textPaint)
                            canvas2.drawText("${stats[0]} حصص", 370f, yPos2 + 17f, textPaint)
                            
                            textPaint.color = colorLightGreen
                            canvas2.drawText("${stats[1]}", 265f, yPos2 + 17f, textPaint)
                            
                            textPaint.color = if (stats[2] > 0) colorRed else colorDarkGray
                            textPaint.isFakeBoldText = stats[2] > 0
                            canvas2.drawText("${stats[2]}", 175f, yPos2 + 17f, textPaint)
                            
                            textPaint.color = if (stats[3] > 0) colorOrange else colorDarkGray
                            textPaint.isFakeBoldText = stats[3] > 0
                            canvas2.drawText("${stats[3]}", 80f, yPos2 + 17f, textPaint)
                            
                            yPos2 += 25f
                        }
                    }
                    
                    // Footer Page 2 final
                    paint.color = colorGrayBorder
                    canvas2.drawRect(30f, 790f, 565f, 791f, paint)
                    
                    textPaint.textSize = 9f
                    textPaint.isFakeBoldText = false
                    textPaint.color = colorTextGray
                    textPaint.textAlign = android.graphics.Paint.Align.LEFT
                    canvas2.drawText("صفحة $currentPageNum", 30f, 810f, textPaint)
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    canvas2.drawText("تقرير الحضور والغياب الشهري للموسم الدراسي", 565f, 810f, textPaint)
                    
                    pdfDocument.finishPage(page2Obj)
                    
                    // ==========================================
                    // PAGE 3 — MONTHLY PAYMENTS & SUBSCRIPTIONS (Portrait 595 x 842)
                    // ==========================================
                    currentPageNum++
                    val pageInfo3 = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, currentPageNum).create()
                    var page3Obj = pdfDocument.startPage(pageInfo3)
                    var canvas3 = page3Obj.canvas
                    canvas3.drawColor(colorWhite)
                    
                    // 1. Header (Primary Green)
                    paint.color = colorPrimaryGreen
                    canvas3.drawRoundRect(30f, 25f, 565f, 105f, 10f, 10f, paint)
                    
                    textPaint.color = colorWhite
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    textPaint.isFakeBoldText = true
                    textPaint.textSize = 18f
                    canvas3.drawText("الاشتراكات والوضعية المالية", 545f, 65f, textPaint)
                    
                    headerNamePaint.textAlign = android.graphics.Paint.Align.LEFT
                    canvas3.drawText(headerNameText, 50f, 62f, headerNamePaint)
                    val shortStatusStr = if (totalRemaining > 0) "عليك متأخرات" else "خالص السداد"
                    canvas3.drawText("الحالة العامة: $shortStatusStr", 50f, 82f, headerNamePaint)
                    
                    // 2. Twin Stats Cards at Top (y: 120f to 185f)
                    val drawPortraitPaymentCard: (Float, Float, String, String, Int) -> Unit = { l, r, label, value, bottomColor ->
                        val t = 120f
                        val b = 185f
                        paint.color = colorWhite
                        canvas3.drawRoundRect(l, t, r, b, 8f, 8f, paint)
                        
                        // border
                        paint.color = colorGrayBorder
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.strokeWidth = 1f
                        canvas3.drawRoundRect(l, t, r, b, 8f, 8f, paint)
                        paint.style = android.graphics.Paint.Style.FILL
                        
                        // Bottom bar
                        paint.color = bottomColor
                        canvas3.drawRect(l, b - 4f, r, b, paint)
                        
                        // Texts
                        textPaint.textAlign = android.graphics.Paint.Align.CENTER
                        textPaint.color = colorTextGray
                        textPaint.textSize = 10f
                        textPaint.isFakeBoldText = false
                        canvas3.drawText(label, (l + r) / 2f, t + 24f, textPaint)
                        
                        textPaint.color = colorDarkGray
                        textPaint.textSize = 16f
                        textPaint.isFakeBoldText = true
                        canvas3.drawText(value, (l + r) / 2f, t + 50f, textPaint)
                    }
                    
                    drawPortraitPaymentCard(307.5f, 565f, "دفع كام (المدفوع)", "${totalPaid.toInt()} ج.م", colorLightGreen)
                    drawPortraitPaymentCard(30f, 287.5f, "عليه كام (المتبقي)", "${totalRemaining.toInt()} ج.م", colorRed)
                    
                    // 3. Status Alert Bar
                    paint.color = 0xFFFDF2F2.toInt() // light rose-red
                    canvas3.drawRoundRect(30f, 195f, 565f, 240f, 6f, 6f, paint)
                    paint.color = colorGrayBorder
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.strokeWidth = 1f
                    canvas3.drawRoundRect(30f, 195f, 565f, 240f, 6f, 6f, paint)
                    paint.style = android.graphics.Paint.Style.FILL
                    
                    paint.color = colorRed
                    canvas3.drawRect(30f, 195f, 34f, 240f, paint)
                    
                    val unpaidMonths = cleanPayments.filter { it.second.second > 0.0 }.map { it.first.month }
                    val financialAlertStr = if (unpaidMonths.isNotEmpty()) {
                        "يوجد متأخرات مستحقة لشهر (${unpaidMonths.joinToString(" و ")})"
                    } else {
                        "لا توجد أي متأخرات مالية حية - خالص السداد بالكامل (مكتمل)"
                    }
                    
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    textPaint.color = colorDarkGray
                    textPaint.textSize = 10f
                    textPaint.isFakeBoldText = true
                    canvas3.drawText("حالة السداد العامة: $financialAlertStr", 545f, 222f, textPaint)
                    
                    // 4. Payments Table with dynamic overflow
                    var yPos3 = 255f
                    
                    // Header
                    paint.color = 0xFF004D26.toInt()
                    canvas3.drawRoundRect(30f, yPos3, 565f, yPos3 + 28f, 6f, 6f, paint)
                    
                    textPaint.color = colorWhite
                    textPaint.textSize = 10.5f
                    textPaint.isFakeBoldText = true
                    textPaint.textAlign = android.graphics.Paint.Align.CENTER
                    
                    // الشهر | المطلوب | المدفوع | المتبقي | الحالة
                    canvas3.drawText("الشهر الدراسي", 502.5f, yPos3 + 18f, textPaint)
                    canvas3.drawText("المطلوب المالي", 390f, yPos3 + 18f, textPaint)
                    canvas3.drawText("المدفوع الفعلي", 290f, yPos3 + 18f, textPaint)
                    canvas3.drawText("المتبقي", 195f, yPos3 + 18f, textPaint)
                    canvas3.drawText("الحالة", 90f, yPos3 + 18f, textPaint)
                    
                    yPos3 += 28f
                    
                    if (sortedPayments.isEmpty()) {
                        textPaint.textAlign = android.graphics.Paint.Align.CENTER
                        textPaint.textSize = 12f
                        textPaint.color = android.graphics.Color.GRAY
                        canvas3.drawText("لا توجد سجلات اشتراكات لهذا الطالب.", 297.5f, yPos3 + 40f, textPaint)
                    } else {
                        sortedPayments.forEachIndexed { index, pair ->
                            if (yPos3 + 25f > 740f) {
                                // Draw Footer before finishing page
                                paint.color = colorGrayBorder
                                canvas3.drawRect(30f, 790f, 565f, 791f, paint)
                                textPaint.textSize = 9f
                                textPaint.isFakeBoldText = false
                                textPaint.color = colorTextGray
                                textPaint.textAlign = android.graphics.Paint.Align.LEFT
                                canvas3.drawText("صفحة $currentPageNum", 30f, 810f, textPaint)
                                textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                                canvas3.drawText("التقرير المالي الفوري لمتابعة غطاء الاشتراكات", 565f, 810f, textPaint)
                                
                                pdfDocument.finishPage(page3Obj)
                                
                                currentPageNum++
                                val pageInfoNew = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, currentPageNum).create()
                                page3Obj = pdfDocument.startPage(pageInfoNew)
                                canvas3 = page3Obj.canvas
                                canvas3.drawColor(colorWhite)
                                
                                // Header
                                paint.color = colorPrimaryGreen
                                canvas3.drawRoundRect(30f, 25f, 565f, 105f, 10f, 10f, paint)
                                textPaint.color = colorWhite
                                textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                                textPaint.isFakeBoldText = true
                                textPaint.textSize = 18f
                                canvas3.drawText("الاشتراكات والوضعية المالية (تابع)", 545f, 65f, textPaint)
                                
                                textPaint.textSize = 11f
                                textPaint.isFakeBoldText = false
                                textPaint.textAlign = android.graphics.Paint.Align.LEFT
                                canvas3.drawText(headerNameText, 50f, 62f, headerNamePaint)
                                canvas3.drawText("الحالة العامة: $shortStatusStr", 50f, 82f, headerNamePaint)
                                
                                yPos3 = 130f
                                
                                // Table Header Row
                                paint.color = 0xFF004D26.toInt()
                                canvas3.drawRoundRect(30f, yPos3, 565f, yPos3 + 28f, 6f, 6f, paint)
                                textPaint.color = colorWhite
                                textPaint.textSize = 10.5f
                                textPaint.isFakeBoldText = true
                                textPaint.textAlign = android.graphics.Paint.Align.CENTER
                                
                                canvas3.drawText("الشهر الدراسي", 502.5f, yPos3 + 18f, textPaint)
                                canvas3.drawText("المطلوب المالي", 390f, yPos3 + 18f, textPaint)
                                canvas3.drawText("المدفوع الفعلي", 290f, yPos3 + 18f, textPaint)
                                canvas3.drawText("المتبقي", 195f, yPos3 + 18f, textPaint)
                                canvas3.drawText("الحالة", 90f, yPos3 + 18f, textPaint)
                                
                                yPos3 += 28f
                            }
                            
                            val p = pair.first
                            val (req, rem) = pair.second
                            
                            yPos3 += 2f
                            if (index % 2 == 1) {
                                paint.color = colorLightGray
                                canvas3.drawRect(30f, yPos3, 565f, yPos3 + 25f, paint)
                            }
                            
                            textPaint.textAlign = android.graphics.Paint.Align.CENTER
                            textPaint.color = colorDarkGray
                            textPaint.isFakeBoldText = false
                            textPaint.textSize = 10f
                            
                            // Write cells
                            canvas3.drawText(p.month, 502.5f, yPos3 + 17f, textPaint)
                            canvas3.drawText("${req.toInt()} ج.م", 390f, yPos3 + 17f, textPaint)
                            canvas3.drawText("${p.amountPaid.toInt()} ج.م", 290f, yPos3 + 17f, textPaint)
                            
                            textPaint.color = if (rem > 0f) colorRed else colorDarkGray
                            textPaint.isFakeBoldText = rem > 0f
                            canvas3.drawText("${rem.toInt()} ج.م", 195f, yPos3 + 17f, textPaint)
                            
                            val statusText: String
                            val statusC: Int
                            if (p.amountPaid >= req) {
                                statusText = "مدفوع"
                                statusC = colorLightGreen
                            } else if (p.amountPaid > 0f) {
                                statusText = "مدفوع جزئياً"
                                statusC = colorOrange
                            } else {
                                statusText = "غير مدفوع"
                                statusC = colorRed
                            }
                            
                            textPaint.color = statusC
                            textPaint.isFakeBoldText = true
                            canvas3.drawText(statusText, 90f, yPos3 + 17f, textPaint)
                            
                            yPos3 += 25f
                        }
                    }
                    
                    // Footer Page 3
                    paint.color = colorGrayBorder
                    canvas3.drawRect(30f, 790f, 565f, 791f, paint)
                    
                    textPaint.textSize = 9f
                    textPaint.isFakeBoldText = false
                    textPaint.color = colorTextGray
                    textPaint.textAlign = android.graphics.Paint.Align.LEFT
                    canvas3.drawText("صفحة $currentPageNum", 30f, 810f, textPaint)
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    canvas3.drawText("التقرير المالي الفوري لمتابعة غطاء الاشتراكات", 565f, 810f, textPaint)
                    
                    pdfDocument.finishPage(page3Obj)
                    
                    // ==========================================
                    // PAGE 4 — EXAM GRADES & SCORES (Portrait 595 x 842)
                    // ==========================================
                    currentPageNum++
                    val pageInfo4 = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, currentPageNum).create()
                    var page4Obj = pdfDocument.startPage(pageInfo4)
                    var canvas4 = page4Obj.canvas
                    canvas4.drawColor(colorWhite)
                    
                    // 1. Header (Primary Green)
                    paint.color = colorPrimaryGreen
                    canvas4.drawRoundRect(30f, 25f, 565f, 105f, 10f, 10f, paint)
                    
                    textPaint.color = colorWhite
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    textPaint.isFakeBoldText = true
                    textPaint.textSize = 18f
                    canvas4.drawText("نتائج وعلامات الامتحانات", 545f, 65f, textPaint)
                    
                    headerNamePaint.textAlign = android.graphics.Paint.Align.LEFT
                    canvas4.drawText(headerNameText, 50f, 62f, headerNamePaint)
                    canvas4.drawText("الدرجة الكلية: $overallPercentage%", 50f, 82f, headerNamePaint)
                    
                    // 2. Twin Stats Cards at Top
                    val drawPortraitExamCard: (Float, Float, String, String, Int) -> Unit = { l, r, label, value, bottomColor ->
                        val t = 120f
                        val b = 185f
                        paint.color = colorWhite
                        canvas4.drawRoundRect(l, t, r, b, 8f, 8f, paint)
                        
                        // border
                        paint.color = colorGrayBorder
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.strokeWidth = 1f
                        canvas4.drawRoundRect(l, t, r, b, 8f, 8f, paint)
                        paint.style = android.graphics.Paint.Style.FILL
                        
                        // Bottom bar
                        paint.color = bottomColor
                        canvas4.drawRect(l, b - 4f, r, b, paint)
                        
                        // Texts
                        textPaint.textAlign = android.graphics.Paint.Align.CENTER
                        textPaint.color = colorTextGray
                        textPaint.textSize = 10f
                        textPaint.isFakeBoldText = false
                        canvas4.drawText(label, (l + r) / 2f, t + 24f, textPaint)
                        
                        textPaint.color = colorDarkGray
                        textPaint.textSize = 13.5f
                        textPaint.isFakeBoldText = true
                        canvas4.drawText(value, (l + r) / 2f, t + 50f, textPaint)
                    }
                    
                    val highestExam = exams.maxByOrNull { if (it.maxScore > 0) (it.score * 100 / it.maxScore) else 0.0 }
                    val highestPct = if (exams.isNotEmpty()) exams.maxOf { if (it.maxScore > 0) (it.score * 100 / it.maxScore).toInt() else 0 } else 0
                    val highestScoreText = if (highestExam != null) "${highestExam.examName} (${highestPct}%)" else "لا يوجد"
                    
                    val lowestExam = exams.minByOrNull { if (it.maxScore > 0) (it.score * 100 / it.maxScore) else 0.0 }
                    val lowestPct = if (exams.isNotEmpty()) exams.minOf { if (it.maxScore > 0) (it.score * 100 / it.maxScore).toInt() else 0 } else 0
                    val lowestScoreText = if (lowestExam != null) "${lowestExam.examName} (${lowestPct}%)" else "لا يوجد"
                    
                    drawPortraitExamCard(307.5f, 565f, "أعلى درجة حصل عليها", highestScoreText, colorLightGreen)
                    drawPortraitExamCard(30f, 287.5f, "أقل درجة حصل عليها", lowestScoreText, colorRed)
                    
                    // 3. Wide Rating Card
                    paint.color = 0xFFECFDF5.toInt() // light green tint
                    canvas4.drawRoundRect(30f, 195f, 565f, 240f, 6f, 6f, paint)
                    paint.color = colorGrayBorder
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.strokeWidth = 1f
                    canvas4.drawRoundRect(30f, 195f, 565f, 240f, 6f, 6f, paint)
                    paint.style = android.graphics.Paint.Style.FILL
                    
                    paint.color = colorLightGreen
                    canvas4.drawRect(30f, 195f, 34f, 240f, paint)
                    
                    val academicRatingLabel = when {
                        overallPercentage >= 90 -> "ممتاز  (أ)"
                        overallPercentage >= 80 -> "جيد جداً  (ب)"
                        overallPercentage >= 65 -> "جيد  (ج)"
                        else -> "مقبول  (د)"
                    }
                    
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    textPaint.color = colorDarkGray
                    textPaint.textSize = 10f
                    textPaint.isFakeBoldText = true
                    canvas4.drawText("التقييم والتقدير العام للأداء: $academicRatingLabel  ($ratingExplanation)", 545f, 222f, textPaint)
                    
                    // 4. Exams Table with dynamic overflow
                    var yPos4 = 255f
                    
                    // Header
                    paint.color = 0xFF004D26.toInt()
                    canvas4.drawRoundRect(30f, yPos4, 565f, yPos4 + 28f, 6f, 6f, paint)
                    
                    textPaint.color = colorWhite
                    textPaint.textSize = 10.5f
                    textPaint.isFakeBoldText = true
                    textPaint.textAlign = android.graphics.Paint.Align.CENTER
                    
                    // الامتحان | الدرجة | من | النسبة | التقييم
                    canvas4.drawText("الامتحان", 487.5f, yPos4 + 18f, textPaint)
                    canvas4.drawText("درجة الطالب", 365f, yPos4 + 18f, textPaint)
                    canvas4.drawText("من", 280f, yPos4 + 18f, textPaint)
                    canvas4.drawText("النسبة", 195f, yPos4 + 18f, textPaint)
                    canvas4.drawText("التقييم", 90f, yPos4 + 18f, textPaint)
                    
                    yPos4 += 28f
                    
                    if (sortedExams.isEmpty()) {
                        textPaint.textAlign = android.graphics.Paint.Align.CENTER
                        textPaint.textSize = 12f
                        textPaint.color = android.graphics.Color.GRAY
                        canvas4.drawText("لم تجر اختبارات أكاديمية للطالب حتى تاريخ اليوم.", 297.5f, yPos4 + 40f, textPaint)
                    } else {
                        sortedExams.forEachIndexed { index, e ->
                            val numLines = getWrappedLinesCount(e.examName, 130f, textPaint)
                            val rowHeight = if (numLines > 1) 12f + (numLines * 12f) else 25f

                            if (yPos4 + rowHeight > 740f) {
                                // Draw Footer before finishing page
                                paint.color = colorGrayBorder
                                canvas4.drawRect(30f, 790f, 565f, 791f, paint)
                                textPaint.textSize = 9f
                                textPaint.isFakeBoldText = false
                                textPaint.color = colorTextGray
                                textPaint.textAlign = android.graphics.Paint.Align.LEFT
                                canvas4.drawText("صفحة $currentPageNum", 30f, 810f, textPaint)
                                textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                                canvas4.drawText("نتائج وعلامات الامتحانات للأداء الأكاديمي الشامل", 565f, 810f, textPaint)
                                
                                pdfDocument.finishPage(page4Obj)
                                
                                currentPageNum++
                                val pageInfoNew = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, currentPageNum).create()
                                page4Obj = pdfDocument.startPage(pageInfoNew)
                                canvas4 = page4Obj.canvas
                                canvas4.drawColor(colorWhite)
                                
                                // Header
                                paint.color = colorPrimaryGreen
                                canvas4.drawRoundRect(30f, 25f, 565f, 105f, 10f, 10f, paint)
                                textPaint.color = colorWhite
                                textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                                textPaint.isFakeBoldText = true
                                textPaint.textSize = 18f
                                canvas4.drawText("نتائج وعلامات الامتحانات (تابع)", 545f, 65f, textPaint)
                                
                                textPaint.textSize = 11f
                                textPaint.isFakeBoldText = false
                                textPaint.textAlign = android.graphics.Paint.Align.LEFT
                                canvas4.drawText(headerNameText, 50f, 62f, headerNamePaint)
                                canvas4.drawText("الدرجة الكلية: $overallPercentage%", 50f, 82f, headerNamePaint)
                                
                                yPos4 = 130f
                                
                                // Table Header Row
                                paint.color = 0xFF004D26.toInt()
                                canvas4.drawRoundRect(30f, yPos4, 565f, yPos4 + 28f, 6f, 6f, paint)
                                textPaint.color = colorWhite
                                textPaint.textSize = 10.5f
                                textPaint.isFakeBoldText = true
                                textPaint.textAlign = android.graphics.Paint.Align.CENTER
                                
                                canvas4.drawText("الامتحان", 487.5f, yPos4 + 18f, textPaint)
                                canvas4.drawText("درجة الطالب", 365f, yPos4 + 18f, textPaint)
                                canvas4.drawText("من", 280f, yPos4 + 18f, textPaint)
                                canvas4.drawText("النسبة", 195f, yPos4 + 18f, textPaint)
                                canvas4.drawText("التقييم", 90f, yPos4 + 18f, textPaint)
                                
                                yPos4 += 28f
                            }
                            
                            yPos4 += 2f
                            if (index % 2 == 1) {
                                paint.color = colorLightGray
                                canvas4.drawRect(30f, yPos4, 565f, yPos4 + rowHeight, paint)
                            }
                            
                            val cellCenterY = yPos4 + (rowHeight / 2f) + (textPaint.textSize / 2f) - 1f
                            
                            textPaint.color = colorDarkGray
                            textPaint.isFakeBoldText = false
                            textPaint.textSize = 10f
                            
                            // Write wrapped exam name
                            drawWrappedText(
                                canvas = canvas4,
                                text = e.examName,
                                x = 487.5f,
                                startY = yPos4 + (rowHeight / 2f) - ((numLines - 1) * 6f) + (textPaint.textSize / 2f) - 1f,
                                maxWidth = 130f,
                                paint = textPaint,
                                lineHeight = 12f,
                                align = android.graphics.Paint.Align.CENTER
                            )
                            
                            val examPct = if (e.maxScore > 0) (e.score * 100 / e.maxScore).toInt() else 0
                            val accentC = when {
                                examPct >= 85 -> colorLightGreen
                                examPct >= 65 -> colorOrange
                                else -> colorRed
                            }
                            
                            textPaint.textAlign = android.graphics.Paint.Align.CENTER
                            textPaint.color = accentC
                            textPaint.isFakeBoldText = true
                            canvas4.drawText("${e.score.toInt()}", 365f, cellCenterY, textPaint)
                            
                            textPaint.color = colorDarkGray
                            textPaint.isFakeBoldText = false
                            canvas4.drawText("${e.maxScore.toInt()}", 280f, cellCenterY, textPaint)
                            
                            textPaint.color = accentC
                            textPaint.isFakeBoldText = true
                            canvas4.drawText("$examPct%", 195f, cellCenterY, textPaint)
                            
                            val examRating = when {
                                examPct >= 90 -> "ممتاز"
                                examPct >= 80 -> "جيد جداً"
                                examPct >= 65 -> "جيد"
                                else -> "مقبول"
                            }
                            canvas4.drawText(examRating, 90f, cellCenterY, textPaint)
                            
                            yPos4 += rowHeight
                        }
                    }
                    
                    // Footer Page 4
                    paint.color = colorGrayBorder
                    canvas4.drawRect(30f, 790f, 565f, 791f, paint)
                    
                    textPaint.textSize = 9f
                    textPaint.isFakeBoldText = false
                    textPaint.color = colorTextGray
                    textPaint.textAlign = android.graphics.Paint.Align.LEFT
                    canvas4.drawText("صفحة $currentPageNum", 30f, 810f, textPaint)
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    canvas4.drawText("نتائج وعلامات الامتحانات للأداء الأكاديمي الشامل", 565f, 810f, textPaint)
                    
                    pdfDocument.finishPage(page4Obj)
                    
                    // ==========================================
                    // PAGE 5 — DETAILED BREAKDOWN OF ABSENCE & LATENESS (Portrait 595 x 842 - Dynamic Stream Layout)
                    // ==========================================
                    currentPageNum++
                    val pageInfo5 = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, currentPageNum).create()
                    var page5Obj = pdfDocument.startPage(pageInfo5)
                    var canvas5 = page5Obj.canvas
                    canvas5.drawColor(colorWhite)
                    
                    // 1. Header (Primary Green)
                    paint.color = colorPrimaryGreen
                    canvas5.drawRoundRect(30f, 25f, 565f, 105f, 10f, 10f, paint)
                    
                    textPaint.color = colorWhite
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    textPaint.isFakeBoldText = true
                    textPaint.textSize = 18f
                    canvas5.drawText("التفاصيل وشبكة الغيابات والتأخير", 545f, 65f, textPaint)
                    
                    headerNamePaint.textAlign = android.graphics.Paint.Align.LEFT
                    canvas5.drawText(headerNameText, 50f, 62f, headerNamePaint)
                    canvas5.drawText("سجل التاريخ التفصيلي للمتابعة", 50f, 82f, headerNamePaint)
                    
                    // Prepare Absence Grouping
                    val absentRecords = sortedAttendances.filter { it.status == AttendanceStatus.absent }
                    val lateRecords = sortedAttendances.filter { it.status == AttendanceStatus.late }
                    
                    val absencesByMonth = absentRecords.groupBy { record ->
                        val recordDate = if (record.attendanceDate.isNotBlank()) record.attendanceDate else record.timestamp
                        getYearMonthKey(recordDate)
                    }.toSortedMap()
                    
                    val latenessesByMonth = lateRecords.groupBy { record ->
                        val recordDate = if (record.attendanceDate.isNotBlank()) record.attendanceDate else record.timestamp
                        getYearMonthKey(recordDate)
                    }.toSortedMap()
                    
                    var yPos5 = 140f
                    
                    val startNewPage5: () -> Unit = {
                        paint.color = colorGrayBorder
                        canvas5.drawRect(30f, 790f, 565f, 791f, paint)
                        textPaint.textSize = 9f
                        textPaint.isFakeBoldText = false
                        textPaint.color = colorTextGray
                        textPaint.textAlign = android.graphics.Paint.Align.LEFT
                        canvas5.drawText("صفحة $currentPageNum", 30f, 810f, textPaint)
                        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                        canvas5.drawText("سجل رصد الغياب والتأثير التفصيلي", 565f, 810f, textPaint)
                        
                        pdfDocument.finishPage(page5Obj)
                        
                        currentPageNum++
                        val pageInfoNew = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, currentPageNum).create()
                        page5Obj = pdfDocument.startPage(pageInfoNew)
                        canvas5 = page5Obj.canvas
                        canvas5.drawColor(colorWhite)
                        
                        // Header
                        paint.color = colorPrimaryGreen
                        canvas5.drawRoundRect(30f, 25f, 565f, 105f, 10f, 10f, paint)
                        textPaint.color = colorWhite
                        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                        textPaint.isFakeBoldText = true
                        textPaint.textSize = 18f
                        canvas5.drawText("سجل رصد الغياب والتأخير التفصيلي (تابع)", 545f, 65f, textPaint)
                        
                        textPaint.textSize = 11f
                        textPaint.isFakeBoldText = false
                        textPaint.textAlign = android.graphics.Paint.Align.LEFT
                        canvas5.drawText(headerNameText, 50f, 62f, headerNamePaint)
                        canvas5.drawText("سجل تاريخي للمتابعة", 50f, 82f, headerNamePaint)
                        
                        yPos5 = 130f
                    }
                    
                    // --- SECTION 1: ABSENCES ---
                    if (yPos5 > 720f) {
                        startNewPage5()
                    }
                    
                    textPaint.color = colorRed
                    textPaint.textSize = 12f
                    textPaint.isFakeBoldText = true
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    canvas5.drawText("تفاصيل الغياب (حسب الشهور)", 545f, yPos5, textPaint)
                    yPos5 += 5f
                    
                    paint.color = colorRed
                    canvas5.drawRect(30f, yPos5, 565f, yPos5 + 1.5f, paint)
                    yPos5 += 20f
                    
                    if (absencesByMonth.isEmpty()) {
                        textPaint.color = colorTextGray
                        textPaint.textSize = 10f
                        textPaint.isFakeBoldText = false
                        canvas5.drawText("لا يوجد غياب مسجل للطالب حالياً", 545f, yPos5, textPaint)
                        yPos5 += 20f
                    } else {
                        absencesByMonth.forEach { (mKey, records) ->
                            if (yPos5 > 740f) {
                                startNewPage5()
                            }
                            
                            val monthName = arabicMonths[mKey.split("-").getOrNull(1)] ?: mKey
                            textPaint.color = colorDarkGray
                            textPaint.textSize = 10f
                            textPaint.isFakeBoldText = true
                            canvas5.drawText("شهر $monthName", 545f, yPos5, textPaint)
                            yPos5 += 18f
                            
                            records.forEach { record ->
                                if (yPos5 > 740f) {
                                    startNewPage5()
                                }
                                val recordDate = if (record.attendanceDate.isNotBlank()) record.attendanceDate else record.timestamp
                                val displayDay = com.example.data.DateUtils.getArabicDayName(recordDate)
                                val displayDateStr = DateUtils.formatDateForDisplay(recordDate)
                                val sess = sessionMap[record.sessionId]
                                val sessionNumText = if (sess != null && sess.sessionNumber > 0) "حصة (${sess.sessionNumber})" else "حصة"
                                
                                textPaint.color = colorTextGray
                                textPaint.textSize = 9.5f
                                textPaint.isFakeBoldText = false
                                canvas5.drawText("- $displayDateStr (يوافق يوم $displayDay) - $sessionNumText", 525f, yPos5, textPaint)
                                yPos5 += 16f
                            }
                            yPos5 += 5f
                        }
                    }
                    
                    yPos5 += 15f
                    
                    // --- SECTION 2: LATENESSES ---
                    if (yPos5 > 720f) {
                        startNewPage5()
                    }
                    
                    textPaint.color = colorOrange
                    textPaint.textSize = 12f
                    textPaint.isFakeBoldText = true
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    canvas5.drawText("تفاصيل التأخير (حسب الشهور)", 545f, yPos5, textPaint)
                    yPos5 += 5f
                    
                    paint.color = colorOrange
                    canvas5.drawRect(30f, yPos5, 565f, yPos5 + 1.5f, paint)
                    yPos5 += 20f
                    
                    if (latenessesByMonth.isEmpty()) {
                        textPaint.color = colorTextGray
                        textPaint.textSize = 10f
                        textPaint.isFakeBoldText = false
                        canvas5.drawText("لا يوجد تأخير مسجل للطالب حالياً", 545f, yPos5, textPaint)
                        yPos5 += 20f
                    } else {
                        latenessesByMonth.forEach { (mKey, records) ->
                            if (yPos5 > 740f) {
                                startNewPage5()
                            }
                            
                            val monthName = arabicMonths[mKey.split("-").getOrNull(1)] ?: mKey
                            textPaint.color = colorDarkGray
                            textPaint.textSize = 10f
                            textPaint.isFakeBoldText = true
                            canvas5.drawText("شهر $monthName", 545f, yPos5, textPaint)
                            yPos5 += 18f
                            
                            records.forEach { record ->
                                if (yPos5 > 740f) {
                                    startNewPage5()
                                }
                                val recordDate = if (record.attendanceDate.isNotBlank()) record.attendanceDate else record.timestamp
                                val displayDay = com.example.data.DateUtils.getArabicDayName(recordDate)
                                val displayDateStr = DateUtils.formatDateForDisplay(recordDate)
                                val arrivalTime = record.lateArrivalTime ?: "04:15 م"
                                
                                textPaint.color = colorTextGray
                                textPaint.textSize = 9.5f
                                textPaint.isFakeBoldText = false
                                canvas5.drawText("- $displayDateStr (يوافق يوم $displayDay) - حضر $arrivalTime", 525f, yPos5, textPaint)
                                yPos5 += 16f
                            }
                            yPos5 += 5f
                        }
                    }
                    
                    // Final page 5 footer
                    paint.color = colorGrayBorder
                    canvas5.drawRect(30f, 790f, 565f, 791f, paint)
                    
                    textPaint.textSize = 9f
                    textPaint.isFakeBoldText = false
                    textPaint.color = colorTextGray
                    textPaint.textAlign = android.graphics.Paint.Align.LEFT
                    canvas5.drawText("صفحة $currentPageNum", 30f, 810f, textPaint)
                    textPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    canvas5.drawText("مركز التفوق الرقمي - رصد ذكي وتأمين البيانات", 565f, 810f, textPaint)
                    
                    pdfDocument.finishPage(page5Obj)
                    
                    // Save to app internal cache
                    val cachePath = File(context.cacheDir, "reports")
                    cachePath.mkdirs()
                    val pdfFile = File(cachePath, fileName)
                    FileOutputStream(pdfFile).use { output ->
                        pdfDocument.writeTo(output)
                    }
                    pdfDocument.close()
                    pdfFile
                }

                // Create Uri safely via FileProvider
                val cleanUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    FILE_PROVIDER_AUTHORITY,
                    resultFile
                )

                progressDialog.dismiss()

                if (viewImmediately) {
                    if (!isPdfViewerInstalled(context)) {
                        Toast.makeText(context, "تنبيه: لا يوجد تطبيق قارئ ملفات PDF مثبت على جهازك، سنفتح لك واجهة المشاركة لمشاهدة التقرير.", Toast.LENGTH_LONG).show()
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_SUBJECT, "عرض تقرير الطالب: ${student.name}")
                            putExtra(Intent.EXTRA_STREAM, cleanUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "عرض ومشاركة ملف PDF للطالب"))
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(cleanUri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val chooser = Intent.createChooser(intent, "عرض ملف PDF")
                            context.startActivity(chooser)
                        }
                    }
                } else {
                    // save directly inside Downloads!
                    val savedToGallery = withContext(Dispatchers.IO) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                }
                                val uri = context.contentResolver.insert(
                                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, 
                                    contentValues
                                )
                                uri?.let { outputUri ->
                                    context.contentResolver.openOutputStream(outputUri)?.use { output ->
                                        resultFile.inputStream().use { input ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                                true
                            } else {
                                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                                val destFile = File(downloadsDir, fileName)
                                resultFile.inputStream().use { input ->
                                    destFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            false
                        }
                    }

                    if (savedToGallery) {
                        Toast.makeText(context, "تم حفظ تقرير الطالب في مجلد التنزيلات (Downloads) بنجاح!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "فشل حفظ الملف في المجلد العام، سنقوم بفتح واجهة المشاركة والمحافظة على ملف PDF.", Toast.LENGTH_LONG).show()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_SUBJECT, "تنزيل تقرير الطالب: ${student.name}")
                            putExtra(Intent.EXTRA_STREAM, cleanUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "تحميل ومشاركة ملف PDF للطالب"))
                    }
                }

            } catch (e: Exception) {
                progressDialog.dismiss()
                Toast.makeText(context, "خطأ أثناء توليد ملف PDF: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
