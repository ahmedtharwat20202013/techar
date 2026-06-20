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
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TeacherViewModel
import com.example.viewmodel.TeacherViewModelFactory
import timber.log.Timber

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Set the global default JVM TimeZone to Egypt/Cairo
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Africa/Cairo"))
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
