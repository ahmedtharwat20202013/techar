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
        Enrollment::class
    ],
    version = 13,
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
                    // 1. إنشاء جدول academic_years
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS `academic_years` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `yearLabel` TEXT NOT NULL,
                            `startDate` TEXT NOT NULL,
                            `endDate` TEXT NOT NULL,
                            `isCurrent` INTEGER NOT NULL DEFAULT 0,
                            `status` TEXT NOT NULL DEFAULT 'active'
                        )
                    """.trimIndent())
                    
                    // 2. إنشاء جدول enrollments
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS `enrollments` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `studentId` INTEGER NOT NULL,
                            `groupId` INTEGER NOT NULL,
                            `academicYearId` INTEGER NOT NULL,
                            `status` TEXT NOT NULL DEFAULT 'active',
                            `enrollmentDate` TEXT NOT NULL DEFAULT '',
                            FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(`academicYearId`) REFERENCES `academic_years`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())
                    
                    // 3. إنشاء فهرس فريد
                    database.execSQL("""
                        CREATE UNIQUE INDEX IF NOT EXISTS `index_enrollments_studentId_academicYearId` 
                        ON `enrollments` (`studentId`, `academicYearId`)
                    """.trimIndent())
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_enrollments_groupId` ON `enrollments` (`groupId`)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS `index_enrollments_academicYearId` ON `enrollments` (`academicYearId`)")
                    
                    // 4. إضافة academicYearId للجداول الأخرى
                    database.execSQL("ALTER TABLE `attendance_records` ADD COLUMN `academicYearId` INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE `payments` ADD COLUMN `academicYearId` INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE `new_exams` ADD COLUMN `academicYearId` INTEGER NOT NULL DEFAULT 0")
                    database.execSQL("ALTER TABLE `daily_notes` ADD COLUMN `academicYearId` INTEGER NOT NULL DEFAULT 0")
                    
                    // 5. إنشاء سنة افتراضية للبيانات الحالية
                    database.execSQL("""
                        INSERT INTO `academic_years` (`yearLabel`, `startDate`, `endDate`, `isCurrent`, `status`)
                        VALUES ('2025/2026', '2025-09-01', '2026-06-30', 1, 'active')
                    """.trimIndent())
                    
                    // 6. نقل groupId من students إلى enrollments بالتحقق من وجود العمود
                    val hasGroupId = try {
                        val cursor = database.query("PRAGMA table_info(students)")
                        var found = false
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                            if (name == "groupId") {
                                found = true
                                break
                            }
                        }
                        cursor.close()
                        found
                    } catch (e: Exception) {
                        true
                    }

                    if (hasGroupId) {
                        try {
                            database.execSQL("""
                                INSERT INTO `enrollments` (`studentId`, `groupId`, `academicYearId`, `status`, `enrollmentDate`)
                                SELECT `id`, `groupId`, 1, 'active', `joinDate` FROM `students`
                            """.trimIndent())
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to copy student groupId to enrollments")
                        }

                        // Recreate students table to drop the groupId column safely
                        try {
                            database.execSQL("""
                                CREATE TABLE IF NOT EXISTS `students_new` (
                                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                    `name` TEXT NOT NULL,
                                    `parentPhone` TEXT NOT NULL,
                                    `joinDate` TEXT NOT NULL,
                                    `notes` TEXT NOT NULL,
                                    `sessionsRemaining` INTEGER NOT NULL,
                                    `isActive` INTEGER NOT NULL,
                                    `deletedAt` TEXT
                                )
                            """.trimIndent())

                            database.execSQL("""
                                INSERT INTO `students_new` (`id`, `name`, `parentPhone`, `joinDate`, `notes`, `sessionsRemaining`, `isActive`, `deletedAt`)
                                SELECT `id`, `name`, `parentPhone`, `joinDate`, `notes`, `sessionsRemaining`, `isActive`, `deletedAt` FROM `students`
                            """.trimIndent())

                            database.execSQL("DROP TABLE IF EXISTS `students`")
                            database.execSQL("ALTER TABLE `students_new` RENAME TO `students`")
                            Timber.d("Successfully migrates: dropped groupId column from students table")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to drop groupId column from students table during migration")
                        }
                    } else {
                        Timber.d("students table does not have groupId column, skipping transfer/drop block")
                    }
                    
                    // 7. تحديث academicYearId للبيانات الحالية
                    database.execSQL("UPDATE `attendance_records` SET `academicYearId` = 1")
                    database.execSQL("UPDATE `payments` SET `academicYearId` = 1")
                    database.execSQL("UPDATE `new_exams` SET `academicYearId` = 1")
                    database.execSQL("UPDATE `daily_notes` SET `academicYearId` = 1")
                } catch (e: Exception) {
                    Timber.e(e, "Failed in MIGRATION_12_13")
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
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .fallbackToDestructiveMigration()
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
