package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // --- GROUPS ---
    @Query("SELECT * FROM groups ORDER BY id DESC")
    fun getAllGroups(): Flow<List<Group>>

    @Transaction
    @Query("SELECT * FROM groups ORDER BY id DESC")
    fun getAllGroupsWithSessionsFlow(): Flow<List<GroupWithSessions>>

    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: Int): Group?

    @Query("SELECT * FROM groups WHERE id = :groupId")
    fun getGroupByIdFlow(groupId: Int): Flow<Group?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: Group): Long

    @Update
    suspend fun updateGroup(group: Group)

    @Delete
    suspend fun deleteGroup(group: Group)


    // --- STUDENTS ---
    @Query("SELECT * FROM students ORDER BY name ASC")
    fun getAllStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE groupId = :groupId ORDER BY name ASC")
    fun getStudentsByGroup(groupId: Int): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE id = :studentId")
    suspend fun getStudentById(studentId: Int): Student?

    @Query("SELECT * FROM students WHERE id = :studentId")
    fun getStudentByIdFlow(studentId: Int): Flow<Student?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student): Long

    @Update
    suspend fun updateStudent(student: Student)

    @Delete
    suspend fun deleteStudent(student: Student)


    // --- SESSIONS ---
    @Query("SELECT * FROM sessions ORDER BY date DESC, time DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE groupId = :groupId ORDER BY date DESC, time DESC")
    fun getSessionsByGroup(groupId: Int): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE date = :date")
    fun getSessionsForDate(date: String): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Int): Session?

    @Query("SELECT * FROM sessions WHERE groupId = :groupId AND date = :date LIMIT 1")
    suspend fun getSessionByGroupAndDate(groupId: Int, date: String): Session?

    @Query("SELECT EXISTS(SELECT 1 FROM sessions WHERE groupId = :groupId AND date LIKE :todayDateStr || '%')")
    fun checkSessionExistsFlow(groupId: Int, todayDateStr: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM sessions WHERE groupId = :groupId AND date LIKE :todayDate || '%')")
    fun isSessionRecordedTodayFlow(groupId: Int, todayDate: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM sessions WHERE groupId = :groupId AND date LIKE :todayDateStr || '%')")
    suspend fun checkSessionExistsDirect(groupId: Int, todayDateStr: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session): Long

    @Delete
    suspend fun deleteSession(session: Session)

    @Query("SELECT * FROM groups")
    suspend fun getAllGroupsDirect(): List<Group>

    @Query("SELECT * FROM students")
    suspend fun getAllStudentsDirect(): List<Student>

    @Query("SELECT * FROM payments WHERE month = :month")
    suspend fun getPaymentsForMonthDirect(month: String): List<Payment>

    @Transaction
    suspend fun recordManualSessionTransaction(
        groupId: Int,
        todayDate: String,
        timeStr: String,
        monthYearStr: String,
        createdAtStr: String
    ): Boolean {
        return try {
            val group = getGroupById(groupId) ?: return false
            val newTotalSessionsHeld = group.totalSessionsHeld + 1

            val session = Session(
                groupId = groupId,
                date = todayDate,
                time = timeStr,
                monthYear = monthYearStr,
                createdAt = createdAtStr,
                sessionNumber = newTotalSessionsHeld
            )
            insertSession(session)
            
            updateGroup(group.copy(totalSessionsHeld = newTotalSessionsHeld))

            val todayDateSlash = todayDate.replace("-", "/")
            // Ensure there is a blank daily note for this date
            val existingNote = getDailyNote(groupId, todayDate) ?: getDailyNote(groupId, todayDateSlash)
            if (existingNote == null) {
                insertDailyNote(
                    DailyNote(
                        groupId = groupId,
                        date = todayDate,
                        sessionNumber = newTotalSessionsHeld,
                        content = ""
                    )
                )
            } else {
                updateDailyNote(existingNote.copy(sessionNumber = newTotalSessionsHeld))
            }
            true
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            false
        }
    }

    @Transaction
    suspend fun runMonthlyBillingAutomationTransaction(currentMonthStr: String) {
        val existingPayments = getPaymentsForMonthDirect(currentMonthStr)
        if (existingPayments.isNotEmpty()) {
            return
        }

        val groups = getAllGroupsDirect()
        val eligibleGroupIds = groups.filter {
            it.groupType == GroupType.public || (it.groupType == GroupType.private && it.billingMode == BillingMode.monthly)
        }.map { it.id }.toSet()

        val students = getAllStudentsDirect()
        val eligibleStudents = students.filter { eligibleGroupIds.contains(it.groupId) }

        val groupMap = groups.associateBy { it.id }
        for (student in eligibleStudents) {
            val group = groupMap[student.groupId] ?: continue
            val bp = BillingPeriod.parseBillingPeriod(currentMonthStr)
            val payment = Payment(
                studentId = student.id,
                month = currentMonthStr,
                isPaid = false,
                amountPaid = 0.0,
                amountDue = group.monthlyFee,
                monthVal = bp.month,
                yearVal = bp.year,
                groupId = student.groupId
            )
            insertPayment(payment)
        }
    }


    // --- ATTENDANCE RECORDS ---
    @Query("SELECT * FROM attendance_records")
    fun getAllAttendance(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE sessionId = :sessionId")
    fun getAttendanceForSession(sessionId: Int): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId")
    fun getAttendanceForStudent(studentId: Int): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceBatch(records: List<AttendanceRecord>)

    @Query("DELETE FROM attendance_records WHERE sessionId = :sessionId")
    suspend fun deleteAttendanceForSession(sessionId: Int)


    // --- PAYMENTS ---
    @Query("SELECT * FROM payments ORDER BY month DESC")
    fun getAllPayments(): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE studentId = :studentId ORDER BY month DESC")
    fun getPaymentsForStudent(studentId: Int): Flow<List<Payment>>

    @Query("SELECT * FROM payments WHERE studentId = :studentId ORDER BY month DESC")
    suspend fun getPaymentsForStudentDirect(studentId: Int): List<Payment>

    @Query("SELECT * FROM payments WHERE id = :paymentId")
    suspend fun getPaymentById(paymentId: Int): Payment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Update
    suspend fun updatePayment(payment: Payment)

    @Delete
    suspend fun deletePayment(payment: Payment)

    @Transaction
    suspend fun savePaymentTransaction(
        studentId: Int,
        month: String,
        amount: Double,
        isPaid: Boolean,
        paymentDate: String?,
        paymentTime: String?,
        paidAt: Long?
    ) {
        val existing = getPaymentsForStudentDirect(studentId).find { it.month == month }
        val student = getStudentById(studentId)
        val gId = student?.groupId ?: 0
        val bp = BillingPeriod.parseBillingPeriod(month)
        if (existing != null) {
            updatePayment(
                existing.copy(
                    isPaid = isPaid,
                    amountPaid = if (isPaid) amount else 0.0,
                    paymentDate = paymentDate,
                    paymentTime = paymentTime,
                    paidAt = paidAt,
                    monthVal = bp.month,
                    yearVal = bp.year,
                    groupId = gId
                )
            )
        } else {
            insertPayment(
                Payment(
                    studentId = studentId,
                    month = month,
                    isPaid = isPaid,
                    amountPaid = if (isPaid) amount else 0.0,
                    paymentDate = paymentDate,
                    paymentTime = paymentTime,
                    amountDue = amount,
                    paidAt = paidAt,
                    monthVal = bp.month,
                    yearVal = bp.year,
                    groupId = gId
                )
            )
        }
    }


    // --- DAILY NOTES ---
    @Query("SELECT * FROM daily_notes WHERE groupId = :groupId AND date = :date LIMIT 1")
    suspend fun getDailyNote(groupId: Int, date: String): DailyNote?

    @Query("SELECT * FROM daily_notes WHERE groupId = :groupId ORDER BY date ASC")
    fun getDailyNotesForGroup(groupId: Int): Flow<List<DailyNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyNote(note: DailyNote): Long

    @Update
    suspend fun updateDailyNote(note: DailyNote)


    @Query("SELECT COUNT(*) FROM attendance_records WHERE sessionId = :sessionId")
    suspend fun getAttendanceCountForSession(sessionId: Int): Int

    @Query("SELECT * FROM attendance_records WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getOneAttendanceRecordForSession(sessionId: Int): AttendanceRecord?

    @Query("SELECT isPresent FROM attendance_records WHERE sessionId = :sessionId AND studentId = :studentId LIMIT 1")
    suspend fun getStudentPreviousAttendance(sessionId: Int, studentId: Int): Boolean?

    @Query("SELECT * FROM attendance_records WHERE sessionId = :sessionId AND studentId = :studentId LIMIT 1")
    suspend fun getAttendanceRecordForStudentAndSession(sessionId: Int, studentId: Int): AttendanceRecord?


    // --- TRANSACTIONAL ATTENDANCE LAYER ---
    @Transaction
    suspend fun commitAttendanceTransaction(
        sessionId: Int,
        recordsMap: Map<Int, AttendanceStatus>,
        lateArrivalTimesMap: Map<Int, String?>,
        timestamp: String
    ) {
        val session = getSessionById(sessionId) ?: return
        val group = getGroupById(session.groupId) ?: return

        val existingRecordsCount = getAttendanceCountForSession(sessionId)
        val isFirstCommit = existingRecordsCount == 0

        val sessionNumber = if (session.sessionNumber > 0) {
            session.sessionNumber
        } else if (isFirstCommit) {
            group.totalSessionsHeld + 1
        } else {
            val oneRecord = getOneAttendanceRecordForSession(sessionId)
            oneRecord?.sessionNumber ?: group.totalSessionsHeld
        }

        // Cache previous attendance status before deleting existing records
        val previousPresenceMap = mutableMapOf<Int, AttendanceStatus>()
        if (!isFirstCommit) {
            for (studentId in recordsMap.keys) {
                val previousAttendance = getAttendanceRecordForStudentAndSession(sessionId, studentId)
                if (previousAttendance != null) {
                    previousPresenceMap[studentId] = previousAttendance.status
                }
            }
        }

        // Delete previous records for this session
        deleteAttendanceForSession(sessionId)

        // Insert new records and update student count
        for ((studentId, status) in recordsMap) {
            val isPresent = status == AttendanceStatus.present || status == AttendanceStatus.late
            val recordLateTime = if (status == AttendanceStatus.late) {
                lateArrivalTimesMap[studentId] ?: DateUtils.formatStandard("hh:mm a")
            } else {
                null
            }

            val record = AttendanceRecord(
                sessionId = sessionId,
                studentId = studentId,
                isPresent = isPresent,
                timestamp = timestamp,
                status = status,
                attendanceDate = session.date,
                lateArrivalTime = recordLateTime
            ).apply {
                this.groupId = session.groupId
                this.date = session.date
                this.sessionNumber = sessionNumber
            }
            insertAttendance(record)

            if (group.groupType == GroupType.private && group.billingMode == BillingMode.per_session) {
                val student = getStudentById(studentId)
                if (student != null) {
                    var change = 0
                    if (isFirstCommit) {
                        if (isPresent) {
                            change = -1 // Consume session
                        }
                    } else {
                        // Check if student's attendance changed
                        val wasPresent = previousPresenceMap[studentId] == AttendanceStatus.present || previousPresenceMap[studentId] == AttendanceStatus.late
                        if (wasPresent && !isPresent) {
                            change = 1 // Refund session
                        } else if (!wasPresent && isPresent) {
                            change = -1 // Consume session
                        }
                    }
                    if (change != 0) {
                        val newBalance = student.sessionsRemaining + change
                        val updatedRemaining = maxOf(0, newBalance)
                        updateStudent(student.copy(sessionsRemaining = updatedRemaining))
                    }
                }
            }
         }

        // Update group session count ONLY if this is the first commit and not already set
        if (isFirstCommit && session.sessionNumber <= 0) {
            updateGroup(group.copy(totalSessionsHeld = sessionNumber))
        }

        // Update daily notes
        val existingNote = getDailyNote(session.groupId, session.date)
        if (existingNote != null) {
            updateDailyNote(existingNote.copy(sessionNumber = sessionNumber))
        } else {
            insertDailyNote(
                DailyNote(
                    groupId = session.groupId,
                    date = session.date,
                    sessionNumber = sessionNumber,
                    content = ""
                )
            )
        }
    }

    @Transaction
    suspend fun addStudentWithProRataBillingTransaction(student: Student, currentMonthStr: String): Long {
        val studentId = insertStudent(student).toInt()
        val group = getGroupById(student.groupId)
        if (group != null) {
            val fullFee = group.monthlyFee
            val sessionsPerMonth = if (group.sessionsPerMonth > 0) group.sessionsPerMonth else 8
            val totalSessionsHeld = group.totalSessionsHeld
            
            val isProRataEligible = group.groupType == GroupType.public || 
                    (group.groupType == GroupType.private && group.billingMode == BillingMode.monthly)

            val amountDue = if (isProRataEligible) {
                DateUtils.calculateProRata(fullFee, student.joinDate)
            } else {
                fullFee
            }
            
            val bp = BillingPeriod.parseBillingPeriod(currentMonthStr)
            insertPayment(
                Payment(
                    studentId = studentId,
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
        return studentId.toLong()
    }

    @Transaction
    suspend fun confirmPaymentTransaction(paymentId: Int, dateStr: String, timeStr: String) {
        val payment = getPaymentById(paymentId)
        if (payment != null) {
            val updated = payment.copy(
                isPaid = true,
                paidAt = System.currentTimeMillis(),
                paymentDate = dateStr,
                paymentTime = timeStr
            )
            updatePayment(updated)
        }
    }


    // --- EXAMS AND GRADES (NORMALIZED INTERNALLY) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: Exam): Long

    @Update
    suspend fun updateExam(exam: Exam)

    @Delete
    suspend fun deleteExam(exam: Exam)

    @Query("SELECT * FROM new_exams WHERE name = :name AND groupId = :groupId AND date = :date LIMIT 1")
    suspend fun getExamByNameGroupAndDate(name: String, groupId: Int, date: String): Exam?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrade(grade: Grade): Long

    @Update
    suspend fun updateGrade(grade: Grade)

    @Query("SELECT * FROM grades WHERE examId = :examId AND studentId = :studentId LIMIT 1")
    suspend fun getGradeByExamAndStudent(examId: Int, studentId: Int): Grade?

    @Query("""
        SELECT g.id AS id, g.studentId AS studentId, e.name AS examName, g.score AS score, e.totalScore AS maxScore, e.date AS date
        FROM grades g
        INNER JOIN new_exams e ON g.examId = e.id
        ORDER BY e.date DESC
    """)
    fun getAllExamScores(): Flow<List<ExamScore>>

    @Query("""
        SELECT g.id AS id, g.studentId AS studentId, e.name AS examName, g.score AS score, e.totalScore AS maxScore, e.date AS date
        FROM grades g
        INNER JOIN new_exams e ON g.examId = e.id
        WHERE g.studentId = :studentId
        ORDER BY e.date DESC
    """)
    fun getExamScoresForStudent(studentId: Int): Flow<List<ExamScore>>

    @Transaction
    suspend fun insertExamScore(score: ExamScore): Long {
        val student = getStudentById(score.studentId) ?: return 0L
        val groupId = student.groupId
        
        // Find or create Exam
        var exam = getExamByNameGroupAndDate(score.examName, groupId, score.date)
        val examId = if (exam == null) {
            insertExam(Exam(groupId = groupId, name = score.examName, totalScore = score.maxScore, date = score.date)).toInt()
        } else {
            exam.id
        }

        // Find or create Grade
        val existingGrade = getGradeByExamAndStudent(examId, score.studentId)
        if (existingGrade != null) {
            updateGrade(existingGrade.copy(score = score.score))
        } else {
            insertGrade(Grade(examId = examId, studentId = score.studentId, score = score.score))
        }
        return examId.toLong()
    }

    @Query("""
        DELETE FROM grades 
        WHERE studentId = :studentId 
          AND examId IN (SELECT id FROM new_exams WHERE name = :examName AND date = :date)
    """)
    suspend fun deleteExamScoreSpecific(studentId: Int, examName: String, date: String)

    @Transaction
    suspend fun deleteExamScore(score: ExamScore) {
        deleteExamScoreSpecific(score.studentId, score.examName, score.date)
    }

    // --- DB HARD CLEAN UTILITIES ---
    @Query("DELETE FROM groups")
    suspend fun clearGroups()

    @Query("DELETE FROM students")
    suspend fun clearStudents()

    @Query("DELETE FROM sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM attendance_records")
    suspend fun clearAttendance()

    @Query("DELETE FROM payments")
    suspend fun clearPayments()

    @Query("DELETE FROM new_exams")
    suspend fun clearExams()

    @Query("DELETE FROM grades")
    suspend fun clearGrades()

    @Query("""
        DELETE FROM sessions 
        WHERE (date >= '2026-06-15' OR date >= '2026/06/15' OR date LIKE '2026-06-%' OR date LIKE '2026/06/%') 
          AND id NOT IN (SELECT DISTINCT sessionId FROM attendance_records)
    """)
    suspend fun deleteOrphanRecentSessions()

    @Transaction
    suspend fun clearAllDatabaseData() {
        clearGrades()
        clearExams()
        clearPayments()
        clearAttendance()
        clearSessions()
        clearStudents()
        clearGroups()
    }
}
