package com.example.hellocompose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(navController: NavController) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val uid = auth.currentUser?.uid
    val email = auth.currentUser?.email ?: "-"

    // Profile basics
    var age by remember { mutableStateOf<Int?>(null) }
    var heightCm by remember { mutableStateOf<Int?>(null) }
    var profileWeight by remember { mutableStateOf<Double?>(null) }

    // Latest metrics cached on users/{uid}
    var currentWeight by remember { mutableStateOf<Double?>(null) }
    var lastBmi by remember { mutableStateOf<Double?>(null) }
    var lastCalories by remember { mutableStateOf<Double?>(null) }

    // Fallback (latest logged weight directly from progress if cache missing)
    var latestWeightFromProgress by remember { mutableStateOf<Double?>(null) }

    // Preferences
    var notificationsEnabled by remember { mutableStateOf(false) }
    var units by remember { mutableStateOf("Metric") } // or "Imperial"

    var loading by remember { mutableStateOf(true) }

    // Live Firestore listeners with proper cleanup
    DisposableEffect(uid) {
        var userReg: ListenerRegistration? = null
        var progressReg: ListenerRegistration? = null

        if (uid != null) {
            val userRef = db.collection("users").document(uid)

            // Main user doc snapshot (includes cached currentWeightKg, lastBmi, lastCalorieTarget)
            userReg = userRef.addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    age = snap.getLong("age")?.toInt()
                    heightCm = snap.getLong("heightCm")?.toInt()

                    // legacy field if you ever stored weight on the profile
                    profileWeight = snap.getDouble("weightKg")
                        ?: snap.getLong("weightKg")?.toDouble()

                    // cached “last known” metrics written by ProgressScreen
                    currentWeight = snap.getDouble("currentWeightKg")
                        ?: snap.getLong("currentWeightKg")?.toDouble()

                    lastBmi = snap.getDouble("lastBmi")
                        ?: snap.getLong("lastBmi")?.toDouble()

                    lastCalories = snap.getDouble("lastCalorieTarget")
                        ?: snap.getLong("lastCalorieTarget")?.toDouble()

                    notificationsEnabled = snap.getBoolean("notificationsEnabled") ?: false
                    units = snap.getString("units") ?: "Metric"
                }
                loading = false
            }

            // Fallback to *latest* progress log by timestamp (supports multiple per day)
            progressReg = userRef.collection("progress")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .addSnapshotListener { qs, _ ->
                    val w = qs?.documents?.firstOrNull()?.let { d ->
                        d.getDouble("weightKg") ?: d.getLong("weightKg")?.toDouble()
                    }
                    latestWeightFromProgress = w
                }
        } else {
            loading = false
        }

        onDispose {
            userReg?.remove()
            progressReg?.remove()
        }
    }

    AppScaffold(
        navController = navController,
        currentRoute = NavRoutes.ACCOUNT,
        onAccountClick = { /* already here */ }
    ) { modifier ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Account",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            if (loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            // Personal Info
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Personal Info", style = MaterialTheme.typography.titleLarge)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = heightCm?.let { formatHeight(it, units) } ?: "--",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text("Height", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column {
                            val showWeight = currentWeight
                                ?: latestWeightFromProgress
                                ?: profileWeight
                            Text(
                                text = showWeight?.let { formatWeight(it, units) } ?: "--",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text("Weight", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column {
                            Text(
                                text = age?.toString() ?: "--",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text("Age", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Latest snapshot of metrics (kept in users/{uid} by ProgressScreen)
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Latest Metrics", style = MaterialTheme.typography.titleLarge)
                    Text("BMI: ${lastBmi?.let { round1(it) } ?: "--"}")
                    Text("Calories target: ${lastCalories?.toInt() ?: "--"}")
                }
            }

            // Preferences
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Preferences", style = MaterialTheme.typography.titleLarge)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Notifications")
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { checked ->
                                notificationsEnabled = checked
                                uid?.let {
                                    db.collection("users").document(it)
                                        .update("notificationsEnabled", checked)
                                }
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Units")
                        UnitsSegment(
                            selected = units,
                            onSelected = { sel ->
                                units = sel
                                uid?.let {
                                    db.collection("users").document(it)
                                        .update("units", sel)
                                }
                            }
                        )
                    }
                }
            }

            // Account email
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Account", style = MaterialTheme.typography.titleLarge)
                    Text(email, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(NavRoutes.LOGIN.name) {
                        popUpTo(NavRoutes.LOGIN.name) { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Log out") }
        }
    }
}

/* ---- UI helpers ---- */

@Composable
private fun UnitsSegment(selected: String, onSelected: (String) -> Unit) {
    val options = listOf("Metric", "Imperial")
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = (label == selected),
                onClick = { onSelected(label) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size)
            ) { Text(label) }
        }
    }
}

private fun formatWeight(kg: Double, units: String): String =
    if (units == "Imperial") {
        val lbs = kg * 2.20462
        "${round1(lbs)} lb"
    } else {
        "${round1(kg)} kg"
    }

private fun formatHeight(cm: Int, units: String): String =
    if (units == "Imperial") {
        val inchesTotal = cm / 2.54
        val feet = (inchesTotal / 12).toInt()
        val inches = (inchesTotal - feet * 12).toInt()
        "${feet}′ ${inches}″"
    } else {
        "$cm cm"
    }

private fun round1(x: Double) = kotlin.math.round(x * 10) / 10.0
