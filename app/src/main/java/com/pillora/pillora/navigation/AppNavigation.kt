package com.pillora.pillora.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pillora.pillora.screens.AuthScreen
import com.pillora.pillora.screens.HomeScreen
import com.pillora.pillora.screens.MedicineFormScreen
import com.pillora.pillora.screens.MedicineListScreen
import com.pillora.pillora.screens.SplashScreen
import com.pillora.pillora.screens.TermsScreen
import com.pillora.pillora.repository.AuthRepository

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("pillora_prefs", Context.MODE_PRIVATE)
    }
    val acceptedTerms = remember {
        prefs.getBoolean("accepted_terms", false)
    }

    // Start with splash screen, which will handle navigation logic
    NavHost(navController = navController, startDestination = "splash") {
        // Splash screen - will check terms and authentication status
        composable("splash") {
            SplashScreen(
                navController = navController,
                acceptedTerms = acceptedTerms
            )
        }

        // Terms screen
        composable("terms") {
            TermsScreen(navController = navController)
        }

        // Authentication screen
        composable("auth") {
            AuthScreen(navController = navController)
        }

        // Home screen
        composable("home") {
            HomeScreen(navController = navController)
        }

        // Medicine list screen
        composable("medicine_list") {
            MedicineListScreen(navController = navController)
        }

        // Medicine form screen

        composable(
            route = Screen.MedicineForm.route + "?id={id}",
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val medicineId = backStackEntry.arguments?.getString("id")
            MedicineFormScreen(navController = navController, medicineId = medicineId)
        }
    }
}
