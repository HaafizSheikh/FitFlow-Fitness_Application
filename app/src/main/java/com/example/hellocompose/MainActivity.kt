package com.example.hellocompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.hellocompose.ui.theme.HelloComposeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HelloComposeTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = NavRoutes.LOGIN.name
                ) {
                    // LOGIN
                    composable(NavRoutes.LOGIN.name) {
                        LoginScreen(
                            onSignupClick = {
                                navController.navigate(NavRoutes.SIGNUP.name)
                            },
                            onLoginSuccess = {
                                navController.navigate(NavRoutes.DASHBOARD.name) {
                                    popUpTo(NavRoutes.LOGIN.name) { inclusive = true }
                                }
                            }
                        )
                    }

                    // SIGNUP
                    composable(NavRoutes.SIGNUP.name) {
                        SignupScreen(
                            onLoginClick = {
                                navController.navigate(NavRoutes.LOGIN.name)
                            },
                            onSignupSuccess = {
                                navController.navigate(NavRoutes.PROFILE.name) {
                                    popUpTo(NavRoutes.LOGIN.name) { inclusive = true }
                                }
                            }
                        )
                    }

                    // PROFILE (edit details once after sign up, or when user chooses to edit)
                    composable(NavRoutes.PROFILE.name) {
                        // onProfileSaved is optional in our ProfileScreen; pass it so we jump to Dashboard
                        ProfileScreen(
                            navController = navController,
                            onProfileSaved = {
                                navController.navigate(NavRoutes.DASHBOARD.name) {
                                    popUpTo(NavRoutes.LOGIN.name) { inclusive = true }
                                }
                            }
                        )
                    }

                    // DASHBOARD (home after auth)
                    composable(NavRoutes.DASHBOARD.name) {
                        DashboardScreen(navController)
                    }
                    composable(NavRoutes.PROGRESS.name) {
                        ProgressScreen(navController)
                    }
                    // Workouts DASHBOARD (main page)
                    composable(NavRoutes.WORKOUTS.name) {
                        WorkoutsDashboardScreen(navController)
                    }

// Workouts PICKER (your existing list screen)
                    composable(NavRoutes.WORKOUTS_PICK.name) {
                        WorkoutsScreen(navController)   // this is your existing picker file
                    }

                    composable(NavRoutes.MEALS.name) {
                        MealsDashboardScreen(navController)
                    }
                    composable(NavRoutes.MEALS_PICK.name) {
                        MealsScreen(navController)
                    }

                    composable(NavRoutes.COMMUNITY.name) { CommunityScreen(navController) }

                    // ACCOUNT → our new AccountScreen (read-only info, prefs, logout)
                    composable(NavRoutes.ACCOUNT.name) {
                        AccountScreen(navController)
                    }
                }
            }
        }
    }
}

@Composable
fun TextScreen(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}
