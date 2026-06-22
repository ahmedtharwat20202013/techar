package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "enrollments",
    foreignKeys = [
        ForeignKey(entity = Student::class, parentColumns = ["id"], childColumns = ["studentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Group::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AcademicYear::class, parentColumns = ["id"], childColumns = ["academicYearId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index(value = ["studentId", "academicYearId"], unique = true),
        Index(value = ["groupId"]),
        Index(value = ["academicYearId"])
    ]
)
data class Enrollment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val groupId: Int,
    val academicYearId: Int,
    val status: String = "active", // active, graduated, transferred
    val enrollmentDate: String = ""
)
