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


    // --- ACADEMIC YEARS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAcademicYear(year: AcademicYear): Long

    @Update
    suspend fun updateAcademicYear(year: AcademicYear)

    @Query("SELECT * FROM academic_years WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrentAcademicYear(): AcademicYear?

    @Query("SELECT * FROM academic_years WHERE isCurrent = 1 LIMIT 1")
    fun getCurrentAcademicYearFlow(): Flow<AcademicYear?>

    @Query("SELECT * FROM academic_years ORDER BY id DESC")
    fun getAllAcademicYears(): Flow<List<AcademicYear>>

    @Query("SELECT * FROM academic_years WHERE id = :id LIMIT 1")
    suspend fun getAcademicYearById(id: Int): AcademicYear?


    // --- ENROLLMENTS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnrollment(enrollment: Enrollment): Long

    @Update
    suspend fun updateEnrollment(enrollment: Enrollment)

    @Query("SELECT * FROM enrollments WHERE studentId = :studentId")
    fun getEnrollmentsForStudentFlow(studentId: Int): Flow<List<Enrollment>>

    @Query("SELECT * FROM enrollments WHERE studentId = :studentId")
    suspend fun getEnrollmentsForStudentDirect(studentId: Int): List<Enrollment>

    @Query("SELECT * FROM enrollments WHERE studentId = :studentId AND academicYearId = :academicYearId LIMIT 1")
    suspend fun getEnrollmentForStudentAndYear(studentId: Int, academicYearId: Int): Enrollment?

    @Query("SELECT * FROM enrollments WHERE groupId = :groupId AND academicYearId = :academicYearId")
    fun getEnrollmentsForGroupAndYear(groupId: Int, academicYearId: Int): Flow<List<Enrollment>>

    @Query("SELECT * FROM enrollments")
    fun getAllEnrollments(): Flow<List<Enrollment>>

    @Query("SELECT e.* FROM enrollments e INNER JOIN academic_years y ON e.academicYearId = y.id WHERE e.studentId = :studentId AND y.isCurrent = 1 LIMIT 1")
    suspend fun getCurrentEnrollmentForStudent(studentId: Int): Enrollment?

    @Delete
    suspend fun deleteEnrollment(enrollment: Enrollment)


    // --- STUDENTS ---
    @Query("""
        SELECT DISTINCT s.* FROM students s
        INNER JOIN enrollments e ON s.id = e.studentId
        INNER JOIN academic_years y ON e.academicYearId = y.id
        WHERE s.isActive = 1 AND y.isCurrent = 1 AND e.status = 'active'
        ORDER BY s.name ASC
    """)
    fun getActiveStudents(): Flow<List<Student>>

    @Query("""
        SELECT DISTINCT s.* FROM students s
        LEFT JOIN enrollments e ON s.id = e.studentId
        WHERE s.isActive = 0 OR e.status = 'graduated'
        ORDER BY s.name ASC
    """)
    fun getGraduatedStudents(): Flow<List<Student>>

    @Query("""
        SELECT DISTINCT s.* FROM students s
        INNER JOIN enrollments e ON s.id = e.studentId
        INNER JOIN academic_years y ON e.academicYearId = y.id
        WHERE y.isCurrent = 1 AND e.status = 'dropped'
        ORDER BY s.name ASC
    """)
    fun getDroppedStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students ORDER BY name ASC")
    fun getAllStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE isActive = 0 ORDER BY name ASC")
    fun getDeletedStudentsFlow(): Flow<List<Student>>

    @Query("""
        SELECT s.* FROM students s
        INNER JOIN enrollments e ON s.id = e.studentId
        INNER JOIN academic_years y ON e.academicYearId = y.id
        WHERE s.isActive = 1 AND y.isCurrent = 1 AND e.groupId = :groupId AND e.status = 'active'
        ORDER BY s.name ASC
    """)
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

    @Query("SELECT EXISTS(SELECT 1 FROM sessions WHERE groupId = :groupId AND date = :todayDateStr)")
    fun checkSessionExistsFlow(groupId: Int, todayDateStr: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM sessions WHERE groupId = :groupId AND date = :todayDate)")
    fun isSessionRecordedTodayFlow(groupId: Int, todayDate: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM sessions WHERE groupId = :groupId AND date = :todayDateStr)")
    suspend fun checkSessionExistsDirect(groupId: Int, todayDateStr: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session): Long

    @Delete
    suspend fun deleteSession(session: Session)

    @Query("SELECT * FROM groups")
    suspend fun getAllGroupsDirect(): List<Group>

    @Query("""
        SELECT DISTINCT s.* FROM students s
        INNER JOIN enrollments e ON s.id = e.studentId
        INNER JOIN academic_years y ON e.academicYearId = y.id
        WHERE s.isActive = 1 AND y.isCurrent = 1 AND e.status = 'active'
    """)
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

        val currentYear = getCurrentAcademicYear() ?: return
        val students = getAllStudentsDirect()
        val groupMap = groups.associateBy { it.id }

        for (student in students) {
            val enrollment = getEnrollmentForStudentAndYear(student.id, currentYear.id) ?: continue
            if (enrollment.status != "active" || !eligibleGroupIds.contains(enrollment.groupId)) {
                continue
            }
            val group = groupMap[enrollment.groupId] ?: continue
            val bp = BillingPeriod.parseBillingPeriod(currentMonthStr)
            val payment = Payment(
                studentId = student.id,
                month = currentMonthStr,
                isPaid = false,
                amountPaid = 0.0,
                amountDue = group.monthlyFee,
                monthVal = bp.month,
                yearVal = bp.year,
                groupId = enrollment.groupId,
                academicYearId = currentYear.id
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

    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId")
    suspend fun getAttendanceForStudentDirect(studentId: Int): List<AttendanceRecord>

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

    @Query("SELECT * FROM payments WHERE studentId = :studentId AND month = :month LIMIT 1")
    suspend fun getPaymentForStudentAndMonth(studentId: Int, month: String): Payment?

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
        val existing = getPaymentForStudentAndMonth(studentId, month)
        val currentYear = getCurrentAcademicYear()
        val enrollment = currentYear?.let { getEnrollmentForStudentAndYear(studentId, it.id) }
        val gId = enrollment?.groupId ?: 0
        val bp = BillingPeriod.parseBillingPeriod(month)
        val yId = currentYear?.id ?: 1
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
                    groupId = gId,
                    academicYearId = yId
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
                    groupId = gId,
                    academicYearId = yId
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
            val student = getStudentById(studentId)
            if (student?.isDropped == true) {
                continue
            }
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
    suspend fun addStudentWithProRataBillingTransaction(student: Student, groupId: Int, currentMonthStr: String): Long {
        val studentId = insertStudent(student).toInt()
        val currentYear = getCurrentAcademicYear()
        val yId = currentYear?.id ?: 1
        
        insertEnrollment(
            Enrollment(
                studentId = studentId,
                groupId = groupId,
                academicYearId = yId,
                status = "active",
                enrollmentDate = student.joinDate
            )
        )
        
        val group = getGroupById(groupId)
        if (group != null) {
            val fullFee = group.monthlyFee
            
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
                    groupId = groupId,
                    academicYearId = yId
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

    @Query("""
        SELECT g.id AS id, g.studentId AS studentId, e.name AS examName, g.score AS score, e.totalScore AS maxScore, e.date AS date
        FROM grades g
        INNER JOIN new_exams e ON g.examId = e.id
        WHERE g.studentId = :studentId AND e.groupId = :groupId
        ORDER BY e.date DESC
    """)
    fun getExamScoresForStudentAndGroup(studentId: Int, groupId: Int): Flow<List<ExamScore>>

    @Transaction
    suspend fun insertExamScore(score: ExamScore): Long {
        val student = getStudentById(score.studentId) ?: return 0L
        val currentYear = getCurrentAcademicYear()
        val enrollment = currentYear?.let { getEnrollmentForStudentAndYear(student.id, it.id) }
        val groupId = enrollment?.groupId ?: 0
        val yId = currentYear?.id ?: 1
        
        // Find or create Exam
        var exam = getExamByNameGroupAndDate(score.examName, groupId, score.date)
        val examId = if (exam == null) {
            insertExam(Exam(groupId = groupId, name = score.examName, totalScore = score.maxScore, date = score.date, academicYearId = yId)).toInt()
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

    @Transaction
    suspend fun startNewAcademicYearTransaction(
        newYear: AcademicYear,
        enrollmentsToInsert: List<Enrollment>,
        oldYearEnrollmentsToUpdate: List<Enrollment>
    ) {
        // 1. Mark existing years as not current and archived
        val currentYear = getCurrentAcademicYear()
        if (currentYear != null) {
            updateAcademicYear(currentYear.copy(isCurrent = false, status = "archived"))
        }
        
        // 2. Insert new academic year
        val newYearId = insertAcademicYear(newYear).toInt()
        
        // 3. Update old enrollments status
        for (oldEnrollment in oldYearEnrollmentsToUpdate) {
            updateEnrollment(oldEnrollment)
        }
        
        // 4. Insert new enrollments
        for (enrollment in enrollmentsToInsert) {
            insertEnrollment(enrollment.copy(academicYearId = newYearId))
        }
    }

    // --- SNAPSHOT ARCHIVES ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGraduatedStudent(graduated: GraduatedStudent): Long

    @Query("SELECT * FROM graduated_students ORDER BY id DESC")
    fun getAllGraduatedStudentsFlow(): Flow<List<GraduatedStudent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithdrawnStudent(withdrawn: WithdrawnStudent): Long

    @Query("SELECT * FROM withdrawn_students ORDER BY id DESC")
    fun getAllWithdrawnStudentsFlow(): Flow<List<WithdrawnStudent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDroppedStudent(dropped: DroppedStudent): Long

    @Query("SELECT * FROM dropped_students ORDER BY id DESC")
    fun getAllDroppedStudentsFlow(): Flow<List<DroppedStudent>>

    @Query("DELETE FROM graduated_students")
    suspend fun clearGraduatedStudents()

    @Query("DELETE FROM withdrawn_students")
    suspend fun clearWithdrawnStudents()

    @Query("DELETE FROM dropped_students")
    suspend fun clearDroppedStudents()

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
        WHERE (date >= :currentDateStr OR date LIKE :monthPattern) 
          AND id NOT IN (SELECT DISTINCT sessionId FROM attendance_records)
    """)
    suspend fun deleteOrphanRecentSessions(currentDateStr: String, monthPattern: String)

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
