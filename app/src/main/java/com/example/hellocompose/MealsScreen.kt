package com.example.hellocompose

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

data class MealItem(
    val name: String,
    val kcal: Int,
    val protein: Int, // g
    val carbs: Int,   // g
    val fat: Int      // g
)

@Composable
fun MealsScreen(navController: NavController) {
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    val meals = remember {
        listOf(
            MealItem("Oats & Banana",              350, 12, 60,  7),
            MealItem("Grilled Chicken & Rice",     520, 42, 60, 12),
            MealItem("Paneer Wrap",                480, 24, 45, 22),
            MealItem("Greek Yogurt & Nuts",        280, 18, 15, 16),
            MealItem("Salmon & Quinoa",            560, 40, 45, 20),
            MealItem("Veg Khichdi + Curd",         420, 16, 68, 10),
        )
    }

    AppScaffold(
        navController = navController,
        currentRoute = NavRoutes.MEALS_PICK,
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
            Text(
                text = "Tap \"Add to Today\" to plan your meals. Mark as eaten from the Meals page.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(meals) { item ->
                    Card(shape = MaterialTheme.shapes.extraLarge, elevation = CardDefaults.cardElevation(6.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Calories: ${item.kcal}  •  P:${item.protein}g  C:${item.carbs}g  F:${item.fat}g",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(onClick = {
                                    if (uid == null) return@Button
                                    scope.launch {
                                        try {
                                            val today = todayEpochDay()
                                            val doc = mapOf(
                                                "name" to item.name,
                                                "kcal" to item.kcal,
                                                "protein" to item.protein,
                                                "carbs" to item.carbs,
                                                "fat" to item.fat,
                                                "dateEpochDay" to today,
                                                "createdAt" to FieldValue.serverTimestamp()
                                            )

                                            db.collection("users").document(uid)
                                                .collection("mealPlansToday")
                                                .add(doc)
                                                .await()

                                            snackbarHost.showSnackbar("Added to today")
                                        } catch (e: Exception) {
                                            snackbarHost.showSnackbar(
                                                "Add failed: ${e.message ?: "permission or network error"}"
                                            )
                                        }
                                    }
                                }) { Text("Add to Today") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* --------- helpers (kept private to avoid duplicate symbols) --------- */

private fun todayEpochDay(): Int =
    LocalDate.now(ZoneOffset.UTC).toEpochDay().toInt()

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(task.result)
            else cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
        }
    }
