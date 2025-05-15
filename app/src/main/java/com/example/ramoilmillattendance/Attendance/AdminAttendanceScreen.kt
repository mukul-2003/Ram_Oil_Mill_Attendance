package com.example.ramoilmillattendance.Attendance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.ramoilmillattendance.TopAppBar

@Composable
fun AdminAttendanceScreen(navController: NavController) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    TopAppBar(
        navController = navController,
        context = context,
        drawerState = drawerState,
        scope = scope,
        screenTitle = "Attendance"
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate("viewAttendanceList")
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "View Attendance", style = MaterialTheme.typography.titleMedium)
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate("modifyAttendanceList")
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Modify Attendance", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
