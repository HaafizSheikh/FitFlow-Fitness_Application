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
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class WorkoutListItem(
    val name: String,
    val intensity: String,
    val durationMin: Int,
    val met: Double
)

@Composable
fun WorkoutsScreen(navController: NavController) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Catalog of workouts (edit freely)
    val workouts = remember {
        listOf(
            WorkoutListItem("Full Body Beginner", "Easy",   20, 4.5),
            WorkoutListItem("Push Day",           "Medium", 30, 6.0),
            WorkoutListItem("Pull Day",           "Medium", 30, 6.0),
            WorkoutListItem("Legs & Core",        "Hard",   35, 7.5),
            WorkoutListItem("HIIT Fat Burn",      "Hard",   18, 9.0),
        )
    }

    AppScaffold(
        navController = navController,
        currentRoute = NavRoutes.WORKOUTS_PICK,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        onAccountClick = { navController.navigate(NavRoutes.ACCOUNT.name) }
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Workouts", style = MaterialTheme.typography.headlineSmall)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(workouts) { item ->
                    WorkoutCard(
                        item = item,
                        onAddToToday = {
                            scope.launch {
                                addPlannedForToday(item, snackbarHostState)
                                // If you want to bounce back to the dashboard after adding:
                                // navController.navigate(NavRoutes.WORKOUTS.name)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutCard(
    item: WorkoutListItem,
    onAddToToday: () -> Unit
) {
    Card(shape = MaterialTheme.shapes.extraLarge, elevation = CardDefaults.cardElevation(6.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "Difficulty: ${item.intensity} • ${item.durationMin} min",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("MET: ${item.met}", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalButton(onClick = onAddToToday) { Text("Add to Today") }
            }
        }
    }
}

private suspend fun addPlannedForToday(
    item: WorkoutListItem,
    snackbar: SnackbarHostState
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    if (uid == null) {
        snackbar.showSnackbar("Not signed in")
        return
    }

    val db = FirebaseFirestore.getInstance()
    val todayEpoch = LocalDate.now(ZoneOffset.UTC).toEpochDay().toInt()

    val payload = hashMapOf(
        "name" to item.name,
        "intensity" to item.intensity,
        "durationMin" to item.durationMin,
        "met" to item.met,
        "dateEpochDay" to todayEpoch,
        "createdAt" to FieldValue.serverTimestamp()
    )

    try {
        // Avoid duplicate by name for today
        val existing = db.collection("users").document(uid)
            .collection("workoutsToday")
            .whereEqualTo("dateEpochDay", todayEpoch)
            .whereEqualTo("name", item.name)
            .limit(1)
            .get()
            .await()

        if (!existing.isEmpty) {
            snackbar.showSnackbar("Already added for today")
            return
        }

        db.collection("users").document(uid)
            .collection("workoutsToday")
            .add(payload)
            .await()

        snackbar.showSnackbar("Added to today")
    } catch (e: Exception) {
        snackbar.showSnackbar("Failed: ${e.message}")
    }
}

/* ------- tiny Task.await helper ------- */
private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(task.result)
            else cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
        }
    }
