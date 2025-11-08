package com.example.hellocompose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun DashboardScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val uid = auth.currentUser?.uid
    val email = auth.currentUser?.email ?: ""

    // Show a nice name from email (or later from profile "name")
    val greetingName = remember(email) { email.substringBefore("@").ifBlank { "Athlete" } }

    // Progress snapshot (optional until we add the Progress page)
    var todayDone by remember { mutableStateOf(false) }
    var streak by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(uid) {
        if (uid == null) {
            loading = false
            return@LaunchedEffect
        }
        val todayEpoch = LocalDate.now(ZoneOffset.UTC).toEpochDay().toInt()
        db.collection("users").document(uid).collection("progress")
            .get()
            .addOnSuccessListener { snap ->
                val days = snap.documents.mapNotNull { it.getLong("dateEpochDay")?.toInt() }.toSet()
                todayDone = days.contains(todayEpoch)

                var s = 0
                var d = todayEpoch
                while (days.contains(d)) { s += 1; d -= 1 }
                streak = s
                loading = false
            }
            .addOnFailureListener { loading = false }
    }

    AppScaffold(
        navController = navController,
        currentRoute = NavRoutes.DASHBOARD,
        onAccountClick = { navController.navigate(NavRoutes.ACCOUNT.name) }
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Hi, $greetingName 👋",
                style = MaterialTheme.typography.headlineMedium
            )

            if (loading) {
                CircularProgressIndicator()
            } else {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = CardDefaults.cardElevation(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Today", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (todayDone) "✅ Workout logged" else "— Not logged yet",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Streak: $streak days", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            // Quick actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { navController.navigate(NavRoutes.WORKOUTS.name) }) {
                    Text("Choose workout")
                }
                Button(onClick = { navController.navigate(NavRoutes.MEALS.name) }) {
                    Text("Choose meal")
                }
                Button(onClick = { navController.navigate(NavRoutes.PROGRESS.name) }) {
                    Text("Log progress")
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "Use the left menu to switch between pages.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
