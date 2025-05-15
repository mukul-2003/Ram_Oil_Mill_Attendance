package com.example.ramoilmillattendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.faceattendanceapp.LoginScreen
import com.example.ramoilmillattendance.Attendance.AdminAttendanceScreen
import com.example.ramoilmillattendance.Attendance.DownloadAttendanceReportScreen
import com.example.ramoilmillattendance.Attendance.ModifyWorkerAttendanceScreen
import com.example.ramoilmillattendance.Attendance.ModifyWorkerListScreen
import com.example.ramoilmillattendance.Attendance.ViewWorkerAttendanceScreen
import com.example.ramoilmillattendance.Attendance.ViewWorkerListScreen
import com.example.ramoilmillattendance.FacesScanning.AdminHomeScreen
import com.example.ramoilmillattendance.FacesScanning.RegisterFaceCaptureScreen
import com.example.ramoilmillattendance.FacesScanning.RegisterFaceScreen
import com.example.ramoilmillattendance.loginsystem.AdminCameraScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AttendanceApp()
        }
    }
}

@Composable
fun NetworkErrorScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoadingScreen()
    }
}

@Composable
fun AttendanceApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val networkState = remember { NetworkState(context) }
    DisposableEffect(Unit) {
        networkState.startMonitoring()
        onDispose { networkState.stopMonitoring() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        if (networkState.isConnected.value) {
            NavHost(navController = navController, startDestination = "login") {
                composable("login") {
                    LoginScreen(navController)
                }
                composable("adminHome") {
                    AdminHomeScreen(navController) // Manager Home
                }
                composable("faceScanner") {
                    AdminCameraScreen(navController)
                }
                composable("registerFace") {
                    RegisterFaceScreen(navController)
                }
                composable(
                    route = "registerFaceCapture/{uid}",
                    arguments = listOf(navArgument("uid") { type = NavType.StringType })
                ) { backStackEntry ->
                    val uid = backStackEntry.arguments?.getString("uid") ?: ""
                    RegisterFaceCaptureScreen(navController, uid)
                }
                composable("attendance") {
                    AdminAttendanceScreen(navController)
                }
                composable("viewAttendanceList") {
                    ViewWorkerListScreen(navController)
                }
                composable("modifyAttendanceList") {
                    ModifyWorkerListScreen(navController)
                }
                composable(
                    route = "viewWorkerAttendance/{uid}",
                    arguments = listOf(navArgument("uid") { type = NavType.StringType })
                ) { backStackEntry ->
                    val uid = backStackEntry.arguments?.getString("uid") ?: ""
                    ViewWorkerAttendanceScreen(navController, uid)
                }
                composable(
                    route = "modifyWorkerAttendance/{uid}",
                    arguments = listOf(navArgument("uid") { type = NavType.StringType })
                ) { backStackEntry ->
                    val uid = backStackEntry.arguments?.getString("uid") ?: ""
                    ModifyWorkerAttendanceScreen(navController, uid)
                }
                composable("downloadReport") {
                    DownloadAttendanceReportScreen(navController)
                }

            }
        } else {
            NetworkErrorScreen(onRetry = {
                networkState.startMonitoring()
            })
        }
    }
}
