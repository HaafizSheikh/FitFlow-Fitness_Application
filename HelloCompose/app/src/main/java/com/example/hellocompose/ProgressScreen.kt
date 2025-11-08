package com.example.hellocompose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.round

@Composable
fun ProgressScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val uid = auth.currentUser?.uid ?: return

    val todayEpoch = remember { LocalDate.now(ZoneOffset.UTC).toEpochDay().toInt() }

    // profile basics
    var heightCm by remember { mutableStateOf<Int?>(null) }
    var goal by remember { mutableStateOf("Maintain") } // Lose | Maintain | Gain

    // form
    var weightInput by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // history for chart
    var history by remember { mutableStateOf<List<ProgressPoint>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    // fetch profile (once)
    LaunchedEffect(uid) {
        db.collection("users").document(uid)
            .get()
            .addOnSuccessListener { d ->
                heightCm = d.getLong("heightCm")?.toInt()
                goal = d.getString("goal") ?: "Maintain"
            }
    }

    // fetch latest 60 logs (multi per day supported)
    LaunchedEffect(uid) {
        db.collection("users").document(uid)
            .collection("progress")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(60)
            .get()
            .addOnSuccessListener { snap ->
                val items = snap.documents.mapNotNull { doc ->
                    val ts = doc.getLong("timestamp") ?: return@mapNotNull null
                    val w = numFrom(doc.get("weightKg"))
                    val b = numFrom(doc.get("bmi"))
                    ProgressPoint(ts, w, b)
                }.sortedBy { it.timestamp }
                history = items
                loading = false
            }
            .addOnFailureListener { loading = false }
    }

    val weightKg = weightInput.toDoubleOrNull()
    val bmi = if (weightKg != null && heightCm != null) bmiFrom(weightKg, heightCm!!) else null
    val calories = if (weightKg != null) {
        val bmr = weightKg * 22.0 * 1.4
        val adj = when (goal.lowercase()) { "lose" -> -400.0; "gain" -> 300.0; else -> 0.0 }
        round1(bmr + adj)
    } else null

    AppScaffold(
        navController = navController,
        currentRoute = NavRoutes.PROGRESS,
        onAccountClick = { navController.navigate(NavRoutes.ACCOUNT.name) }
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Progress", style = MaterialTheme.typography.headlineMedium)
            Text("Today: ${LocalDate.now(ZoneOffset.UTC)}")

            if (heightCm == null) {
                // If AssistChip import causes issues on older Material3, replace with OutlinedButton
                AssistChip(
                    onClick = { navController.navigate(NavRoutes.ACCOUNT.name) },
                    label = { Text("Add your height in Account to enable BMI") }
                )
            }

            OutlinedTextField(
                value = weightInput,
                onValueChange = { t -> weightInput = t.filter { it.isDigit() || it == '.' } },
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("BMI: ${bmi?.let { round1(it) } ?: "--"}")
                Text("Calories target: ${calories?.toInt() ?: "--"}")
            }

            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)

            Button(
                onClick = {
                    if (weightKg == null) { error = "Enter a valid weight"; return@Button }

                    val safeGoal = goal.ifBlank { "Maintain" }
                    val h = heightCm
                    val nowMs = System.currentTimeMillis()

                    val bmiNow = if (h != null) bmiFrom(weightKg, h) else null
                    val caloriesTarget = run {
                        val bmr = weightKg * 22.0 * 1.4
                        val adj = when (safeGoal.lowercase()) { "lose" -> -400.0; "gain" -> 300.0; else -> 0.0 }
                        round1(bmr + adj)
                    }

                    saving = true; error = null

                    val log = buildMap<String, Any> {
                        put("timestamp", nowMs)
                        put("dateEpochDay", todayEpoch)
                        put("weightKg", weightKg)
                        bmiNow?.let { put("bmi", round1(it)) }
                        put("calorieTarget", caloriesTarget)
                        put("updatedAt", nowMs)
                    }

                    val userRef = db.collection("users").document(uid)

                    // 1) append a log
                    userRef.collection("progress").add(log)
                        .addOnSuccessListener {
                            history = (history + ProgressPoint(nowMs, weightKg, bmiNow)).sortedBy { it.timestamp }
                            weightInput = ""

                            // 2) cache latest for other pages (Account, Meals, Workouts)
                            val cache = mapOf(
                                "currentWeightKg" to weightKg,
                                "lastBmi" to (bmiNow?.let { round1(it) }),
                                "calorieTarget" to caloriesTarget,   // <- consistent key
                                "goal" to safeGoal,
                                "weightUpdatedAt" to nowMs
                            )
                            userRef.set(cache, SetOptions.merge())
                            saving = false
                        }
                        .addOnFailureListener {
                            saving = false
                            error = it.message ?: "Failed to save"
                        }
                },
                enabled = !saving
            ) { Text(if (saving) "Saving..." else "Save") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("Your trend (last 60 logs)", style = MaterialTheme.typography.titleMedium)

            when {
                loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                history.isEmpty() -> Text("No logs yet. Add your first weight above.")
                else -> {
                    ProgressChartIndexed(
                        points = history,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .padding(end = 8.dp)
                    )
                    Text("Weight: blue • BMI: green", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/* ---------------- Data + helpers ---------------- */

data class ProgressPoint(
    val timestamp: Long,          // ms since epoch
    val weightKg: Double?,
    val bmi: Double?
)

private fun bmiFrom(weightKg: Double, heightCm: Int): Double {
    val m = heightCm / 100.0
    return weightKg / (m * m)
}

private fun round1(x: Double) = round(x * 10) / 10.0

private fun numFrom(any: Any?): Double? = when (any) {
    is Number -> any.toDouble()
    is String -> any.toDoubleOrNull()
    else -> null
}

/* ---------------- Chart ---------------- */

@Composable
private fun ProgressChartIndexed(points: List<ProgressPoint>, modifier: Modifier = Modifier) {
    val wSeries = points.mapIndexedNotNull { idx, p -> p.weightKg?.let { idx.toFloat() to it } }
    val bSeries = points.mapIndexedNotNull { idx, p -> p.bmi?.let { idx.toFloat() to it } }

    if (wSeries.isEmpty() && bSeries.isEmpty()) {
        Text("Not enough data to draw chart")
        return
    }

    Canvas(modifier) {
        val pad = 24f
        val left = pad
        val right = size.width - pad
        val top = pad
        val bottom = size.height - pad

        val maxIndex = ((wSeries.maxOfOrNull { it.first } ?: 0f)
            .coerceAtLeast(bSeries.maxOfOrNull { it.first } ?: 0f))
            .coerceAtLeast(1f)

        fun xToPx(i: Float): Float = left + (i / maxIndex) * (right - left)

        fun drawSeries(series: List<Pair<Float, Double>>, color: Color) {
            if (series.isEmpty()) return
            val ys = series.map { it.second }
            val minY = ys.minOrNull() ?: 0.0
            val maxY = ys.maxOrNull() ?: 1.0
            val yPad = (maxY - minY).coerceAtLeast(0.1) * 0.15

            fun yToPx(y: Double): Float {
                val min = minY - yPad
                val max = maxY + yPad
                val t = ((y - min) / (max - min)).toFloat()
                return bottom - t * (bottom - top)
            }

            var prev: Offset? = null
            for ((ix, iy) in series) {
                val p = Offset(xToPx(ix), yToPx(iy))
                prev?.let { p0 -> drawLine(color, p0, p, strokeWidth = 5f) }
                prev = p
                drawCircle(color, radius = 7f, center = p)
            }
        }

        // axes
        drawLine(Color.LightGray, Offset(left, bottom), Offset(right, bottom), 2f)
        drawLine(Color.LightGray, Offset(left, bottom), Offset(left, top), 2f)

        // weight (blue) + BMI (green)
        drawSeries(wSeries, Color(0xFF1976D2))
        drawSeries(bSeries, Color(0xFF2E7D32))
    }
}
