package com.example.repository

import com.example.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class TeacherRepository(private val appDao: AppDao, private val database: AppDatabase) {

    // Groups
    val allGroups: Flow<List<Group>> = appDao.getAllGroups()
    fun getAllGroupsWithSessionsFlow(): Flow<List<GroupWithSessions>> = appDao.getAllGroupsWithSessionsFlow()
    suspend fun getGroupById(id: Int): Group? = appDao.getGroupById(id)
    fun getGroupByIdFlow(id: Int): Flow<Group?> = appDao.getGroupByIdFlow(id)
    suspend fun insertGroup(group: Group): Long = appDao.insertGroup(group)
    suspend fun updateGroup(group: Group) = appDao.updateGroup(group)
    suspend fun deleteGroup(group: Group) = appDao.deleteGroup(group)

    // Students
    val allStudents: Flow<List<Student>> = appDao.getAllStudents()
    fun getDeletedStudentsFlow(): Flow<List<Student>> = appDao.getDeletedStudentsFlow()
    fun getStudentsByGroup(groupId: Int): Flow<List<Student>> = appDao.getStudentsByGroup(groupId)
    suspend fun getStudentById(id: Int): Student? = appDao.getStudentById(id)
    fun getStudentByIdFlow(id: Int): Flow<Student?> = appDao.getStudentByIdFlow(id)

    private fun forceDatabaseCheckpoint() {
        try {
            database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
        } catch (e: Exception) {
            android.util.Log.e("TeacherRepository", "Error running full DB checkpoint", e)
        }
    }

    suspend fun insertStudent(student: Student): Long = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val id = appDao.insertStudent(student)
        forceDatabaseCheckpoint()
        id
    }
    suspend fun updateStudent(student: Student) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        appDao.updateStudent(student)
        forceDatabaseCheckpoint()
    }
    suspend fun deleteStudent(student: Student) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        appDao.deleteStudent(student)
        forceDatabaseCheckpoint()
    }

    // Sessions
    val allSessions: Flow<List<Session>> = appDao.getAllSessions()
    fun getSessionsByGroup(groupId: Int): Flow<List<Session>> = appDao.getSessionsByGroup(groupId)
    fun getSessionsForDate(date: String): Flow<List<Session>> = appDao.getSessionsForDate(date)
    suspend fun getSessionById(id: Int): Session? = appDao.getSessionById(id)
    suspend fun getSessionByGroupAndDate(groupId: Int, date: String): Session? = appDao.getSessionByGroupAndDate(groupId, date)
    fun checkSessionExistsFlow(groupId: Int, todayDateStr: String): Flow<Boolean> = appDao.checkSessionExistsFlow(groupId, todayDateStr)
    fun isSessionRecordedTodayFlow(groupId: Int, todayDate: String): Flow<Boolean> = appDao.isSessionRecordedTodayFlow(groupId, todayDate)
    suspend fun insertSession(session: Session): Long = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val id = appDao.insertSession(session)
        forceDatabaseCheckpoint()
        id
    }
    suspend fun deleteSession(session: Session) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        appDao.deleteSession(session)
        forceDatabaseCheckpoint()
    }

    // Attendance
    val allAttendance: Flow<List<AttendanceRecord>> = appDao.getAllAttendance()
    fun getAttendanceForSession(sessionId: Int): Flow<List<AttendanceRecord>> = appDao.getAttendanceForSession(sessionId)
    fun getAttendanceForStudent(studentId: Int): Flow<List<AttendanceRecord>> = appDao.getAttendanceForStudent(studentId)
    suspend fun insertAttendance(record: AttendanceRecord) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        appDao.insertAttendance(record)
        forceDatabaseCheckpoint()
    }
    suspend fun insertAttendanceBatch(records: List<AttendanceRecord>) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        appDao.insertAttendanceBatch(records)
        forceDatabaseCheckpoint()
    }
    suspend fun deleteAttendanceForSession(sessionId: Int) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        appDao.deleteAttendanceForSession(sessionId)
        forceDatabaseCheckpoint()
    }

    // Payments
    val allPayments: Flow<List<Payment>> = appDao.getAllPayments()
    fun getPaymentsForStudent(studentId: Int): Flow<List<Payment>> = appDao.getPaymentsForStudent(studentId)
    suspend fun getPaymentForStudentAndMonth(studentId: Int, month: String): Payment? = appDao.getPaymentForStudentAndMonth(studentId, month)
    suspend fun insertPayment(payment: Payment): Long = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val id = appDao.insertPayment(payment)
        forceDatabaseCheckpoint()
        id
    }
    suspend fun updatePayment(payment: Payment) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        appDao.updatePayment(payment)
        forceDatabaseCheckpoint()
    }
    suspend fun deletePayment(payment: Payment) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        appDao.deletePayment(payment)
        forceDatabaseCheckpoint()
    }

    // Exams
    val allExamScores: Flow<List<ExamScore>> = appDao.getAllExamScores()
    fun getExamScoresForStudent(studentId: Int): Flow<List<ExamScore>> = appDao.getExamScoresForStudent(studentId)
    suspend fun insertExamScore(score: ExamScore): Long = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val id = appDao.insertExamScore(score)
        forceDatabaseCheckpoint()
        id
    }
    suspend fun deleteExamScore(score: ExamScore) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        appDao.deleteExamScore(score)
        forceDatabaseCheckpoint()
    }
    suspend fun deleteExamScoreSpecific(studentId: Int, examName: String, date: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        appDao.deleteExamScoreSpecific(studentId, examName, date)
        forceDatabaseCheckpoint()
    }


    // --- REBUILD ADDITIONS ---

    // Daily Notes Layer
    fun getDailyNotesForGroup(groupId: Int): Flow<List<DailyNote>> = appDao.getDailyNotesForGroup(groupId)
    suspend fun getDailyNote(groupId: Int, date: String): DailyNote? = appDao.getDailyNote(groupId, date)
    suspend fun insertDailyNote(note: DailyNote): Long = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val id = appDao.insertDailyNote(note)
        forceDatabaseCheckpoint()
        id
    }
    suspend fun updateDailyNote(note: DailyNote) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        appDao.updateDailyNote(note)
        forceDatabaseCheckpoint()
    }

    // Cash Payment Confirmation Layer
    suspend fun confirmPayment(paymentId: Int) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        appDao.confirmPaymentTransaction(
            paymentId = paymentId,
            dateStr = DateUtils.formatStandard("dd/MM/yyyy"),
            timeStr = DateUtils.formatStandard("HH:mm")
        )
        forceDatabaseCheckpoint()
    }

    suspend fun savePayment(
        studentId: Int,
        month: String,
        amount: Double,
        isPaid: Boolean,
        paymentDate: String?,
        paymentTime: String?,
        paidAt: Long?
    ) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        appDao.savePaymentTransaction(
            studentId = studentId,
            month = month,
            amount = amount,
            isPaid = isPaid,
            paymentDate = paymentDate,
            paymentTime = paymentTime,
            paidAt = paidAt
        )
        forceDatabaseCheckpoint()
    }

    // Atomic Attendance Committer
    suspend fun commitAttendance(sessionId: Int, recordsMap: Map<Int, AttendanceStatus>, lateArrivalTimesMap: Map<Int, String?>, timestamp: String) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        appDao.commitAttendanceTransaction(sessionId, recordsMap, lateArrivalTimesMap, timestamp)
        forceDatabaseCheckpoint()
    }

    suspend fun triggerMonthlyBillingAutomation() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val currentMonthStr = DateUtils.formatStandard("yyyy-MM")
        appDao.runMonthlyBillingAutomationTransaction(currentMonthStr)
        forceDatabaseCheckpoint()
    }

    suspend fun recordManualSession(
        groupId: Int,
        todayDate: String,
        timeStr: String,
        monthYearStr: String,
        createdAtStr: String
    ): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val result = appDao.recordManualSessionTransaction(
            groupId = groupId,
            todayDate = todayDate,
            timeStr = timeStr,
            monthYearStr = monthYearStr,
            createdAtStr = createdAtStr
        )
        try {
            database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)") // Ensure SQLite flushes pages / commits fully to disk
        } catch (e: Exception) {
            android.util.Log.e("TeacherRepository", "Error running checkpoint", e)
        }
        result
    }

    suspend fun runMonthlyBillingAutomation(currentMonthStr: String) {
        val groupsList = appDao.getAllGroups().first()
        val eligibleGroupIds = groupsList.filter { 
            it.groupType == GroupType.public || (it.groupType == GroupType.private && it.billingMode == BillingMode.monthly)
        }.map { it.id }.toSet()
        val studentsList = appDao.getAllStudents().first().filter { eligibleGroupIds.contains(it.groupId) }
        
        val paymentsList = appDao.getAllPayments().first().filter { it.month == currentMonthStr }
        val coveredStudentIds = paymentsList.map { it.studentId }.toSet()
        
        studentsList.forEach { student ->
            if (!coveredStudentIds.contains(student.id)) {
                val group = groupsList.find { it.id == student.groupId }
                val amountDue = group?.monthlyFee ?: 200.0
                val bp = BillingPeriod.parseBillingPeriod(currentMonthStr)
                appDao.insertPayment(
                    Payment(
                        studentId = student.id,
                        month = currentMonthStr,
                        isPaid = false,
                        amountPaid = 0.0,
                        amountDue = amountDue,
                        monthVal = bp.month,
                        yearVal = bp.year,
                        groupId = student.groupId
                    )
                )
            }
        }
    }

    suspend fun addStudentWithProRataBilling(student: Student, currentMonthStr: String): Long = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val id = appDao.addStudentWithProRataBillingTransaction(student, currentMonthStr)
        forceDatabaseCheckpoint()
        id
    }

    // Prepopulate system with dummy testing data if DB is empty (disabled for clean test/production readiness)
    suspend fun prepopulateIfEmpty() {
        // Clear prepopulation for clean test and standard production usage
    }

    suspend fun clearAllDatabaseData() {
        appDao.clearAllDatabaseData()
    }

    suspend fun deleteOrphanRecentSessions() {
        val today = DateUtils.formatStandard("yyyy-MM-dd")
        val monthPattern = DateUtils.formatStandard("yyyy-MM-") + "%"
        appDao.deleteOrphanRecentSessions(today, monthPattern)
    }
}
