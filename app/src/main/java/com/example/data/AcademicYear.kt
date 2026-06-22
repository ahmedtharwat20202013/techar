package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "academic_years")
data class AcademicYear(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val yearLabel: String,        // e.g., "2025/2026"
    val startDate: String,        // e.g., "2025-09-01"
    val endDate: String,          // e.g., "2026-06-30"
    val isCurrent: Boolean = false,
    val status: String = "active" // active, closed, archived
)
