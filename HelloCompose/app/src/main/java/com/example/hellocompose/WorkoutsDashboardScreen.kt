package com.example.hellocompose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

@Composable
fun WorkoutsDashboardScreen(navController: NavController) {
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    // for kcal calc fallback
    var weightKg by remember { mutableStateOf<Double?>(null) }

    // live data
    var planned by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var todayLogs by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var weekLogs by remember { mutableStateOf(listOf<Map<String, Any?>>()) }
    var loading by remember { mutableStateOf(true) }

    DisposableEffect(uid) {
        if (uid == null) {
            loading = false
            onDispose { }
        } else {
            val userRef = db.collection("users").document(uid)
            val today = todayEpochDay()

            val r1: ListenerRegistration = userRef.addSnapshotListener { snap, _ ->
                weightKg = (snap?.getDouble("currentWeightKg")
                    ?: snap?.getLong("currentWeightKg")?.toDouble()
                    ?: snap?.getLong("weightKg")?.toDouble())
            }

            val r2: ListenerRegistration = userRef.collection("workoutsToday")
                .whereEqualTo("dateEpochDay", today)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener { qs, _ ->
                    planned = qs?.documents?.map { it.data ?: emptyMap() } ?: emptyList()
                    loading = false
                }

            val r3: ListenerRegistration = userRef.collection("workoutLogs")
                .whereEqualTo("dateEpochDay", today)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener { qs, _ ->
                    todayLogs = qs?.documents?.map { it.data ?: emptyMap() } ?: emptyList()
                }

            val r4: ListenerRegistration = userRef.collection("workoutLogs")
                .whereGreaterThanOrEqualTo("dateEpochDay", today - 6)
                .orderBy("dateEpochDay", Query.Direction.DESCENDING)
                .addSnapshotListener { qs, _ ->
                    weekLogs = qs?.documents?.map { it.data ?: emptyMap() } ?: emptyList()
                }

            onDispose { r1.remove(); r2.remove(); r3.remove(); r4.remove() }
        }
    }

    val todayKcal = remember(todayLogs, weightKg) { sumWorkoutKcal(todayLogs, weightKg) }
    val weekKcal  = remember(weekLogs,  weightKg) { sumWorkoutKcal(weekLogs,  weightKg) }

    AppScaffold(
        navController = navController,
        currentRoute = NavRoutes.WORKOUTS,
        snackbarHost = { SnackbarHost(snack) },
        onAccountClick = { navController.navigate(NavRoutes.ACCOUNT.name) }
    ) { modifier ->
        Column(
            modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Workouts", style = MaterialTheme.typography.headlineSmall)

            // Today card
            Card(shape = MaterialTheme.shapes.extraLarge, elevation = CardDefaults.cardElevation(6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Today", style = MaterialTheme.typography.titleMedium)
                    if (loading) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Text("Calories burned: $todayKcal")
                        Text("This week: $weekKcal kcal")

                        if (planned.isEmpty()) {
                            Text(
                                "Planned for today: No workouts planned yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text("Planned for today:", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            // Planned list (mark completed here)
            if (planned.isNotEmpty()) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(planned) { row ->
                        val name = (row["name"] as? String) ?: "Workout"
                        val met = (row["met"] as? Number)?.toDouble() ?: 6.0
                        val duration = (row["durationMin"] as? Number)?.toInt() ?: 30

                        Card(shape = MaterialTheme.shapes.extraLarge, elevation = CardDefaults.cardElevation(3.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "MET: $met  •  Duration: $duration min",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        if (uid == null) return@Button
                                        scope.launch {
                                            try {
                                                // always store numeric kcal in workoutLogs
                                                val kcal = calcKcal(met, duration, weightKg)
                                                val today = todayEpochDay()
                                                val userRef = db.collection("users").document(uid)
                                                val plans   = userRef.collection("workoutsToday")
                                                val logs    = userRef.collection("workoutLogs")

                                                // remove one matching planned row
                                                plans.whereEqualTo("dateEpochDay", today)
                                                    .whereEqualTo("name", name)
                                                    .limit(1)
                                                    .get().await()
                                                    .documents.firstOrNull()?.reference?.delete()

                                                // append log WITH kcal stored
                                                logs.add(
                                                    mapOf(
                                                        "name" to name,
                                                        "met" to met,
                                                        "durationMin" to duration,
                                                        "kcal" to kcal, // <— numeric, fixes “0 kcal” issues
                                                        "dateEpochDay" to today,
                                                        "createdAt" to FieldValue.serverTimestamp()
                                                    )
                                                ).await()

                                                snack.showSnackbar("Logged ${kcal} kcal")
                                            } catch (e: Exception) {
                                                snack.showSnackbar("Failed: ${e.message}")
                                            }
                                        }
                                    }) { Text("Mark completed") }

                                    OutlinedButton(onClick = {
                                        if (uid == null) return@OutlinedButton
                                        scope.launch {
                                            try {
                                                val today = todayEpochDay()
                                                val plans = db.collection("users").document(uid)
                                                    .collection("workoutsToday")
                                                plans.whereEqualTo("dateEpochDay", today)
                                                    .whereEqualTo("name", name)
                                                    .limit(1)
                                                    .get().await()
                                                    .documents.firstOrNull()?.reference?.delete()
                                                snack.showSnackbar("Removed from today")
                                            } catch (e: Exception) {
                                                snack.showSnackbar("Failed: ${e.message}")
                                            }
                                        }
                                    }) { Text("Remove") }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { navController.navigate(NavRoutes.WORKOUTS_PICK.name) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("Choose workout") }
        }
    }
}

/* ---------- helpers ---------- */

private fun todayEpochDay(): Int = LocalDate.now(ZoneOffset.UTC).toEpochDay().toInt()

private fun calcKcal(met: Double, durationMin: Int, weightKg: Double?): Int {
    val w = weightKg ?: 70.0
    // ACSM: kcal = MET × 3.5 × weight(kg) / 200 × minutes
    val kcal = met * 3.5 * w / 200.0 * durationMin
    return kcal.roundToInt().coerceAtLeast(0)
}

private fun rowToKcal(row: Map<String, Any?>, weightKg: Double?): Int {
    val explicit = (row["kcal"] as? Number)?.toInt()
    if (explicit != null) return explicit
    val met = (row["met"] as? Number)?.toDouble() ?: return 0
    val dur = (row["durationMin"] as? Number)?.toInt() ?: return 0
    return calcKcal(met, dur, weightKg)
}

private fun sumWorkoutKcal(rows: List<Map<String, Any?>>, weightKg: Double?): Int {
    var total = 0
    for (r in rows) total += rowToKcal(r, weightKg)
    return total
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) cont.resume(task.result) {}
        else cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
    }
}
