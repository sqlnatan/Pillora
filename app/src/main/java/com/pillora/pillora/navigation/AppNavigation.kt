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
import com.pillora.pillora.screens.HomeScreen
import com.pillora.pillora.screens.FirestoreScreen
import com.pillora.pillora.screens.TermsScreen
import com.pillora.pillora.screens.MedicineFormScreen
import com.pillora.pillora.screens.MedicineListScreen


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

    val startDestination = if (acceptedTerms) "home" else "terms"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("terms") {
            TermsScreen(navController = navController)
        }
        composable("home") {
            HomeScreen(navController = navController)
        }
        composable("medicine_list") {
            MedicineListScreen(navController = navController)
        }
        composable(
            route = "medicine_form?medicineId={medicineId}",
            arguments = listOf(
                navArgument("medicineId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val medicineId = backStackEntry.arguments?.getString("medicineId")
            MedicineFormScreen(
                navController = navController,
                medicineId = medicineId
            )
        }
        composable("firestore") {
            FirestoreScreen(navController = navController)
        }
    }
}