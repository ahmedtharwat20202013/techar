package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.AppDatabase
import com.example.repository.TeacherRepository
import com.example.ui.*
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import com.example.data.PinStorage
import com.example.data.storage.ActivationStorage
import com.example.viewmodel.ActivationViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TeacherViewModel
import com.example.viewmodel.TeacherViewModelFactory
import timber.log.Timber
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set the global default JVM TimeZone to Egypt/Cairo
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Africa/Cairo"))
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("crash_reports", android.content.Context.MODE_PRIVATE)
        val lastCrash = prefs.getString("last_crash", null)

        if (lastCrash != null) {
            setContent {
                MyApplicationTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        CrashReportScreen(
                            stackTrace = lastCrash,
                            onClear = {
                                prefs.edit().remove("last_crash").apply()
                                recreate()
                            }
                        )
                    }
                }
            }
            return
        }

        // Catch uncaught exceptions on other threads or later in execution
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("CRASH_HANDLER", "CRASH DETECTED on thread ${thread.name}", throwable)
            prefs.edit().putString("last_crash", android.util.Log.getStackTraceString(throwable)).commit()
            oldHandler?.uncaughtException(thread, throwable)
        }

        try {
            // Plant Timber debug tree for logging
            if (Timber.forest().isEmpty()) {
                Timber.plant(Timber.DebugTree())
            }

            // 1. Initialize DB & Repository
            val database = AppDatabase.getDatabase(applicationContext)
            val repository = TeacherRepository(database.appDao(), database)

            // 2. Instantiate Main Shared ViewModel from Factory
            val factory = TeacherViewModelFactory(repository, application)
            val viewModel = ViewModelProvider(this, factory)[TeacherViewModel::class.java]

            setContent {
                MyApplicationTheme {
                    val activationStorage = remember { ActivationStorage(this@MainActivity) }
                    var isActivated by remember { mutableStateOf(activationStorage.isActivated()) }

                    androidx.compose.runtime.DisposableEffect(activationStorage) {
                        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                            if (key == null || key == ActivationStorage.KEY_IS_ACTIVATED) {
                                isActivated = activationStorage.isActivated()
                            }
                        }
                        activationStorage.registerListener(listener)
                        onDispose {
                            activationStorage.unregisterListener(listener)
                        }
                    }

                    val pinStorage = remember { PinStorage(this@MainActivity) }
                    var isAuthenticated by remember { mutableStateOf(pinStorage.isAuthenticated()) }
                    var pinEnabled by remember { mutableStateOf(pinStorage.isPinEnabled()) }
                    var hasPin by remember { mutableStateOf(pinStorage.hasPin()) }

                    androidx.compose.runtime.DisposableEffect(pinStorage) {
                        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                            when (key) {
                                "is_auth" -> isAuthenticated = pinStorage.isAuthenticated()
                                "pin_enabled" -> pinEnabled = pinStorage.isPinEnabled()
                                "user_pin" -> hasPin = pinStorage.hasPin()
                            }
                        }
                        pinStorage.registerListener(listener)
                        onDispose {
                            pinStorage.unregisterListener(listener)
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when {
                            !isActivated -> {
                                val activationViewModel = remember { ActivationViewModel() }
                                ActivationScreen(
                                    viewModel = activationViewModel,
                                    onActivationSuccess = {
                                        isActivated = true
                                    }
                                )
                            }

                            // Show PIN screen if enabled and not authenticated
                            pinEnabled && !isAuthenticated -> {
                                PinLockScreen(
                                    onAuthenticated = { isAuthenticated = true }
                                )
                            }

                            // First time setup - no PIN set yet
                            !hasPin && !isAuthenticated -> {
                                PinLockScreen(
                                    onAuthenticated = { isAuthenticated = true }
                                )
                            }

                            // Normal app
                            else -> {
                                if (!isAuthenticated) {
                                    pinStorage.setAuthenticated(true)
                                    isAuthenticated = true
                                }

                            val navController = rememberNavController()

                            // State to control global search sheet overlay
                            var showGlobalSearchOverlay by remember { mutableStateOf(false) }

                            val navBackStackEntry by navController.currentBackStackEntryAsState()
                            val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route

                            // Root Scaffold with Bottom Navigation
                            Scaffold(
                                modifier = Modifier.fillMaxSize(),
                                bottomBar = {
                                    // Only show bottom bar for main top-level destinations
                                    val showBottomBar = currentRoute == Screen.Dashboard.route ||
                                            currentRoute == Screen.Classes.route ||
                                            currentRoute == Screen.Students.route ||
                                            currentRoute == Screen.Payments.route ||
                                            currentRoute == Screen.ReportsBackup.route

                                    if (showBottomBar) {
                                        TeacherNavigationBar(
                                            currentRoute = currentRoute,
                                            onNavigate = { destination ->
                                                navController.navigate(destination) {
                                                    popUpTo(navController.graph.startDestinationId) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        )
                                    }
                                }
                            ) { innerPadding ->
                                NavHost(
                                    navController = navController,
                                    startDestination = Screen.Dashboard.route,
                                    modifier = Modifier.padding(innerPadding)
                                ) {
                                    // A. Dashboard Screen
                                    composable(Screen.Dashboard.route) {
                                        DashboardScreen(
                                            viewModel = viewModel,
                                            onTakeAttendance = { sessionId ->
                                                navController.navigate("attendance_sheet/$sessionId")
                                            },
                                            onNavigateToClasses = {
                                                navController.navigate(Screen.Classes.route)
                                            },
                                            onSearchClick = {
                                                showGlobalSearchOverlay = true
                                            },
                                            onNavigateToPayments = {
                                                navController.navigate(Screen.Payments.route)
                                            },
                                            onNavigateToExams = {
                                                navController.navigate(Screen.Exams.route)
                                            },
                                            onNavigateToGroup = { groupId ->
                                                navController.navigate(Screen.GroupDetail.createRoute(groupId))
                                            }
                                        )
                                    }

                                    // B. Classes/Groups List Screen
                                    composable(Screen.Classes.route) {
                                        ClassesScreen(
                                            viewModel = viewModel,
                                            onNavigateToGroup = { groupId ->
                                                navController.navigate(Screen.GroupDetail.createRoute(groupId))
                                            }
                                        )
                                    }

                                    // B2. All Students Screen (with fast search)
                                    composable(Screen.Students.route) {
                                        StudentsScreen(
                                            viewModel = viewModel,
                                            onNavigateToStudent = { studentId, groupId ->
                                                navController.navigate(Screen.StudentProfile.createRoute(studentId, groupId))
                                            }
                                        )
                                    }

                                    // C. Group Details / Session lists page
                                    composable(
                                        route = Screen.GroupDetail.route,
                                        arguments = listOf(navArgument("groupId") { type = NavType.IntType })
                                    ) { backStackEntry ->
                                        val groupId = backStackEntry.arguments?.getInt("groupId") ?: 0
                                        GroupDetailScreen(
                                            groupId = groupId,
                                            viewModel = viewModel,
                                            onBack = { navController.popBackStack() },
                                            onNavigateToStudent = { id ->
                                                if (id < 0) {
                                                    // Negative ID signals take attendance for session ID
                                                    val actualSessionId = -id
                                                    navController.navigate("attendance_sheet/$actualSessionId")
                                                } else {
                                                    navController.navigate(Screen.StudentProfile.createRoute(id, groupId))
                                                }
                                            }
                                        )
                                    }

                                    // D. Student Profile Card page
                                    composable(
                                        route = Screen.StudentProfile.route,
                                        arguments = listOf(
                                            navArgument("studentId") { type = NavType.IntType },
                                            navArgument("groupId") { type = NavType.IntType }
                                        )
                                    ) { backStackEntry ->
                                        val studentId = backStackEntry.arguments?.getInt("studentId") ?: 0
                                        val groupId = backStackEntry.arguments?.getInt("groupId") ?: 0
                                        StudentProfileScreen(
                                            studentId = studentId,
                                            groupId = groupId,
                                            viewModel = viewModel,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    // E. Attendance Sheets (session based)
                                    composable(
                                        route = "attendance_sheet/{sessionId}",
                                        arguments = listOf(navArgument("sessionId") { type = NavType.IntType })
                                    ) { backStackEntry ->
                                        val sessionId = backStackEntry.arguments?.getInt("sessionId") ?: 0
                                        AttendanceSheetScreen(
                                            sessionId = sessionId,
                                            viewModel = viewModel,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    // F. Payments filter and toggle screen
                                    composable(Screen.Payments.route) {
                                        PaymentsScreen(
                                            viewModel = viewModel,
                                            onNavigateToStudent = { studentId, groupId ->
                                                navController.navigate(Screen.StudentProfile.createRoute(studentId, groupId))
                                            },
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    // J. Exams Management Screen
                                    composable(Screen.Exams.route) {
                                        ExamsScreen(
                                            viewModel = viewModel,
                                            onBack = { navController.popBackStack() },
                                            onNavigateToStudent = { studentId, groupId ->
                                                navController.navigate(Screen.StudentProfile.createRoute(studentId, groupId))
                                            }
                                        )
                                    }

                                    // G. Backup Exporter / Restore database manager
                                    composable(Screen.ReportsBackup.route) {
                                        ReportsBackupScreen(
                                            viewModel = viewModel
                                        )
                                    }
                                }

                                // --- POPUP: INSTANT GLOBAL SEARCH OVERLAY SHEET ---
                                if (showGlobalSearchOverlay) {
                                    SearchSystemOverlay(
                                        viewModel = viewModel,
                                        onNavigateToStudent = { studentId, groupId ->
                                            navController.navigate(Screen.StudentProfile.createRoute(studentId, groupId))
                                        },
                                        onDismissRequest = {
                                            showGlobalSearchOverlay = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        } catch (e: Throwable) {
            android.util.Log.e("CRASH_HANDLER", "CRASH IN ONCREATE", e)
            prefs.edit().putString("last_crash", android.util.Log.getStackTraceString(e)).commit()
            setContent {
                MyApplicationTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        CrashReportScreen(
                            stackTrace = android.util.Log.getStackTraceString(e),
                            onClear = {
                                prefs.edit().remove("last_crash").apply()
                                recreate()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Lock app when backgrounded unless picking a file
        if (isPickingFile) {
            isPickingFile = false
            return
        }
        val pinStorage = PinStorage(this)
        if (pinStorage.isPinEnabled()) {
            pinStorage.setAuthenticated(false)
        }
    }

    companion object {
        var isPickingFile = false
    }
}

@Composable
fun CrashReportScreen(stackTrace: String, onClear: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF0F0))
            .padding(24.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFC62828),
                modifier = androidx.compose.ui.Modifier.size(64.dp)
            )
            Text(
                text = "تنبيه: حدث خطأ غير متوقع",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC62828)
            )
            Text(
                text = "لقد واجه التطبيق مشكلة أدت إلى إغلاقه. تفاصيل الخطأ:",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = Color.DarkGray
            )
            Surface(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2))
            ) {
                Text(
                    text = stackTrace,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = androidx.compose.ui.Modifier.padding(12.dp),
                    color = Color(0xFFB71C1C)
                )
            }
            Button(
                onClick = onClear,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
            ) {
                Text("مسح التقرير وإعادة التشغيل", color = Color.White)
            }
        }
    }
}
