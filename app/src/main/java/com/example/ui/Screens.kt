package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.theme.*
import com.example.viewmodel.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

fun isValidEgyptianPhoneNumber(num: String): Boolean {
    val clean = num.trim().replace(" ", "").replace("-", "")
    val regex = "^(\\+?2?0?)?1[0-2,5][0-9]{8}$".toRegex()
    return clean.matches(regex)
}

// --- SCREEN ORCHESTRATOR ROUTING ROUTES ---
sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Classes : Screen("classes")
    object Students : Screen("students")
    object GroupDetail : Screen("group_detail/{groupId}") {
        fun createRoute(groupId: Int) = "group_detail/$groupId"
    }
    object StudentProfile : Screen("student_profile/{studentId}/{groupId}") {
        fun createRoute(studentId: Int, groupId: Int) = "student_profile/$studentId/$groupId"
    }
    object Payments : Screen("payments")
    object Exams : Screen("exams")
    object ReportsBackup : Screen("reports_backup")
}

// Custom Localized Header/Footer utilities
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherAppBar(
    title: String,
    showSearch: Boolean = false,
    onSearchClick: () -> Unit = {},
    navigationIcon: @Composable () -> Unit = {},
    onHomeClick: (() -> Unit)? = null,
    onEditClick: (() -> Unit)? = null
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                fontFamily = Typography.titleLarge.fontFamily,
                fontWeight = FontWeight.Bold,
                color = PrimaryDarkGreen
            )
        },
        navigationIcon = navigationIcon,
        actions = {
            if (showSearch) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = PrimaryGreen
                    )
                }
            }
            if (onEditClick != null) {
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "تعديل",
                        tint = PrimaryGreen
                    )
                }
            }
            if (onHomeClick != null) {
                IconButton(onClick = onHomeClick) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "الرئيسية",
                        tint = PrimaryGreen
                    )
                }
            }
            // Minimalist profile badge matching screenshots
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(PrimaryGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M", // "معلم"
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.White
        )
    )
}

// Custom bottom navigation bar mimicking screenshots with perfect safe drawings
@Composable
fun TeacherNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = Color.White,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        val items = listOf(
            Triple(Screen.Dashboard.route, "الرئيسية", Icons.Default.Dashboard),
            Triple(Screen.Classes.route, "المجموعات", Icons.Default.Groups),
            Triple(Screen.Students.route, "الطلاب", Icons.Default.Person),
            Triple(Screen.Payments.route, "المدفوعات", Icons.Default.Payments),
            Triple(Screen.ReportsBackup.route, "التقارير", Icons.Default.Analytics)
        )

        items.forEach { (route, label, icon) ->
            val selected = currentRoute == route || 
                    (route == Screen.Classes.route && currentRoute.startsWith("group_detail")) ||
                    (route == Screen.Students.route && currentRoute.startsWith("student_profile"))
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(route) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selected) PrimaryDarkGreen else TextGray
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 10.sp,
                        color = if (selected) PrimaryDarkGreen else TextGray
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = SoftBgGreen
                )
            )
        }
    }
}

// WhatsApp Intent Dispatcher
fun launchWhatsApp(context: Context, phone: String, message: String) {
    try {
        val cleanPhone = phone.replace(" ", "").replace("+", "")
        // Ensure proper international indicator (assumes Egypt +2 by default if starts with 0)
        val formattedPhone = if (cleanPhone.startsWith("0")) "2$cleanPhone" else cleanPhone
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(message)}")
        )
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "تطبيق واتساب غير مثبت", Toast.LENGTH_LONG).show()
    }
}

// Dialer Intent Dispatcher
fun launchDialer(context: Context, phone: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "فشل تشغيل لوحة الاتصال", Toast.LENGTH_SHORT).show()
    }
}

fun normalizeArabicDigits(input: String): String {
    val arabicChars = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')
    var result = input
    for (i in 0..9) {
        result = result.replace(arabicChars[i], ('0' + i))
    }
    return result
}

// 30-minute pre-start upcoming session window check for Dashboard Banner
fun isSessionSoonForBanner(sessionTime: String): Boolean {
    try {
        val now = java.util.Calendar.getInstance()
        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(java.util.Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute

        // Standardize time parsing
        val normalizedTime = normalizeArabicDigits(sessionTime.uppercase().trim())
        var targetHour = 0
        var targetMinute = 0

        // Check PM indicators
        val isPm = normalizedTime.contains("PM") || 
                   normalizedTime.contains("م") || 
                   normalizedTime.contains("مساء") || 
                   normalizedTime.contains("مساءً")

        // Check AM indicators
        val isAm = normalizedTime.contains("AM") || 
                   normalizedTime.contains("ص") || 
                   normalizedTime.contains("صباح") || 
                   normalizedTime.contains("صباحًا")

        // Extract numbers separated by colon
        val digits = normalizedTime.filter { it.isDigit() || it == ':' }
        val parts = digits.split(":")
        if (parts.size >= 2) {
            targetHour = parts[0].toIntOrNull() ?: 0
            targetMinute = parts[1].toIntOrNull() ?: 0
            
            // Apply PM/AM adjustments
            if (isPm && targetHour < 12) {
                targetHour += 12
            } else if (isAm && targetHour == 12) {
                targetHour = 0
            } else if (!isPm && !isAm) {
                // If no indicator is specified, guess based on natural school hours
                if (targetHour in 1..11 && currentHour >= 12) {
                    targetHour += 12
                }
            }
        } else {
            return false
        }

        val targetTotalMinutes = targetHour * 60 + targetMinute
        val diff = targetTotalMinutes - currentTotalMinutes
        
        // Return true ONLY if current time is within 30 minutes before class start (diff in 0..30)
        return diff in 0..30
    } catch (e: Exception) {
        return false
    }
}

// 1-hour centered session window check for taking attendance fast-actions
fun isAttendanceCollectable(sessionTime: String): Boolean {
    try {
        val now = java.util.Calendar.getInstance()
        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(java.util.Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute

        // Standardize time parsing
        val normalizedTime = normalizeArabicDigits(sessionTime.uppercase().trim())
        var targetHour = 0
        var targetMinute = 0

        // Check PM indicators
        val isPm = normalizedTime.contains("PM") || 
                   normalizedTime.contains("م") || 
                   normalizedTime.contains("مساء") || 
                   normalizedTime.contains("مساءً")

        // Check AM indicators
        val isAm = normalizedTime.contains("AM") || 
                   normalizedTime.contains("ص") || 
                   normalizedTime.contains("صباح") || 
                   normalizedTime.contains("صباحًا")

        // Extract numbers separated by colon
        val digits = normalizedTime.filter { it.isDigit() || it == ':' }
        val parts = digits.split(":")
        if (parts.size >= 2) {
            targetHour = parts[0].toIntOrNull() ?: 0
            targetMinute = parts[1].toIntOrNull() ?: 0
            
            // Apply PM/AM adjustments
            if (isPm && targetHour < 12) {
                targetHour += 12
            } else if (isAm && targetHour == 12) {
                targetHour = 0
            } else if (!isPm && !isAm) {
                if (targetHour in 1..11 && currentHour >= 12) {
                    targetHour += 12
                }
            }
        } else {
            return false
        }

        val targetTotalMinutes = targetHour * 60 + targetMinute
        val diff = targetTotalMinutes - currentTotalMinutes
        
        // Return true if the session starts within centered -30 to 30 minutes
        return diff in -30..30
    } catch (e: Exception) {
        return false
    }
}

fun isSessionSoon(sessionTime: String): Boolean {
    return isAttendanceCollectable(sessionTime)
}


// ==========================================
// 1. DASHBOARD SCREEN
// ==========================================
@Composable
fun DashboardScreen(
    viewModel: TeacherViewModel,
    onTakeAttendance: (Int) -> Unit,
    onNavigateToClasses: () -> Unit,
    onSearchClick: () -> Unit,
    onNavigateToPayments: () -> Unit,
    onNavigateToExams: () -> Unit,
    onNavigateToGroup: ((Int) -> Unit)? = null
) {
    val stats by viewModel.dashboardStats.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val todayScheduledGroups by viewModel.todaysScheduledGroups.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val students by viewModel.students.collectAsState()
    val enrollments by viewModel.enrollments.collectAsState()
    val currentYear by viewModel.currentAcademicYear.collectAsState()
    val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
    
    // Dialog state for selections
    var showAddGroupDialog by rememberSaveable { mutableStateOf(false) }
    var showDashboardAddStudentDialog by rememberSaveable { mutableStateOf(false) }
    var showAttendanceGroupPickerDialog by rememberSaveable { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.notification.collect { msg ->
            if (msg == "Already Recorded") {
                Toast.makeText(context, "تم تسجيل حصة هذه المجموعة اليوم بالفعل!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBgGreen)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "مدير المعلم",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDarkGreen
                    )
                    Text(
                        text = "لوحة المتابعة والمؤشرات الحية",
                        fontSize = 13.sp,
                        color = TextGray
                    )
                }
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftBgGreen)
                        .clickable { onSearchClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = PrimaryGreen)
                }
            }
        }

        // Today's Scheduled Groups Session Manager list
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "متابعة المجموعات والنشاط اليومي",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDarkGreen,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "تسجيل حضور الحصص النشطة ومراجعة الجداول اليومية بلمسة واحدة",
                    fontSize = 11.sp,
                    color = TextGray,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (groups.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.HomeWork,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "لا توجد مجموعات دراسية مضافة حتى الآن.",
                                fontSize = 14.sp,
                                color = Color(0xFF111827),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else if (todayScheduledGroups.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "لا توجد حصص مجدولة لليوم الحالي.",
                                fontSize = 14.sp,
                                color = Color(0xFF374151),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            items(todayScheduledGroups) { group ->
                val groupStudsCount = remember(enrollments, currentYear, group.id) {
                    val yId = currentYear?.id ?: 1
                    enrollments.count { it.groupId == group.id && it.academicYearId == yId && it.status == "active" }
                }
                val todayDateStr = remember { com.example.data.DateUtils.formatStandard("yyyy-MM-dd") }
                val recordedFlow = remember(group.id, todayDateStr) {
                    viewModel.isSessionRecordedTodayFlow(group.id, todayDateStr)
                }
                DashboardGroupCard(
                    group = group,
                    studentCount = groupStudsCount,
                    sessions = sessions,
                    todaySessionRecordedFlow = recordedFlow,
                    onClick = {
                        if (onNavigateToGroup != null) {
                            onNavigateToGroup(group.id)
                        } else {
                            onNavigateToClasses()
                        }
                    },
                    onRecordSessionClick = {
                        viewModel.recordManualSession(group.id)
                    },
                    onNavigateToSession = { sessionId ->
                        onTakeAttendance(sessionId)
                    }
                )
            }
        }

        // --- NEW SECTION 1: QUICK ACTIONS (إجراءات سريعة) 2x2 Responsive Grid ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Row 0 - Attendance and Payments
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. الغياب (Attendance Card)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(3.dp),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.2.dp, SoftBgGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(145.dp)
                                .clickable { showAttendanceGroupPickerDialog = true }
                                .testTag("quick_action_record_attendance")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFF7ED)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "الغياب",
                                        tint = WarningOrange,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "الغياب",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryDarkGreen
                                )
                            }
                        }

                        // 2. الشهرية (Monthly Payments Card)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(3.dp),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.2.dp, SoftBgGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(145.dp)
                                .clickable { onNavigateToPayments() }
                                .testTag("quick_action_add_payment")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(SoftBgGreen),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CreditCard,
                                        contentDescription = "الشهرية",
                                        tint = PrimaryGreen,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "الشهرية",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryDarkGreen
                                )
                            }
                        }
                    }

                    // Row 1 - Grades and Add Student
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 3. تحصيل درجات (Grades Card)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(3.dp),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.2.dp, SoftBgGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(145.dp)
                                .clickable { onNavigateToExams() }
                                .testTag("quick_action_new_test")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEEF2FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "تحصيل درجات",
                                        tint = Color(0xFF4F46E5),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "تحصيل درجات",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryDarkGreen
                                )
                            }
                        }

                        // 4. إضافة طالب (Add Student Card)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(3.dp),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.2.dp, SoftBgGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(145.dp)
                                .clickable { showDashboardAddStudentDialog = true }
                                .testTag("quick_action_add_student")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFECFDF5)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = "إضافة طالب",
                                        tint = AccentGreen,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "إضافة طالب",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryDarkGreen
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- END OF QUICK ACTIONS ---

        item { Spacer(modifier = Modifier.height(84.dp)) }
    }

    // --- DIALOG: ADD GROUP ---
    if (showAddGroupDialog) {
        AddGroupDialog(
            onDismiss = { showAddGroupDialog = false },
            onSave = { name, startDate, fee, schedule, groupType, billingMode, sessionsPerMonth ->
                viewModel.addGroup(name, startDate, fee, schedule, groupType, billingMode, sessionsPerMonth)
                showAddGroupDialog = false
            }
        )
    }

    // --- DIALOG: ADD STUDENT ---
    if (showDashboardAddStudentDialog) {
        DashboardAddStudentDialog(
            groupsList = groups,
            onDismiss = { showDashboardAddStudentDialog = false },
            onSave = { groupId, name, phone, joinDate, notes, sessionsRemaining ->
                viewModel.addStudent(groupId, name, phone, joinDate, notes, sessionsRemaining)
                showDashboardAddStudentDialog = false
            }
        )
    }

    // --- DIALOG: CHOOSE CLASS FOR ATTENDANCE ---
    if (showAttendanceGroupPickerDialog) {
        AlertDialog(
            onDismissRequest = { showAttendanceGroupPickerDialog = false },
            title = {
                Text(
                    text = "اختر الفصل / المجموعة للحضور",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDarkGreen,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)
                ) {
                    if (groups.isEmpty()) {
                        item {
                            Text("لا توجد فصول دراسية مفعّلة حالياً", color = TextGray)
                        }
                    } else {
                        items(groups) { group ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SoftBgGreen),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAttendanceGroupPickerDialog = false
                                        coroutineScope.launch {
                                            val sessId = viewModel.getOrCreateSessionForToday(group.id, todayDate)
                                            onTakeAttendance(sessId)
                                        }
                                    }
                                    .testTag("attendance_group_choice_${group.id}"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = group.name,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryDarkGreen
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = PrimaryGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAttendanceGroupPickerDialog = false }) {
                    Text("إلغاء", color = PrimaryGreen)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}


// --- COMPONENT: ADD GROUP DIALOG ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddGroupDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Double, String, GroupType, BillingMode, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var groupTime by remember { mutableStateOf("04:00 PM") }
    var groupType by remember { mutableStateOf(GroupType.public) }
    var billingMode by remember { mutableStateOf(BillingMode.monthly) }
    var sessionsPerMonth by remember { mutableStateOf("8") }
    
    val daysOfWeek = remember { listOf("السبت", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة") }
    var selectedDays by remember { mutableStateOf(setOf<String>()) }

    val context = LocalContext.current
    val calendar = remember { java.util.Calendar.getInstance() }

    val datePickerDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                startDate = String.format(Locale.ENGLISH, "%04d/%02d/%02d", year, month + 1, dayOfMonth)
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    val timePickerDialog = remember {
        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val amPm = if (hourOfDay < 12) "AM" else "PM"
                val hour12 = when {
                    hourOfDay == 0 -> 12
                    hourOfDay > 12 -> hourOfDay - 12
                    else -> hourOfDay
                }
                groupTime = String.format(Locale.ENGLISH, "%02d:%02d %s", hour12, minute, amPm)
            },
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE),
            false
        )
    }

    // Init dates nicely
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
        startDate = sdf.format(Date())
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "تهيئة وإعداد مجموعة جديدة",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDarkGreen,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم المجموعة") },
                    modifier = Modifier.fillMaxWidth().testTag("class_name_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() }
                ) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { },
                        label = { Text("تاريخ البداية (YYYY/MM/DD)") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.Black,
                            disabledBorderColor = PrimaryGreen,
                            disabledLabelColor = PrimaryDarkGreen
                        )
                    )
                }

                val feeLabel = if (groupType == GroupType.private && billingMode == BillingMode.per_session) {
                    "سعر الدورة / الباكيدج بالكامل ( Package Price )"
                } else {
                    "القيمة المالية الشهرية ( Fees )"
                }

                OutlinedTextField(
                    value = fee,
                    onValueChange = { fee = it },
                    label = { Text(feeLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { timePickerDialog.show() }
                ) {
                    OutlinedTextField(
                        value = groupTime,
                        onValueChange = { },
                        label = { Text("وقت الحصة للمجموعة") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.Black,
                            disabledBorderColor = PrimaryGreen,
                            disabledLabelColor = PrimaryDarkGreen
                        )
                    )
                }

                // Days selection block
                Text(
                    text = "أيام وجدول المجموعات (اختر أيام العمل فقط):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDarkGreen,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    daysOfWeek.forEach { day ->
                        val isSelected = selectedDays.contains(day)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) PrimaryGreen else Color(0xFFF3F4F6))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) PrimaryDarkGreen else Color(0xFFD1D5DB),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedDays = if (isSelected) {
                                        selectedDays - day
                                    } else {
                                        selectedDays + day
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                color = if (isSelected) Color.White else Color(0xFF374151),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Text(
                    text = "نوع المجموعة (Group Type):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDarkGreen,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            groupType = GroupType.public
                            billingMode = BillingMode.monthly // Focus on Monthly/Static
                            sessionsPerMonth = "0"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (groupType == GroupType.public) PrimaryGreen else Color(0xFFF3F4F6)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "عامة (Public)",
                            color = if (groupType == GroupType.public) Color.White else Color.Black,
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { groupType = GroupType.private },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (groupType == GroupType.private) PrimaryGreen else Color(0xFFF3F4F6)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "خاصة (Private)",
                            color = if (groupType == GroupType.private) Color.White else Color.Black,
                            fontSize = 11.sp
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = (groupType == GroupType.private)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "نظام الفوترة والمحاسبة بالباقة:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryDarkGreen,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { billingMode = BillingMode.monthly },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (billingMode == BillingMode.monthly) PrimaryGreen else Color(0xFFF3F4F6)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "شهري ثابث (Monthly)",
                                    color = if (billingMode == BillingMode.monthly) Color.White else Color.Black,
                                    fontSize = 11.sp
                                )
                            }

                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { billingMode = BillingMode.per_session },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (billingMode == BillingMode.per_session) PrimaryGreen else Color(0xFFF3F4F6)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "عدد الحصص (Sessions)",
                                    color = if (billingMode == BillingMode.per_session) Color.White else Color.Black,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (billingMode == BillingMode.per_session) {
                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = sessionsPerMonth,
                                onValueChange = { sessionsPerMonth = it },
                                label = { Text("عدد الحصص في الدورة/الشهر") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SoftBgGreen)
                    ) {
                        Text("إلغاء", color = PrimaryDarkGreen)
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (name.isNotBlank()) {
                                val timeSuffix = if (groupTime.isNotBlank()) " | $groupTime" else ""
                                val scheduleString = selectedDays.joinToString("، ") + timeSuffix
                                val calculatedBillingMode = if (groupType == GroupType.public) BillingMode.monthly else billingMode
                                val calculatedSessionsPerMonth = if (groupType == GroupType.public || calculatedBillingMode == BillingMode.monthly) {
                                    0
                                } else {
                                    sessionsPerMonth.toIntOrNull() ?: 8
                                }
                                onSave(
                                    name,
                                    startDate,
                                    fee.toDoubleOrNull() ?: 200.0,
                                    scheduleString,
                                    groupType,
                                    calculatedBillingMode,
                                    calculatedSessionsPerMonth
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("حفظ المجموعة", color = Color.White)
                    }
                }
            }
        }
    }
}


// ==========================================
// 2. CLASSES / GROUPS SCREEN
// ==========================================
@Composable
fun ClassesScreen(
    viewModel: TeacherViewModel,
    onNavigateToGroup: (Int) -> Unit
) {
    val groups by viewModel.groups.collectAsState()
    val students by viewModel.students.collectAsState()
    val enrollments by viewModel.enrollments.collectAsState()
    val currentYear by viewModel.currentAcademicYear.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showAddGroupDialog by rememberSaveable { mutableStateOf(false) }
    var selectedGroupToEdit by remember { mutableStateOf<Group?>(null) }
    var selectedGroupToDelete by remember { mutableStateOf<Group?>(null) }

    val filteredGroups = groups.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.scheduleDays.contains(searchQuery, ignoreCase = true)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBgGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "المجموعات المجدولة (${groups.size})",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryDarkGreen
            )
            Text(
                text = "الإدارة الكاملة لجداول الفصول التعليمية",
                fontSize = 12.sp,
                color = TextGray
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Search input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث باسم المجموعة أو جدول الأسبوع") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredGroups.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.HomeWork, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextGray.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("لا توجد فصول دراسية مطابقة لمجال بحثك", color = TextGray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredGroups) { group ->
                        val groupStudsCount = remember(enrollments, currentYear, group.id) {
                            val yId = currentYear?.id ?: 1
                            enrollments.count { it.groupId == group.id && it.academicYearId == yId && it.status == "active" }
                        }
                        GroupCard(
                            group = group,
                            studentCount = groupStudsCount,
                            onClick = { onNavigateToGroup(group.id) },
                            onEditClick = { selectedGroupToEdit = it },
                            onDeleteClick = { selectedGroupToDelete = it }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(100.dp)) }
                }
            }
        }

        // Beautiful Floating Action Button matching screenshots
        LargeFloatingActionButton(
            onClick = { showAddGroupDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 78.dp, end = 20.dp)
                .testTag("add_class_fab"),
            containerColor = AccentGreen,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Class", modifier = Modifier.size(34.dp))
        }
    }

    if (showAddGroupDialog) {
        AddGroupDialog(
            onDismiss = { showAddGroupDialog = false },
            onSave = { name, startDate, fee, schedule, groupType, billingMode, sessionsPerMonth ->
                viewModel.addGroup(name, startDate, fee, schedule, groupType, billingMode, sessionsPerMonth)
                showAddGroupDialog = false
            }
        )
    }

    selectedGroupToEdit?.let { group ->
        EditGroupDialog(
            group = group,
            onDismiss = { selectedGroupToEdit = null },
            onSave = { updatedGroup ->
                viewModel.updateGroup(updatedGroup)
                selectedGroupToEdit = null
            }
        )
    }

    selectedGroupToDelete?.let { group ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedGroupToDelete = null },
            title = { Text("تحذير: حذف الفصل الدراسي بكامله", color = DangerRed, fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد تماماً من رغبتك في حذف فصل \"${group.name}\"؟\n\nتنبيه: سيؤدي هذا الإجراء الحاسم وبفضل قاعدة البيانات إلى حذف كافة الطلاب المسجلين بهذا الفصل فوراً وبشكل نهائي، بالإضافة لجميع سجلات حضورهم، درجات الامتحانات والمدفوعات الخاصة بهم بالكامل!") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGroup(group)
                        selectedGroupToDelete = null
                    }
                ) {
                    Text("نعم، احذف الفصل وكل سجلاته", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedGroupToDelete = null }) {
                    Text("إلغاء", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun GroupCard(
    group: Group,
    studentCount: Int,
    onClick: () -> Unit,
    onEditClick: (Group) -> Unit,
    onDeleteClick: (Group) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("group_card_${group.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, SoftBgGreen)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Elegant green visual side stripe
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(90.dp)
                    .background(PrimaryGreen)
                    .align(Alignment.CenterStart)
            )

            Column(modifier = Modifier.padding(16.dp).padding(start = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryDarkGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${group.monthlyFee} ج.م/شهر",
                            fontSize = 13.sp,
                            color = PrimaryGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Box {
                            IconButton(
                                onClick = { expanded = true },
                                modifier = Modifier.size(28.dp).testTag("group_menu_${group.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = TextGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("تعديل الفصل", color = PrimaryDarkGreen, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryDarkGreen, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        expanded = false
                                        onEditClick(group)
                                    },
                                    modifier = Modifier.testTag("edit_group_item_${group.id}")
                                )
                                DropdownMenuItem(
                                    text = { Text("حذف النهائي للفصل", color = DangerRed, fontWeight = FontWeight.Bold) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        expanded = false
                                        onDeleteClick(group)
                                    },
                                    modifier = Modifier.testTag("delete_group_item_${group.id}")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = TextGray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "البداية: ${com.example.data.DateUtils.formatDateWithArabicDay(group.startDate)}", fontSize = 11.sp, color = TextGray)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = TextGray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = group.scheduleDays, fontSize = 11.sp, color = TextGray)
                    }

                    Box(
                        modifier = Modifier
                            .background(SoftBgGreen, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$studentCount طالب",
                            fontSize = 11.sp,
                            color = PrimaryDarkGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun DashboardGroupCard(
    group: Group,
    studentCount: Int,
    sessions: List<Session>,
    todaySessionRecordedFlow: kotlinx.coroutines.flow.Flow<Boolean>,
    onClick: () -> Unit,
    onRecordSessionClick: () -> Unit,
    onNavigateToSession: (Int) -> Unit
) {
    val todayDate = remember { DateUtils.formatStandard("yyyy-MM-dd") }
    val todayDateSlash = remember { todayDate.replace("-", "/") }
    
    val isSessionRecordedToday by todaySessionRecordedFlow.collectAsState(initial = false)
    
    val todaySession = remember(sessions, group.id, todayDate, todayDateSlash) {
        sessions.find { 
            val sessionDateCore = if (it.date.contains("T")) {
                it.date.split("T")[0]
            } else if (it.date.contains(" ")) {
                it.date.split(" ")[0]
            } else {
                it.date
            }
            val sessionDateClean = sessionDateCore.replace("/", "-")
            it.groupId == group.id && (sessionDateClean == todayDate || sessionDateClean == todayDateSlash.replace("/", "-"))
        }
    }

      Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("dashboard_group_card_${group.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .matchParentSize()
                    .background(PrimaryGreen)
                    .align(Alignment.CenterStart)
            )

            Column(modifier = Modifier.padding(12.dp).padding(start = 12.dp)) {
                Text(
                    text = group.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isSessionRecordedToday) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .height(38.dp)
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "تم تسجيل حصة اليوم",
                                    fontSize = 10.sp,
                                    color = Color(0xFF334155),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Button(
                            onClick = { todaySession?.let { onNavigateToSession(it.id) } },
                            enabled = todaySession != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("open_attendance_notes_btn_${group.id}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryGreen,
                                contentColor = Color.White,
                                disabledContainerColor = Color(0xFFE2E8F0),
                                disabledContentColor = Color(0xFF94A3B8)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Launch,
                                    contentDescription = null,
                                    tint = if (todaySession != null) Color.White else Color(0xFF94A3B8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "أخذ الغياب",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (todaySession != null) Color.White else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = onRecordSessionClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("record_session_btn_${group.id}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGreen,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تسجيل حصة اليوم",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}


// ==========================================
// 3. GROUP DETAILS SCREEN (STUDENTS IN GROUP)
// ==========================================
@Composable
fun GroupDetailScreen(
    groupId: Int,
    viewModel: TeacherViewModel,
    onNavigateToStudent: (Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val groups by viewModel.groups.collectAsState()
    val group = remember(groups, groupId) { groups.find { it.id == groupId } }
    val students by viewModel.getStudentsByGroup(groupId).collectAsState(initial = emptyList())
    val sessions by viewModel.getSessionsByGroup(groupId).collectAsState(initial = emptyList())
    val dailyNotes by viewModel.getDailyNotesForGroup(groupId).collectAsState(initial = emptyList())

    val isPerSessionPrivate = group?.groupType == GroupType.private && group?.billingMode == BillingMode.per_session

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showAddStudentDialog by rememberSaveable { mutableStateOf(false) }
    var showAddSessionDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteGroupPrompt by rememberSaveable { mutableStateOf(false) }
    var showEditGroupDialog by rememberSaveable { mutableStateOf(false) }
    var showPromoteGroupDialog by rememberSaveable { mutableStateOf(false) }
    var selectedStudentToEdit by remember { mutableStateOf<Student?>(null) }
    var selectedStudentToDelete by remember { mutableStateOf<Student?>(null) }

    Scaffold(
        topBar = {
            TeacherAppBar(
                title = group?.name ?: "تفاصيل المجموعة الدراسية",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = PrimaryDarkGreen)
                    }
                },
                onEditClick = { showEditGroupDialog = true }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(LightBgGreen)
        ) {
            // Group General Metadata Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(0.dp, 0.dp, 20.dp, 20.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "مواعيد الحصص: ${group?.scheduleDays ?: ""}", fontSize = 13.sp, color = TextGray)
                            Text(text = "تاريخ البداية: ${group?.startDate?.let { com.example.data.DateUtils.formatDateWithArabicDay(it) } ?: ""}", fontSize = 13.sp, color = TextGray)
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.wrapContentWidth()) {
                            val subscriptionLabel = if (group?.groupType == GroupType.private && group?.billingMode == BillingMode.per_session) {
                                "قيمة الباقة:"
                            } else {
                                "الاشتراك الشهري:"
                            }
                            Text(
                                text = subscriptionLabel,
                                fontSize = 11.sp,
                                color = TextGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${group?.monthlyFee ?: 0.0} ج.م",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAddSessionDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SoftBgGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PrimaryDarkGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("جدولة حصة", color = PrimaryDarkGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showDeleteGroupPrompt = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("حذف المجموعة", color = DangerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { showPromoteGroupDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ترحيل / نقل طلاب المجموعة", color = AccentGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Tab Rows
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.White,
                contentColor = PrimaryDarkGreen
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("الطلاب (${students.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("سجل الحصص (${sessions.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("ملاحظات الحصص والدروس", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Content
            AnimatedContent(targetState = selectedTabIndex, label = "tab") { tab ->
                when (tab) {
                    0 -> {
                        // --- STUDENTS LIST TAB ---
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (students.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.PeopleOutline, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextGray.copy(alpha = 0.5f))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("لا يوجد طلاب في هذه المجموعة حالياً", color = TextGray)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("اضغط على الزر الدائري لتسجيل طالب جديد", fontSize = 12.sp, color = TextGray.copy(alpha = 0.7f))
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(students) { student ->
                                        StudentListCard(
                                            student = student,
                                            onClick = { onNavigateToStudent(student.id) },
                                            onEditClick = { selectedStudentToEdit = it },
                                            onDeleteClick = { selectedStudentToDelete = it },
                                            isPerSessionPrivate = isPerSessionPrivate
                                        )
                                    }
                                    item { Spacer(modifier = Modifier.height(100.dp)) }
                                }
                            }

                            // Dynamic circular floating Add Student Floating Action Button
                            LargeFloatingActionButton(
                                onClick = { showAddStudentDialog = true },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 20.dp, end = 20.dp)
                                    .testTag("add_student_fab"),
                                containerColor = AccentGreen,
                                contentColor = Color.White,
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = "Add Student", modifier = Modifier.size(30.dp))
                            }
                        }
                    }
                    1 -> {
                        // --- SESSIONS LIST TAB ---
                        val allAttendanceRecords by viewModel.attendance.collectAsState(initial = emptyList())
                        val currentCal = remember { Calendar.getInstance() }
                        var selectedCalendar by remember { mutableStateOf(currentCal) }

                        val englishMonths = remember {
                            arrayOf(
                                "January", "February", "March", "April", "May", "June",
                                "July", "August", "September", "October", "November", "December"
                            )
                        }
                        val arabicMonths = remember {
                            arrayOf(
                                "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                                "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
                            )
                        }

                        val filteredSessions = remember(sessions, selectedCalendar) {
                            sessions.filter { session ->
                                val engMatch = "${englishMonths[selectedCalendar.get(Calendar.MONTH)]} ${selectedCalendar.get(Calendar.YEAR)}"
                                val araMatch = "${arabicMonths[selectedCalendar.get(Calendar.MONTH)]} ${selectedCalendar.get(Calendar.YEAR)}"
                                session.monthYear == engMatch || session.monthYear == araMatch ||
                                viewModel.extractMonthYearFromDate(session.date) == engMatch ||
                                viewModel.extractMonthYearArabic(session.date) == araMatch
                            }
                        }

                        val filteredSessionIds = remember(filteredSessions) { filteredSessions.map { it.id }.toSet() }
                        val filteredAttendances = remember(allAttendanceRecords, filteredSessionIds) {
                            allAttendanceRecords.filter { it.sessionId in filteredSessionIds }
                        }

                        val totalSessionsThisMonth = filteredSessions.size
                        val totalPresentCount = filteredAttendances.count { it.isPresent }
                        val totalAbsentCount = filteredAttendances.count { !it.isPresent }
                        val attendancePercentage = if (filteredAttendances.isNotEmpty()) {
                            (totalPresentCount * 100) / filteredAttendances.size
                        } else {
                            100
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            // Month Switcher Component card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SoftBgGreen),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = {
                                        val newCal = Calendar.getInstance().apply {
                                            time = selectedCalendar.time
                                            add(Calendar.MONTH, -1)
                                        }
                                        selectedCalendar = newCal
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "السابق", tint = PrimaryGreen)
                                    }

                                    Text(
                                        text = "${arabicMonths[selectedCalendar.get(Calendar.MONTH)]} ${selectedCalendar.get(Calendar.YEAR)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = PrimaryDarkGreen
                                    )

                                    IconButton(onClick = {
                                        val newCal = Calendar.getInstance().apply {
                                            time = selectedCalendar.time
                                            add(Calendar.MONTH, 1)
                                        }
                                        selectedCalendar = newCal
                                    }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "التالي", tint = PrimaryGreen)
                                    }
                                }
                            }

                            // Performance metrics card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SoftBgGreen.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "مؤشرات الحضور لشهر ${arabicMonths[selectedCalendar.get(Calendar.MONTH)]}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryDarkGreen,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("إجمالي الحصص", fontSize = 10.sp, color = TextGray)
                                            Text("$totalSessionsThisMonth", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("حاضر (كلي)", fontSize = 10.sp, color = TextGray)
                                            Text("$totalPresentCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("غائب (كلي)", fontSize = 10.sp, color = TextGray)
                                            Text("$totalAbsentCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("نسبة الحضور", fontSize = 10.sp, color = TextGray)
                                            Text("$attendancePercentage%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                                        }
                                    }
                                }
                            }

                            if (filteredSessions.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextGray.copy(alpha = 0.5f))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("لا توجد حصص مجدولة في هذا الشهر", color = TextGray)
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(filteredSessions) { session ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            modifier = Modifier.fillMaxWidth(),
                                            elevation = CardDefaults.cardElevation(1.dp),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(text = "حصة يوم ${com.example.data.DateUtils.formatDateWithArabicDay(session.date)}", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)
                                                    Text(text = "توقيت الحصة: ${session.time}", fontSize = 12.sp, color = TextGray)
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    // Take Attendance button
                                                    Button(
                                                        onClick = { onNavigateToStudent(-session.id) }, // Signal navigate to attendance sheet
                                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("الحضور والغياب", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            viewModel.deleteSession(session)
                                                            Toast.makeText(context, "تم حذف الحصة", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    item { Spacer(modifier = Modifier.height(100.dp)) }
                                }
                            }
                        }
                    }
                    2 -> {
                        // --- DAILY LESSON NOTES TAB ---
                        val todayDateStr = remember { DateUtils.formatStandard("yyyy-MM-dd") }
                        var todayNoteText by remember { mutableStateOf("") }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(2.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "مذكرة الحصة الجارية اليوم ($todayDateStr)",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryDarkGreen,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Right
                                        )

                                        OutlinedTextField(
                                            value = todayNoteText,
                                            onValueChange = { todayNoteText = it },
                                            placeholder = { Text("قم بكتابة محتوى الدرس، الواجبات والتعليمات الموجهة إلى الطلاب هنا...") },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(140.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            maxLines = 10
                                        )

                                        Button(
                                            onClick = {
                                                if (todayNoteText.isNotBlank()) {
                                                    viewModel.saveDailyNote(
                                                        groupId = groupId,
                                                        date = todayDateStr,
                                                        sessionNumber = 0,
                                                        content = todayNoteText
                                                    )
                                                    todayNoteText = ""
                                                    Toast.makeText(context, "تم حفظ الملاحظة بنجاح", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "يرجى كتابة محتوى الملاحظة أولاً", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("حفظ مذكرة الحصة", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            if (dailyNotes.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "المذكرات والملاحظات السابقة:",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryDarkGreen,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Right
                                    )
                                }

                                items(dailyNotes.sortedByDescending { it.date }) { note ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.85f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        elevation = CardDefaults.cardElevation(1.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "تاريخ: ${com.example.data.DateUtils.formatDateWithArabicDay(note.date)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryDarkGreen
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = note.content,
                                                fontSize = 12.sp,
                                                color = Color.DarkGray
                                            )
                                        }
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(100.dp)) }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG: ADD STUDENT ---
    if (showAddStudentDialog) {
        AddStudentDialog(
            groupStartDate = group?.startDate ?: "2026-05-01",
            onDismiss = { showAddStudentDialog = false },
            onSave = { name, phone, joinDate, notes, sessionsRemaining ->
                viewModel.addStudent(groupId, name, phone, joinDate, notes, sessionsRemaining)
                showAddStudentDialog = false
            },
            isPerSessionPrivate = isPerSessionPrivate
        )
    }

    selectedStudentToEdit?.let { student ->
        EditStudentDialog(
            student = student,
            onDismiss = { selectedStudentToEdit = null },
            onSave = { updatedStudent ->
                viewModel.updateStudent(updatedStudent)
                selectedStudentToEdit = null
            },
            isPerSessionPrivate = isPerSessionPrivate
        )
    }

    selectedStudentToDelete?.let { student ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedStudentToDelete = null },
            title = { Text("تحذير: حذف الطالب نهائياً", color = DangerRed, fontWeight = FontWeight.Bold) },
            text = { Text("هل أنت متأكد تماماً من حذف الطالب \"${student.name}\"؟\n\nتنبيه: سيقوم البرنامج بمسح كافة السجلات التابعة له تلقائياً بما فيها حضور الحصص، درجات الاختبارات، وقائمة فواتير الدفع والاشتراكات الحالية والماضية نهائياً بدون رجوع!") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteStudent(student)
                        selectedStudentToDelete = null
                    }
                ) {
                    Text("نعم، احذف الطالب بكل سجلاته", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedStudentToDelete = null }) {
                    Text("إلغاء", color = Color.Gray)
                }
            }
        )
    }

    // --- DIALOG: ADD SESSION ---
    if (showAddSessionDialog) {
        AddSessionDialog(
            onDismiss = { showAddSessionDialog = false },
            onSave = { date, time ->
                viewModel.addSession(groupId, date, time)
                showAddSessionDialog = false
            }
        )
    }

    // --- DELETE PROMPT GROUP ---
    if (showDeleteGroupPrompt) {
        AlertDialog(
            onDismissRequest = { showDeleteGroupPrompt = false },
            title = { Text("تأكيد الحذف الكلي") },
            text = { Text("هل أنت متأكد من رغبتك في حذف هذه المجموعة بالكامل؟ سيؤدي ذلك إلى مسح كافة الطلاب، سجلات الحضور، وسجلات الدفع والدرجات المقترنة بشكل نهائي.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        group?.let { viewModel.deleteGroup(it) }
                        showDeleteGroupPrompt = false
                        onBack()
                    }
                ) {
                    Text("حذف نهائي", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupPrompt = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showPromoteGroupDialog && group != null) {
        var selectedTargetGroupId by remember { mutableStateOf(groups.firstOrNull { it.id != groupId }?.id) }
        var dropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPromoteGroupDialog = false },
            containerColor = Color.White,
            title = { Text("ترحيل طلاب المجموعة", color = PrimaryDarkGreen, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("سيتم ترحيل جميع الطلاب النشطين من هذه المجموعة إلى المجموعة الجديدة. سيحفظ أرشيف المجموعة الحالية للطلاب.", fontSize = 13.sp, color = TextGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("المجموعة الوجهة:", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { dropdownExpanded = true },
                            colors = CardDefaults.cardColors(containerColor = SoftBgGreen)
                        ) {
                            Text(
                                text = groups.find { it.id == selectedTargetGroupId }?.name ?: "اختر مجموعة",
                                modifier = Modifier.padding(12.dp),
                                color = PrimaryDarkGreen
                            )
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            groups.filter { it.id != groupId }.forEach { tgtGrp ->
                                DropdownMenuItem(
                                    text = { Text(tgtGrp.name, color = PrimaryDarkGreen) },
                                    onClick = {
                                        selectedTargetGroupId = tgtGrp.id
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedTargetGroupId?.let { tgtId ->
                            viewModel.promoteGroup(groupId, tgtId) {
                                android.widget.Toast.makeText(context, "تم ترحيل الطلاب بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                                showPromoteGroupDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                    enabled = selectedTargetGroupId != null
                ) {
                    Text("تأكيد الترحيل", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPromoteGroupDialog = false }) {
                    Text("إلغاء", color = TextGray)
                }
            }
        )
    }

    if (showEditGroupDialog && group != null) {
        EditGroupDialog(
            group = group,
            onDismiss = { showEditGroupDialog = false },
            onSave = { updatedGroup ->
                viewModel.updateGroup(updatedGroup)
                showEditGroupDialog = false
            }
        )
    }
}

@Composable
fun StudentListCard(
    student: Student,
    onClick: () -> Unit,
    onEditClick: (Student) -> Unit,
    onDeleteClick: (Student) -> Unit,
    isPerSessionPrivate: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("student_card_${student.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SoftBgGreen)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(68.dp)
                    .background(PrimaryGreen)
                    .align(Alignment.CenterStart)
            )

            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .padding(start = 6.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = student.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PrimaryDarkGreen)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = TextGray, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = student.parentPhone, fontSize = 11.sp, color = TextGray)
                    }
                    if (isPerSessionPrivate) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = if (student.sessionsRemaining <= 0) DangerRed else PrimaryGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "الحصص المتبقية: ${student.sessionsRemaining}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (student.sessionsRemaining <= 0) DangerRed else PrimaryDarkGreen
                            )
                        }
                        if (student.sessionsRemaining <= 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠️ انتهى الرصيد - برجاء تحصيل الاشتراك النقدي",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DangerRed
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(
                            onClick = { expanded = true },
                            modifier = Modifier.size(32.dp).testTag("student_menu_${student.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = TextGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("تعديل الطالب", color = PrimaryDarkGreen, fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryDarkGreen, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    expanded = false
                                    onEditClick(student)
                                },
                                modifier = Modifier.testTag("edit_student_item_${student.id}")
                            )
                            DropdownMenuItem(
                                text = { Text("حذف الطالب والبيانات", color = DangerRed, fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    expanded = false
                                    onDeleteClick(student)
                                },
                                modifier = Modifier.testTag("delete_student_item_${student.id}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowBackIosNew,
                        contentDescription = null,
                        tint = TextGray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// --- ADD STUDENT DIALOG ---
@Composable
fun AddStudentDialog(
    groupStartDate: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Int) -> Unit,
    isPerSessionPrivate: Boolean = false
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var joinDate by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var sessionsRemaining by remember { mutableStateOf("8") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val calendar = remember { java.util.Calendar.getInstance() }

    val datePickerDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                joinDate = String.format(Locale.ENGLISH, "%04d/%02d/%02d", year, month + 1, dayOfMonth)
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    LaunchedEffect(Unit) {
        joinDate = if (groupStartDate.isNotBlank()) groupStartDate.replace("-", "/") else "2026/06/12"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "تسجيل طالب جديد",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDarkGreen,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it 
                        nameError = if (it.isBlank()) "الاسم بالكامل مطلوب" else null
                    },
                    label = { Text("الاسم الكامل للطالب") },
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth().testTag("student_name_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { input ->
                        val clean = input.trim()
                        if (clean.isEmpty() || clean.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
                            phone = input
                        }
                        phoneError = if (input.isBlank()) {
                            "رقم هاتف ولي الأمر مطلوب"
                        } else if (!isValidEgyptianPhoneNumber(input)) {
                            "صيغة رقم الهاتف غير صالحة. يجب أن يكون رقم مصري صحيح (مثال: 01012345678)"
                        } else {
                            null
                        }
                    },
                    label = { Text("رقم هاتف ولي الأمر") },
                    isError = phoneError != null,
                    supportingText = { phoneError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() }
                ) {
                    OutlinedTextField(
                        value = joinDate,
                        onValueChange = { },
                        label = { Text("تاريخ الانضمام (YYYY/MM/DD)") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.Black,
                            disabledBorderColor = PrimaryGreen,
                            disabledLabelColor = PrimaryDarkGreen
                        )
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات خاصة ومعلومات طبية") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                if (isPerSessionPrivate) {
                    OutlinedTextField(
                        value = sessionsRemaining,
                        onValueChange = { sessionsRemaining = it },
                        label = { Text("عدد الحصص المدفوعة مقدماً") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SoftBgGreen)
                    ) {
                        Text("إلغاء", color = PrimaryDarkGreen)
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            nameError = if (name.isBlank()) "الاسم بالكامل مطلوب" else null
                            phoneError = if (phone.isBlank()) {
                                "رقم هاتف ولي الأمر مطلوب"
                            } else if (!isValidEgyptianPhoneNumber(phone)) {
                                "صيغة رقم الهاتف غير صالحة. يجب أن يكون رقم مصري صحيح (مثال: 01012345678)"
                            } else {
                                null
                            }
                            if (nameError == null && phoneError == null && name.isNotBlank() && phone.isNotBlank()) {
                                onSave(name, phone, joinDate, notes, sessionsRemaining.toIntOrNull() ?: 8)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("حفظ", color = Color.White)
                    }
                }
            }
        }
    }
}

// --- EDIT GROUP DIALOG ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditGroupDialog(
    group: Group,
    onDismiss: () -> Unit,
    onSave: (Group) -> Unit
) {
    var name by remember { mutableStateOf(group.name) }
    var startDate by remember { mutableStateOf(group.startDate) }
    var fee by remember { mutableStateOf(group.monthlyFee.toString()) }
    var groupType by remember { mutableStateOf(group.groupType) }
    var billingMode by remember { mutableStateOf(group.billingMode) }
    var sessionsPerMonth by remember { mutableStateOf(group.sessionsPerMonth.toString()) }
    
    var nameError by remember { mutableStateOf<String?>(null) }
    var feeError by remember { mutableStateOf<String?>(null) }

    // Parse time if schedule contains " | "
    val scheduleParts = group.scheduleDays.split(" | ")
    val daysPart = scheduleParts.getOrNull(0) ?: ""
    val groupTimeValue = scheduleParts.getOrNull(1) ?: "04:00 PM"
    var groupTime by remember { mutableStateOf(groupTimeValue) }
    
    val daysOfWeek = remember { listOf("السبت", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة") }
    var selectedDays by remember {
        val daysList = daysPart.split("، ").map { it.trim() }
        mutableStateOf(daysOfWeek.filter { daysList.contains(it) }.toSet())
    }

    val context = LocalContext.current
    val calendar = remember { java.util.Calendar.getInstance() }

    val datePickerDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                startDate = String.format(Locale.ENGLISH, "%04d/%02d/%02d", year, month + 1, dayOfMonth)
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    val timePickerDialog = remember {
        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val amPm = if (hourOfDay < 12) "AM" else "PM"
                val hour12 = when {
                    hourOfDay == 0 -> 12
                    hourOfDay > 12 -> hourOfDay - 12
                    else -> hourOfDay
                }
                groupTime = String.format(Locale.ENGLISH, "%02d:%02d %s", hour12, minute, amPm)
            },
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE),
            false
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "تعديل بيانات المجموعة",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDarkGreen,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = if (it.isBlank()) "اسم المجموعة مطلوب ولا يمكن تركه فارغاً" else null
                    },
                    label = { Text("اسم المجموعة") },
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth().testTag("edit_class_name_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() }
                ) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { },
                        label = { Text("تاريخ البداية (YYYY/MM/DD)") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.Black,
                            disabledBorderColor = PrimaryGreen,
                            disabledLabelColor = PrimaryDarkGreen
                        )
                    )
                }

                val feeLabel = if (groupType == GroupType.private && billingMode == BillingMode.per_session) {
                    "سعر الدورة / الباكيدج بالكامل ( Package Price )"
                } else {
                    "القيمة المالية الشهرية ( Fees )"
                }

                OutlinedTextField(
                    value = fee,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' }) {
                            fee = input
                        }
                        feeError = if (input.isBlank()) {
                            "القيمة المالية مطلوبة"
                        } else if (input.toDoubleOrNull() == null) {
                            "يجب إدخال قيمة مالية رقمية صحيحة"
                        } else {
                            null
                        }
                    },
                    label = { Text(feeLabel) },
                    isError = feeError != null,
                    supportingText = { feeError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { timePickerDialog.show() }
                ) {
                    OutlinedTextField(
                        value = groupTime,
                        onValueChange = { },
                        label = { Text("وقت الحصة للمجموعة") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.Black,
                            disabledBorderColor = PrimaryGreen,
                            disabledLabelColor = PrimaryDarkGreen
                        )
                    )
                }

                // Days selection block
                Text(
                    text = "أيام وجدول المجموعات (اختر أيام العمل فقط):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDarkGreen,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    daysOfWeek.forEach { day ->
                        val isSelected = selectedDays.contains(day)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) PrimaryGreen else Color(0xFFF3F4F6))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) PrimaryDarkGreen else Color(0xFFD1D5DB),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedDays = if (isSelected) {
                                        selectedDays - day
                                    } else {
                                        selectedDays + day
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                color = if (isSelected) Color.White else Color(0xFF374151),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Text(
                    text = "نوع المجموعة (Group Type):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDarkGreen,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            groupType = GroupType.public
                            billingMode = BillingMode.monthly // Focus on Monthly/Static
                            sessionsPerMonth = "0"
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (groupType == GroupType.public) PrimaryGreen else Color(0xFFF3F4F6)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "عامة (Public)",
                            color = if (groupType == GroupType.public) Color.White else Color.Black,
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { groupType = GroupType.private },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (groupType == GroupType.private) PrimaryGreen else Color(0xFFF3F4F6)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "خاصة (Private)",
                            color = if (groupType == GroupType.private) Color.White else Color.Black,
                            fontSize = 11.sp
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = (groupType == GroupType.private)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "نظام الفوترة والمحاسبة بالباقة:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryDarkGreen,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { billingMode = BillingMode.monthly },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (billingMode == BillingMode.monthly) PrimaryGreen else Color(0xFFF3F4F6)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "شهري ثابث (Monthly)",
                                    color = if (billingMode == BillingMode.monthly) Color.White else Color.Black,
                                    fontSize = 11.sp
                                )
                            }

                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { billingMode = BillingMode.per_session },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (billingMode == BillingMode.per_session) PrimaryGreen else Color(0xFFF3F4F6)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "عدد الحصص (Sessions)",
                                    color = if (billingMode == BillingMode.per_session) Color.White else Color.Black,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (billingMode == BillingMode.per_session) {
                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = sessionsPerMonth,
                                onValueChange = { sessionsPerMonth = it },
                                label = { Text("عدد الحصص في الدورة/الشهر") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SoftBgGreen)
                    ) {
                        Text("إلغاء", color = PrimaryDarkGreen)
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (name.isBlank()) {
                                nameError = "اسم المجموعة مطلوب ولا يمكن تركه فارغاً"
                            }
                            if (fee.isBlank()) {
                                feeError = "القيمة المالية مطلوبة"
                            }
                            if (selectedDays.isEmpty()) {
                                Toast.makeText(context, "الرجاء تحديد يوم واحد على الأقل للمجموعة", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (nameError == null && feeError == null && name.isNotBlank() && fee.isNotBlank()) {
                                val timeSuffix = if (groupTime.isNotBlank()) " | $groupTime" else ""
                                val scheduleString = selectedDays.joinToString("، ") + timeSuffix
                                val calculatedBillingMode = if (groupType == GroupType.public) BillingMode.monthly else billingMode
                                val calculatedSessionsPerMonth = if (groupType == GroupType.public || calculatedBillingMode == BillingMode.monthly) {
                                    0
                                } else {
                                    sessionsPerMonth.toIntOrNull() ?: group.sessionsPerMonth
                                }

                                onSave(
                                    group.copy(
                                        name = name,
                                        startDate = startDate,
                                        monthlyFee = fee.toDoubleOrNull() ?: group.monthlyFee,
                                        scheduleDays = scheduleString,
                                        groupType = groupType,
                                        billingMode = calculatedBillingMode,
                                        sessionsPerMonth = calculatedSessionsPerMonth,
                                        daysOfWeek = DateUtils.parseScheduleToDaysOfWeek(scheduleString)
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("تعديل المجموعة", color = Color.White)
                    }
                }
            }
        }
    }
}

// --- EDIT STUDENT DIALOG ---
@Composable
fun EditStudentDialog(
    student: Student,
    onDismiss: () -> Unit,
    onSave: (Student) -> Unit,
    isPerSessionPrivate: Boolean = false
) {
    var name by remember { mutableStateOf(student.name) }
    var phone by remember { mutableStateOf(student.parentPhone) }
    var joinDate by remember { mutableStateOf(student.joinDate) }
    var notes by remember { mutableStateOf(student.notes) }
    var sessionsRemaining by remember { mutableStateOf(student.sessionsRemaining.toString()) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val calendar = remember { java.util.Calendar.getInstance() }

    val datePickerDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                joinDate = String.format(Locale.ENGLISH, "%04d/%02d/%02d", year, month + 1, dayOfMonth)
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "تعديل بيانات الطالب",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDarkGreen,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = if (it.isBlank()) "اسم الطالب مطلوب ولا يمكن تركه فارغاً" else null
                    },
                    label = { Text("اسم الطالب ثلاثي") },
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth().testTag("edit_student_name_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { input ->
                        val clean = input.trim()
                        if (clean.isEmpty() || clean.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
                            phone = input
                        }
                        phoneError = if (input.isBlank()) {
                            "رقم هاتف ولي الأمر مطلوب"
                        } else if (!isValidEgyptianPhoneNumber(input)) {
                            "صيغة رقم الهاتف غير صالحة. يجب أن يكون رقم مصري صحيح (مثال: 01012345678)"
                        } else {
                            null
                        }
                    },
                    label = { Text("رقم هاتف ولي الأمر") },
                    isError = phoneError != null,
                    supportingText = { phoneError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() }
                ) {
                    OutlinedTextField(
                        value = joinDate,
                        onValueChange = { },
                        label = { Text("تاريخ الانضمام (YYYY/MM/DD)") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.Black,
                            disabledBorderColor = PrimaryGreen,
                            disabledLabelColor = PrimaryDarkGreen
                        )
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات خاصة ومعلومات طبية") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                if (isPerSessionPrivate) {
                    OutlinedTextField(
                        value = sessionsRemaining,
                        onValueChange = { sessionsRemaining = it },
                        label = { Text("عدد الحصص المتبقية") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SoftBgGreen)
                    ) {
                        Text("إلغاء", color = PrimaryDarkGreen)
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            nameError = if (name.isBlank()) "اسم الطالب مطلوب ولا يمكن تركه فارغاً" else null
                            phoneError = if (phone.isBlank()) {
                                "رقم هاتف ولي الأمر مطلوب"
                            } else if (!isValidEgyptianPhoneNumber(phone)) {
                                "صيغة رقم الهاتف غير صالحة. يجب أن يكون رقم مصري صحيح (مثال: 01012345678)"
                            } else {
                                null
                            }
                            if (nameError == null && phoneError == null && name.isNotBlank() && phone.isNotBlank()) {
                                onSave(
                                    student.copy(
                                        name = name,
                                        parentPhone = phone,
                                        joinDate = joinDate,
                                        notes = notes,
                                        sessionsRemaining = sessionsRemaining.toIntOrNull() ?: student.sessionsRemaining
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("حفظ التغييرات", color = Color.White)
                    }
                }
            }
        }
    }
}

// --- DASHBOARD ADD STUDENT DIALOG WITH GROUP SELECTOR ---
@Composable
fun DashboardAddStudentDialog(
    groupsList: List<Group>,
    onDismiss: () -> Unit,
    onSave: (Int, String, String, String, String, Int) -> Unit
) {
    if (groupsList.isEmpty()) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "عذراً، يجب عليك إنشاء مجموعة أولاً قبل إضافة الطلاب.",
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDarkGreen,
                        fontSize = 14.sp
                    )
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("موافق", color = Color.White)
                    }
                }
            }
        }
        return
    }

    var selectedGroupIdx by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var joinDate by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var sessionsRemaining by remember { mutableStateOf("8") }
    var expanded by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedGroupIdx) {
        joinDate = groupsList.getOrNull(selectedGroupIdx)?.startDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "تسجيل طالب جديد",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDarkGreen,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                // Group dropdown selector with overlay clicking bug fixed!
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = groupsList.getOrNull(selectedGroupIdx)?.name ?: "اختر الفئة",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("المجموعة الدراسية") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = PrimaryGreen) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PrimaryDarkGreen,
                            unfocusedTextColor = PrimaryDarkGreen,
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = PrimaryGreen,
                            focusedLabelColor = PrimaryDarkGreen,
                            unfocusedLabelColor = PrimaryDarkGreen,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                    // Invisible overlay to make it perfectly clickable on any coordinate
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { expanded = true }
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        groupsList.forEachIndexed { index, group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = {
                                    selectedGroupIdx = index
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it 
                        nameError = if (it.isBlank()) "الاسم بالكامل مطلوب" else null
                    },
                    label = { Text("الاسم الكامل للطالب") },
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth().testTag("student_name_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = PrimaryGreen
                    )
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { input ->
                        val clean = input.trim()
                        if (clean.isEmpty() || clean.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }) {
                            phone = input
                        }
                        phoneError = if (input.isBlank()) {
                            "رقم هاتف ولي الأمر مطلوب"
                        } else if (!isValidEgyptianPhoneNumber(input)) {
                            "صيغة رقم الهاتف غير صالحة. يجب أن يكون رقم مصري صحيح (مثال: 01012345678)"
                        } else {
                            null
                        }
                    },
                    label = { Text("رقم هاتف ولي الأمر") },
                    isError = phoneError != null,
                    supportingText = { phoneError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = PrimaryGreen
                    )
                )

                OutlinedTextField(
                    value = joinDate,
                    onValueChange = { joinDate = it },
                    label = { Text("تاريخ الانضمام (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = PrimaryGreen
                    )
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("ملاحظات خاصة ومعلومات طبية") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = PrimaryGreen
                    )
                )

                val selectedGroup = groupsList.getOrNull(selectedGroupIdx)
                val isPerSessionPrivate = selectedGroup?.groupType == GroupType.private && selectedGroup?.billingMode == BillingMode.per_session

                if (isPerSessionPrivate) {
                    OutlinedTextField(
                        value = sessionsRemaining,
                        onValueChange = { sessionsRemaining = it },
                        label = { Text("عدد الحصص المدفوعة مقدماً") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = PrimaryGreen
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SoftBgGreen)
                    ) {
                        Text("إلغاء", color = PrimaryDarkGreen)
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            nameError = if (name.isBlank()) "الاسم بالكامل مطلوب" else null
                            phoneError = if (phone.isBlank()) {
                                "رقم هاتف ولي الأمر مطلوب"
                            } else if (!isValidEgyptianPhoneNumber(phone)) {
                                "صيغة رقم الهاتف غير صالحة. يجب أن يكون رقم مصري صحيح (مثال: 01012345678)"
                            } else {
                                null
                            }
                            if (nameError == null && phoneError == null && name.isNotBlank() && phone.isNotBlank()) {
                                onSave(
                                    groupsList[selectedGroupIdx].id,
                                    name,
                                    phone,
                                    joinDate,
                                    notes,
                                    sessionsRemaining.toIntOrNull() ?: 8
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("حفظ", color = Color.White)
                    }
                }
            }
        }
    }
}

// --- ADD SESSION DIALOG ---
@Composable
fun AddSessionDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }

    val context = LocalContext.current
    val calendar = remember { java.util.Calendar.getInstance() }

    val datePickerDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                date = String.format(Locale.ENGLISH, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    val timePickerDialog = remember {
        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val amPm = if (hourOfDay < 12) "AM" else "PM"
                val hour12 = when {
                    hourOfDay == 0 -> 12
                    hourOfDay > 12 -> hourOfDay - 12
                    else -> hourOfDay
                }
                time = String.format(Locale.ENGLISH, "%02d:%02d %s", hour12, minute, amPm)
            },
            calendar.get(java.util.Calendar.HOUR_OF_DAY),
            calendar.get(java.util.Calendar.MINUTE),
            false
        )
    }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        date = sdf.format(Date())
        val timeSdf = SimpleDateFormat("hh:mm", Locale.ENGLISH)
        val isPm = Calendar.getInstance().get(Calendar.AM_PM) == Calendar.PM
        time = timeSdf.format(Date()) + if (isPm) " PM" else " AM"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "جدولة حصة / جلسة تدريسية جديدة",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDarkGreen,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() }
                ) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { },
                        label = { Text("التاريخ (YYYY-MM-DD)") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.Black,
                            disabledBorderColor = PrimaryGreen,
                            disabledLabelColor = PrimaryDarkGreen
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { timePickerDialog.show() }
                ) {
                    OutlinedTextField(
                        value = time,
                        onValueChange = { },
                        label = { Text("الموعد / التوقيت") },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.Black,
                            disabledBorderColor = PrimaryGreen,
                            disabledLabelColor = PrimaryDarkGreen
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SoftBgGreen)
                    ) {
                        Text("إلغاء", color = PrimaryDarkGreen)
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (date.isNotBlank() && time.isNotBlank()) {
                                onSave(date, time)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Text("حفظ", color = Color.White)
                    }
                }
            }
        }
    }
}


// ==========================================
// 4. ATTENDANCE SHEET SUB-SCREEN
// ==========================================
@Composable
fun AttendanceSheetScreen(
    sessionId: Int,
    viewModel: TeacherViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var session by remember { mutableStateOf<Session?>(null) }
    var groupName by remember { mutableStateOf("اسم المجموعة") }
    val students by viewModel.students.collectAsState()
    val enrollments by viewModel.enrollments.collectAsState()
    val currentYear by viewModel.currentAcademicYear.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    // Temporary attendances state
    val attendanceMapState = remember { mutableStateMapOf<Int, AttendanceStatus>() }
    val lateArrivalTimeMapState = remember { mutableStateMapOf<Int, String?>() }

    // Read details
    LaunchedEffect(sessionId) {
        attendanceMapState.clear()
        lateArrivalTimeMapState.clear()
        val s = viewModel.getSessionById(sessionId)
        session = s
        s?.let {
            val grp = viewModel.getGroupById(it.groupId)
            grp?.let { g -> groupName = g.name }

            // Fetch current attendance list if exists
            viewModel.getAttendanceForSession(sessionId).first().forEach { record ->
                attendanceMapState[record.studentId] = record.status
                lateArrivalTimeMapState[record.studentId] = record.lateArrivalTime
            }
        }
    }

    // Filter students belonging only to this group
    val activeGroupStudents = remember(session, students, enrollments, currentYear) {
        if (session == null) emptyList()
        else {
            val yId = currentYear?.id ?: 1
            val enrolledStudentIds = enrollments
                .filter { it.groupId == session!!.groupId && it.academicYearId == yId && it.status == "active" }
                .map { it.studentId }
            students.filter { it.id in enrolledStudentIds }
        }
    }

    // Init attendance map with present for all students initially
    LaunchedEffect(activeGroupStudents) {
        activeGroupStudents.forEach { student ->
            if (!attendanceMapState.containsKey(student.id)) {
                attendanceMapState[student.id] = AttendanceStatus.present
            }
        }
    }

    val filteredStudents = remember(activeGroupStudents, searchQuery) {
        activeGroupStudents.filter { student ->
            student.name.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TeacherAppBar(
                title = "سجل حضور: $groupName",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = PrimaryDarkGreen)
                    }
                },
                onHomeClick = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(LightBgGreen)
                .padding(16.dp)
        ) {
            // Stats Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "تاريخ الحصة: ${session?.date ?: ""}", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)
                        Text(text = "التوقيت: ${session?.time ?: ""}", fontSize = 12.sp, color = TextGray)
                    }

                    Box(
                        modifier = Modifier
                            .background(SoftBgGreen, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        val presentCount = filteredStudents.count { (attendanceMapState[it.id] ?: AttendanceStatus.present) == AttendanceStatus.present }
                        val lateCount = filteredStudents.count { (attendanceMapState[it.id] ?: AttendanceStatus.present) == AttendanceStatus.late }
                        val absentCount = filteredStudents.count { (attendanceMapState[it.id] ?: AttendanceStatus.present) == AttendanceStatus.absent }
                        Text(
                            text = "حاضر: $presentCount | متأخر: $lateCount | غائب: $absentCount",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryDarkGreen
                        )
                    }
                }
            }

            // Search Bar Component
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("بحث باسم الطالب...") },
                placeholder = { Text("اكتب اسم الطالب للفلترة وسرعة الاختيار...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryGreen) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("attendance_search_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Quick present/absent buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        filteredStudents.forEach { 
                            attendanceMapState[it.id] = AttendanceStatus.present 
                            lateArrivalTimeMapState[it.id] = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SoftBgGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("الكل حاضر", color = PrimaryDarkGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        filteredStudents.forEach { 
                            attendanceMapState[it.id] = AttendanceStatus.absent 
                            lateArrivalTimeMapState[it.id] = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SoftBgGreen),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("الكل غائب", color = PrimaryDarkGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Student list with cell click toggling
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filteredStudents.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا يوجد طلاب يطابقون البحث", color = TextGray)
                        }
                    }
                } else {
                    items(filteredStudents, key = { it.id }) { student ->
                        val status = attendanceMapState[student.id] ?: AttendanceStatus.present
                        val lateTime = lateArrivalTimeMapState[student.id]

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when (status) {
                                    AttendanceStatus.present -> Color.White
                                    AttendanceStatus.absent -> Color(0xFFFDF2F2)
                                    AttendanceStatus.late -> Color(0xFFFFFBEB)
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = when (status) {
                                    AttendanceStatus.present -> SoftBgGreen
                                    AttendanceStatus.absent -> DangerRed.copy(alpha = 0.3f)
                                    AttendanceStatus.late -> WarningOrange.copy(alpha = 0.3f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val nextStatus = when (status) {
                                        AttendanceStatus.present -> AttendanceStatus.absent
                                        AttendanceStatus.absent -> {
                                            lateArrivalTimeMapState[student.id] = DateUtils.formatStandard("hh:mm a")
                                            AttendanceStatus.late
                                        }
                                        AttendanceStatus.late -> {
                                            lateArrivalTimeMapState[student.id] = null
                                            AttendanceStatus.present
                                        }
                                    }
                                    attendanceMapState[student.id] = nextStatus
                                }
                                .testTag("student_row_${student.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = student.name,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryDarkGreen,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "هاتف: ${student.parentPhone}",
                                        fontSize = 11.sp,
                                        color = TextGray
                                    )
                                }

                                // Interactive design Indicator status badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when (status) {
                                                AttendanceStatus.present -> SuccessGreen.copy(alpha = 0.12f)
                                                AttendanceStatus.absent -> DangerRed.copy(alpha = 0.12f)
                                                AttendanceStatus.late -> WarningOrange.copy(alpha = 0.12f)
                                            }
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = when (status) {
                                                AttendanceStatus.present -> SuccessGreen
                                                AttendanceStatus.absent -> DangerRed
                                                AttendanceStatus.late -> WarningOrange
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    val statusText = when (status) {
                                        AttendanceStatus.present -> "حاضر"
                                        AttendanceStatus.absent -> "غائب"
                                        AttendanceStatus.late -> {
                                            if (lateTime != null) "متأخر ($lateTime)" else "متأخر"
                                        }
                                    }
                                    Text(
                                        text = statusText,
                                        color = when (status) {
                                            AttendanceStatus.present -> SuccessGreen
                                            AttendanceStatus.absent -> DangerRed
                                            AttendanceStatus.late -> WarningOrange
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Save footer button
            Button(
                onClick = {
                    viewModel.saveAttendance(sessionId, attendanceMapState.toMap(), lateArrivalTimeMapState.toMap())
                    Toast.makeText(context, "تم حفظ الحضور والغياب بنجاح", Toast.LENGTH_LONG).show()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_attendance_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("تأكيد وحفظ التغييرات الكلية", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}


// ==========================================
// 5. STUDENT PROFILE SCREEN
// ==========================================
@Composable
fun StudentProfileScreen(
    studentId: Int,
    groupId: Int,
    viewModel: TeacherViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val studentsList by viewModel.students.collectAsState()
    val student = remember(studentsList, studentId) { studentsList.find { it.id == studentId } }
    val profileStats by viewModel.getStudentProfileStats(studentId, groupId).collectAsState(initial = StudentProfileStats())
    val paymentList by viewModel.getPaymentsForStudent(studentId).collectAsState(initial = emptyList())
    val examScores by viewModel.getExamScoresForStudent(studentId).collectAsState(initial = emptyList())
    val attendances by viewModel.getAttendanceForStudent(studentId).collectAsState(initial = emptyList())
    val groupSessions by viewModel.getSessionsByGroup(groupId).collectAsState(initial = emptyList())

    val groups by viewModel.groups.collectAsState()
    val group = remember(groups, groupId) { groups.find { it.id == groupId } }
    val isPerSessionPrivate = group?.groupType == com.example.data.GroupType.private && group?.billingMode == com.example.data.BillingMode.per_session

    var pdfGroupId by rememberSaveable { mutableIntStateOf(groupId) }
    val pdfGroupSessions by viewModel.getSessionsByGroup(pdfGroupId).collectAsState(initial = emptyList())
    val pdfGroup = remember(groups, pdfGroupId) { groups.find { it.id == pdfGroupId } }

    // Dialog sheets
    var showPaymentDialog by rememberSaveable { mutableStateOf(false) }
    var showExamDialog by rememberSaveable { mutableStateOf(false) }
    var showNotesDialog by rememberSaveable { mutableStateOf(false) }
    var showDeletePrompt by rememberSaveable { mutableStateOf(false) }
    var showEditStudentDialog by rememberSaveable { mutableStateOf(false) }

    var expandedPdfEnrollment by remember { mutableStateOf(false) }
    var selectedPdfEnrollment by remember { mutableStateOf<com.example.data.Enrollment?>(null) }
    val studentEnrollments by viewModel.getStudentEnrollments(studentId).collectAsState(initial = emptyList())
    val academicYears by viewModel.academicYears.collectAsState(initial = emptyList())

    LaunchedEffect(studentEnrollments, groupId) {
        if (selectedPdfEnrollment == null && studentEnrollments.isNotEmpty()) {
            selectedPdfEnrollment = studentEnrollments.find { it.groupId == groupId } ?: studentEnrollments.lastOrNull()
        }
    }

    Scaffold(
        topBar = {
            TeacherAppBar(
                title = "ملف الطالب",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = PrimaryDarkGreen)
                    }
                },
                onHomeClick = onBack,
                onEditClick = { showEditStudentDialog = true }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(LightBgGreen)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // First item: Student Identity Badge Card
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth().testTag("student_profile_card")
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // User Avatar
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(PrimaryDarkGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = student?.name?.take(2) ?: "ط",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = student?.name ?: "", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)

                        Box(
                            modifier = Modifier
                                .background(SoftBgGreen, RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Text(text = profileStats.groupName, color = PrimaryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stats Summary Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "نسبة الحضور",
                                    fontSize = 11.sp,
                                    color = TextGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${profileStats.attendancePercentage}%",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryGreen,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Box(modifier = Modifier.width(1.dp).height(30.dp).background(SurfaceContainer))
                            Column(
                                modifier = Modifier.weight(1.2f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "تاريخ الانضمام",
                                    fontSize = 11.sp,
                                    color = TextGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = profileStats.studentJoinDate,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryDarkGreen,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Box(modifier = Modifier.width(1.dp).height(30.dp).background(SurfaceContainer))
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val labelText = if (isPerSessionPrivate) "الحصص المتبقية" else "جلسات فائتة"
                                val valueText = if (isPerSessionPrivate) "${student?.sessionsRemaining ?: 0}" else "${profileStats.missedSessionsBeforeJoin}"
                                val valueColor = if (isPerSessionPrivate) PrimaryGreen else WarningOrange

                                Text(
                                    text = labelText,
                                    fontSize = 11.sp,
                                    color = TextGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = valueText,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = valueColor,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Call parent and whatsapp actions
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                modifier = Modifier.weight(1f).height(44.dp),
                                onClick = { student?.let { launchDialer(context, it.parentPhone) } },
                                colors = ButtonDefaults.buttonColors(containerColor = SoftBgGreen),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = PrimaryDarkGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("اتصال بولي الأمر", color = PrimaryDarkGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                modifier = Modifier.weight(1f).height(44.dp),
                                onClick = {
                                    student?.let {
                                        val message = "السلام عليكم ورحمة الله وبركاته، بخصوص الطالب ${it.name} مقيد في ${profileStats.groupName}"
                                        launchWhatsApp(context, it.parentPhone, message)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SoftBgGreen),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("واتساب", color = PrimaryDarkGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Private Notes Section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ملاحظات المعلم الخاصة", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)
                            IconButton(onClick = { showNotesDialog = true }) {
                                Icon(Icons.Default.EditNote, contentDescription = "Edit Notes", tint = PrimaryGreen, modifier = Modifier.size(24.dp))
                            }
                        }
                        Text(
                            text = if (student?.notes?.isNotBlank() == true) student!!.notes else "لا توجد ملاحظات تفصيلية حالية. اضغط على زر تحرير لإضافة تحصيل الطالب وأدائه.",
                            fontSize = 13.sp,
                            color = if (student?.notes?.isNotBlank() == true) TextDark else TextGray,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Quick Actions Block
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الإجراءات والتحميلات الذكية", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen, fontSize = 14.sp)
                    
                    // Row 1: Register Payment & Add Exam Score
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f).height(44.dp),
                            onClick = { showPaymentDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                            shape = RoundedCornerShape(10.dp),
                            enabled = student?.status == "active" && student.isDropped == false
                        ) {
                            Text("تسجيل دفع", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            modifier = Modifier.weight(1f).height(44.dp),
                            onClick = { showExamDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                            shape = RoundedCornerShape(10.dp),
                            enabled = student?.status == "active" && student.isDropped == false
                        ) {
                            Text("إضافة درجة اختبار", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Dedicated PDF Section
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F8)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "تقارير الطالب (PDF)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = PrimaryDarkGreen
                            )
                            
                            if (studentEnrollments.size > 1) {
                                Text("اختر بيانات المرحلة/السنة لملف الـ PDF:", fontSize = 12.sp, color = TextGray)
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable { expandedPdfEnrollment = true },
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val selGroup = groups.find { it.id == pdfGroupId }
                                            Text(selGroup?.name ?: "مجموعة غير معروفة", color = PrimaryDarkGreen, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = PrimaryDarkGreen)
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = expandedPdfEnrollment,
                                        onDismissRequest = { expandedPdfEnrollment = false }
                                    ) {
                                        studentEnrollments.forEach { enr ->
                                            val g = groups.find { it.id == enr.groupId }
                                            val year = academicYears.find { it.id == enr.academicYearId }
                                            DropdownMenuItem(
                                                text = { Text("${g?.name ?: "غير معروف"} - ${year?.yearLabel ?: ""}") },
                                                onClick = {
                                                    pdfGroupId = enr.groupId
                                                    expandedPdfEnrollment = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    onClick = {
                                        student?.let { stud ->
                                            exportStudentProfilePdf(
                                                context = context,
                                                student = stud,
                                                group = pdfGroup,
                                                payments = paymentList,
                                                exams = examScores,
                                                attendances = attendances,
                                                sessions = pdfGroupSessions,
                                                viewImmediately = true
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("عرض ملف PDF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    onClick = {
                                        student?.let { stud ->
                                            exportStudentProfilePdf(
                                                context = context,
                                                student = stud,
                                                group = pdfGroup,
                                                payments = paymentList,
                                                exams = examScores,
                                                attendances = attendances,
                                                sessions = pdfGroupSessions,
                                                viewImmediately = false
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("تحميل ملف PDF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Row 2.5: Drop/Freeze Student (Anqataa)
                    if (student != null) {
                        var showDropConfirmPrompt by remember { mutableStateOf(false) }
                        
                        if (showDropConfirmPrompt) {
                            AlertDialog(
                                onDismissRequest = { showDropConfirmPrompt = false },
                                title = { Text("تسجيل انقطاع الطالب", color = DangerRed, fontWeight = FontWeight.Bold) },
                                text = { Text("هل أنت متأكد من تسجيل انقطاع الطالب \"${student.name}\" وتجميد بياناته؟\n\nتنبيه: سيتم حفظ كافة سجلات حضوره ودرجاته ومدفوعاته الحالية في الأرشيف فوراً، ولن يُسمح بإضافة أي بيانات جديدة له.") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            viewModel.dropStudent(student.id) {
                                                showDropConfirmPrompt = false
                                            }
                                        }
                                    ) {
                                        Text("نعم، انقطع الطالب", color = DangerRed, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDropConfirmPrompt = false }) {
                                        Text("إلغاء")
                                    }
                                }
                            )
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            onClick = { 
                                if (!student.isDropped) {
                                    showDropConfirmPrompt = true 
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (student.isDropped) Color(0xFFF3F4F6) else Color(0xFFFFECEB)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (student.isDropped) Icons.Default.Lock else Icons.Default.Block,
                                    contentDescription = null,
                                    tint = if (student.isDropped) Color.Gray else DangerRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (student.isDropped) "⚠️ الطالب منقطع حالياً (البيانات مجمدة)" else "تسجيل انقطاع الطالب (انقطع)",
                                    color = if (student.isDropped) Color.Gray else DangerRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Row 3: Delete Student (spaced safely below)
                    Button(
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        onClick = { showDeletePrompt = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("حذف الطالب", color = DangerRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ATTENDANCE TIMELINE HISTORY
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("سجل حضور وغياب الطالب التراكمي", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen, modifier = Modifier.padding(bottom = 12.dp))

                        val todayDate = remember { com.example.data.DateUtils.formatStandard("yyyy-MM-dd") }
                        val isTodaySessionCreated = groupSessions.any { 
                            val sessionDateCore = if (it.date.contains("T")) {
                                it.date.split("T")[0]
                            } else if (it.date.contains(" ")) {
                                it.date.split(" ")[0]
                            } else {
                                it.date
                            }
                            val normalizedDate = sessionDateCore.replace("-", "/")
                            normalizedDate == todayDate.replace("-", "/")
                        }

                        if (groupSessions.isEmpty() && !isTodaySessionCreated) {
                            Text("لا توجد حصص تعليمية مسجلة حالياً.", fontSize = 12.sp, color = TextGray)
                        } else {
                            if (!isTodaySessionCreated) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${todayDate.replace("-", "/")} — ${group?.name ?: ""}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextGray
                                        )
                                        Text(text = "حصة اليوم", fontSize = 11.sp, color = TextGray)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .background(SurfaceContainer, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("لم تبدأ الحصة بعد", color = TextGray, fontSize = 11.sp)
                                    }
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SurfaceContainer).padding(vertical = 2.dp))
                            }

                            val sortedGroupSessions = groupSessions.sortedWith(
                                compareByDescending<Session> { it.date.replace("-", "/") }
                                    .thenByDescending { it.time }
                            )
                            sortedGroupSessions.forEach { sess ->
                                val attendeeRecord = attendances.find { it.sessionId == sess.id }
                                val presentStatus = attendeeRecord?.isPresent

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${sess.date.replace("-", "/")} — ${group?.name ?: ""}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = PrimaryDarkGreen
                                        )
                                        Text(text = sess.time, fontSize = 11.sp, color = TextGray)
                                    }

                                    // Status Badge
                                    val normSessDate = sess.date.replace("-", "/")
                                    val normJoinDate = (student?.joinDate ?: "").replace("-", "/")
                                    val isTodaySess = normSessDate == todayDate.replace("-", "/")
                                    val isBeforeJoin = !isTodaySess && (normSessDate < normJoinDate)
                                    if (isBeforeJoin) {
                                        Box(
                                            modifier = Modifier
                                                .background(SurfaceContainer, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("مسبق للتسجيل", color = TextGray, fontSize = 11.sp)
                                        }
                                    } else {
                                        if (attendeeRecord == null) {
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "غير مسجل / No Record",
                                                    color = Color(0xFF64748B),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else {
                                            val badgeBg = when (attendeeRecord.status) {
                                                AttendanceStatus.present -> SuccessGreen.copy(alpha = 0.1f)
                                                AttendanceStatus.absent -> DangerRed.copy(alpha = 0.1f)
                                                AttendanceStatus.late -> WarningOrange.copy(alpha = 0.1f)
                                            }
                                            val badgeColor = when (attendeeRecord.status) {
                                                AttendanceStatus.present -> SuccessGreen
                                                AttendanceStatus.absent -> DangerRed
                                                AttendanceStatus.late -> WarningOrange
                                            }
                                            val statusLabel = when (attendeeRecord.status) {
                                                AttendanceStatus.present -> "حاضر / Present"
                                                AttendanceStatus.absent -> "غائب / Absent"
                                                AttendanceStatus.late -> {
                                                    val tm = attendeeRecord.lateArrivalTime
                                                    if (tm != null) "متأخر / Late ($tm)" else "متأخر / Late"
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .background(badgeBg, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = statusLabel,
                                                    color = badgeColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SurfaceContainer).padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            // LATE ATTENDANCE HISTORY SECTION
            item {
                val lateRecords = attendances.filter { it.status == AttendanceStatus.late }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(1.dp),
                    modifier = Modifier.fillMaxWidth().testTag("late_attendance_history_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "سجل التأخر (Late Attendance History)",
                            fontWeight = FontWeight.Bold,
                            color = PrimaryDarkGreen,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (lateRecords.isEmpty()) {
                            Text("لا توجد سجلات تأخر للطالب حالياً.", fontSize = 12.sp, color = TextGray)
                        } else {
                            // Column Headers
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SoftBgGreen, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("التاريخ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryDarkGreen, modifier = Modifier.weight(1f))
                                Text("اليوم", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryDarkGreen, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text("وقت الوصول", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryDarkGreen, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            lateRecords.forEach { record ->
                                val dateToFormat = if (record.attendanceDate.isNotBlank()) record.attendanceDate else record.timestamp
                                val displayDate = com.example.data.DateUtils.formatDateForDisplay(dateToFormat)
                                val displayDay = getArabicDayName(dateToFormat)
                                val arrivalTime = record.lateArrivalTime ?: "00:00 AM"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = displayDate, fontSize = 13.sp, color = TextDark, modifier = Modifier.weight(1f))
                                    Text(text = displayDay, fontSize = 13.sp, color = TextDark, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                    Text(text = arrivalTime, fontSize = 13.sp, color = WarningOrange, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SurfaceContainer))
                            }
                        }
                    }
                }
            }

            // PAYMENTS DETAILED SUMMARY
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("مستحقات وسجلات الدفع", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen, modifier = Modifier.padding(bottom = 12.dp))

                        if (paymentList.isEmpty()) {
                            Text("لا توجد فواتير تولدت بعد.", fontSize = 12.sp, color = TextGray)
                        } else {
                            paymentList.forEach { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = "شهر ${p.month}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryDarkGreen)
                                        if (p.isPaid) {
                                            Text(text = "سدد بتاريخ: ${if (p.paymentDate != null) com.example.data.DateUtils.formatDateWithArabicDay(p.paymentDate) else ""}", fontSize = 11.sp, color = TextGray)
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "${p.amountPaid} ج.م", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (p.isPaid) SuccessGreen.copy(alpha = 0.1f) else DangerRed.copy(alpha = 0.1f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (p.isPaid) "تم سداد" else "غير مسدد",
                                                color = if (p.isPaid) SuccessGreen else DangerRed,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SurfaceContainer).padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            // EXAMS DETAILED GRADES
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("نتائج الاختبارات والتحصيل ومعدل الدرجات", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen, modifier = Modifier.padding(bottom = 12.dp))

                        if (examScores.isEmpty()) {
                            Text("لا تتوفر نتائج اختبارات مسجلة للطالب.", fontSize = 12.sp, color = TextGray)
                        } else {
                            val avgScoreStr = String.format(Locale.ENGLISH, "%.1f", examScores.map { it.score }.average())
                            val maxExam = examScores.maxBy { it.score }
                            val minExam = examScores.minBy { it.score }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SoftBgGreen, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("المعدل: $avgScoreStr", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryDarkGreen)
                                Text("الأعلى: ${maxExam.score}/${maxExam.maxScore}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryGreen)
                                Text("الأدنى: ${minExam.score}/${minExam.maxScore}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DangerRed)
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            examScores.forEach { exam ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = exam.examName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryDarkGreen)
                                        Text(text = "حرر بتاريخ: ${exam.date}", fontSize = 11.sp, color = TextGray)
                                    }

                                    // Progress and Pass/Fail indicators
                                    val rating = exam.score / exam.maxScore
                                    val pct = rating * 100.0
                                    val isPass = pct >= 50.0
                                    val statusText = if (isPass) "ناجح" else "راسب"
                                    val statusColor = if (isPass) SuccessGreen else DangerRed

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${exam.score} / ${exam.maxScore} (${String.format(Locale.ENGLISH, "%.0f", pct)}%)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = statusColor
                                        )
                                        Text(text = statusText, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SurfaceContainer).padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }

    // --- DIALOG: PAYMENTS ---
    if (showPaymentDialog) {
        Dialog(onDismissRequest = { showPaymentDialog = false }) {
            var selectedMonth by remember { mutableStateOf(viewModel.getCurrentMonthYearArabic()) }
            var amountString by remember { mutableStateOf("200") }
            var isPaid by remember { mutableStateOf(true) }
            var monthDropdownExpanded by remember { mutableStateOf(false) }

            val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
            val arabicMonthsList = remember {
                listOf(
                    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
                )
            }
            val monthsSelectionList = remember(currentYear) {
                val list = mutableListOf<String>()
                for (y in (currentYear - 1)..(currentYear + 1)) {
                    for (m in arabicMonthsList) {
                        list.add("$m $y")
                    }
                }
                list
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("سحب وتسجيل مستحق دفعة مالية", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen, fontSize = 15.sp)

                    Box(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedMonth,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("الشهر المالي") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = PrimaryGreen) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PrimaryDarkGreen,
                                unfocusedTextColor = PrimaryDarkGreen,
                                focusedBorderColor = PrimaryGreen,
                                unfocusedBorderColor = PrimaryGreen,
                                focusedLabelColor = PrimaryDarkGreen,
                                unfocusedLabelColor = PrimaryDarkGreen,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { monthDropdownExpanded = true }
                        )
                        DropdownMenu(
                            expanded = monthDropdownExpanded,
                            onDismissRequest = { monthDropdownExpanded = false },
                            modifier = Modifier.heightIn(max = 240.dp)
                        ) {
                            monthsSelectionList.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        selectedMonth = m
                                        monthDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = amountString,
                        onValueChange = { amountString = it },
                        label = { Text("القيمة المالية المستلمة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isPaid, onCheckedChange = { isPaid = it })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تم استلام القيمة كلياً ودفع الفاتورة", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { showPaymentDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = SoftBgGreen)
                        ) {
                            Text("إلغاء", color = PrimaryDarkGreen)
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                viewModel.addPayment(studentId, selectedMonth, amountString.toDoubleOrNull() ?: 200.0, isPaid)
                                Toast.makeText(context, "تم حفظ الدفعات بنجاح", Toast.LENGTH_SHORT).show()
                                showPaymentDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                        ) {
                            Text("تأكيد الدفع", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG: ADD EXAM ---
    if (showExamDialog) {
        Dialog(onDismissRequest = { showExamDialog = false }) {
            var name by remember { mutableStateOf("اختبار يونيو") }
            var scoreStr by remember { mutableStateOf("18") }
            var maxStr by remember { mutableStateOf("20") }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("تسجيل درجة اختبار جديدة", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen, fontSize = 15.sp)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("اسم أو عنوان الاختبار") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = scoreStr,
                        onValueChange = { scoreStr = it },
                        label = { Text("الدرجة المحققة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = maxStr,
                        onValueChange = { maxStr = it },
                        label = { Text("الدرجة العظمى للاختبار") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { showExamDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = SoftBgGreen)
                        ) {
                            Text("إلغاء", color = PrimaryDarkGreen)
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val s = scoreStr.toDoubleOrNull() ?: 0.0
                                val m = maxStr.toDoubleOrNull() ?: 20.0
                                val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
                                viewModel.addExamScore(studentId, name, s, m, todayDate)
                                Toast.makeText(context, "تم حفظ الدرجة بنجاح", Toast.LENGTH_SHORT).show()
                                showExamDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                        ) {
                            Text("حفظ", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG: NOTES ---
    if (showNotesDialog) {
        Dialog(onDismissRequest = { showNotesDialog = false }) {
            var updatedNotes by remember { mutableStateOf(student?.notes ?: "") }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("تحديث الملاحظات التراكمية", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen, fontSize = 15.sp)

                    OutlinedTextField(
                        value = updatedNotes,
                        onValueChange = { updatedNotes = it },
                        label = { Text("أكتب ملاحظات عامة حول السلوك والتحصيل") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { showNotesDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = SoftBgGreen)
                        ) {
                            Text("إلغاء", color = PrimaryDarkGreen)
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                student?.let {
                                    val updatedStudent = it.copy(notes = updatedNotes)
                                    viewModel.updateStudent(updatedStudent)
                                }
                                Toast.makeText(context, "تم حفظ التغييرات", Toast.LENGTH_SHORT).show()
                                showNotesDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                        ) {
                            Text("تأكيد وتعديل كلي", color = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showEditStudentDialog && student != null) {
        EditStudentDialog(
            student = student!!,
            onDismiss = { showEditStudentDialog = false },
            onSave = { updatedStudent ->
                viewModel.updateStudent(updatedStudent)
                showEditStudentDialog = false
            },
            isPerSessionPrivate = isPerSessionPrivate
        )
    }

    // --- PROMPT DELETE STUDENT ---
    if (showDeletePrompt) {
        AlertDialog(
            onDismissRequest = { showDeletePrompt = false },
            title = { Text("حذف الطالب") },
            text = { Text("هل أنت متأكد من رغبتك في حذف هذا الطالب؟ سيؤدي هذا إلى مسح كافة سجلات المدفوعات والدرجات المخصصة له.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        student?.let { viewModel.deleteStudent(it) }
                        showDeletePrompt = false
                        onBack()
                    }
                ) {
                    Text("حذف بشكل نهائي", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePrompt = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}


// ==========================================
// 6. PAYMENT SYSTEM SCREEN
// ==========================================
@Composable
fun PaymentsScreen(
    viewModel: TeacherViewModel,
    onNavigateToStudent: (Int, Int) -> Unit,
    onBack: () -> Unit
) {
    val groups by viewModel.groups.collectAsState()
    val students by viewModel.students.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val selectedPeriod by viewModel.selectedBillingPeriod.collectAsState()
    val stats by viewModel.paymentStats.collectAsState()
    val enrollments by viewModel.enrollments.collectAsState()
    val currentYear by viewModel.currentAcademicYear.collectAsState()

    var selectedGroupIdx by remember { mutableIntStateOf(0) } // 0 = All Groups
    var selectedStatus by remember { mutableIntStateOf(0) } // 0 = All, 1 = Paid, 2 = Unpaid

    val displayedGroupsList = listOf(Group(id = 0, name = "جميع المجموعات", startDate = "", monthlyFee = 0.0, scheduleDays = "")) + groups

    // Mapping each student to their database payment record or building a virtual unpaid payment
    val filteredPaymentsList = remember(students, payments, selectedPeriod, selectedGroupIdx, selectedStatus, enrollments, currentYear) {
        val legacyMonthStr = selectedPeriod.toLegacyString()
        val yId = currentYear?.id ?: 1
        val studentGroupMap = enrollments
            .filter { it.academicYearId == yId && it.status == "active" }
            .associate { it.studentId to it.groupId }

        students.map { student ->
            val pay = payments.find { 
                it.studentId == student.id && 
                ((it.monthVal == selectedPeriod.month && it.yearVal == selectedPeriod.year) || it.month == legacyMonthStr)
            }
            val studentGroupId = studentGroupMap[student.id] ?: 0
            pay ?: Payment(
                studentId = student.id, 
                month = legacyMonthStr, 
                isPaid = false, 
                amountPaid = 0.0,
                monthVal = selectedPeriod.month,
                yearVal = selectedPeriod.year,
                groupId = studentGroupId
            )
        }.filter { payment ->
            val studentGroupId = studentGroupMap[payment.studentId] ?: 0
            val matchesGroup = selectedGroupIdx == 0 || studentGroupId == displayedGroupsList[selectedGroupIdx].id
            val matchesStatus = when (selectedStatus) {
                0 -> true
                1 -> payment.isPaid
                else -> !payment.isPaid
            }
            matchesGroup && matchesStatus
        }
    }

    Scaffold(
        topBar = {
            TeacherAppBar(
                title = "إدارة الدفع والاشتراكات",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = PrimaryDarkGreen
                        )
                    }
                },
                onHomeClick = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(LightBgGreen)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Month Navigator Card Component
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { 
                                viewModel.selectedBillingPeriod.value = selectedPeriod.previousMonth()
                            },
                            modifier = Modifier.background(SoftBgGreen, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "الشهر السابق",
                                tint = PrimaryDarkGreen
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "الشهر المالي الحالي",
                                color = TextGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = selectedPeriod.formatArabicMonth(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryDarkGreen
                            )
                        }

                        IconButton(
                            onClick = { 
                                viewModel.selectedBillingPeriod.value = selectedPeriod.nextMonth()
                            },
                            modifier = Modifier.background(SoftBgGreen, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "الشهر التالي",
                                tint = PrimaryDarkGreen
                            )
                        }
                    }

                    val currentPeriod = remember {
                        val cal = DateUtils.getCairoCalendar()
                        BillingPeriod(
                            month = cal.get(Calendar.MONTH) + 1,
                            year = cal.get(Calendar.YEAR)
                        )
                    }

                    if (selectedPeriod != currentPeriod) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = SoftBgGreen.copy(alpha = 0.5f)
                        )
                        TextButton(
                            onClick = {
                                viewModel.selectedBillingPeriod.value = currentPeriod
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = PrimaryDarkGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Today,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "الذهاب للشهر الحالي",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Financial Status Top Card
            Card(
                colors = CardDefaults.cardColors(containerColor = PrimaryDarkGreen),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("التحصيل المالي لشهر ${selectedPeriod.formatArabicMonth()}", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                        Text("${stats.monthlyRevenue} ج.م", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Box(
                        modifier = Modifier
                            .background(WarningOrange.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text("الديون المتبقية: ${stats.totalDebt} ج.م", color = WarningOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Dropdowns Filters
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Simple Group filter row selector
                    var expanded by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                    ) {
                        OutlinedTextField(
                            value = displayedGroupsList.getOrNull(selectedGroupIdx)?.name ?: "اختر الفئة",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("المجموعة") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = PrimaryGreen) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PrimaryDarkGreen,
                                unfocusedTextColor = PrimaryDarkGreen,
                                focusedBorderColor = PrimaryGreen,
                                unfocusedBorderColor = PrimaryGreen,
                                focusedLabelColor = PrimaryDarkGreen,
                                unfocusedLabelColor = PrimaryDarkGreen,
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                        // Clickable overlay on top to ensure clicks are caught properly
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expanded = true }
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            displayedGroupsList.forEachIndexed { index, group ->
                                DropdownMenuItem(
                                    text = { Text(group.name) },
                                    onClick = {
                                        selectedGroupIdx = index
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Payment Status Filter buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val states = listOf("الكل", "المسددين", "غير مسددين")
                    states.forEachIndexed { idx, label ->
                        Button(
                            onClick = { selectedStatus = idx },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedStatus == idx) PrimaryDarkGreen else SoftBgGreen
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                color = if (selectedStatus == idx) Color.White else PrimaryDarkGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Results List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (filteredPaymentsList.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text("لا توجد فواتير مطابقة لخيارات التصفية الحالية.", color = TextGray)
                        }
                    }
                } else {
                    items(filteredPaymentsList) { payment ->
                        val stud = students.find { it.id == payment.studentId }
                        val studGroupId = payment.groupId
                        val grp = groups.find { it.id == studGroupId }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { stud?.let { onNavigateToStudent(it.id, studGroupId) } },
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stud?.name ?: "طالب غير معروف",
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryDarkGreen,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "المجموعة: ${grp?.name ?: "غير معروف"}",
                                        fontSize = 11.sp,
                                        color = TextGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "رقم الهاتف: ${stud?.parentPhone ?: "غير معروف"}",
                                        fontSize = 11.sp,
                                        color = TextGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "المستحق: ${grp?.monthlyFee ?: 200.0} ج.م",
                                        fontSize = 11.sp,
                                        color = TextGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
                                        Text(text = "مسدد:", fontSize = 10.sp, color = TextGray)
                                        Text(text = "${payment.amountPaid} ج.م", fontWeight = FontWeight.Bold, color = PrimaryGreen, fontSize = 14.sp)
                                    }

                                    // Status Badge click to fast toggle payment
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (payment.isPaid) SuccessGreen.copy(alpha = 0.1f) else DangerRed.copy(alpha = 0.1f),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .clickable {
                                                val feeAmount = grp?.monthlyFee ?: 200.0
                                                val targetState = !payment.isPaid
                                                viewModel.addPayment(
                                                    studentId = payment.studentId,
                                                    month = payment.month,
                                                    amount = feeAmount,
                                                    isPaid = targetState
                                                )
                                            }
                                    ) {
                                        Text(
                                            text = if (payment.isPaid) "تم سداد" else "اضغط للتسديد",
                                            color = if (payment.isPaid) SuccessGreen else DangerRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(84.dp)) }
            }
        }
    }
}


// ==========================================
// 7. BACKUP, RESTORE & EXCEL REPORTS SCREEN
// ==========================================
@Composable
fun ReportsBackupScreen(
    viewModel: TeacherViewModel
) {
    val context = LocalContext.current
    val groups by viewModel.groups.collectAsState()
    val students by viewModel.students.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val exams by viewModel.exams.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val deletedStudents by viewModel.deletedStudents.collectAsState()
    val enrollments by viewModel.enrollments.collectAsState()

    var backupTextState by remember { mutableStateOf("") }
    var restoreTextState by remember { mutableStateOf("") }
    var showCsvSharingSuccess by remember { mutableStateOf(false) }
    var showStartNewYearDialog by remember { mutableStateOf(false) }

    if (showStartNewYearDialog) {
        StartNewAcademicYearDialog(
            viewModel = viewModel,
            onDismissRequest = { showStartNewYearDialog = false }
        )
    }

    // Backup serializer logic
    fun generateOfflineBackupJson(): String {
        return try {
            val root = JSONObject()
            val studentGroupMap = enrollments.associate { it.studentId to it.groupId }

            // 1. Groups to Json
            val grpsArray = JSONArray()
            groups.forEach { g ->
                val o = JSONObject()
                o.put("id", g.id)
                o.put("name", g.name)
                o.put("startDate", g.startDate)
                o.put("monthlyFee", g.monthlyFee)
                o.put("scheduleDays", g.scheduleDays)
                grpsArray.put(o)
            }
            root.put("groups", grpsArray)

            // 2. Students
            val studsArray = JSONArray()
            students.forEach { s ->
                val o = JSONObject()
                o.put("id", s.id)
                o.put("groupId", studentGroupMap[s.id] ?: 0)
                o.put("name", s.name)
                o.put("parentPhone", s.parentPhone)
                o.put("joinDate", s.joinDate)
                o.put("notes", s.notes)
                studsArray.put(o)
            }
            root.put("students", studsArray)

            // 3. Sessions
            val sessArray = JSONArray()
            sessions.forEach { s ->
                val o = JSONObject()
                o.put("id", s.id)
                o.put("groupId", s.groupId)
                o.put("date", s.date)
                o.put("time", s.time)
                sessArray.put(o)
            }
            root.put("sessions", sessArray)

            // 4. Payments
            val paymentsArray = JSONArray()
            payments.forEach { p ->
                val o = JSONObject()
                o.put("id", p.id)
                o.put("studentId", p.studentId)
                o.put("month", p.month)
                o.put("isPaid", p.isPaid)
                o.put("amountPaid", p.amountPaid)
                o.put("paymentDate", p.paymentDate ?: "")
                paymentsArray.put(o)
            }
            root.put("payments", paymentsArray)

            // 5. Exams
            val examsArray = JSONArray()
            exams.forEach { e ->
                val o = JSONObject()
                o.put("id", e.id)
                o.put("studentId", e.studentId)
                o.put("examName", e.examName)
                o.put("score", e.score)
                o.put("maxScore", e.maxScore)
                o.put("date", e.date)
                examsArray.put(o)
            }
            root.put("exams", examsArray)

            root.toString(2) // Intented JSON
        } catch (e: Exception) {
            "فشل توليد النسخة الاحتياطية"
        }
    }

    // Restore deserializer logic
    fun restoreOfflineBackupJson(jsonString: String) {
        try {
            val root = JSONObject(jsonString)

            // 1. Rebuild Groups
            val grpsArray = root.getJSONArray("groups")
            for (i in 0 until grpsArray.length()) {
                val o = grpsArray.getJSONObject(i)
                viewModel.addGroup(
                    name = o.getString("name"),
                    startDate = o.getString("startDate"),
                    fee = o.getDouble("monthlyFee"),
                    schedule = o.getString("scheduleDays")
                )
            }

            // 2. Rebuild Students
            val studsArray = root.getJSONArray("students")
            for (i in 0 until studsArray.length()) {
                val o = studsArray.getJSONObject(i)
                viewModel.addStudent(
                    groupId = o.getInt("groupId"),
                    name = o.getString("name"),
                    parentPhone = o.getString("parentPhone"),
                    joinDate = o.getString("joinDate"),
                    notes = o.optString("notes", "")
                )
            }

            Toast.makeText(context, "تم استرجاع البيانات بنجاح كلياً", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "خطأ في بنية كود التهيئة الاحتياطية", Toast.LENGTH_LONG).show()
        }
    }

    // CSV generator sharing dispatcher
    fun exportStudentsCsv() {
        try {
            val cachePath = File(context.cacheDir, "reports")
            cachePath.mkdirs()
            val csvFile = File(cachePath, "teacher_students_report.csv")
            val outputStream = FileOutputStream(csvFile)

            // Write CSV headers (bomba excel compatible Arabic values)
            val header = "معرف الطالب,اسم الطالب,رقم هاتف ولي الأمر,الفصل الدراسي,تاريخ التسجيل,ملاحظات عامة\n"
            outputStream.write(header.toByteArray(Charsets.UTF_8))

            val studentGroupMap = enrollments.associate { it.studentId to it.groupId }

            students.forEach { s ->
                val sGrpId = studentGroupMap[s.id] ?: 0
                val grp = groups.find { it.id == sGrpId }
                val row = "${s.id},${s.name},${s.parentPhone},${grp?.name ?: "فصل غير معروف"},${s.joinDate},${s.notes}\n"
                outputStream.write(row.toByteArray(Charsets.UTF_8))
            }
            outputStream.close()

            // Sharing dispatch sheet
            val cleanUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "com.aistudio.teachermanager.qyhwpx.fileprovider",
                csvFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "تقرير وإيرادات الطلاب Excel")
                putExtra(Intent.EXTRA_STREAM, cleanUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "تصدير ورقة بيانات إكسل"))
            showCsvSharingSuccess = true
        } catch (e: Exception) {
            Toast.makeText(context, "خطأ في تصدير البيانات: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBgGreen)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "تقارير وقاعدة البيانات الاحتياطية",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryDarkGreen
            )
            Text(
                text = "إدارة البيانات وتوليد التقارير الورقية والإلكترونية أوفلاين بالكامل",
                fontSize = 12.sp,
                color = TextGray
            )
        }

        // --- SECTION EXPORTER SHARING CHEETS ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تصدير ورقة بيانات Excel للطلاب", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)
                    }
                    Text(
                        text = "يقوم النظام بتجميع كافة تفاصيل الطلاب الحاليين، وتوليد ورقة بيانات CSV كاملة مشفرة لفتحها ومراجعتها ببرنامج Microsoft Excel بسلاسة.",
                        fontSize = 12.sp,
                        color = TextGray,
                        lineHeight = 16.sp
                    )
                    Button(
                        onClick = { exportStudentsCsv() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text("مشاركة وتصدير ملف Excel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- START NEW ACADEMIC YEAR SECTION ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.School, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("بداية سنة دراسية جديدة (ترحيل الطلاب)", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)
                    }
                    Text(
                        text = "يتيح لك هذا القسم إعداد العام الدراسي الجديد وترحيل الطلاب تلقائياً أو فردياً بين المراحل والصفوف (مثلاً ترحيل طلاب رابع لخامس، وتخريج طلاب سادس)، مع الحفاظ الكامل على كافة الأرشيف المالي وحضور الأعوام السابقة.",
                        fontSize = 12.sp,
                        color = TextGray,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = { showStartNewYearDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryDarkGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text("إعداد الموسم الجديد وترحيل الطلاب", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- BACKUP RESTORE CARD SECTION ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("توليد نسخة احتياطية ( JSON )", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)
                    }
                    Text(
                        text = "اضغط على زر التوليد لتفويض نسخة نصية كاملة ومبسطة لقاعدة البيانات والطلاب تتيح لك نقلها أو الاحتفاظ وتخزينها بأمان تام.",
                        fontSize = 12.sp,
                        color = TextGray,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = { backupTextState = generateOfflineBackupJson() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryDarkGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تفويض وتوليد كود الحفظ", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (backupTextState.isNotBlank()) {
                        OutlinedTextField(
                            value = backupTextState,
                            onValueChange = { backupTextState = it },
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            readOnly = true,
                            shape = RoundedCornerShape(10.dp),
                            label = { Text("انسخ كود الحفظ الاحتياطي الناتج:") }
                        )

                        // Copy button helper
                        Button(
                            onClick = {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clipData = android.content.ClipData.newPlainText("Teacher Manager Backup", backupTextState)
                                clipboardManager.setPrimaryClip(clipData)
                                Toast.makeText(context, "تم نسخ كود الاحتفاظ بالذاكرة!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("نسخ كود الحفظ الاحتياطي للاستخدام لاحقاً", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // --- RESTORE BACKUP SYSTEM ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, tint = DangerRed, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("استجلاب واستعادة البيانات", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)
                    }
                    Text(
                        text = "الصق الكود الاحتياطي ( JSON ) المطابق بالأسفل واضغط استجابة لاسترجاع كافة الأرقام والمجموعات في لحظة.",
                        fontSize = 12.sp,
                        color = TextGray
                    )

                    OutlinedTextField(
                        value = restoreTextState,
                        onValueChange = { restoreTextState = it },
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        placeholder = { Text("الصق كود الحفظ هنا...") },
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            if (restoreTextState.isNotBlank()) {
                                restoreOfflineBackupJson(restoreTextState)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("تأكيد استدعاء واستعادة البيانات", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // --- HARD RESET / CLEAN ALL DATA ---
        item {
            var showConfirmResetDialog by remember { mutableStateOf(false) }

            if (showConfirmResetDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showConfirmResetDialog = false },
                    title = { Text("تحذير: حذف كافة البيانات", color = DangerRed, fontWeight = FontWeight.Bold) },
                    text = { Text("هل أنت متأكد تماماً من رغبتك في مسح كافة الفصول، الطلاب، الحصص، عمليات الدفع، والامتحانات المسجلة؟ لا يمكن التراجع عن هذا الإجراء أبداً.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.clearAllDatabaseData {
                                    Toast.makeText(context, "تم مسح كافة البيانات بنجاح، التطبيق جاهز للاستخدام الفعلي الآن.", Toast.LENGTH_LONG).show()
                                }
                                showConfirmResetDialog = false
                            }
                        ) {
                            Text("نعم، احذف كل شيء", color = DangerRed, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmResetDialog = false }) {
                            Text("إلغاء", color = Color.Gray)
                        }
                    }
                )
            }

            // --- DELETED STUDENTS / ADVANCED OPTIONS (TRASH BIN) ---
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("الطلاب المحذوفون (سلة المهملات)", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)
                    }
                    Text(
                        text = "الطلاب الموجودون هنا تم إلغاء تفعيلهم (حذف مؤقت) لمنع فقد المعاملات المالية والسجلات المرتبطة بهم. يمكنك استعادتهم أو حذف بياناتهم نهائياً.",
                        fontSize = 12.sp,
                        color = TextGray,
                        lineHeight = 16.sp
                    )

                    if (deletedStudents.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("سلة المهملات فارغة حالياً.", color = TextGray, fontSize = 12.sp)
                        }
                    } else {
                        deletedStudents.forEach { student ->
                            val studentEnrollmentGroupId = viewModel.enrollments.value.find { it.studentId == student.id }?.groupId ?: 0
                            val groupName = groups.find { it.id == studentEnrollmentGroupId }?.name ?: "فصل غير معروف"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF7F9FA), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(student.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                                    Text("المجموعة: $groupName", fontSize = 11.sp, color = TextGray)
                                    student.deletedAt?.let {
                                        Text("تاريخ الحذف: ${DateUtils.formatDateForDisplay(it.split(" ")[0])}", fontSize = 10.sp, color = TextGray)
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Restore Button
                                    Button(
                                        onClick = {
                                            viewModel.restoreStudent(student)
                                            Toast.makeText(context, "تم استعادة الطالب بنجاح!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("استعادة", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Hard Delete Button
                                    Button(
                                        onClick = {
                                            viewModel.deleteStudentPermanently(student)
                                            Toast.makeText(context, "تم حذف الطالب وسجلاته نهائياً!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("حذف نهائي", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- PIN SECURITY CARD SECTION ---
            val pinStorage = remember { PinStorage(context) }
            var pinEnabled by remember { mutableStateOf(pinStorage.isPinEnabled()) }
            var showPinDialog by remember { mutableStateOf(false) }

            if (showPinDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showPinDialog = false },
                    title = { Text("تفعيل قفل PIN", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen) },
                    text = { Text("سيطلب التطبيق إنشاء رقم PIN جديد (4 أرقام) للحماية عند الخروج أو إعادة فتح التطبيق.", color = TextGray) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pinStorage.setPinEnabled(true)
                                pinStorage.setAuthenticated(false)
                                pinStorage.clearPin() // Force setup on next launch
                                pinEnabled = true
                                showPinDialog = false
                            }
                        ) {
                            Text("تفعيل وتعيين", color = PrimaryGreen, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPinDialog = false }) {
                            Text("إلغاء", color = Color.Gray)
                        }
                    }
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("قفل التطبيق برقم PIN حماية", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)
                        }
                        Switch(
                            checked = pinEnabled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = PrimaryGreen,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.LightGray
                            ),
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showPinDialog = true
                                } else {
                                    pinStorage.setPinEnabled(false)
                                    pinStorage.clearPin()
                                    pinEnabled = false
                                }
                            }
                        )
                    }

                    Text(
                        text = "يقوم هذا الخيار بحماية خصوصية بيانات طلابك ونتائجهم برقم سري PIN مكون من 4 أرقام عند قفل أو فتح التطبيق.",
                        fontSize = 12.sp,
                        color = TextGray,
                        lineHeight = 16.sp
                    )

                    if (pinEnabled && pinStorage.hasPin()) {
                        Button(
                            onClick = {
                                pinStorage.clearPin()
                                pinStorage.setAuthenticated(false)
                                Toast.makeText(context, "يرجى تعيين الكود الجديد الآن", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تغيير رقم PIN الحالي", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = DangerRed, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تهيئة ومسح كافة البيانات (تنظيف كامل)", fontWeight = FontWeight.Bold, color = DangerRed)
                    }
                    Text(
                        text = "إذا كنت ترغب في حذف كافة الفصول التجريبية أو البدء بقاعدة بيانات فارغة تماماً ومجهزة للعمل المباشر، يمكنك إجراء تهيئة كاملة بضغطة واحدة.",
                        fontSize = 12.sp,
                        color = TextGray,
                        lineHeight = 16.sp
                    )

                    Button(
                        onClick = { showConfirmResetDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("مسح وحذف قاعدة البيانات بالكامل", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(84.dp)) }
    }
}


@Composable
fun StartNewAcademicYearDialog(
    viewModel: TeacherViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val groups by viewModel.groups.collectAsState()
    val students by viewModel.students.collectAsState()
    val enrollments by viewModel.enrollments.collectAsState()
    val currentYear by viewModel.currentAcademicYear.collectAsState()

    var currentStep by remember { mutableStateOf(1) }

    // --- STEP 1: New Year Metadata ---
    val currentYearLabel = currentYear?.yearLabel ?: "2025/2026"
    val suggestedLabel = remember(currentYearLabel) {
        try {
            val parts = currentYearLabel.split("/")
            if (parts.size == 2) {
                val y1 = parts[0].toIntOrNull()
                val y2 = parts[1].toIntOrNull()
                if (y1 != null && y2 != null) {
                    "${y1 + 1}/${y2 + 1}"
                } else "2026/2027"
            } else "2026/2027"
        } catch (e: Exception) {
            "2026/2027"
        }
    }
    val suggestedStartDate = remember(currentYear) {
        try {
            val currentEnd = currentYear?.endDate ?: "2026-06-30"
            val parts = currentEnd.split("-")
            if (parts.size == 3) {
                val nextYear = parts[0].toInt() + 1
                "$nextYear-09-01"
            } else "2026-09-01"
        } catch (e: Exception) {
            "2026-09-01"
        }
    }
    val suggestedEndDate = remember(currentYear) {
        try {
            val currentEnd = currentYear?.endDate ?: "2026-06-30"
            val parts = currentEnd.split("-")
            if (parts.size == 3) {
                val nextYear = parts[0].toInt() + 1
                "$nextYear-06-30"
            } else "2027-06-30"
        } catch (e: Exception) {
            "2027-06-30"
        }
    }

    var newYearLabel by remember { mutableStateOf(suggestedLabel) }
    var newYearStartDate by remember { mutableStateOf(suggestedStartDate) }
    var newYearEndDate by remember { mutableStateOf(suggestedEndDate) }

    LaunchedEffect(newYearStartDate, newYearEndDate) {
        try {
            val startParts = newYearStartDate.split("-")
            val endParts = newYearEndDate.split("-")
            if (startParts.size == 3 && endParts.size == 3) {
                newYearLabel = "${startParts[0]}/${endParts[0]}"
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    fun showDatePicker(currentDateStr: String, onDateSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        try {
            val parts = currentDateStr.split("-")
            if (parts.size == 3) {
                val year = parts[0].toInt()
                val month = parts[1].toInt() - 1 // 0-based
                val day = parts[2].toInt()
                calendar.set(year, month, day)
            }
        } catch (e: Exception) {
            // fallback
        }
        android.app.DatePickerDialog(
            context,
            { _, year, monthOfYear, dayOfMonth ->
                val formattedDate = String.format(Locale.US, "%04d-%02d-%02d", year, monthOfYear + 1, dayOfMonth)
                onDateSelected(formattedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // --- STEP 2: Group & Student Mappings ---
    val activeGroups = remember(groups, enrollments, currentYear) {
        val yId = currentYear?.id ?: 1
        val enrolledGroupIds = enrollments.filter { it.academicYearId == yId && it.status == "active" }.map { it.groupId }.toSet()
        groups.filter { it.id in enrolledGroupIds }
    }

    // Default target group mapping (Heuristic based or manually changed)
    val groupTargetMap = remember { mutableStateMapOf<Int, Int>() }
    
    // Group expansion state
    val expandedGroups = remember { mutableStateMapOf<Int, Boolean>() }

    // Student promotion status
    val studentPromotionChoices = remember { mutableStateMapOf<Int, String>() }
    val studentTargetGroups = remember { mutableStateMapOf<Int, Int>() }

    // Set initial configuration
    LaunchedEffect(activeGroups) {
        activeGroups.forEach { g ->
            if (groupTargetMap[g.id] == null) {
                // Determine heuristic target
                val candidates = groups.filter { it.id != g.id }
                var choice = 0 // Default to Graduate (0)
                if (g.name.contains("رابع") || g.name.contains("الرابع")) {
                    candidates.find { it.name.contains("خامس") || it.name.contains("الخامس") }?.let { choice = it.id }
                } else if (g.name.contains("خامس") || g.name.contains("الخامس")) {
                    candidates.find { it.name.contains("سادس") || it.name.contains("السادس") }?.let { choice = it.id }
                } else if (g.name.contains("أول") || g.name.contains("الأول")) {
                    candidates.find { it.name.contains("ثاني") || it.name.contains("الثاني") }?.let { choice = it.id }
                } else if (g.name.contains("ثاني") || g.name.contains("الثاني")) {
                    candidates.find { it.name.contains("ثالث") || it.name.contains("الثالث") }?.let { choice = it.id }
                } else if (g.name.contains("ثالث") || g.name.contains("الثالث")) {
                    candidates.find { it.name.contains("رابع") || it.name.contains("الرابع") }?.let { choice = it.id }
                }
                groupTargetMap[g.id] = choice
            }
        }
    }

    // Key: GroupID -> List of Students
    val studentsInGroup = remember(students, enrollments, currentYear) {
        val yId = currentYear?.id ?: 1
        activeGroups.associate { g ->
            val enrolledStudentIds = enrollments
                .filter { it.groupId == g.id && it.academicYearId == yId && it.status == "active" }
                .map { it.studentId }
            g.id to students.filter { it.id in enrolledStudentIds && it.isActive && it.deletedAt == null }
        }
    }

    // Populate default student choices
    LaunchedEffect(studentsInGroup, groupTargetMap.toMap()) {
        studentsInGroup.forEach { (groupId, studs) ->
            val defaultTarget = groupTargetMap[groupId] ?: 0
            studs.forEach { s ->
                if (studentPromotionChoices[s.id] == null) {
                    studentPromotionChoices[s.id] = "promote" // Default is promote
                }
                if (studentTargetGroups[s.id] == null) {
                    studentTargetGroups[s.id] = defaultTarget
                }
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { onDismissRequest() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header of Dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onDismissRequest() }) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                    Text(
                        text = "بدء الموسم الدراسي الجديد",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDarkGreen
                    )
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = PrimaryDarkGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Step indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (step in 1..3) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .background(
                                    if (step <= currentStep) PrimaryGreen else Color.LightGray.copy(alpha = 0.5f),
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }

                // Main Content depending on step
                Box(modifier = Modifier.weight(1f)) {
                    when (currentStep) {
                        1 -> {
                            // Step 1: Years metadata
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "الخطوة الأولى: تحديد بيانات العام الدراسي الجديد",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Text(
                                    text = "الرجاء تأكيد تسمية العام الجديد وتواريخ بدايته ونهايته. سيقوم هذا الإجراء بأرشفة العام الحالي (${currentYearLabel}) وجعله غير نشط.",
                                    fontSize = 12.sp,
                                    color = TextGray,
                                    lineHeight = 18.sp
                                )

                                OutlinedTextField(
                                    value = newYearLabel,
                                    onValueChange = { newYearLabel = it },
                                    label = { Text("مسمى العام الدراسي الجديد") },
                                    placeholder = { Text("مثال: 2026/2027") },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = newYearStartDate,
                                        onValueChange = { },
                                        label = { Text("تاريخ بداية العام الجديد (YYYY-MM-DD)") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        readOnly = true,
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = "اختر التاريخ",
                                                tint = PrimaryGreen
                                            )
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = TextDark,
                                            unfocusedTextColor = TextDark,
                                            focusedBorderColor = PrimaryGreen,
                                            unfocusedBorderColor = PrimaryGreen.copy(alpha = 0.5f),
                                            focusedLabelColor = PrimaryGreen,
                                            unfocusedLabelColor = TextGray
                                        ),
                                        singleLine = true
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clickable {
                                                showDatePicker(newYearStartDate) { selectedDate ->
                                                    newYearStartDate = selectedDate
                                                }
                                            }
                                    )
                                }

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = newYearEndDate,
                                        onValueChange = { },
                                        label = { Text("تاريخ نهاية العام الجديد (YYYY-MM-DD)") },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        readOnly = true,
                                        trailingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = "اختر التاريخ",
                                                tint = PrimaryGreen
                                            )
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = TextDark,
                                            unfocusedTextColor = TextDark,
                                            focusedBorderColor = PrimaryGreen,
                                            unfocusedBorderColor = PrimaryGreen.copy(alpha = 0.5f),
                                            focusedLabelColor = PrimaryGreen,
                                            unfocusedLabelColor = TextGray
                                        ),
                                        singleLine = true
                                    )
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .clickable {
                                                showDatePicker(newYearEndDate) { selectedDate ->
                                                    newYearEndDate = selectedDate
                                                 }
                                             }
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SoftBgGreen),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryGreen)
                                        Text(
                                            text = "سيتم نقل الطلاب المختارين تلقائياً إلى العام الدراسي الجديد",
                                            fontSize = 11.sp,
                                            color = PrimaryDarkGreen,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            // Step 2: Mappings
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "الخطوة الثانية: تحديد صفوف الوجهة والطلاب",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "اختر الوجهة التلقائية لكل مجموعة، وحدد نوع الإجراء (ترحيل أو تخرج).",
                                    fontSize = 11.sp,
                                    color = TextGray,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                if (activeGroups.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("لا يوجد مجموعات نشطة حالياً في هذا العام الدراسي ترحيل طلابها.", color = TextGray)
                                    }
                                } else {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        items(activeGroups) { group ->
                                            var showGroupDropdown by remember { mutableStateOf(false) }
                                            val currentTargetId = groupTargetMap[group.id] ?: 0
                                            val groupStudentsList = studentsInGroup[group.id] ?: emptyList()
                                            val isExpanded = expandedGroups[group.id] ?: false

                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                                                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    // Group Header
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Icon(Icons.Default.School, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                                                            Text(
                                                                text = group.name,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 14.sp,
                                                                color = Color.Black
                                                            )
                                                            Text(
                                                                text = "(${groupStudentsList.size} طالب)",
                                                                fontSize = 12.sp,
                                                                color = TextGray
                                                            )
                                                        }
                                                        
                                                        // Remove expansion button since we don't show individual students anymore
                                                    }
                                                    
                                                    // State for the group's chosen action. We take the choice from the first student in the group.
                                                    val groupFirstStudent = groupStudentsList.firstOrNull()
                                                    val groupChoice = if (groupFirstStudent != null) studentPromotionChoices[groupFirstStudent.id] ?: "promote" else "promote"
                                                    val groupTargetGroup = if (groupFirstStudent != null) studentTargetGroups[groupFirstStudent.id] else null

                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    
                                                    // Select action
                                                    Row(
                                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        androidx.compose.material3.FilterChip(
                                                            selected = groupChoice == "promote",
                                                            onClick = { 
                                                                groupStudentsList.forEach { s -> studentPromotionChoices[s.id] = "promote" }
                                                            },
                                                            label = { Text("ترحيل المجموعة ➡️", fontSize = 11.sp) },
                                                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                                                selectedContainerColor = Color(0xFFE3F2FD)
                                                            )
                                                        )
                                                        androidx.compose.material3.FilterChip(
                                                            selected = groupChoice == "graduated",
                                                            onClick = { 
                                                                groupStudentsList.forEach { s -> studentPromotionChoices[s.id] = "graduated" }
                                                            },
                                                            label = { Text("تخريج المجموعة 🎓", fontSize = 11.sp) },
                                                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                                                selectedContainerColor = Color(0xFFE8F5E9)
                                                            )
                                                        )
                                                    }

                                                    if (groupChoice == "promote") {
                                                        TargetGroupDropdown(
                                                            groups = groups.filter { it.id != group.id },
                                                            selectedGroupId = groupTargetGroup,
                                                            onGroupSelected = { newGroupId ->
                                                                groupStudentsList.forEach { s -> studentTargetGroups[s.id] = newGroupId }
                                                            }
                                                        )
                                                    }

                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                            // Step 3: Confirmation Summary
                            val summaryPromote = studentPromotionChoices.values.count { it == "promote" }
                            val summaryGraduated = studentPromotionChoices.values.count { it == "graduated" }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "الخطوة الثالثة: مراجعة ملخص الترحيل والتأكيد",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Text(
                                    text = "الرجاء مراجعة الإجراءات الإجمالية بالأسفل جيداً قبل التنفيذ الفعلي للسنة الدراسية الجديدة.",
                                    fontSize = 12.sp,
                                    color = TextGray
                                )

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text("ملخص ترحيل الطلاب:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryDarkGreen)
                                        HorizontalDivider()
                                        
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("سنة دراسية جديدة مضافة:", fontSize = 12.sp, color = TextGray)
                                            Text(newYearLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("عدد الطلاب المنتقلون لمجموعات جديدة:", fontSize = 12.sp, color = TextGray)
                                            Text("$summaryPromote طالب", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SuccessGreen)
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("عدد الطلاب المتخرجين:", fontSize = 12.sp, color = TextGray)
                                            Text("$summaryGraduated طالب", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AccentGreen)
                                        }
                                    }
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                             text = "⚠️ تنبيه هام للغاية",
                                             fontWeight = FontWeight.Bold,
                                             fontSize = 13.sp,
                                             color = Color(0xFFE65100)
                                        )
                                        Text(
                                            text = "انتقال الطلاب يعني إفراغ المجموعة السابقة لتكون جاهزة لاستقبال طلاب جدد ومسح جداول الحصص الأسبوعية السابقة وبدء صفحة مالية جديدة فارغة للعام الجديد. ستبقى كافة تقارير الدفع وسجلات الحضور والغياب للطلاب ونتائج الامتحانات السابقة مؤرشفة ومحفوظة بالاسم والتواريخ للعودة وتصفحها بأي وقت.",
                                            fontSize = 11.sp,
                                            color = Color(0xFFD84315),
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Controls footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 1) {
                        Button(
                            onClick = { currentStep-- },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("السابق", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { onDismissRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إلغاء", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (currentStep < 3) {
                        Button(
                            onClick = {
                                if (currentStep == 1) {
                                    val trimmedLabel = newYearLabel.trim()
                                    if (trimmedLabel.isBlank() || newYearStartDate.isBlank() || newYearEndDate.isBlank()) {
                                        Toast.makeText(context, "جميع الحقول مطلوبة، يرجى ملء كافة البيانات.", Toast.LENGTH_SHORT).show()
                                    } else if (newYearStartDate >= newYearEndDate) {
                                        Toast.makeText(context, "تاريخ نهاية العام يجب أن يكون بعد تاريخ البداية", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val startParts = newYearStartDate.split("-")
                                        val endParts = newYearEndDate.split("-")
                                        if (startParts.size == 3 && endParts.size == 3) {
                                            val startYear = startParts[0].toIntOrNull() ?: 0
                                            val startMonth = startParts[1].toIntOrNull() ?: 0
                                            val endYear = endParts[0].toIntOrNull() ?: 0
                                            val endMonth = endParts[1].toIntOrNull() ?: 0

                                            val diffMonths = (endYear - startYear) * 12 + (endMonth - startMonth)
                                            val expectedName = "$startYear/$endYear"

                                            if (diffMonths < 3) {
                                                Toast.makeText(context, "الفترة بين البداية والنهاية يجب أن تكون 3 أشهر على الأقل", Toast.LENGTH_SHORT).show()
                                            } else if (trimmedLabel != expectedName) {
                                                Toast.makeText(context, "اسم العام يجب أن يكون $expectedName", Toast.LENGTH_SHORT).show()
                                            } else {
                                                currentStep++
                                            }
                                        } else {
                                            Toast.makeText(context, "صيغة التواريخ غير صالحة", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else if (currentStep == 2) {
                                    val errors = mutableListOf<String>()
                                    activeGroups.forEach { group ->
                                        val studs = studentsInGroup[group.id] ?: emptyList()
                                        studs.forEach { student ->
                                            val choice = studentPromotionChoices[student.id] ?: "promote"
                                            if (choice == "promote") {
                                                val targetGroup = studentTargetGroups[student.id]
                                                if (targetGroup == null || targetGroup == 0) {
                                                    errors.add("${student.name}: لم يتم اختيار المجموعة الجديدة")
                                                }
                                            }
                                        }
                                    }
                                    if (errors.isNotEmpty()) {
                                        Toast.makeText(context, errors.first(), Toast.LENGTH_LONG).show()
                                    } else {
                                        currentStep++
                                    }
                                } else {
                                    currentStep++
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("التالي", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                // COMMIT YEAR ROLLOVER TRANSACT
                                val oldEnrollmentsToUpdate = mutableListOf<Enrollment>()
                                val newEnrollmentsToInsert = mutableListOf<Enrollment>()

                                activeGroups.forEach { group ->
                                    val studs = studentsInGroup[group.id] ?: emptyList()
                                    
                                    studs.forEach { student ->
                                        val choice = studentPromotionChoices[student.id] ?: "promote"
                                        val targetGroupId = studentTargetGroups[student.id] ?: 0
                                        val oldEnrollment = enrollments.find { it.studentId == student.id && it.groupId == group.id && it.academicYearId == (currentYear?.id ?: 1) }
                                        
                                        when (choice) {
                                            "promote" -> {
                                                if (targetGroupId > 0) {
                                                    newEnrollmentsToInsert.add(
                                                        Enrollment(
                                                            studentId = student.id,
                                                            groupId = targetGroupId,
                                                            academicYearId = 0,
                                                            status = "active",
                                                            enrollmentDate = newYearStartDate
                                                        )
                                                    )
                                                }
                                                oldEnrollment?.let {
                                                    oldEnrollmentsToUpdate.add(it.copy(status = "active"))
                                                }
                                            }
                                            "graduated" -> {
                                                oldEnrollment?.let {
                                                    oldEnrollmentsToUpdate.add(it.copy(status = "graduated"))
                                                }
                                            }
                                            "withdrawn" -> {
                                                oldEnrollment?.let {
                                                    oldEnrollmentsToUpdate.add(it.copy(status = "withdrawn"))
                                                }
                                            }
                                            "dropped" -> {
                                                oldEnrollment?.let {
                                                    oldEnrollmentsToUpdate.add(it.copy(status = "dropped"))
                                                }
                                            }
                                        }
                                    }
                                }

                                val newYear = AcademicYear(
                                    yearLabel = newYearLabel,
                                    startDate = newYearStartDate,
                                    endDate = newYearEndDate,
                                    isCurrent = true,
                                    status = "active"
                                )

                                viewModel.startNewAcademicYear(
                                    newYear = newYear,
                                    enrollmentsToInsert = newEnrollmentsToInsert,
                                    oldYearEnrollmentsToUpdate = oldEnrollmentsToUpdate,
                                    onSuccess = {
                                        Toast.makeText(context, "تم بدء العام الدراسي الجديد بنجاح فائق وترحيل الطلاب ومزامنة الجداول تلقائياً!", Toast.LENGTH_LONG).show()
                                        onDismissRequest()
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, "خطأ في بدء العام الجديد: $err", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("تأكيد وبدء السنة الدراسية", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}


// --- GLOBAL SEARCH BAR SHEET SYSTEM ---
@Composable
fun SearchSystemOverlay(
    viewModel: TeacherViewModel,
    onNavigateToStudent: (Int, Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    val groups by viewModel.groups.collectAsState()
    val students by viewModel.students.collectAsState()
    val enrollments by viewModel.enrollments.collectAsState()
    val currentYear by viewModel.currentAcademicYear.collectAsState()

    var query by remember { mutableStateOf("") }

    val studentGroupMap = remember(enrollments, currentYear) {
        val yId = currentYear?.id ?: 1
        enrollments
            .filter { it.academicYearId == yId && it.status == "active" }
            .associate { it.studentId to it.groupId }
    }

    val results = remember(query, students, groups, studentGroupMap) {
        if (query.trim().isBlank()) emptyList()
        else {
            students.filter { student ->
                val sGrpId = studentGroupMap[student.id] ?: 0
                val grpName = groups.find { it.id == sGrpId }?.name ?: ""
                student.name.contains(query, ignoreCase = true) ||
                        student.parentPhone.contains(query) ||
                        grpName.contains(query, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("البحث الفوري الشامل", fontWeight = FontWeight.Bold, color = PrimaryDarkGreen, fontSize = 16.sp)
                    IconButton(onClick = onDismissRequest) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("أكتب اسم الطالب أو رقم ولي الأمر...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryGreen) }
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (results.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (query.isBlank()) "ابدأ بكتابة الاستعلام للبحث" else "لم يتم العثور على نتائج مطابقة", color = TextGray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results) { student ->
                            val sGrpId = studentGroupMap[student.id] ?: 0
                            val grpName = groups.find { it.id == sGrpId }?.name ?: ""
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SoftBgGreen)
                                    .clickable {
                                        onDismissRequest()
                                        onNavigateToStudent(student.id, sGrpId)
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = student.name, fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)
                                    Text(text = "المجموعة: $grpName", fontSize = 11.sp, color = TextGray)
                                }
                                Icon(Icons.Default.ArrowOutward, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}


// ==========================================
// 8. ALL STUDENTS DETAILED LIST SCREEN
// ==========================================
@Composable
fun StudentsScreen(
    viewModel: TeacherViewModel,
    onNavigateToStudent: (Int, Int) -> Unit
) {
    val groups by viewModel.groups.collectAsState()
    
    val activeStudents by viewModel.activeStudents.collectAsState()
    val graduatedStudents by viewModel.graduatedStudents.collectAsState()
    val withdrawnStudents by viewModel.withdrawnStudents.collectAsState()
    val droppedStudents by viewModel.droppedStudents.collectAsState()
    val allStudentsList by viewModel.allStudentsList.collectAsState()
    val enrollments by viewModel.enrollments.collectAsState()
    val currentYear by viewModel.currentAcademicYear.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var selectedFilter by rememberSaveable { mutableStateOf("active") } // "active", "graduated", "withdrawn", "dropped", "all"

    val studentsSource = when (selectedFilter) {
        "active" -> activeStudents
        "graduated" -> graduatedStudents
        "withdrawn" -> withdrawnStudents
        "dropped" -> droppedStudents
        else -> allStudentsList
    }

    val studentGroupMap = remember(enrollments) {
        enrollments.associate { it.studentId to it.groupId }
    }

    val filteredStudents = remember(searchQuery, studentsSource, groups, studentGroupMap) {
        if (searchQuery.trim().isBlank()) {
            studentsSource
        } else {
            studentsSource.filter { student ->
                val sGrpId = studentGroupMap[student.id] ?: 0
                val grpName = groups.find { it.id == sGrpId }?.name ?: ""
                student.name.contains(searchQuery, ignoreCase = true) ||
                        student.parentPhone.contains(searchQuery) ||
                        grpName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TeacherAppBar(title = "جميع الطلاب")
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddStudentDialog = true },
                containerColor = PrimaryGreen,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 16.dp).testTag("add_student_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "إضافة طالب")
                    Text("إضافة طالب جديد", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(LightBgGreen)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // High contrast beautiful search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث عن طالب بالاسم، الهاتف أو المجموعة...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryGreen) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                )
            )

            // Dynamic filter chips for Student tab
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("active", "نشطون", Color(0xFF2E7D32)),
                    Triple("graduated", "خريجون", Color(0xFF1976D2)),
                    Triple("withdrawn", "منسحبون", Color(0xFFF57C00)),
                    Triple("dropped", "منقطعون", Color(0xFFC62828)),
                    Triple("all", "الكل", Color(0xFF374151))
                ).forEach { (filterType, label, color) ->
                    val isSelected = selectedFilter == filterType
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filterType },
                        label = { Text(label, color = if (isSelected) Color.White else color, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color
                        )
                    )
                }
            }

            Text(
                text = "إجمالي عدد الطلاب: ${filteredStudents.size} طالب",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryDarkGreen
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (filteredStudents.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا توجد نتائج مطابقة لبحثك الحالي.", color = TextGray)
                        }
                    }
                } else {
                    items(filteredStudents) { student ->
                        val sGrpId = studentGroupMap[student.id] ?: 0
                        val grp = groups.find { it.id == sGrpId }
                        val context = LocalContext.current

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToStudent(student.id, sGrpId) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, SoftBgGreen),
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = student.name,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryDarkGreen,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "المجموعة: ${grp?.name ?: "غير معروف"}",
                                        fontSize = 12.sp,
                                        color = PrimaryGreen,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "تاريخ الالتحاق: ${com.example.data.DateUtils.formatDateWithArabicDay(student.joinDate)}",
                                        fontSize = 11.sp,
                                        color = TextGray
                                    )
                                }

                                Row(
                                    modifier = Modifier.padding(end = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Call Button
                                    IconButton(
                                        onClick = { launchDialer(context, student.parentPhone) },
                                        modifier = Modifier
                                            .background(SoftBgGreen, RoundedCornerShape(50))
                                            .size(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = "اتصال",
                                            tint = PrimaryGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Whatsapp Button
                                    IconButton(
                                        onClick = {
                                            launchWhatsApp(
                                                context,
                                                student.parentPhone,
                                                "أهلاً بحضرتك، بخصوص الطالب ${student.name} في مجموعة ${grp?.name ?: ""}"
                                            )
                                        },
                                        modifier = Modifier
                                            .background(SuccessGreen.copy(alpha = 0.1f), RoundedCornerShape(50))
                                            .size(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Chat,
                                            contentDescription = "واتساب",
                                            tint = SuccessGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddStudentDialog) {
        DashboardAddStudentDialog(
            groupsList = groups,
            onDismiss = { showAddStudentDialog = false },
            onSave = { groupId, name, phone, joinDate, notes, sessionsRemaining ->
                viewModel.addStudent(groupId, name, phone, joinDate, notes, sessionsRemaining)
                showAddStudentDialog = false
            }
        )
    }
}


// --- COMPONENT: NATIVE PDF COMPREHENSIVE GENERATOR ---
fun exportStudentProfilePdf(
    context: Context,
    student: Student,
    group: Group?,
    payments: List<Payment>,
    exams: List<ExamScore>,
    attendances: List<AttendanceRecord>,
    sessions: List<Session>,
    viewImmediately: Boolean = false
) {
    PdfHelper.generateAndExportStudentProfile(
        context,
        student,
        group,
        payments,
        exams,
        attendances,
        sessions,
        viewImmediately
    )
}

/*
fun exportStudentProfilePdfDisabled(
    context: Context,
    student: Student,
    group: Group?,
    payments: List<Payment>,
    exams: List<ExamScore>,
    attendances: List<AttendanceRecord>,
    sessions: List<Session>,
    viewImmediately: Boolean = false
) {
    try {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        
        // --- DATA CALCULATIONS ---
        val normJoinDate = student.joinDate.isNotBlank().let { if (it) student.joinDate.replace("-", "/") else "2026-06-12" }
        
        // Filter sessions starting on or after Student joinDate (keeps statistics 100% consistent with UI metrics)
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
        
        val starsStr = when {
            overallPercentage >= 90 -> "⭐⭐⭐⭐⭐"
            overallPercentage >= 80 -> "⭐⭐⭐⭐"
            overallPercentage >= 65 -> "⭐⭐⭐"
            else -> "⭐⭐"
        }
        
        // --- CALENDAR & EXAM PERFORMANCE CALCULATIONS ---
        val sortedSessions = sessions.sortedBy { it.date }
        val sortedAttendances = activeAttendances.sortedByDescending { it.attendanceDate.ifBlank { it.timestamp } }
        val sortedPayments = cleanPayments.sortedByDescending { it.first.month } // e.g. June, May etc.
        val sortedExams = exams.sortedByDescending { it.date }
        
        val minPct = if (exams.isNotEmpty()) exams.minOf { if (it.maxScore > 0) (it.score * 100 / it.maxScore).toInt() else 0 } else 0
        val maxPct = if (exams.isNotEmpty()) exams.maxOf { if (it.maxScore > 0) (it.score * 100 / it.maxScore).toInt() else 0 } else 0
        
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
        
        // Calculate dynamic pages bounds to avoid container shrinking and overflow
        val totalPdfPages = 5
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
            if (dateStr.isBlank()) return ""
            return try {
                val normalized = dateStr.replace("/", "-").trim()
                val cleanStr = if (normalized.contains("T")) normalized.split("T")[0] else if (normalized.contains(" ")) normalized.split(" ")[0] else normalized
                val parts = cleanStr.split("-")
                if (parts.size >= 3) {
                    val p1 = parts[1].padStart(2, '0')
                    if (parts[0].length == 4) {
                        "${parts[0]}-$p1"
                    } else if (parts[2].length == 4) {
                        "${parts[2]}-$p1"
                    } else {
                        "2026-$p1"
                    }
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        }
        
        val sessionMap = sessions.associateBy { it.id }
        val monthKeysSet = mutableSetOf<String>()
        val monthlyStats = mutableMapOf<String, IntArray>()
        
        activeAttendances.forEach { record ->
            val recordDate = if (record.attendanceDate.isNotBlank()) record.attendanceDate else record.timestamp
            val key = getYearMonthKey(recordDate)
            if (key.isNotBlank()) {
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
            attendancePercentage >= 90 -> "ممتاز (ملتزم جداً بالحضور والانضباط بالمواعيد)"
            attendancePercentage >= 80 -> "جيد جداً (مواظب على الحضور ويعتمد عليه)"
            attendancePercentage >= 65 -> "مقبول (يحتاج إلى تحسين الالتزام بالحضور والمواعيد)"
            else -> "مستواه حرج (غياب متكرر وتأخر مستمر، يتطلب متابعة فورية)"
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
        
        // Render large academic cap centered in circle
        textPaint.color = colorPrimaryGreen
        textPaint.textSize = 30f
        textPaint.textAlign = android.graphics.Paint.Align.CENTER
        textPaint.isFakeBoldText = true
        canvas1.drawText("👨‍🎓", 745f, 281f, textPaint)
        
        // Student Profile details
        textPaint.color = colorWhite
        textPaint.isFakeBoldText = true
        textPaint.textSize = 20f
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas1.drawText(student.name, 680f, 205f, textPaint)
        
        textPaint.isFakeBoldText = false
        textPaint.textSize = 11f
        // Group Name / Level
        canvas1.drawText("📚 الصف والمسار: ${group?.name ?: "غير محدد"}", 680f, 240f, textPaint)
        
        // Parent Phone
        canvas1.drawText("📞 هاتف ولي الأمر: ${student.parentPhone}", 680f, 275f, textPaint)
        
        // Enrolled
        canvas1.drawText("🗓️ الفترة الدراسية: العام الدراسي 2025 / 2026", 680f, 310f, textPaint)
        
        // Dynamic evaluation / Recommendation Text
        textPaint.color = 0xFFFCD34D.toInt() // gold/light orange text for emphasis
        textPaint.isFakeBoldText = true
        textPaint.textSize = 10.5f
        canvas1.drawText("📝 تقييم عام: $attendanceAdvice", 680f, 355f, textPaint)
        
        // 3. 2x2 Grid of Left Side Cards (mتبقي, مدفوع, الحضور, الامتحانات)
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
        
        // Rating Stars Card 2x2
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
        canvas1.drawText("$academicLevel 🌟", 255f, 355f, textPaint)
        
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
        canvas1.drawText("صفحة 1 من 5", 30f, 575f, textPaint)
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas1.drawText("مركز النجاح التعليمي - إدارة أوفلاين ذكية", 812f, 575f, textPaint)
        
        pdfDocument.finishPage(page1)
        
        // ==========================================
        // PAGE 2 — MONTHLY ATTENDANCE SUMMARY (Portrait 595 x 842)
        // ==========================================
        currentPageNum++
        val pageInfo2 = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, currentPageNum).create()
        val page2 = pdfDocument.startPage(pageInfo2)
        val canvas2 = page2.canvas
        canvas2.drawColor(colorWhite)
        
        // 1. Header (Primary Green)
        paint.color = colorPrimaryGreen
        canvas2.drawRoundRect(30f, 25f, 565f, 105f, 10f, 10f, paint)
        
        textPaint.color = colorWhite
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        textPaint.isFakeBoldText = true
        textPaint.textSize = 18f
        canvas2.drawText("📊 الحضور والغياب والتأخير (شهري)", 545f, 65f, textPaint)
        
        textPaint.textSize = 11f
        textPaint.isFakeBoldText = false
        textPaint.textAlign = android.graphics.Paint.Align.LEFT
        canvas2.drawText("الطالب: ${student.name}", 50f, 62f, textPaint)
        canvas2.drawText("الصف: ${group?.name ?: "-"}", 50f, 82f, textPaint)
        
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
        canvas2.drawText("⚠️ أكثر شهر غياباً: $mostAbsentMonthsStr", 545f, 222f, textPaint)
        
        // 4. Monthly Attendance Table
        var yPos2 = 255f
        
        // Header Row
        paint.color = 0xFF004D26.toInt()
        canvas2.drawRoundRect(30f, yPos2, 565f, yPos2 + 28f, 6f, 6f, paint)
        
        textPaint.color = colorWhite
        textPaint.textSize = 10.5f
        textPaint.isFakeBoldText = true
        textPaint.textAlign = android.graphics.Paint.Align.CENTER
        
        // RTL columns coordinates: الشهر (565 to 430), إجمالي الحصص (430 to 310), حضور (310 to 220), غياب (220 to 130), تأخير (130 to 30)
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
                
                // Write stats
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
        
        // Footer Page 2
        paint.color = colorGrayBorder
        canvas2.drawRect(30f, 790f, 565f, 791f, paint)
        
        textPaint.textSize = 9f
        textPaint.isFakeBoldText = false
        textPaint.color = colorTextGray
        textPaint.textAlign = android.graphics.Paint.Align.LEFT
        canvas2.drawText("صفحة 2 من 5", 30f, 810f, textPaint)
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas2.drawText("تقرير الحضور والغياب الشهري للموسم الدراسي", 565f, 810f, textPaint)
        
        pdfDocument.finishPage(page2)
        
        // ==========================================
        // PAGE 3 — MONTHLY PAYMENTS & SUBSCRIPTIONS (Portrait 595 x 842)
        // ==========================================
        currentPageNum++
        val pageInfo3 = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, currentPageNum).create()
        val page3 = pdfDocument.startPage(pageInfo3)
        val canvas3 = page3.canvas
        canvas3.drawColor(colorWhite)
        
        // 1. Header (Primary Green)
        paint.color = colorPrimaryGreen
        canvas3.drawRoundRect(30f, 25f, 565f, 105f, 10f, 10f, paint)
        
        textPaint.color = colorWhite
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        textPaint.isFakeBoldText = true
        textPaint.textSize = 18f
        canvas3.drawText("💰 الاشتراكات والوضعية المالية", 545f, 65f, textPaint)
        
        textPaint.textSize = 11f
        textPaint.isFakeBoldText = false
        textPaint.textAlign = android.graphics.Paint.Align.LEFT
        canvas3.drawText("الطالب: ${student.name}", 50f, 62f, textPaint)
        val shortStatusStr = if (totalRemaining > 0) "عليك متأخرات" else "خالص السداد"
        canvas3.drawText("الحالة العامة: $shortStatusStr", 50f, 82f, textPaint)
        
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
            "لا توجد أي متأخرات مالية حية - خالص السداد بالكامل ✅"
        }
        
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        textPaint.color = colorDarkGray
        textPaint.textSize = 10f
        textPaint.isFakeBoldText = true
        canvas3.drawText("💳 حالة السداد العامه: $financialAlertStr", 545f, 222f, textPaint)
        
        // 4. Payments Table
        var yPos3 = 255f
        
        // Header
        paint.color = 0xFF004D26.toInt()
        canvas3.drawRoundRect(30f, yPos3, 565f, yPos3 + 28f, 6f, 6f, paint)
        
        textPaint.color = colorWhite
        textPaint.textSize = 10.5f
        textPaint.isFakeBoldText = true
        textPaint.textAlign = android.graphics.Paint.Align.CENTER
        
        // الشهر | المطلوب | المدفوع | المتبقي | الحالة
        // Coordinate mapping RTL: الشهر (565 to 440), المطلوب (440 to 340), المدفوع (340 to 240), المتبقي (240 to 150), الحالة (150 to 30)
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
        canvas3.drawText("صفحة 3 من 5", 30f, 810f, textPaint)
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas3.drawText("التقرير المالي الفوري لمتابعة غطاء الاشتراكات", 565f, 810f, textPaint)
        
        pdfDocument.finishPage(page3)
        
        // ==========================================
        // PAGE 4 — EXAM GRADES & SCORES (Portrait 595 x 842)
        // ==========================================
        currentPageNum++
        val pageInfo4 = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, currentPageNum).create()
        val page4 = pdfDocument.startPage(pageInfo4)
        val canvas4 = page4.canvas
        canvas4.drawColor(colorWhite)
        
        // 1. Header (Primary Green)
        paint.color = colorPrimaryGreen
        canvas4.drawRoundRect(30f, 25f, 565f, 105f, 10f, 10f, paint)
        
        textPaint.color = colorWhite
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        textPaint.isFakeBoldText = true
        textPaint.textSize = 18f
        canvas4.drawText("📝 نتائج وعلامات الامتحانات", 545f, 65f, textPaint)
        
        textPaint.textSize = 11f
        textPaint.isFakeBoldText = false
        textPaint.textAlign = android.graphics.Paint.Align.LEFT
        canvas4.drawText("الطالب: ${student.name}", 50f, 62f, textPaint)
        canvas4.drawText("الدرجة الكلية: $overallPercentage%", 50f, 82f, textPaint)
        
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
        canvas4.drawText("🏆 التقييم والتقدير العام للأداء: $academicRatingLabel  $starsStr", 545f, 222f, textPaint)
        
        // 4. Exams Table
        var yPos4 = 255f
        
        // Header
        paint.color = 0xFF004D26.toInt()
        canvas4.drawRoundRect(30f, yPos4, 565f, yPos4 + 28f, 6f, 6f, paint)
        
        textPaint.color = colorWhite
        textPaint.textSize = 10.5f
        textPaint.isFakeBoldText = true
        textPaint.textAlign = android.graphics.Paint.Align.CENTER
        
        // الامتحان | الدرجة | من | النسبة | التقييم
        // RTL columns coordinates: الامتحان (565 to 410), الدرجة (410 to 320), من (320 to 240), النسبة (240 to 150), التقييم (150 to 30)
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
                yPos4 += 2f
                if (index % 2 == 1) {
                    paint.color = colorLightGray
                    canvas4.drawRect(30f, yPos4, 565f, yPos4 + 25f, paint)
                }
                
                textPaint.textAlign = android.graphics.Paint.Align.CENTER
                textPaint.color = colorDarkGray
                textPaint.isFakeBoldText = false
                textPaint.textSize = 10f
                
                // Write cells
                canvas4.drawText(e.examName, 487.5f, yPos4 + 17f, textPaint)
                
                val examPct = if (e.maxScore > 0) (e.score * 100 / e.maxScore).toInt() else 0
                val accentC = when {
                    examPct >= 85 -> colorLightGreen
                    examPct >= 65 -> colorOrange
                    else -> colorRed
                }
                
                textPaint.color = accentC
                textPaint.isFakeBoldText = true
                canvas4.drawText("${e.score.toInt()}", 365f, yPos4 + 17f, textPaint)
                
                textPaint.color = colorDarkGray
                textPaint.isFakeBoldText = false
                canvas4.drawText("${e.maxScore.toInt()}", 280f, yPos4 + 17f, textPaint)
                
                textPaint.color = accentC
                textPaint.isFakeBoldText = true
                canvas4.drawText("$examPct%", 195f, yPos4 + 17f, textPaint)
                
                val examRating = when {
                    examPct >= 90 -> "ممتاز"
                    examPct >= 80 -> "جيد جداً"
                    examPct >= 65 -> "جيد"
                    else -> "مقبول"
                }
                canvas4.drawText(examRating, 90f, yPos4 + 17f, textPaint)
                
                yPos4 += 25f
            }
        }
        
        // Footer Page 4
        paint.color = colorGrayBorder
        canvas4.drawRect(30f, 790f, 565f, 791f, paint)
        
        textPaint.textSize = 9f
        textPaint.isFakeBoldText = false
        textPaint.color = colorTextGray
        textPaint.textAlign = android.graphics.Paint.Align.LEFT
        canvas4.drawText("صفحة 4 من 5", 30f, 810f, textPaint)
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas4.drawText("نتائج وعلامات الامتحانات للأداء الأكاديمي الشامل", 565f, 810f, textPaint)
        
        pdfDocument.finishPage(page4)
        
        // ==========================================
        // PAGE 5 — DETAILED BREAKDOWN OF ABSENCE & LATENESS (Portrait 595 x 842)
        // ==========================================
        currentPageNum++
        val pageInfo5 = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, currentPageNum).create()
        val page5 = pdfDocument.startPage(pageInfo5)
        val canvas5 = page5.canvas
        canvas5.drawColor(colorWhite)
        
        // 1. Header (Primary Green)
        paint.color = colorPrimaryGreen
        canvas5.drawRoundRect(30f, 25f, 565f, 105f, 10f, 10f, paint)
        
        textPaint.color = colorWhite
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        textPaint.isFakeBoldText = true
        textPaint.textSize = 18f
        canvas5.drawText("📁 التفاصيل وشبكة الغيابات والتأخير", 545f, 65f, textPaint)
        
        textPaint.textSize = 11f
        textPaint.isFakeBoldText = false
        textPaint.textAlign = android.graphics.Paint.Align.LEFT
        canvas5.drawText("الطالب: ${student.name}", 50f, 62f, textPaint)
        canvas5.drawText("سجل التاريخ التفصيلي", 50f, 82f, textPaint)
        
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
        
        // 2. Absence Details (Top Half)
        var yAbs = 155f
        textPaint.color = colorRed
        textPaint.textSize = 12f
        textPaint.isFakeBoldText = true
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas5.drawText("🔴 تفاصيل الغياب (حسب الشهور)", 545f, 140f, textPaint)
        
        // divider
        paint.color = colorRed
        canvas5.drawRect(30f, 145f, 565f, 146.5f, paint)
        
        if (absencesByMonth.isEmpty()) {
            textPaint.color = colorTextGray
            textPaint.textSize = 10f
            textPaint.isFakeBoldText = false
            canvas5.drawText("← لا يوجد غياب مسجل للطالب حالياً", 545f, 175f, textPaint)
            yAbs = 200f
        } else {
            absencesByMonth.forEach { (mKey, records) ->
                if (yAbs < 420f) {
                    val monthName = arabicMonths[mKey.split("-").getOrNull(1)] ?: mKey
                    
                    textPaint.color = colorDarkGray
                    textPaint.textSize = 10f
                    textPaint.isFakeBoldText = true
                    canvas5.drawText("📁 $monthName", 545f, yAbs + 15f, textPaint)
                    yAbs += 20f
                    
                    records.forEach { record ->
                        if (yAbs < 425f) {
                            val recordDate = if (record.attendanceDate.isNotBlank()) record.attendanceDate else record.timestamp
                            val displayDay = getArabicDayName(recordDate)
                            val displayDateStr = DateUtils.formatDateForDisplay(recordDate)
                            
                            val sess = sessionMap[record.sessionId]
                            val sessionNumText = if (sess != null && sess.sessionNumber > 0) "حصة (${sess.sessionNumber})" else "حصة"
                            
                            textPaint.color = colorTextGray
                            textPaint.textSize = 9.5f
                            textPaint.isFakeBoldText = false
                            canvas5.drawText("• $displayDateStr (يوافق يوم $displayDay) — $sessionNumText", 525f, yAbs + 12f, textPaint)
                            yAbs += 16f
                        }
                    }
                    yAbs += 5f
                }
            }
        }
        
        // 3. Lateness Details (Bottom Half)
        var yLate = 485f
        textPaint.color = colorOrange
        textPaint.textSize = 12f
        textPaint.isFakeBoldText = true
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas5.drawText("🟡 تفاصيل التأخير (حسب الشهور)", 545f, 470f, textPaint)
        
        paint.color = colorOrange
        canvas5.drawRect(30f, 475f, 565f, 476.5f, paint)
        
        if (latenessesByMonth.isEmpty()) {
            textPaint.color = colorTextGray
            textPaint.textSize = 10f
            textPaint.isFakeBoldText = false
            canvas5.drawText("← لا يوجد تأخير مسجل للطالب حالياً", 545f, 505f, textPaint)
        } else {
            latenessesByMonth.forEach { (mKey, records) ->
                if (yLate < 750f) {
                    val monthName = arabicMonths[mKey.split("-").getOrNull(1)] ?: mKey
                    
                    textPaint.color = colorDarkGray
                    textPaint.textSize = 10f
                    textPaint.isFakeBoldText = true
                    canvas5.drawText("📁 $monthName", 545f, yLate + 15f, textPaint)
                    yLate += 20f
                    
                    records.forEach { record ->
                        if (yLate < 760f) {
                            val recordDate = if (record.attendanceDate.isNotBlank()) record.attendanceDate else record.timestamp
                            val displayDay = getArabicDayName(recordDate)
                            val displayDateStr = DateUtils.formatDateForDisplay(recordDate)
                            val arrivalTime = record.lateArrivalTime ?: "04:15 م"
                            
                            textPaint.color = colorTextGray
                            textPaint.textSize = 9.5f
                            textPaint.isFakeBoldText = false
                            canvas5.drawText("• $displayDateStr (يوافق يوم $displayDay) — حضر $arrivalTime", 525f, yLate + 12f, textPaint)
                            yLate += 16f
                        }
                    }
                    yLate += 5f
                }
            }
        }
        
        // Footer Page 5
        paint.color = colorGrayBorder
        canvas5.drawRect(30f, 790f, 565f, 791f, paint)
        
        textPaint.textSize = 9f
        textPaint.isFakeBoldText = false
        textPaint.color = colorTextGray
        textPaint.textAlign = android.graphics.Paint.Align.LEFT
        canvas5.drawText("صفحة 5 من 5", 30f, 810f, textPaint)
        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
        canvas5.drawText("مركز التفوق الرقمي - رصد ذكي وتأمين البيانات", 565f, 810f, textPaint)
        
        pdfDocument.finishPage(page5)
        
        // ==========================================
        // EXPORT TO FILE SYSTEM
        // ==========================================
        val cachePath = File(context.cacheDir, "reports")
        cachePath.mkdirs()
        val pdfFile = File(cachePath, "report_student_${student.id}.pdf")
        val outputStream = FileOutputStream(pdfFile)
        val pathOfFile = pdfFile.absolutePath
        pdfDocument.writeTo(outputStream)
        outputStream.close()
        pdfDocument.close()
        
        val cleanUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        
        if (viewImmediately) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(cleanUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // FALLBACK: If no PDF viewer is found on the device, try chooser or share
                try {
                    val chooser = Intent.createChooser(intent, "عرض ملف PDF")
                    context.startActivity(chooser)
                } catch (ex: Exception) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_SUBJECT, "عرض تقرير الطالب: ${student.name}")
                        putExtra(Intent.EXTRA_STREAM, cleanUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "عرض ومشاركة ملف PDF للطالب"))
                }
            }
        } else {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_SUBJECT, "تنزيل تقرير الطالب: ${student.name}")
                putExtra(Intent.EXTRA_STREAM, cleanUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "تحميل ومشاركة ملف PDF للطالب"))
        }
        
    } catch (e: Exception) {
        Toast.makeText(context, "خطأ أثناء توليد ملف PDF: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
*/



// ==========================================
// 6. EXAMS MANAGEMENT SCREEN (Polished Offline-First)
// ==========================================
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    viewModel: TeacherViewModel,
    onBack: () -> Unit,
    onNavigateToStudent: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val groups by viewModel.groups.collectAsState()
    val allExams by viewModel.exams.collectAsState()
    val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = New Exam, 1 = Previous Exams

    // Tab 0: New Exam Form State
    var examName by remember { mutableStateOf("") }
    var maxScoreStr by remember { mutableStateOf("20") }
    var examDate by remember { mutableStateOf(todayDate) }
    var selectedGroupId by remember { mutableStateOf<Int?>(null) }
    
    // Student scores mapping state
    val scoreInputs = remember { mutableStateMapOf<Int, String>() }

    Scaffold(
        topBar = {
            TeacherAppBar(
                title = "تحصيل درجات الامتحان",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryDarkGreen
                        )
                    }
                },
                onHomeClick = onBack
            )
        },
        containerColor = LightBgGreen
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Segmented Tab Selectors
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("تحصيل درجات الامتحان", "مؤشرات وسجل الاختبارات").forEachIndexed { index, title ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == index) SoftBgGreen else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (selectedTab == index) PrimaryDarkGreen else TextGray
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                // --- TAB 1: NEW EXAM CREATION ---
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, SoftBgGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("تفاصيل وبيانات الاختبار", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PrimaryDarkGreen)
                                
                                OutlinedTextField(
                                    value = examName,
                                    onValueChange = { examName = it },
                                    label = { Text("عنوان الامتحان *") },
                                    placeholder = { Text("مثال: اختبار الباب الأول") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("exam_name_input"),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = maxScoreStr,
                                        onValueChange = { maxScoreStr = it },
                                        label = { Text("الدرجة الكبرى *") },
                                        placeholder = { Text("مثال: 20") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f).testTag("exam_max_score_input"),
                                        shape = RoundedCornerShape(10.dp)
                                    )

                                    OutlinedTextField(
                                        value = examDate,
                                        onValueChange = { examDate = it },
                                        label = { Text("التاريخ") },
                                        placeholder = { Text("YYYY-MM-DD") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.2f).testTag("exam_date_input"),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }

                                // Dropdown to Select Group
                                Text("اختر المجموعة الدراسية:", fontSize = 12.sp, color = TextGray)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (groups.isEmpty()) {
                                        Text("لا توجد مجموعات حالية. يرجى تهيئة مجموعة أولاً.", color = DangerRed, fontSize = 13.sp)
                                    } else {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(groups) { grp ->
                                                val isSelected = selectedGroupId == grp.id
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(if (isSelected) PrimaryDarkGreen else SoftBgGreen)
                                                        .border(1.dp, if (isSelected) AccentGreen else Color.Transparent, RoundedCornerShape(10.dp))
                                                        .clickable {
                                                            selectedGroupId = grp.id
                                                            scoreInputs.clear()
                                                        }
                                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                                ) {
                                                    Text(
                                                        text = grp.name,
                                                        color = if (isSelected) Color.White else PrimaryDarkGreen,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Graded Student List Block
                    val maxScore = maxScoreStr.toDoubleOrNull() ?: 20.0
                    val currentGroup = groups.find { it.id == selectedGroupId }
                    if (currentGroup != null) {
                        item {
                            Text(
                                text = "رصد درجات طلاب مجموعة: ${currentGroup.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = PrimaryDarkGreen
                            )
                        }

                        // Collect students for selected group
                        item {
                            val groupStudents by viewModel.getStudentsByGroup(currentGroup.id).collectAsState(initial = emptyList())
                            val hasInvalidScores = remember(groupStudents, scoreInputs, maxScore) {
                                groupStudents.any { stud ->
                                    val currentScoreText = scoreInputs[stud.id] ?: ""
                                    val currentScore = currentScoreText.toDoubleOrNull()
                                    currentScore != null && (currentScore < 0.0 || currentScore > maxScore)
                                }
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, SoftBgGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (groupStudents.isEmpty()) {
                                        Text("لا يوجد طلاب مسجلين في هذه المجموعة حالياً.", color = TextGray, fontSize = 13.sp)
                                    } else {
                                        groupStudents.forEach { stud ->
                                            val currentScoreText = scoreInputs[stud.id] ?: ""
                                            val currentScore = currentScoreText.toDoubleOrNull()
                                            val isScoreInvalid = currentScore != null && (currentScore < 0.0 || currentScore > maxScore)
                                            val percent = if (currentScore != null && maxScore > 0f) (currentScore / maxScore) * 100.0 else null
                                            val isPass = percent != null && percent >= 50.0

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        if (isScoreInvalid) DangerRed.copy(alpha = 0.05f) else LightBgGreen.copy(alpha = 0.5f),
                                                        RoundedCornerShape(10.dp)
                                                    )
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(stud.name, fontWeight = FontWeight.Bold, color = PrimaryDarkGreen, fontSize = 13.sp)
                                                    when {
                                                        isScoreInvalid -> {
                                                            Text(
                                                                text = "الدرجة غير صالحة! (بين 0 و $maxScore)",
                                                                color = DangerRed,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        percent != null -> {
                                                            Text(
                                                                text = "النسبة: " + String.format(Locale.ENGLISH, "%.1f", percent) + "% | " + (if (isPass) "ناجح" else "راسب"),
                                                                color = if (isPass) SuccessGreen else DangerRed,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                        else -> {
                                                            Text("بانتظار إدخال الدرجة", color = TextGray, fontSize = 11.sp)
                                                        }
                                                    }
                                                }

                                                OutlinedTextField(
                                                    value = currentScoreText,
                                                    onValueChange = { scoreInputs[stud.id] = it },
                                                    placeholder = { Text("0") },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier
                                                        .width(80.dp)
                                                        .testTag("score_input_${stud.id}"),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = if (isScoreInvalid) DangerRed else if (percent != null) (if (isPass) SuccessGreen else DangerRed) else PrimaryGreen,
                                                        unfocusedBorderColor = if (isScoreInvalid) DangerRed else SoftBgGreen
                                                    )
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Save Unified Button
                                        Button(
                                            onClick = {
                                                if (examName.isBlank()) {
                                                    Toast.makeText(context, "الرجاء إدخال عنوان او اسم الامتحان أولاً", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                if (hasInvalidScores) {
                                                    Toast.makeText(context, "الرجاء تصحيح الدرجات غير الصالحة أولاً", Toast.LENGTH_SHORT).show()
                                                    return@Button
                                                }
                                                // Insert in background
                                                var counted = 0
                                                groupStudents.forEach { stud ->
                                                    val raw = scoreInputs[stud.id]
                                                    val scoreVal = raw?.toDoubleOrNull() ?: 0.0
                                                    viewModel.addExamScore(stud.id, examName, scoreVal, maxScore, examDate)
                                                    counted++
                                                }
                                                Toast.makeText(context, "تم رصد وحفظ درجات $counted طالب بنجاح!", Toast.LENGTH_LONG).show()
                                                examName = ""
                                                scoreInputs.clear()
                                                selectedTab = 1 // Switch to stats tab
                                            },
                                            enabled = !hasInvalidScores && examName.isNotBlank(),
                                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_grades_unified_btn"),
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("حفظ الدرجات", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(12.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("يرجى اختيار مجموعة دراسية لبدء رصد الدرجات لطلابها.", color = TextGray, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            } else {
                // --- TAB 2: EXAMS HISTORY AND PEDAGOGICAL INDICATORS ---
                if (allExams.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("لا يوجد امتحانات مسجلة حتى الآن.\nابدأ برصد درجات أول اختبار.", color = TextGray, textAlign = TextAlign.Center)
                    }
                } else {
                    // Group exams in memory by Exam Name + Date
                    val groupedExams = remember(allExams) {
                        allExams.groupBy { Pair(it.examName, it.date) }
                    }

                    var expandedExamKey by remember { mutableStateOf<Pair<String, String>?>(null) }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        groupedExams.forEach { (key, scores) ->
                            val examTitle = key.first
                            val examDateStr = key.second
                            val isExpanded = expandedExamKey == key

                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, SoftBgGreen),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedExamKey = if (isExpanded) null else key }
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(examTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = PrimaryDarkGreen)
                                                Text("التاريخ: ${com.example.data.DateUtils.formatDateWithArabicDay(examDateStr)}", fontSize = 11.sp, color = TextGray)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .background(SoftBgGreen, RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("${scores.size} طلاب", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Computed metrics
                                        val averageScore = scores.map { (it.score * 100.0) / it.maxScore }.average()
                                        val passed = scores.count { it.score >= it.maxScore * 0.5 }
                                        val passPrc = ((passed.toDouble() / scores.size) * 100).toInt()

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Column {
                                                    Text("متوسط الدرجات", fontSize = 10.sp, color = TextGray)
                                                    Text(String.format(Locale.ENGLISH, "%.1f", averageScore) + "%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                                                }
                                                Column {
                                                    Text("نسبة النجاح", fontSize = 10.sp, color = TextGray)
                                                    Text("$passPrc%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                                }
                                            }

                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                tint = PrimaryGreen
                                            )
                                        }

                                        if (isExpanded) {
                                            Divider(modifier = Modifier.padding(vertical = 12.dp), color = SoftBgGreen)
                                            
                                            // 1. Failure tracking & Warning section (Underperforming students detection)
                                            val failedStudents = scores.filter { it.score < it.maxScore * 0.5 }
                                            if (failedStudents.isNotEmpty()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(DangerRed.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                                        .padding(10.dp)
                                                ) {
                                                    Text(
                                                        text = "⚠️ الطلاب المتعثرين في الاختبار الحالي (أقل من %50):",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = DangerRed
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    // Map Student names cleanly
                                                    val studentsList by viewModel.students.collectAsState()
                                                    failedStudents.forEach { worst ->
                                                        val relativeStudent = studentsList.find { it.id == worst.studentId }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = "• " + (relativeStudent?.name ?: "طالب غير معروف"),
                                                                fontSize = 12.sp,
                                                                color = DangerRed,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.clickable {
                                                                    if (relativeStudent != null) {
                                                                        onNavigateToStudent(relativeStudent.id, viewModel.enrollments.value.find { it.studentId == relativeStudent.id && it.academicYearId == (viewModel.currentAcademicYear.value?.id ?: 1) }?.groupId ?: 0)
                                                                    }
                                                                }
                                                            )
                                                            Text(
                                                                text = "${worst.score} / ${worst.maxScore}",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = DangerRed
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(12.dp))
                                            }

                                            // 2. High Scorers Section (Pedagogical Excellence)
                                            val highScorers = scores.filter { it.score >= it.maxScore * 0.9 }
                                            if (highScorers.isNotEmpty()) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(SuccessGreen.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                                        .padding(10.dp)
                                                ) {
                                                    Text(
                                                        text = "🌟 الطلاب المتميزين متفوقي الاختبار (%90 فما فوق):",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = SuccessGreen
                                                    )
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    val studentsList by viewModel.students.collectAsState()
                                                    highScorers.forEach { best ->
                                                        val relativeStudent = studentsList.find { it.id == best.studentId }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = "★ " + (relativeStudent?.name ?: "طالب غير معروف"),
                                                                fontSize = 12.sp,
                                                                color = SuccessGreen,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = "${best.score} / ${best.maxScore}",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = SuccessGreen
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(12.dp))
                                            }

                                            // 3. Complete Student score list
                                            Text("تفاصيل درجات الطلاب بالكامل:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryDarkGreen)
                                            Spacer(modifier = Modifier.height(6.dp))

                                            val studentsList by viewModel.students.collectAsState()
                                            scores.forEach { scoreObj ->
                                                val relativeSt = studentsList.find { it.id == scoreObj.studentId }
                                                val stPercent = (scoreObj.score / scoreObj.maxScore) * 100
                                                
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                        .background(SoftBgGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                        .padding(8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = relativeSt?.name ?: "طالب غير معروف",
                                                        fontSize = 12.sp,
                                                        color = TextDark
                                                    )
                                                    Text(
                                                        text = "${scoreObj.score} / ${scoreObj.maxScore} (${stPercent.toInt()}%)",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (stPercent >= 50.0) SuccessGreen else DangerRed
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getArabicDayName(dateStr: String): String {
    return com.example.data.DateUtils.getArabicDayName(dateStr)
}

@Composable
fun StudentMigrationRow(
    student: com.example.data.Student,
    groups: List<com.example.data.Group>,
    currentGroupId: Int,
    selectedChoice: String,
    selectedTargetGroupId: Int?,
    onChoiceChange: (String) -> Unit,
    onTargetGroupChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(student.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "المجموعة الحالية: ${groups.find { it.id == currentGroupId }?.name ?: "غير معروف"}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            androidx.compose.material3.FilterChip(
                selected = selectedChoice == "promote",
                onClick = { onChoiceChange("promote") },
                label = { Text("ينتقل ➡️", fontSize = 11.sp) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFE3F2FD)
                )
            )
            androidx.compose.material3.FilterChip(
                selected = selectedChoice == "graduated",
                onClick = { onChoiceChange("graduated") },
                label = { Text("يتخرج 🎓", fontSize = 11.sp) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFE8F5E9)
                )
            )
            androidx.compose.material3.FilterChip(
                selected = selectedChoice == "withdrawn",
                onClick = { onChoiceChange("withdrawn") },
                label = { Text("ينسحب ❌", fontSize = 11.sp) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFFFF3E0)
                )
            )
            androidx.compose.material3.FilterChip(
                selected = selectedChoice == "dropped",
                onClick = { onChoiceChange("dropped") },
                label = { Text("انقطع 🚫", fontSize = 11.sp) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFFFEBEE)
                )
            )
        }

        if (selectedChoice == "promote") {
            TargetGroupDropdown(
                groups = groups.filter { it.id != currentGroupId },
                selectedGroupId = selectedTargetGroupId,
                onGroupSelected = onTargetGroupChange
            )
        }
    }
}

@Composable
fun TargetGroupDropdown(
    groups: List<com.example.data.Group>,
    selectedGroupId: Int?,
    onGroupSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedGroup = groups.find { it.id == selectedGroupId }

    Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Surface(
            onClick = { expanded = true },
            border = BorderStroke(1.dp, Color(0xFF4F46E5).copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFEEF2FF)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    selectedGroup?.name ?: "اضغط لاختيار المجموعة الجديدة",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4F46E5)
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF4F46E5),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            groups.forEach { group ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(group.name, fontSize = 12.sp) },
                    onClick = {
                        onGroupSelected(group.id)
                        expanded = false
                    }
                )
            }
        }
    }
}