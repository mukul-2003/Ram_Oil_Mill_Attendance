package com.example.ramoilmillattendance.Attendance

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ramoilmillattendance.TopAppBar
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun DownloadAttendanceReportScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var selectedMode by remember { mutableStateOf("Month") }

    var selectedMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1) }
    var selectedYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }

    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }

    var isGenerating by remember { mutableStateOf(false) }

    TopAppBar(
        navController = navController,
        context = context,
        drawerState = drawerState,
        scope = scope,
        screenTitle = "Download Report"
    ) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) {

            Text("Filter Mode", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.padding(vertical = 8.dp)) {
                RadioButton(selected = selectedMode == "Month", onClick = { selectedMode = "Month" })
                Text("Month")
                Spacer(Modifier.width(16.dp))
                RadioButton(selected = selectedMode == "Range", onClick = { selectedMode = "Range" })
                Text("Date Range")
            }

            if (selectedMode == "Month") {
                OutlinedTextField(
                    value = "$selectedMonth",
                    onValueChange = { selectedMonth = it.toIntOrNull() ?: selectedMonth },
                    label = { Text("Month (1-12)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = "$selectedYear",
                    onValueChange = { selectedYear = it.toIntOrNull() ?: selectedYear },
                    label = { Text("Year") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            } else {
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text("Start Date (yyyy-MM-dd)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text("End Date (yyyy-MM-dd)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        isGenerating = true
                        generateReportWithCalendar(context, selectedMode, selectedMonth, selectedYear, startDate, endDate)
                        isGenerating = false
                    }
                },
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth(),
                enabled = !isGenerating
            ) {
                Text(if (isGenerating) "Generating..." else "Generate & Download CSV")
            }
        }
    }
}

suspend fun generateReportWithCalendar(
    context: Context,
    mode: String,
    month: Int,
    year: Int,
    start: String,
    end: String
) {
    val db = FirebaseFirestore.getInstance()
    val userSnapshot = db.collection("users").whereEqualTo("role", "worker").get().await()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calendar = Calendar.getInstance()

    val dateList = mutableListOf<String>()

    try {
        if (mode == "Month") {
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month - 1)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            for (day in 1..daysInMonth) {
                calendar.set(Calendar.DAY_OF_MONTH, day)
                dateList.add(sdf.format(calendar.time))
            }
        } else {
            val startDate = sdf.parse(start)
            val endDate = sdf.parse(end)
            if (startDate != null && endDate != null) {
                calendar.time = startDate
                while (!calendar.time.after(endDate)) {
                    dateList.add(sdf.format(calendar.time))
                    calendar.add(Calendar.DATE, 1)
                }
            } else {
                Toast.makeText(context, "Invalid date input", Toast.LENGTH_SHORT).show()
                return
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Invalid date input", Toast.LENGTH_SHORT).show()
        return
    }

    val attendanceMap = mutableMapOf<String, MutableMap<String, String>>()

    for (userDoc in userSnapshot.documents) {
        val uid = userDoc.id
        val name = userDoc.getString("name") ?: "Unnamed"
        val attendanceDoc = db.collection("attendance").document(uid).get().await()
        val attendanceData = attendanceDoc.get("attendance") as? Map<String, String> ?: emptyMap()

        val dailyStatus = mutableMapOf<String, String>()
        for (date in dateList) {
            if (attendanceData.containsKey(date)) {
                dailyStatus[date] = attendanceData[date] ?: ""
            } else {
                dailyStatus[date] = ""
            }
        }
        attendanceMap[name] = dailyStatus
    }

    val header = listOf("Name") + dateList
    val rows = mutableListOf(header.joinToString(","))

    for ((name, dailyMap) in attendanceMap) {
        val row = listOf(name) + dateList.map { dailyMap[it] ?: "" }
        rows.add(row.joinToString(","))
    }

    val fileName = "Attendance_Report.csv"
    val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
    file.writeText(rows.joinToString("\n"))

    Toast.makeText(context, "Report saved in Downloads/$fileName", Toast.LENGTH_LONG).show()
}
