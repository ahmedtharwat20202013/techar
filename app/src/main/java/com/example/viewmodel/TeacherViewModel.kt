package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.repository.TeacherRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class PaymentStats(
    val monthlyRevenue: Double = 0.0,
    val totalDebt: Double = 0.0,
    val paidCount: Int = 0,
    val unpaidCount: Int = 0
)

class TeacherViewModel(private val repository: TeacherRepository, private val application: android.app.Application) : ViewModel() {

    // Active/Selected items for Navigation/Editing (optional state)
    private val _selectedGroupId = MutableStateFlow<Int?>(null)
    val selectedGroupId = _selectedGroupId.asStateFlow()

    private val _selectedStudentId = MutableStateFlow<Int?>(null)
    val selectedStudentId = _selectedStudentId.asStateFlow()

    private val _selectedSessionId = MutableStateFlow<Int?>(null)
    val selectedSessionId = _selectedSessionId.asStateFlow()

    private val _notification = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val notification = _notification.asSharedFlow()

    private val arabicMonths = arrayOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )
    private val englishMonths = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    fun toYearMonth(input: String): String {
        val trimmed = input.trim()
        if (trimmed.matches(Regex("\\d{4}-\\d{2}"))) return trimmed
        val parts = trimmed.split(" ")
        if (parts.size == 2) {
            val monthPart = parts[0]
            val yearPart = parts[1]
            var mIdx = arabicMonths.indexOf(monthPart)
            if (mIdx == -1) mIdx = englishMonths.indexOf(monthPart)
            if (mIdx != -1) {
                return String.format(Locale.ENGLISH, "%s-%02d", yearPart, mIdx + 1)
            }
        }
        val clean = trimmed.replace("/", "-")
        val segments = clean.split("-")
        if (segments.size >= 2) {
            val yr = segments[0]
            val mn = segments[1].toIntOrNull() ?: 1
            return String.format(Locale.ENGLISH, "%s-%02d", yr, mn)
        }
        return "2026-06"
    }

    fun fromYearMonth(yearMonth: String): String {
        val parts = yearMonth.trim().split("-")
        if (parts.size == 2) {
            val year = parts[0]
            val month = parts[1].toIntOrNull()
            if (month != null && month in 1..12) {
                return "${arabicMonths[month - 1]} $year"
            }
        }
        return yearMonth
    }

    // Base Flows from DB (Mapped seamlessly to legacy String representations for the UI)
    val groups = repository.allGroups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val groupsWithSessions = repository.getAllGroupsWithSessionsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val students = repository.allStudents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sessions = repository.allSessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val payments = repository.allPayments.map { list ->
        list.map { it.copy(month = fromYearMonth(it.month)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    val selectedBillingPeriod = MutableStateFlow(
        BillingPeriod(
            month = DateUtils.getCairoCalendar().get(Calendar.MONTH) + 1,
            year = DateUtils.getCairoCalendar().get(Calendar.YEAR)
        )
    )

    val paymentStats = combine(students, groups, repository.allPayments, selectedBillingPeriod) { stds, grps, pays, period ->
        val groupMap = grps.associateBy { it.id }
        val periodPayments = pays.filter {
            val bp = it.getBillingPeriod()
            bp.month == period.month && bp.year == period.year
        }
        val studentPayments = periodPayments.associateBy { it.studentId }

        var paidCount = 0
        var unpaidCount = 0
        var monthlyRevenue = 0.0
        var totalDebt = 0.0

        stds.forEach { student ->
            val pay = studentPayments[student.id]
            if (pay != null && pay.isPaid) {
                paidCount++
                monthlyRevenue += pay.amountPaid
            } else {
                unpaidCount++
                val expectedFee = groupMap[student.groupId]?.monthlyFee ?: 200.0
                totalDebt += expectedFee
            }
        }
        PaymentStats(
            monthlyRevenue = monthlyRevenue,
            totalDebt = totalDebt,
            paidCount = paidCount,
            unpaidCount = unpaidCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PaymentStats())
    
    val exams = repository.allExamScores.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val attendance = repository.allAttendance.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val deletedStudents = repository.getDeletedStudentsFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentDate = MutableStateFlow(DateUtils.formatStandard("yyyy-MM-dd"))
    val currentDate = _currentDate.asStateFlow()

    val todaysScheduledGroups = combine(groups, _currentDate) { grps, _ ->
        val calendar = DateUtils.getCairoCalendar()
        val englishDayName = when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SATURDAY -> "SATURDAY"
            java.util.Calendar.SUNDAY -> "SUNDAY"
            java.util.Calendar.MONDAY -> "MONDAY"
            java.util.Calendar.TUESDAY -> "TUESDAY"
            java.util.Calendar.WEDNESDAY -> "WEDNESDAY"
            java.util.Calendar.THURSDAY -> "THURSDAY"
            java.util.Calendar.FRIDAY -> "FRIDAY"
            else -> ""
        }
        grps.filter { 
            val days = if (it.daysOfWeek.isEmpty() && it.scheduleDays.isNotBlank()) {
                DateUtils.parseScheduleToDaysOfWeek(it.scheduleDays)
            } else {
                it.daysOfWeek
            }
            days.contains(englishDayName)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Prepopulate with rich dummy data if DB is empty, run automations and cleanup orphan sessions
        viewModelScope.launch {
            repository.prepopulateIfEmpty()
            
            // Check and run monthly billing automation limit to once per month
            val currentMonthStr = DateUtils.formatStandard("yyyy-MM")
            val prefs = application.getSharedPreferences("BillingPrefs", android.content.Context.MODE_PRIVATE)
            val lastRun = prefs.getString("lastBillingRunMonth", "")
            if (lastRun != currentMonthStr) {
                repository.triggerMonthlyBillingAutomation()
                prefs.edit().putString("lastBillingRunMonth", currentMonthStr).apply()
            }
        }
        
        // Start a daemon coroutine to check and sync day changes automatically
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10000) // check every 10 seconds
                val today = DateUtils.formatStandard("yyyy-MM-dd")
                if (_currentDate.value != today) {
                    _currentDate.value = today
                }
            }
        }
    }

    // Dynamic lists based on selected parameters
    fun getStudentsByGroup(groupId: Int): Flow<List<Student>> {
        return repository.getStudentsByGroup(groupId)
    }

    fun getSessionsByGroup(groupId: Int): Flow<List<Session>> {
        return repository.getSessionsByGroup(groupId)
    }

    fun getPaymentsForStudent(studentId: Int): Flow<List<Payment>> {
        return repository.getPaymentsForStudent(studentId).map { list ->
            list.map { it.copy(month = fromYearMonth(it.month)) }
        }
    }

    fun getExamScoresForStudent(studentId: Int): Flow<List<ExamScore>> {
        return repository.getExamScoresForStudent(studentId)
    }

    // Sessions today logic
    fun getSessionsForToday(date: String): Flow<List<Session>> {
        return repository.getSessionsForDate(date)
    }

    fun getAttendanceForSession(sessionId: Int): Flow<List<AttendanceRecord>> {
        return repository.getAttendanceForSession(sessionId)
    }

    fun getAttendanceForStudent(studentId: Int): Flow<List<AttendanceRecord>> {
        return repository.getAttendanceForStudent(studentId)
    }

    suspend fun getGroupById(id: Int): Group? {
        return repository.getGroupById(id)
    }

    suspend fun getStudentById(id: Int): Student? {
        return repository.getStudentById(id)
    }

    suspend fun getSessionById(id: Int): Session? {
        return repository.getSessionById(id)
    }

    // Current month identifier for default dashboard operations
    fun getCurrentMonthYearEnglish(): String {
        return DateUtils.formatStandard("MMMM yyyy")
    }

    fun getCurrentMonthYearArabic(): String {
        val calendar = DateUtils.getCairoCalendar()
        val monthIdx = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)
        return "${arabicMonths[monthIdx]} $year"
    }

    // Analytics calculations computed dynamically over reactive StateFlows
    val dashboardStats = combine(
        students,
        groups,
        payments,
        sessions,
        attendance,
        exams
    ) { array ->
        val studs = array[0] as List<Student>
        val grps = array[1] as List<Group>
        val pays = array[2] as List<Payment>
        val sess = array[3] as List<Session>
        val atts = array[4] as List<AttendanceRecord>
        val exms = array[5] as List<ExamScore>

        val activeMonth = getCurrentMonthYearArabic()
        val activeMonthCode = toYearMonth(activeMonth)
        
        val totalStudents = studs.size
        val totalGroups = grps.size
        
        val groupMap = grps.associateBy { it.id }
        
        // Find existing payments for this month grouped by studentId
        val currentMonthPayments = pays.filter { toYearMonth(it.month) == activeMonthCode }
        val studentPayments = currentMonthPayments.groupBy { it.studentId }
            .mapValues { entry -> entry.value.first() }
        
        var paidCount = 0
        var unpaidCount = 0
        var monthlyRevenue = 0.0
        var totalDebt = 0.0
        
        studs.forEach { student ->
            val payment = studentPayments[student.id]
            if (payment != null && payment.isPaid) {
                paidCount++
                monthlyRevenue += payment.amountPaid
            } else {
                unpaidCount++
                val expectedFee = groupMap[student.groupId]?.monthlyFee ?: 200.0
                totalDebt += expectedFee
            }
        }
        
        val totalAttendanceRecorded = atts.size
        val presentCount = atts.count { it.isPresent }
        val absentCount = atts.count { !it.isPresent }
        val overallAttendanceRate = if (totalAttendanceRecorded > 0) {
            ((presentCount.toDouble() / totalAttendanceRecorded) * 100).toInt()
        } else {
            100
        }

        // Exam Statistics calculations
        val totalExamsCount = exms.map { Pair(it.examName, it.date) }.distinct().size
        val examAverageScore = if (exms.isNotEmpty()) exms.map { (it.score * 100.0) / it.maxScore }.average() else 0.0
        val passedCount = exms.count { it.score >= it.maxScore * 0.5 }
        val examPassRate = if (exms.isNotEmpty()) ((passedCount.toDouble() / exms.size) * 100).toInt() else 0
        val examFailRate = if (exms.isNotEmpty()) (((exms.size - passedCount).toDouble() / exms.size) * 100).toInt() else 0
        
        DashboardStats(
            totalStudents = totalStudents,
            totalGroups = totalGroups,
            paidStudentsCount = paidCount,
            unpaidStudentsCount = unpaidCount,
            monthlyRevenue = monthlyRevenue,
            totalDebt = totalDebt,
            overallAttendanceRate = overallAttendanceRate,
            totalPresent = presentCount,
            totalAbsent = absentCount,
            totalExamsCount = totalExamsCount,
            examAverageScore = examAverageScore,
            examPassRate = examPassRate,
            examFailRate = examFailRate
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())


    // --- OPERATIONS (MUTATIONS) ---

    fun onGroupSelected(groupId: Int?) {
        _selectedGroupId.value = groupId
    }

    fun onStudentSelected(studentId: Int?) {
        _selectedStudentId.value = studentId
    }

    fun onSessionSelected(sessionId: Int?) {
        _selectedSessionId.value = sessionId
    }

    fun addGroup(name: String, startDate: String, fee: Double, schedule: String) {
        addGroup(name, startDate, fee, schedule, GroupType.public, BillingMode.monthly, 8)
    }

    fun addGroup(
        name: String,
        startDate: String,
        fee: Double,
        schedule: String,
        groupType: GroupType,
        billingMode: BillingMode = BillingMode.monthly,
        sessionsPerMonth: Int = 8
    ) {
        viewModelScope.launch {
            repository.insertGroup(
                Group(
                    name = name,
                    startDate = startDate,
                    monthlyFee = fee,
                    scheduleDays = schedule,
                    groupType = groupType,
                    billingMode = billingMode,
                    sessionsPerMonth = sessionsPerMonth,
                    daysOfWeek = DateUtils.parseScheduleToDaysOfWeek(schedule)
                )
            )
        }
    }

    fun refreshTodaySchedule() {
        _currentDate.value = DateUtils.formatStandard("yyyy-MM-dd") // هذا يُعيد تشغيل الـ combine
    }

    fun updateGroup(group: Group) {
        viewModelScope.launch {
            val updated = group.copy(
                daysOfWeek = DateUtils.parseScheduleToDaysOfWeek(group.scheduleDays)
            )
            repository.updateGroup(updated)
            refreshTodaySchedule()  // <-- إجبار إعادة الحساب
        }
    }

    fun deleteGroup(group: Group) {
        viewModelScope.launch {
            repository.deleteGroup(group)
        }
    }

    fun addStudent(
        groupId: Int,
        name: String,
        parentPhone: String,
        joinDate: String,
        notes: String,
        sessionsRemaining: Int = 0
    ) {
        viewModelScope.launch {
            val currentMonthArabic = getCurrentMonthYearArabic()
            val currentMonthCode = toYearMonth(currentMonthArabic)
            repository.addStudentWithProRataBilling(
                Student(
                    groupId = groupId,
                    name = name,
                    parentPhone = parentPhone,
                    joinDate = joinDate,
                    notes = notes,
                    sessionsRemaining = sessionsRemaining
                ),
                currentMonthCode
            )
        }
    }

    fun updateStudent(student: Student) {
        viewModelScope.launch {
            repository.updateStudent(student)
        }
    }

    fun deleteStudent(student: Student) {
        viewModelScope.launch {
            repository.updateStudent(student.copy(isActive = false, deletedAt = DateUtils.formatStandard("yyyy-MM-dd HH:mm:ss")))
        }
    }

    fun deleteStudentPermanently(student: Student) {
        viewModelScope.launch {
            repository.deleteStudent(student)
        }
    }

    fun restoreStudent(student: Student) {
        viewModelScope.launch {
            repository.updateStudent(student.copy(isActive = true, deletedAt = null))
        }
    }

    fun extractMonthYearFromDate(dateStr: String): String {
        try {
            val normalized = dateStr.replace("-", "/")
            val parts = normalized.split("/")
            if (parts.size >= 2) {
                val year = parts[0]
                val monthInt = parts[1].toIntOrNull() ?: 1
                if (monthInt in 1..12) {
                    return "${englishMonths[monthInt - 1]} $year"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return getCurrentMonthYearEnglish()
    }

    fun extractMonthYearArabic(dateStr: String): String {
        try {
            val normalized = dateStr.replace("-", "/")
            val parts = normalized.split("/")
            if (parts.size >= 2) {
                val year = parts[0]
                val monthInt = parts[1].toIntOrNull() ?: 1
                if (monthInt in 1..12) {
                    return "${arabicMonths[monthInt - 1]} $year"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return getCurrentMonthYearArabic()
    }

    fun addSession(groupId: Int, date: String, time: String) {
        viewModelScope.launch {
            val normalizedDate = date.replace("/", "-")
            val existing = repository.allSessions.first().find { it.groupId == groupId && (it.date == normalizedDate || it.date.replace("/", "-") == normalizedDate) }
            if (existing == null) {
                val monthYear = extractMonthYearFromDate(normalizedDate)
                val createdAt = DateUtils.formatStandard("yyyy-MM-dd'T'HH:mm:ss'Z'")
                repository.insertSession(
                    Session(
                        groupId = groupId,
                        date = normalizedDate,
                        time = time,
                        monthYear = monthYear,
                        createdAt = createdAt
                    )
                )
            }
        }
    }

    fun triggerMonthlyBillingAutomation() {
        viewModelScope.launch {
            repository.triggerMonthlyBillingAutomation()
        }
    }

    suspend fun getOrCreateSessionForToday(groupId: Int, date: String): Int {
        val normalizedDate = date.replace("/", "-")
        val groupSessions = repository.getSessionsByGroup(groupId).first().filter { it.date == normalizedDate || it.date.replace("/", "-") == normalizedDate }
        for (session in groupSessions) {
            val attendanceRecords = repository.getAttendanceForSession(session.id).first()
            if (attendanceRecords.isEmpty()) {
                return session.id // Reuse active uncommitted session on this day
            }
        }
        val arabicTime = formatCurrentTimeArabic()
        val monthYear = extractMonthYearFromDate(normalizedDate)
        val createdAt = DateUtils.formatStandard("yyyy-MM-dd'T'HH:mm:ss'Z'")
        val newSessionId = repository.insertSession(
            Session(
                groupId = groupId,
                date = normalizedDate,
                time = arabicTime,
                monthYear = monthYear,
                createdAt = createdAt
            )
        )
        return newSessionId.toInt()
    }

    fun checkSessionExistsFlow(groupId: Int, todayDateStr: String): Flow<Boolean> {
        val cleanDate = if (todayDateStr.length >= 10) todayDateStr.substring(0, 10).replace("/", "-") else todayDateStr.replace("/", "-")
        return repository.checkSessionExistsFlow(groupId, cleanDate)
    }

    fun isSessionRecordedTodayFlow(groupId: Int, todayDateStr: String): Flow<Boolean> {
        val cleanDate = if (todayDateStr.length >= 10) todayDateStr.substring(0, 10).replace("/", "-") else todayDateStr.replace("/", "-")
        return repository.isSessionRecordedTodayFlow(groupId, cleanDate)
    }

    fun recordManualSession(groupId: Int) {
        viewModelScope.launch {
            val todayDate = DateUtils.formatStandard("yyyy-MM-dd")
            val arabicTime = formatCurrentTimeArabic()
            val monthYear = extractMonthYearFromDate(todayDate)
            val createdAt = DateUtils.formatStandard("yyyy-MM-dd'T'HH:mm:ss'Z'")
            
            val success = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                repository.recordManualSession(
                    groupId = groupId,
                    todayDate = todayDate,
                    timeStr = arabicTime,
                    monthYearStr = monthYear,
                    createdAtStr = createdAt
                )
            }
            if (success) {
                _notification.emit("تم تسجيل الحصة اليومية بنجاح!")
            } else {
                _notification.emit("Already Recorded")
            }
        }
    }

    private fun formatCurrentTimeArabic(): String {
        val now = DateUtils.getCairoCalendar()
        val hour = now.get(Calendar.HOUR)
        val h = if (hour == 0) 12 else hour
        val minute = now.get(Calendar.MINUTE)
        val amPm = if (now.get(Calendar.AM_PM) == Calendar.AM) "ص" else "م"
        return String.format(Locale.ENGLISH, "%02d:%02d %s", h, minute, amPm)
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }

    fun saveAttendance(sessionId: Int, studentAttendance: Map<Int, AttendanceStatus>, lateArrivalTimes: Map<Int, String?>) {
        val timestamp = DateUtils.formatStandard("yyyy-MM-dd HH:mm:ss")
        viewModelScope.launch {
            try {
                if (sessionId <= 0) return@launch
                repository.commitAttendance(sessionId, studentAttendance, lateArrivalTimes, timestamp)

                // Retrieve updated check for remaining sessions low balance warning
                val session = repository.getSessionById(sessionId)
                if (session != null) {
                    val group = repository.getGroupById(session.groupId)
                    if (group != null && group.groupType == GroupType.private && group.billingMode == BillingMode.per_session) {
                        val activeStudents = repository.getStudentsByGroup(session.groupId).first()
                        val lowBalanceStudents = activeStudents.filter {
                            val status = studentAttendance[it.id] ?: AttendanceStatus.present
                            val isPresent = status == AttendanceStatus.present || status == AttendanceStatus.late
                            isPresent && it.sessionsRemaining <= 1
                        }
                        if (lowBalanceStudents.isNotEmpty()) {
                            val names = lowBalanceStudents.joinToString("، ") { it.name }
                            _notification.emit("برجاء تجديد الاشتراك للطلاب: $names (الرصيد حرج!)")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AttendanceDebug", "Failed to commit atomic attendance transaction", e)
            }
        }
    }

    // --- DAILY NOTES & FEEDBACK SYSTEM ---
    fun getDailyNotesForGroup(groupId: Int): Flow<List<DailyNote>> {
        return repository.getDailyNotesForGroup(groupId)
    }

    suspend fun getDailyNote(groupId: Int, date: String): DailyNote? {
        return repository.getDailyNote(groupId, date)
    }

    fun saveDailyNote(groupId: Int, date: String, sessionNumber: Int, content: String) {
        viewModelScope.launch {
            repository.insertDailyNote(
                DailyNote(
                    groupId = groupId,
                    date = date,  // <-- استخدم date المُمرر
                    sessionNumber = sessionNumber,
                    content = content
                )
            )
        }
    }

    // --- CONFIRM CASH PAYMENT ENDPOINT LAYER ---
    fun confirmPayment(paymentId: Int) {
        viewModelScope.launch {
            repository.confirmPayment(paymentId)
        }
    }

    private fun oldSaveAttendanceDummy(sessionId: Int, studentAttendance: Map<Int, Boolean>) {
        val timestamp = DateUtils.formatStandard("yyyy-MM-dd HH:mm:ss")
        android.util.Log.d("AttendanceDebug", "[Timestamp $timestamp] Attempting to save attendance for sessionId: $sessionId. Total students: ${studentAttendance.size}")
        
        viewModelScope.launch {
            try {
                if (sessionId <= 0) {
                    android.util.Log.e("AttendanceDebug", "Validation Error: ID: $sessionId is invalid.")
                    return@launch
                }
                
                val session = repository.getSessionById(sessionId)
                if (session == null) {
                    android.util.Log.e("AttendanceDebug", "Session $sessionId not found inside database")
                    return@launch
                }
                
                repository.deleteAttendanceForSession(sessionId)
                
                val records = studentAttendance.map { (studentId, present) ->
                    android.util.Log.v("AttendanceDebug", "Drafting record: StudentID: $studentId, Present: $present, TS: $timestamp")
                    AttendanceRecord(
                        sessionId = sessionId,
                        studentId = studentId,
                        isPresent = present,
                        timestamp = timestamp,
                        status = if (present) AttendanceStatus.present else AttendanceStatus.absent
                    ).apply {
                        groupId = session.groupId
                        date = session.date
                    }
                }
                
                repository.insertAttendanceBatch(records)
                android.util.Log.i("AttendanceDebug", "Attendance successfully committed for sessionId: $sessionId. Count: ${records.size} records. TS: $timestamp")
            } catch (e: Exception) {
                android.util.Log.e("AttendanceDebug", "Failed to commit attendance records for sessionId: $sessionId", e)
            }
        }
    }

    fun addPayment(studentId: Int, month: String, amount: Double, isPaid: Boolean) {
        viewModelScope.launch {
            val dbMonth = toYearMonth(month)
            val paidAt = if (isPaid) System.currentTimeMillis() else null
            val paymentDate = if (isPaid) DateUtils.formatStandard("yyyy-MM-dd") else null
            val paymentTime = if (isPaid) DateUtils.formatStandard("HH:mm") else null
            repository.savePayment(
                studentId = studentId,
                month = dbMonth,
                amount = amount,
                isPaid = isPaid,
                paymentDate = paymentDate,
                paymentTime = paymentTime,
                paidAt = paidAt
            )
        }
    }

    fun updatePayment(payment: Payment) {
        viewModelScope.launch {
            val dbPayment = payment.copy(month = toYearMonth(payment.month))
            repository.updatePayment(dbPayment)
        }
    }

    fun addExamScore(studentId: Int, examName: String, score: Double, maxScore: Double, date: String) {
        viewModelScope.launch {
            repository.deleteExamScoreSpecific(studentId, examName, date)
            repository.insertExamScore(
                ExamScore(
                    studentId = studentId,
                    examName = examName,
                    score = score,
                    maxScore = maxScore,
                    date = date
                )
            )
        }
    }

    // --- STUDENT PROFILE ANALYTICS & COMPUTED STATS ---

    fun getStudentProfileStats(studentId: Int, groupId: Int): Flow<StudentProfileStats> {
        val studentFlow = repository.getStudentByIdFlow(studentId)
        val groupFlow = repository.getGroupByIdFlow(groupId)
        val attendanceFlow = repository.getAttendanceForStudent(studentId)
        val allGroupSessionsFlow = repository.getSessionsByGroup(groupId)

        return combine(studentFlow, groupFlow, attendanceFlow, allGroupSessionsFlow) { student, group, attendances, sessions ->
            if (student == null || group == null) return@combine StudentProfileStats()

            val normJoinDate = student.joinDate.replace("-", "/")
            val preJoinSessions = sessions.filter { it.date.replace("-", "/") < normJoinDate }.size

            val presents = attendances.filter { it.isPresent }
            val firstPresentSessionId = presents.map { it.sessionId }.toSet()
            val firstPresentSessionObj = sessions.filter { firstPresentSessionId.contains(it.id) }.minByOrNull { it.date.replace("-", "/") }
            val firstAttendanceDate = firstPresentSessionObj?.date ?: "لم يحضر بعد"

            val activeSessions = sessions.filter { it.date.replace("-", "/") >= normJoinDate }
            val activeSessionIds = activeSessions.map { it.id }.toSet()
            val activeAttendance = attendances.filter { activeSessionIds.contains(it.sessionId) }
            val presentsCount = activeAttendance.count { it.isPresent }
            val totalActiveRecorded = activeAttendance.size

            val attendancePercentage = if (totalActiveRecorded > 0) {
                (presentsCount.toDouble() / totalActiveRecorded * 100).toInt()
            } else {
                100
            }

            StudentProfileStats(
                groupName = group.name,
                groupStartDate = group.startDate,
                studentJoinDate = student.joinDate,
                firstAttendanceDate = firstAttendanceDate,
                attendancePercentage = attendancePercentage,
                missedSessionsBeforeJoin = preJoinSessions,
                presentCount = presentsCount,
                absentCount = totalActiveRecorded - presentsCount,
                totalGroupSessions = sessions.size,
                studentActiveSessions = activeSessions.size
            )
        }
    }

    // --- SESSIONS TODAY QUICK ATTENDANCE UTILITY ---
    fun getSessionsForDateStatic(dateString: String): Flow<List<SessionTodayUi>> {
        return combine(repository.allSessions, repository.allGroups, repository.allStudents) { sessList, groupList, studList ->
            sessList.filter { it.date == dateString }.map { sess ->
                val grp = groupList.find { it.id == sess.groupId }
                val numStudents = studList.count { it.groupId == sess.groupId }
                SessionTodayUi(
                    sessionId = sess.id,
                    groupId = sess.groupId,
                    groupName = grp?.name ?: "فصل غير معروف",
                    time = sess.time,
                    studentCount = numStudents
                )
            }
        }
    }

    fun clearAllDatabaseData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearAllDatabaseData()
            onComplete()
        }
    }
}

// Data Classes for States
data class DashboardStats(
    val totalStudents: Int = 0,
    val totalGroups: Int = 0,
    val paidStudentsCount: Int = 0,
    val unpaidStudentsCount: Int = 0,
    val monthlyRevenue: Double = 0.0,
    val totalDebt: Double = 0.0,
    val overallAttendanceRate: Int = 0,
    val totalPresent: Int = 0,
    val totalAbsent: Int = 0,
    val totalExamsCount: Int = 0,
    val examAverageScore: Double = 0.0,
    val examPassRate: Int = 0,
    val examFailRate: Int = 0
)

data class StudentProfileStats(
    val groupName: String = "",
    val groupStartDate: String = "",
    val studentJoinDate: String = "",
    val firstAttendanceDate: String = "",
    val attendancePercentage: Int = 100,
    val missedSessionsBeforeJoin: Int = 0,
    val presentCount: Int = 0,
    val absentCount: Int = 0,
    val totalGroupSessions: Int = 0,
    val studentActiveSessions: Int = 0
)

data class SessionTodayUi(
    val sessionId: Int,
    val groupId: Int,
    val groupName: String,
    val time: String,
    val studentCount: Int
)

class TeacherViewModelFactory(private val repository: TeacherRepository, private val application: android.app.Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TeacherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TeacherViewModel(repository, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
