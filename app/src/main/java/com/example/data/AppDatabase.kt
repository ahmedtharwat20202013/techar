package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.TypeConverter
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import timber.log.Timber

class DateConverter {
    companion object {
        private val formats = arrayOf(
            "yyyy/MM/dd", "yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss'Z'", "hh:mm", "HH:mm"
        )

        @TypeConverter
        @JvmStatic
        fun toLong(dateStr: String?): Long? {
            if (dateStr.isNullOrBlank()) return null
            for (format in formats) {
                try {
                    val sdf = SimpleDateFormat(format, Locale.ENGLISH)
                    sdf.timeZone = DateUtils.CAIRO_ZONE
                    val date = sdf.parse(dateStr)
                    if (date != null) {
                        return date.time
                    }
                } catch (e: Exception) {
                    // try next format
                }
            }
            val numeric = dateStr.toLongOrNull()
            if (numeric != null) return numeric
            return null
        }

        @TypeConverter
        @JvmStatic
        fun fromLong(timestamp: Long?): String {
            if (timestamp == null || timestamp == 0L) {
                return ""
            }
            // Standardize format to YYYY-MM-DD
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            sdf.timeZone = DateUtils.CAIRO_ZONE
            return sdf.format(Date(timestamp))
        }
    }
}

class EnumConverters {
    @TypeConverter
    fun fromBillingMode(value: BillingMode?): String {
        return value?.name ?: BillingMode.monthly.name
    }

    @TypeConverter
    fun toBillingMode(value: String?): BillingMode {
        if (value == null) {
            return BillingMode.monthly
        }
        return try {
            BillingMode.valueOf(value)
        } catch (e: Exception) {
            Timber.e(e, "Invalid BillingMode value: '$value'")
            throw IllegalArgumentException("Invalid BillingMode value: '$value'. Expected: ${BillingMode.values().joinToString { it.name }}")
        }
    }

    @TypeConverter
    fun fromGroupType(value: GroupType?): String {
        return value?.name ?: GroupType.public.name
    }

    @TypeConverter
    fun toGroupType(value: String?): GroupType {
        if (value == null) {
            return GroupType.public
        }
        return try {
            GroupType.valueOf(value)
        } catch (e: Exception) {
            Timber.e(e, "Invalid GroupType value: '$value'")
            throw IllegalArgumentException("Invalid GroupType value: '$value'. Expected: ${GroupType.values().joinToString { it.name }}")
        }
    }

    @TypeConverter
    fun fromAttendanceStatus(value: AttendanceStatus?): String {
        return value?.name ?: AttendanceStatus.present.name
    }

    @TypeConverter
    fun toAttendanceStatus(value: String?): AttendanceStatus {
        if (value == null) {
            return AttendanceStatus.present
        }
        return try {
            AttendanceStatus.valueOf(value)
        } catch (e: Exception) {
            Timber.e(e, "Invalid AttendanceStatus value: '$value'")
            throw IllegalArgumentException("Invalid AttendanceStatus value: '$value'. Expected: ${AttendanceStatus.values().joinToString { it.name }}")
        }
    }
}

class StringListConverter {
    @TypeConverter
    fun fromString(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            value.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing String list: $value")
            emptyList()
        }
    }

    @TypeConverter
    fun toString(list: List<String>?): String {
        if (list == null) return ""
        return list.joinToString(",")
    }
}

@Database(
    entities = [
        Group::class,
        Student::class,
        Session::class,
        AttendanceRecord::class,
        DailyNote::class,
        Payment::class,
        Exam::class,
        Grade::class,
        AcademicYear::class,
        Enrollment::class,
        GraduatedStudent::class,
        WithdrawnStudent::class,
        DroppedStudent::class
    ],
    version = 17,
    exportSchema = false
)
@TypeConverters(DateConverter::class, EnumConverters::class, StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE payments ADD COLUMN monthVal INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    Timber.e(e, "monthVal column already exists or failed")
                }
                try {
                    database.execSQL("ALTER TABLE payments ADD COLUMN yearVal INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    Timber.e(e, "yearVal column already exists or failed")
                }
                try {
                    database.execSQL("ALTER TABLE payments ADD COLUMN groupId INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    Timber.e(e, "groupId column already exists or failed")
                }

                val cursor = database.query("SELECT id, month, studentId FROM payments")
                try {
                    while (cursor.moveToNext()) {
                        val dbId = cursor.getInt(0)
                        val dbMonthStr = cursor.getString(1) ?: ""
                        val dbStudentId = cursor.getInt(2)

                        val billingPeriod = BillingPeriod.parseBillingPeriod(dbMonthStr)
                        val mVal = billingPeriod.month
                        val yVal = billingPeriod.year

                        var gId = 0
                        val studentCursor = database.query("SELECT groupId FROM students WHERE id = ?", arrayOf(dbStudentId))
                        try {
                            if (studentCursor.moveToFirst()) {
                                gId = studentCursor.getInt(0)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error matching student groupId")
                        } finally {
                            studentCursor.close()
                        }

                        database.execSQL(
                            "UPDATE payments SET monthVal = ?, yearVal = ?, groupId = ? WHERE id = ?",
                            arrayOf(mVal, yVal, gId, dbId)
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error converting payments strings")
                } finally {
                    cursor.close()
                }
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Drop the old payment unique index on string month
                try {
                    database.execSQL("DROP INDEX IF EXISTS index_payments_studentId_month")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to drop old payment index")
                }
                // Create the new payments unique index on structured integers
                try {
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payments_studentId_monthVal_yearVal ON payments(studentId, monthVal, yearVal)")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create new payment index")
                }
                // Create the unique index on sessions
                try {
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sessions_groupId_date ON sessions(groupId, date)")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create session unique index")
                }

                // Migrate attendance_records to remove ignored columns
                try {
                    // 1. Create the new clean table matching expected Room schema
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS `attendance_records_new` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                            `sessionId` INTEGER NOT NULL, 
                            `studentId` INTEGER NOT NULL, 
                            `isPresent` INTEGER NOT NULL, 
                            `timestamp` TEXT NOT NULL, 
                            `status` TEXT NOT NULL,
                            FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())

                    // 2. Check if the old table has a 'status' column
                    val hasStatus = try {
                        val cursor = database.query("PRAGMA table_info(attendance_records)")
                        var statusFound = false
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                            if (name == "status") {
                                statusFound = true
                                break
                            }
                        }
                        cursor.close()
                        statusFound
                    } catch (e: Exception) {
                        false
                    }

                    // 3. Populate new table
                    if (hasStatus) {
                        database.execSQL("""
                            INSERT INTO `attendance_records_new` (`id`, `sessionId`, `studentId`, `isPresent`, `timestamp`, `status`)
                            SELECT `id`, `sessionId`, `studentId`, `isPresent`, `timestamp`, `status` FROM `attendance_records`
                        """.trimIndent())
                    } else {
                        database.execSQL("""
                            INSERT INTO `attendance_records_new` (`id`, `sessionId`, `studentId`, `isPresent`, `timestamp`, `status`)
                            SELECT `id`, `sessionId`, `studentId`, `isPresent`, `timestamp`, 
                                   CASE WHEN `isPresent` = 1 THEN 'present' ELSE 'absent' END FROM `attendance_records`
                        """.trimIndent())
                    }

                    // 4. Drop the old table
                    database.execSQL("DROP TABLE IF EXISTS `attendance_records`")

                    // 5. Rename the new table
                    database.execSQL("ALTER TABLE `attendance_records_new` RENAME TO `attendance_records`")

                    // 6. Create required indices
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_sessionId` ON `attendance_records` (`sessionId`)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_records_studentId` ON `attendance_records` (`studentId`)")
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attendance_records_sessionId_studentId` ON `attendance_records` (`sessionId`, `studentId`)")
                    
                    Timber.d("Successfully migrated attendance_records table to version 10 schema")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to migrate attendance_records table to version 10 schema")
                }
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE `attendance_records` ADD COLUMN `attendanceDate` TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to add column attendanceDate to attendance_records")
                }
                try {
                    database.execSQL("ALTER TABLE `attendance_records` ADD COLUMN `lateArrivalTime` TEXT DEFAULT NULL")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to add column lateArrivalTime to attendance_records")
                }
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE `students` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 1")
                    database.execSQL("ALTER TABLE `students` ADD COLUMN `deletedAt` TEXT DEFAULT NULL")
                } catch (e: Exception) {
                    Timber.e(e, "MIGRATION_11_12: Failed to add soft delete columns to students table")
                }
                try {
                    database.execSQL("DROP INDEX IF EXISTS `index_payments_studentId`")
                    database.execSQL("DROP INDEX IF EXISTS `index_payments_studentId_monthVal_yearVal`")
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payments_studentId_month` ON `payments` (`studentId`, `month`)")
                } catch (e: Exception) {
                    Timber.e(e, "MIGRATION_11_12: Failed to alter payment indices")
                }
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    // 1. Create academic_years table
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS `academic_years` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                            `yearLabel` TEXT NOT NULL, 
                            `startDate` TEXT NOT NULL, 
                            `endDate` TEXT NOT NULL, 
                            `isCurrent` INTEGER NOT NULL, 
                            `status` TEXT NOT NULL
                        )
                    """.trimIndent())

                    // 2. Insert default year "2026/2027"
                    database.execSQL("""
                        INSERT INTO `academic_years` (`yearLabel`, `startDate`, `endDate`, `isCurrent`, `status`)
                        VALUES ('2026/2027', '2026-09-01', '2027-06-30', 1, 'active')
                    """.trimIndent())

                    // 3. Create enrollments table
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS `enrollments` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                            `studentId` INTEGER NOT NULL, 
                            `groupId` INTEGER NOT NULL, 
                            `academicYearId` INTEGER NOT NULL, 
                            `status` TEXT NOT NULL, 
                            `enrollmentDate` TEXT NOT NULL,
                            FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(`academicYearId`) REFERENCES `academic_years`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())

                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_enrollments_studentId_academicYearId` ON `enrollments` (`studentId`, `academicYearId`)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_enrollments_groupId` ON `enrollments` (`groupId`)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_enrollments_academicYearId` ON `enrollments` (`academicYearId`)")

                    // 4. Populate enrollments table from existing students group assignment (before we drop groupId column)
                    database.execSQL("""
                        INSERT OR IGNORE INTO `enrollments` (`studentId`, `groupId`, `academicYearId`, `status`, `enrollmentDate`)
                        SELECT `id`, `groupId`, 1, 'active', `joinDate` FROM `students` WHERE `groupId` IS NOT NULL AND `groupId` != 0
                    """.trimIndent())

                    // 5. Add academicYearId column to related tables
                    try {
                        database.execSQL("ALTER TABLE `attendance_records` ADD COLUMN `academicYearId` INTEGER NOT NULL DEFAULT 1")
                    } catch (e: Exception) {
                        Timber.e(e, "MIGRATION_12_13: Failed to add academicYearId to attendance_records")
                    }
                    try {
                        database.execSQL("ALTER TABLE `payments` ADD COLUMN `academicYearId` INTEGER NOT NULL DEFAULT 1")
                    } catch (e: Exception) {
                        Timber.e(e, "MIGRATION_12_13: Failed to add academicYearId to payments")
                    }
                    try {
                        database.execSQL("ALTER TABLE `new_exams` ADD COLUMN `academicYearId` INTEGER NOT NULL DEFAULT 1")
                    } catch (e: Exception) {
                        Timber.e(e, "MIGRATION_12_13: Failed to add academicYearId to new_exams")
                    }
                    try {
                        database.execSQL("ALTER TABLE `daily_notes` ADD COLUMN `academicYearId` INTEGER NOT NULL DEFAULT 1")
                    } catch (e: Exception) {
                        Timber.e(e, "MIGRATION_12_13: Failed to add academicYearId to daily_notes")
                    }

                    // 6. Migrating students table to drop 'groupId' column
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS `students_new` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                            `name` TEXT NOT NULL, 
                            `parentPhone` TEXT NOT NULL, 
                            `joinDate` TEXT NOT NULL, 
                            `notes` TEXT NOT NULL, 
                            `sessionsRemaining` INTEGER NOT NULL, 
                            `isActive` INTEGER NOT NULL DEFAULT 1, 
                            `deletedAt` TEXT DEFAULT NULL
                        )
                    """.trimIndent())

                    database.execSQL("""
                        INSERT INTO `students_new` (`id`, `name`, `parentPhone`, `joinDate`, `notes`, `sessionsRemaining`, `isActive`, `deletedAt`)
                        SELECT `id`, `name`, `parentPhone`, `joinDate`, `notes`, `sessionsRemaining`, `isActive`, `deletedAt` FROM `students`
                    """.trimIndent())

                    database.execSQL("DROP TABLE IF EXISTS `students`")
                    database.execSQL("ALTER TABLE `students_new` RENAME TO `students`")

                } catch (e: Exception) {
                    Timber.e(e, "MIGRATION_12_13 failed", e)
                }
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE `students` ADD COLUMN `isDropped` INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE `students` ADD COLUMN `droppedAt` INTEGER DEFAULT NULL")
                } catch (e: Exception) {
                    Timber.e(e, "MIGRATION_13_14: Failed to add drop columns to students table", e)
                }
                try {
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS `graduated_students` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                            `originalStudentId` INTEGER NOT NULL, 
                            `name` TEXT NOT NULL, 
                            `parentPhone` TEXT, 
                            `graduationYear` TEXT NOT NULL, 
                            `graduationDate` INTEGER NOT NULL, 
                            `finalGroupName` TEXT NOT NULL, 
                            `totalAttendance` INTEGER NOT NULL, 
                            `totalAbsence` INTEGER NOT NULL, 
                            `totalPayments` REAL NOT NULL, 
                            `totalDue` REAL NOT NULL, 
                            `notes` TEXT
                        )
                    """.trimIndent())
                } catch (e: Exception) {
                    Timber.e(e, "MIGRATION_13_14: Failed to create graduated_students table", e)
                }
                try {
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS `withdrawn_students` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                            `originalStudentId` INTEGER NOT NULL, 
                            `name` TEXT NOT NULL, 
                            `parentPhone` TEXT, 
                            `withdrawalYear` TEXT NOT NULL, 
                            `withdrawalDate` INTEGER NOT NULL, 
                            `finalGroupName` TEXT NOT NULL, 
                            `reason` TEXT, 
                            `totalAttendance` INTEGER NOT NULL, 
                            `totalAbsence` INTEGER NOT NULL, 
                            `totalPayments` REAL NOT NULL, 
                            `totalDue` REAL NOT NULL, 
                            `notes` TEXT
                        )
                    """.trimIndent())
                } catch (e: Exception) {
                    Timber.e(e, "MIGRATION_13_14: Failed to create withdrawn_students table", e)
                }
                try {
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS `dropped_students` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                            `originalStudentId` INTEGER NOT NULL, 
                            `name` TEXT NOT NULL, 
                            `parentPhone` TEXT, 
                            `dropYear` TEXT NOT NULL, 
                            `dropDate` INTEGER NOT NULL, 
                            `finalGroupName` TEXT NOT NULL, 
                            `reason` TEXT, 
                            `totalAttendance` INTEGER NOT NULL, 
                            `totalAbsence` INTEGER NOT NULL, 
                            `totalPayments` REAL NOT NULL, 
                            `totalDue` REAL NOT NULL, 
                            `notes` TEXT
                        )
                    """.trimIndent())
                } catch (e: Exception) {
                    Timber.e(e, "MIGRATION_13_14: Failed to create dropped_students table", e)
                }
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `students` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'active'")
                } catch (e: Exception) {
                    Timber.e(e, "MIGRATION_14_15: Failed to add status column to students table", e)
                }
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `daily_notes` ADD COLUMN `attachments` TEXT NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    Timber.e(e, "MIGRATION_15_16: Failed to add attachments column to daily_notes table", e)
                }
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("""
                        UPDATE `academic_years` 
                        SET `yearLabel` = '2026/2027', `startDate` = '2026-09-01', `endDate` = '2027-06-30' 
                        WHERE `yearLabel` = '2025/2026'
                    """.trimIndent())
                } catch (e: Exception) {
                    Timber.e(e, "MIGRATION_16_17: Failed to update academic year to 2026/2027", e)
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "teacher_manager_db"
                )
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        try {
                            db.execSQL("""
                                INSERT INTO `academic_years` (`yearLabel`, `startDate`, `endDate`, `isCurrent`, `status`)
                                VALUES ('2026/2027', '2026-09-01', '2027-06-30', 1, 'active')
                            """.trimIndent())
                        } catch (e: Exception) {
                            Timber.e(e, "AppDatabase onCreate seeding failed")
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
