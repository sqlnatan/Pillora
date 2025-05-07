package com.pillora.pillora.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pillora.pillora.screens.* // Importa todas as suas telas, incluindo as novas de dependentes
import com.pillora.pillora.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Define routes for Recipe screens (can be added to Screen sealed class if preferred)
const val RECIPE_LIST_ROUTE = "recipe_list"
const val RECIPE_FORM_ROUTE = "recipe_form_screen"

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("pillora_prefs", Context.MODE_PRIVATE)
    }

    // Estado para armazenar a rota inicial, começando com um valor temporário
    var startRoute by remember { mutableStateOf<String?>(null) }

    // Determinar a rota inicial de forma assíncrona ou síncrona
    LaunchedEffect(key1 = Unit) {
        val acceptedTerms = prefs.getBoolean("accepted_terms", false)
        val isAuthenticated = withContext(Dispatchers.IO) { // Verificar autenticação fora da thread principal se necessário
            AuthRepository.isUserAuthenticated()
        }

        startRoute = when {
            !acceptedTerms -> Screen.Terms.route
            !isAuthenticated -> "auth" // Corrigido: Usar a string "auth" diretamente
            else -> Screen.Home.route
        }
    }

    // Só exibe o NavHost quando a rota inicial for determinada
    if (startRoute != null) {
        NavHost(navController = navController, startDestination = startRoute!!) {
            // Remover a rota "splash" - A SplashActivity nativa cuida da transição inicial

            // Terms screen
            composable(Screen.Terms.route) {
                TermsScreen(navController = navController)
            }

            // Authentication screen
            composable("auth") { // Garantir que a definição da rota também use "auth"
                AuthScreen(navController = navController)
            }

            // Home screen
            composable(Screen.Home.route) {
                HomeScreen(navController = navController)
            }

            // Medicine list screen
            composable(Screen.MedicineList.route) {
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

            // Settings screen (Nova rota adicionada)
            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController)
            }

            // Consultation list screen
            composable(Screen.ConsultationList.route) {
                ConsultationListScreen(navController = navController)
            }

            // Consultation form screen (handles both add and edit)
            composable(
                route = Screen.ConsultationForm.route + "?id={id}", // Optional ID for editing
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val consultationId = backStackEntry.arguments?.getString("id")
                ConsultationFormScreen(navController = navController, consultationId = consultationId)
            }

            // Vaccine list screen (Nova rota adicionada)
            composable(Screen.VaccineList.route) {
                VaccineListScreen(navController = navController)
            }

            // Vaccine form screen (Nova rota adicionada - handles both add and edit)
            composable(
                route = Screen.VaccineForm.route + "?id={id}", // Optional ID for editing
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val vaccineId = backStackEntry.arguments?.getString("id")
                VaccineFormScreen(navController = navController, vaccineId = vaccineId)
            }

            // Recipe list screen (Nova rota adicionada)
            composable(RECIPE_LIST_ROUTE) {
                RecipeListScreen(navController = navController)
            }

            // Recipe form screen (Nova rota adicionada - handles both add and edit)
            composable(
                route = "$RECIPE_FORM_ROUTE?id={id}", // Optional ID for editing
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null // Default to null for adding new recipe
                    }
                )
            ) { backStackEntry ->
                val recipeId = backStackEntry.arguments?.getString("id")
                // Handle the case where the ID might be a space or empty string from the list screen
                val actualRecipeId = if (recipeId?.trim()?.isNotEmpty() == true) recipeId else null
                RecipeFormScreen(navController = navController, recipeId = actualRecipeId)
            }

            // Novas Rotas para Dependentes
            composable(Screen.DependentList.route) {
                DependentListScreen(navController = navController)
            }
            composable(
                route = Screen.DependentForm.route + "?dependentId={dependentId}",
                arguments = listOf(navArgument("dependentId") {
                    type = NavType.StringType
                    nullable = true // Permite que dependentId seja nulo (para adicionar novo)
                    defaultValue = null
                })
            ) { backStackEntry ->
                val dependentId = backStackEntry.arguments?.getString("dependentId")
                DependentFormScreen(navController = navController, dependentId = dependentId)
            }

        }
    } else {
        // Opcional: Mostrar um indicador de carregamento ou tela vazia enquanto determina a rota inicial
        // No entanto, a SplashActivity nativa já deve estar cobrindo este tempo.
    }
}

