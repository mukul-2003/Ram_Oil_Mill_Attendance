package com.example.ramoilmillattendance.Attendance

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ramoilmillattendance.TopAppBar
import com.example.ramoilmillattendance.LoadingScreen
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun ViewWorkerAttendanceScreen(navController: NavController, uid: String) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var attendanceData by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val doc = FirebaseFirestore.getInstance().collection("attendance")
                    .document(uid).get().await()
                val data = doc.get("attendance") as? Map<String, Map<String, Any>>
                attendanceData = data?.mapValues { entry ->
                    val status = entry.value["status"] as? String ?: "Absent"
                    val time = entry.value["time"] as? String ?: ""
                    "$status at $time".trim()
                } ?: emptyMap()

            } catch (e: Exception) {
                errorMessage = "Failed to load attendance"
            } finally {
                isLoading = false
            }
        }
    }

    TopAppBar(
        navController = navController,
        context = context,
        drawerState = drawerState,
        scope = scope,
        screenTitle = "Worker Attendance"
    ) {
        if (isLoading) {
            LoadingScreen(message = "")
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (errorMessage.isNotEmpty()) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                } else {
                    val presentCount = attendanceData.values.count { it.startsWith("Present") }
                    val absentCount = attendanceData.values.count { it.startsWith("Absent") }
                    val halfDayCount = attendanceData.values.count { it.startsWith("Half Day") }

                    AttendanceCountRow(
                        present = presentCount,
                        halfDay = halfDayCount,
                        absent = absentCount
                    )

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(attendanceData.entries.sortedBy { it.key }) { entry ->
                            val statusColor = when (entry.value) {
                                "Present" -> Color.Green
                                "Absent" -> Color.Red
                                "Half Day" -> Color(255, 235, 59, 255)
                                else -> Color.Gray
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 0.5.dp,
                                        color = Color(11, 69, 69),
                                        shape = RoundedCornerShape(6.dp)
                                    ),
                                shape = RoundedCornerShape(6.dp),
                                elevation = CardDefaults.cardElevation(4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = entry.key)
                                    Text(text = entry.value, color = statusColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceCountRow(present: Int, halfDay: Int, absent: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "P: ", fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "$present", color = Color.Green,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.padding(12.dp))
        Text(
            text = "H: ", fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "$halfDay", color = Color(255, 235, 59, 255),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.padding(12.dp))
        Text(
            text = "A: ", fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "$absent", color = Color.Red,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
