package com.example.hellocompose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    onProfileSaved: (() -> Unit)? = null
) {
    var age by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    val goals = listOf("Lose", "Maintain", "Gain")
    var goal by remember { mutableStateOf(goals.first()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(24.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                elevation = CardDefaults.cardElevation(6.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Complete your profile", style = MaterialTheme.typography.headlineSmall)

                    OutlinedTextField(
                        value = age, onValueChange = { age = it.filter(Char::isDigit) },
                        label = { Text("Age") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = height, onValueChange = { height = it.filter(Char::isDigit) },
                        label = { Text("Height (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Weight (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            modifier = Modifier.menuAnchor(),
                            value = goal, onValueChange = {},
                            readOnly = true, label = { Text("Goal") }
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            goals.forEach {
                                DropdownMenuItem(text = { Text(it) }, onClick = {
                                    goal = it; expanded = false
                                })
                            }
                        }
                    }

                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)

                    Button(
                        onClick = {
                            error = null
                            if (age.isBlank() || height.isBlank() || weight.isBlank()) {
                                error = "Please fill all fields"; return@Button
                            }
                            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
                                error = "Not signed in"; return@Button
                            }
                            saving = true
                            val data = mapOf(
                                "age" to age.toInt(),
                                "heightCm" to height.toInt(),
                                "weightKg" to weight.toDouble(),
                                "goal" to goal
                            )
                            FirebaseFirestore.getInstance()
                                .collection("users").document(uid).set(data)
                                .addOnSuccessListener {
                                    saving = false
                                    // prefer callback if provided; else navigate to DASHBOARD
                                    onProfileSaved?.invoke() ?: navController.navigate(NavRoutes.DASHBOARD.name) {
                                        popUpTo(NavRoutes.LOGIN.name) { inclusive = true }
                                    }
                                }
                                .addOnFailureListener {
                                    saving = false
                                    error = it.message ?: "Failed to save"
                                }
                        },
                        enabled = !saving
                    ) { Text(if (saving) "Saving..." else "Save & Continue") }
                }
            }
        }
    }
}
