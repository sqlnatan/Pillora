package com.pillora.pillora.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pillora.pillora.screens.HomeScreen
import com.pillora.pillora.screens.FirestoreScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController() // cria o navController aqui

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