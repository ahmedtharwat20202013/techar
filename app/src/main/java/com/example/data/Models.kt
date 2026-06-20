package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Embedded
import androidx.room.Relation
import androidx.room.Ignore

enum class GroupType { public, private }
enum class BillingMode { per_session, monthly }
enum class AttendanceStatus { present, absent, late }

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val startDate: String, // String representation formatted as YYYY-MM-DD but stored as Long via converter
    val monthlyFee: Double,
    val scheduleDays: String, // e.g., "Monday, Wednesday" or Arabic equivalent "الإثنين، الأربعاء"
    val groupType: GroupType = GroupType.public,
    val billingMode: BillingMode = BillingMode.monthly,
    val totalSessionsHeld: Int = 0,
    val sessionsPerMonth: Int = 8,
    val daysOfWeek: List<String> = emptyList()
)

data class GroupWithSessions(
    @Embedded val group: Group,
    @Relation(
        parentColumn = "id",
        entityColumn = "groupId"
    )
    val sessions: List<Session>
)

@Entity(
    tableName = "students",
    foreignKeys = [
        ForeignKey(
            entity = Group::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId"])]
)
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val groupId: Int,
    val name: String,
    val parentPhone: String,
    val joinDate: String, // String representation stored as Long in DB
    val notes: String = "",
    val sessionsRemaining: Int = 0
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = Group::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["date"]),
        Index(value = ["monthYear"]),
        Index(value = ["groupId", "date"], unique = true)
    ]
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val groupId: Int,
    val date: String, // YYYY/MM/DD stored as Long in DB
    val time: String, // e.g., "09:00 AM" or "09:00 ص"
    val monthYear: String = "", // e.g., "June 2026", "يونيو 2026"
    val createdAt: String = "", // ISO DateTime
    val sessionNumber: Int = 0
)

@Entity(
    tableName = "attendance_records",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["studentId"]),
        Index(value = ["sessionId", "studentId"], unique = true)
    ]
)
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val studentId: Int,
    val isPresent: Boolean,
    val timestamp: String = "",
    val status: AttendanceStatus = if (isPresent) AttendanceStatus.present else AttendanceStatus.absent,
    val attendanceDate: String = "",
    val lateArrivalTime: String? = null
) {
    @Ignore var groupId: Int = 0
    @Ignore var date: String = "" // stored as Long
    @Ignore var sessionNumber: Int = 0

    @Ignore
    constructor(
        id: Int = 0,
        sessionId: Int,
        studentId: Int,
        isPresent: Boolean,
        timestamp: String = "",
        status: AttendanceStatus = if (isPresent) AttendanceStatus.present else AttendanceStatus.absent
    ) : this(id, sessionId, studentId, isPresent, timestamp, status, "", null)
}

@Entity(
    tableName = "daily_notes",
    foreignKeys = [
        ForeignKey(
            entity = Group::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["groupId", "date"], unique = true)
    ]
)
data class DailyNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val groupId: Int,
    val date: String, // YYYY-MM-DD format
    val sessionNumber: Int,
    val content: String
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["studentId"]),
        Index(value = ["studentId", "monthVal", "yearVal"], unique = true)
    ]
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val month: String, // format YYYY-MM
    val isPaid: Boolean,
    val amountPaid: Double,
    val paymentDate: String? = null,
    val paymentTime: String? = null,
    val amountDue: Double = 0.0,
    val paidAt: Long? = null,
    val receiptString: String? = null,
    val monthVal: Int = 0,
    val yearVal: Int = 0,
    val groupId: Int = 0
)

@Entity(
    tableName = "new_exams",
    foreignKeys = [
        ForeignKey(
            entity = Group::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId"])]
)
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val groupId: Int,
    val name: String,
    val totalScore: Double,
    val date: String // stored as Long in DB
)

@Entity(
    tableName = "grades",
    foreignKeys = [
        ForeignKey(
            entity = Exam::class,
            parentColumns = ["id"],
            childColumns = ["examId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["examId"]),
        Index(value = ["studentId"]),
        Index(value = ["examId", "studentId"], unique = true)
    ]
)
data class Grade(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val examId: Int,
    val studentId: Int,
    val score: Double
)

// Legacy Projection mapping classes
data class ExamScore(
    val id: Int = 0,
    val studentId: Int,
    val examName: String,
    val score: Double,
    val maxScore: Double,
    val date: String
)
