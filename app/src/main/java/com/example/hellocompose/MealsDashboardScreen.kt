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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.coroutines.resumeWithException

@Composable
fun MealsDashboardScreen(navController: NavController) {
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var target by remember { mutableStateOf<Int?>(null) }

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

            // User snapshot: read latest calorieTarget (if you write it elsewhere)
            val reg1: ListenerRegistration = userRef.addSnapshotListener { snap, _ ->
                target = snap?.getLong("calorieTarget")?.toInt()
            }

            // PLANNED FOR TODAY — remove orderBy to avoid composite index requirement
            val reg2: ListenerRegistration = userRef.collection("mealPlansToday")
                .whereEqualTo("dateEpochDay", todayEpochDay())
                .addSnapshotListener { qs, e ->
                    if (e != null) {
                        scope.launch { snackbarHost.showSnackbar("Meals listener error: ${e.message}") }
                        loading = false
                        return@addSnapshotListener
                    }
                    planned = qs?.documents?.map { it.data ?: emptyMap() } ?: emptyList()
                    loading = false
                }

            // LOGS FOR TODAY — also defensive
            val reg3: ListenerRegistration = userRef.collection("mealLogs")
                .whereEqualTo("dateEpochDay", todayEpochDay())
                .addSnapshotListener { qs, e ->
                    if (e != null) {
                        scope.launch { snackbarHost.showSnackbar("Logs listener error: ${e.message}") }
                        return@addSnapshotListener
                    }
                    todayLogs = qs?.documents?.map { it.data ?: emptyMap() } ?: emptyList()
                }

            // LAST 7 DAYS (including today) — keep an order only on the same field we filter
            val reg4: ListenerRegistration = userRef.collection("mealLogs")
                .whereGreaterThanOrEqualTo("dateEpochDay", todayEpochDay() - 6)
                .orderBy("dateEpochDay", Query.Direction.DESCENDING)
                .addSnapshotListener { qs, e ->
                    if (e != null) {
                        scope.launch { snackbarHost.showSnackbar("Week listener error: ${e.message}") }
                        return@addSnapshotListener
                    }
                    weekLogs = qs?.documents?.map { it.data ?: emptyMap() } ?: emptyList()
                }

            onDispose {
                reg1.remove(); reg2.remove(); reg3.remove(); reg4.remove()
            }
        }
    }

    val plannedTotals = remember(planned) { sumMacros(planned) }
    val todayTotals = remember(todayLogs) { sumMacros(todayLogs) }
    val weekTotals  = remember(weekLogs)  { sumMacros(weekLogs) }

    AppScaffold(
        navController = navController,
        currentRoute = NavRoutes.MEALS,
        snackbarHost = { SnackbarHost(snackbarHost) },
        onAccountClick = { navController.navigate(NavRoutes.ACCOUNT.name) }
    ) { modifier ->

        Column(
            modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Meals", style = MaterialTheme.typography.headlineSmall)

            // TODAY CARD
            Card(shape = MaterialTheme.shapes.extraLarge, elevation = CardDefaults.cardElevation(6.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Today", style = MaterialTheme.typography.titleMedium)

                    if (loading) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Text(
                            "Planned • Kcal: ${plannedTotals.kcal}  •  P:${plannedTotals.protein} C:${plannedTotals.carbs} F:${plannedTotals.fat}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            "Eaten   • Kcal: ${todayTotals.kcal}  •  P:${todayTotals.protein} C:${todayTotals.carbs} F:${todayTotals.fat}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        target?.let { tgt ->
                            val diff = tgt - todayTotals.kcal
                            val verdict = when {
                                diff > 80  -> "Under target by ${diff} kcal"
                                diff < -80 -> "Over target by ${-diff} kcal"
                                else       -> "On target"
                            }
                            AssistChip(onClick = { }, label = { Text("Target: $tgt • $verdict") })
                        }

                        if (planned.isEmpty()) {
                            Text(
                                "Planned for today: none. Add meals first.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text("Planned for today:", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            // LIST OF PLANNED MEALS
            if (planned.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(planned) { row ->
                        val name = (row["name"] as? String) ?: "Meal"
                        val kcal = (row["kcal"] as? Number)?.toInt() ?: 0
                        val protein = (row["protein"] as? Number)?.toInt() ?: 0
                        val carbs = (row["carbs"] as? Number)?.toInt() ?: 0
                        val fat = (row["fat"] as? Number)?.toInt() ?: 0

                        Card(shape = MaterialTheme.shapes.extraLarge, elevation = CardDefaults.cardElevation(3.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Calories: $kcal  •  P:${protein}g  C:${carbs}g  F:${fat}g",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        val myUid = uid ?: return@Button
                                        scope.launch {
                                            try {
                                                val today = todayEpochDay()
                                                val colPlans = db.collection("users").document(myUid).collection("mealPlansToday")
                                                val colLogs  = db.collection("users").document(myUid).collection("mealLogs")

                                                // Remove ONE matching planned doc (first found)
                                                val snap = colPlans
                                                    .whereEqualTo("dateEpochDay", today)
                                                    .whereEqualTo("name", name)
                                                    .limit(1)
                                                    .get().await()
                                                snap.documents.firstOrNull()?.reference?.delete()

                                                // Add to logs
                                                val log = mapOf(
                                                    "name" to name,
                                                    "kcal" to kcal,
                                                    "protein" to protein,
                                                    "carbs" to carbs,
                                                    "fat" to fat,
                                                    "dateEpochDay" to today,
                                                    "createdAt" to System.currentTimeMillis()
                                                )
                                                colLogs.add(log).await()

                                                snackbarHost.showSnackbar("Marked eaten")
                                            } catch (e: Exception) {
                                                snackbarHost.showSnackbar("Mark eaten failed: ${e.message}")
                                            }
                                        }
                                    }) { Text("Mark eaten") }

                                    OutlinedButton(onClick = {
                                        val myUid = uid ?: return@OutlinedButton
                                        scope.launch {
                                            try {
                                                val today = todayEpochDay()
                                                val colPlans = db.collection("users").document(myUid).collection("mealPlansToday")
                                                val snap = colPlans
                                                    .whereEqualTo("dateEpochDay", today)
                                                    .whereEqualTo("name", name)
                                                    .limit(1)
                                                    .get().await()
                                                snap.documents.firstOrNull()?.reference?.delete()
                                                snackbarHost.showSnackbar("Removed from today")
                                            } catch (e: Exception) {
                                                snackbarHost.showSnackbar("Remove failed: ${e.message}")
                                            }
                                        }
                                    }) { Text("Remove") }
                                }
                            }
                        }
                    }
                }
            }

            // FOOTER ACTION
            Button(
                onClick = { navController.navigate(NavRoutes.MEALS_PICK.name) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("Choose meal") }

            // WEEK SUMMARY
            if (weekLogs.isNotEmpty()) {
                val w = weekTotals
                Text(
                    "This week eaten • Kcal: ${w.kcal}  •  P:${w.protein}g  C:${w.carbs}g  F:${w.fat}g",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ---------------- helpers ---------------- */

private data class MacroSum(val kcal: Int, val protein: Int, val carbs: Int, val fat: Int)
private fun sumMacros(rows: List<Map<String, Any?>>): MacroSum {
    var kcal = 0; var p = 0; var c = 0; var f = 0
    for (r in rows) {
        kcal += (r["kcal"] as? Number)?.toInt() ?: 0
        p    += (r["protein"] as? Number)?.toInt() ?: 0
        c    += (r["carbs"] as? Number)?.toInt() ?: 0
        f    += (r["fat"] as? Number)?.toInt() ?: 0
    }
    return MacroSum(kcal, p, c, f)
}

private fun todayEpochDay(): Int =
    LocalDate.now(ZoneOffset.UTC).toEpochDay().toInt()

// tiny await
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(task.result, onCancellation = {})
            else cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
        }
    }
