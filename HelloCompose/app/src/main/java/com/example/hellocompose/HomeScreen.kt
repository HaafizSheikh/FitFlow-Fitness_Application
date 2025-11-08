package com.example.hellocompose

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun HomeScreen() {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val userId = auth.currentUser?.uid

    var age by remember { mutableStateOf<String?>(null) }
    var height by remember { mutableStateOf<String?>(null) }
    var weight by remember { mutableStateOf<String?>(null) }
    var goal by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        if (userId == null) {
            error = "Not signed in"
            loading = false
            return@LaunchedEffect
        }

        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                // Read numbers safely regardless of how Firestore stored them
                val ageNum = doc.getLong("age")?.toInt()
                val heightNum = doc.getLong("heightCm")?.toInt()
                val weightDbl = doc.getDouble("weightKg") ?: doc.getLong("weightKg")?.toDouble()
                val goalStr = doc.getString("goal")

                age = ageNum?.toString()
                height = heightNum?.toString()
                weight = weightDbl?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
                goal = goalStr

                loading = false
            }
            .addOnFailureListener {
                error = it.message ?: "Failed to load profile"
                loading = false
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            loading -> CircularProgressIndicator()
            error != null -> Text(text = "Error: $error")
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Your Profile", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))
                Text("Age: ${age ?: "-"}")
                Text("Height: ${height ?: "-"} cm")
                Text("Weight: ${weight ?: "-"} kg")
                Text("Goal: ${goal ?: "-"}")
            }
        }
    }
}
