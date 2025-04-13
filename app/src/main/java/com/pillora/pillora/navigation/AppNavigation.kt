package com.pillora.pillora.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.pillora.pillora.screens.HomeScreen
import com.pillora.pillora.screens.FirestoreScreen

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(navController)
        }
        composable(Screen.Firestore.route) {
            FirestoreScreen(navController)
        }
    }
}