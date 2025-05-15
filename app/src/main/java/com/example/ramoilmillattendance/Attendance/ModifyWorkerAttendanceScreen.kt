package com.example.ramoilmillattendance.Attendance

import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ramoilmillattendance.TopAppBar
import com.example.ramoilmillattendance.LoadingScreen
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class EditableAttendanceEntry(
    val date: String,
    val status: String,
    val isHalfDay: Boolean
)

@Composable
fun ModifyWorkerAttendanceScreen(navController: NavController, uid: String) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var attendanceList by remember { mutableStateOf<List<EditableAttendanceEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val doc = FirebaseFirestore.getInstance().collection("attendance")
                    .document(uid).get().await()
                val data = doc.get("attendance") as? Map<String, Map<String, Any>> ?: emptyMap()

                attendanceList = data.map { (date, value) ->
                    val status = value["status"] as? String ?: "Absent"
                    val isHalfDay = status == "Half Day"
                    EditableAttendanceEntry(
                        date = date,
                        status = if (status == "Present" || status == "Absent") status else "Half Day",
                        isHalfDay = isHalfDay
                    )
                }.sortedBy { it.date }


            } catch (e: Exception) {
                errorMessage = "Failed to load attendance"
            } finally {
                isLoading = false
            }
        }
    }

    fun saveAttendance() {
        scope.launch {
            try {
                val now = java.util.Date()
                val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                val currentTime = timeFormat.format(now)

                val updatedMap = attendanceList.associate {
                    it.date to mapOf(
                        "status" to when {
                            it.isHalfDay -> "Half Day"
                            it.status == "Present" -> "Present"
                            else -> "Absent"
                        },
                        "time" to currentTime
                    )
                }


                FirebaseFirestore.getInstance().collection("attendance")
                    .document(uid)
                    .set(mapOf("attendance" to updatedMap), SetOptions.merge())
                    .await()

                Toast.makeText(context, "Attendance updated!", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save changes", Toast.LENGTH_SHORT).show()
            }
        }
    }

    TopAppBar(
        navController = navController,
        context = context,
        drawerState = drawerState,
        scope = scope,
        screenTitle = "Edit Worker Attendance"
    ) {
        if (isLoading) {
            LoadingScreen(message = "")
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (errorMessage.isNotEmpty()) {
                        Text(errorMessage, color = Color.Red)
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(attendanceList) { entry ->
                                val current = attendanceList.find { it.date == entry.date }!!

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    elevation = CardDefaults.cardElevation(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(entry.date)

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Switch(
                                                checked = current.status == "Present",
                                                onCheckedChange = { isChecked ->
                                                    attendanceList = attendanceList.map {
                                                        if (it.date == current.date && !it.isHalfDay) {
                                                            it.copy(status = if (isChecked) "Present" else "Absent")
                                                        } else it
                                                    }
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = Color(0xFF0B0B45),
                                                    uncheckedThumbColor = Color.White,
                                                    uncheckedTrackColor = Color(0xFFCCCCCC)
                                                ),
                                                enabled = !current.isHalfDay
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Checkbox(
                                                checked = current.isHalfDay,
                                                onCheckedChange = { isChecked ->
                                                    attendanceList = attendanceList.map {
                                                        if (it.date == current.date) {
                                                            it.copy(
                                                                isHalfDay = isChecked,
                                                                status = if (isChecked) "Half Day"
                                                                else if (it.status == "Half Day") "Present" else it.status
                                                            )
                                                        } else it
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = Color(0xFF0B0B45),
                                                    uncheckedColor = Color.Gray
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { saveAttendance() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0B0B45),
                        contentColor = Color.White
                    )
                ) {
                    Text("Save Changes")
                }
            }
        }
    }
}
