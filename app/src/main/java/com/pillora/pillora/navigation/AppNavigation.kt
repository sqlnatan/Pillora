package com.pillora.pillora.navigation

import android.app.Application // Import Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider // Import ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel // Import viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pillora.pillora.screens.*
import com.pillora.pillora.repository.AuthRepository
import com.pillora.pillora.viewmodel.AppViewModel // *** IMPORT AppViewModel ***
import com.pillora.pillora.viewmodel.RecipeViewModel // *** IMPORT RecipeViewModel ***
import com.pillora.pillora.viewmodel.ProfileViewModel // *** IMPORT ProfileViewModel ***
// Import other ViewModels as needed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Define routes for Recipe screens
const val RECIPE_LIST_ROUTE = "recipe_list"
const val RECIPE_FORM_ROUTE = "recipe_form_screen"

// Define routes for Profile screens
const val PROFILE_LIST_ROUTE = "profile_list"
const val PROFILE_FORM_ROUTE = "profile_form_screen"

// *** ADDED: ViewModel Factory for passing AppViewModel ***
class ViewModelFactory(private val appViewModel: AppViewModel) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecipeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RecipeViewModel(appViewModel) as T
        }
        // Add other ViewModels that need AppViewModel here
        // if (modelClass.isAssignableFrom(MedicationViewModel::class.java)) {
        //     @Suppress("UNCHECKED_CAST")
        //     return MedicationViewModel(appViewModel) as T
        // }
        // ... etc.
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

// *** MODIFIED: Accept AppViewModel ***
@Composable
fun AppNavigation(appViewModel: AppViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("pillora_prefs", Context.MODE_PRIVATE)
    }

    // *** ADDED: Create ViewModelFactory instance ***
    val viewModelFactory = remember { ViewModelFactory(appViewModel) }

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
                // *** Pass AppViewModel and ProfileViewModel to HomeScreen ***
                val profileViewModel: ProfileViewModel = viewModel() // Standard factory ok
                HomeScreen(navController = navController, appViewModel = appViewModel, profileViewModel = profileViewModel)
            }

            // Medicine list screen
            composable(Screen.MedicineList.route) {
                // TODO: Adapt MedicationViewModel to accept AppViewModel via factory
                // val medicationViewModel: MedicationViewModel = viewModel(factory = viewModelFactory)
                MedicineListScreen(navController = navController /*, medicationViewModel = medicationViewModel */)
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
                // TODO: Adapt MedicationViewModel to accept AppViewModel via factory
                // val medicationViewModel: MedicationViewModel = viewModel(factory = viewModelFactory)
                MedicineFormScreen(navController = navController, medicineId = medicineId /*, medicationViewModel = medicationViewModel */)
            }

            // Settings screen
            composable(Screen.Settings.route) {
                // *** Pass AppViewModel and ProfileViewModel to SettingsScreen ***
                val profileViewModel: ProfileViewModel = viewModel() // Standard factory is ok here
                SettingsScreen(navController = navController, appViewModel = appViewModel, profileViewModel = profileViewModel)
            }

            // Consultation list screen
            composable(Screen.ConsultationList.route) {
                // TODO: Adapt ConsultationViewModel
                ConsultationListScreen(navController = navController)
            }

            // Consultation form screen
            composable(
                route = Screen.ConsultationForm.route + "?id={id}",
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val consultationId = backStackEntry.arguments?.getString("id")
                // TODO: Adapt ConsultationViewModel
                ConsultationFormScreen(navController = navController, consultationId = consultationId)
            }

            // Vaccine list screen
            composable(Screen.VaccineList.route) {
                // TODO: Adapt VaccineViewModel
                VaccineListScreen(navController = navController)
            }

            // Vaccine form screen
            composable(
                route = Screen.VaccineForm.route + "?id={id}",
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val vaccineId = backStackEntry.arguments?.getString("id")
                // TODO: Adapt VaccineViewModel
                VaccineFormScreen(navController = navController, vaccineId = vaccineId)
            }

            // Recipe list screen
            composable(RECIPE_LIST_ROUTE) {
                // *** Use factory to create RecipeViewModel ***
                val recipeViewModel: RecipeViewModel = viewModel(factory = viewModelFactory)
                RecipeListScreen(navController = navController, recipeViewModel = recipeViewModel)
            }

            // Recipe form screen
            composable(
                route = "$RECIPE_FORM_ROUTE?id={id}",
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val recipeId = backStackEntry.arguments?.getString("id")
                val actualRecipeId = if (recipeId?.trim()?.isNotEmpty() == true) recipeId else null
                // *** Use factory to create RecipeViewModel ***
                val recipeViewModel: RecipeViewModel = viewModel(factory = viewModelFactory)
                RecipeFormScreen(navController = navController, recipeId = actualRecipeId, recipeViewModel = recipeViewModel)
            }

            // *** ADDED: Profile List Screen Route ***
            composable(PROFILE_LIST_ROUTE) {
                val profileViewModel: ProfileViewModel = viewModel() // Standard factory ok
                ProfileListScreen(navController = navController, profileViewModel = profileViewModel)
            }

            // *** ADDED: Profile Form Screen Route ***
            composable(
                route = "$PROFILE_FORM_ROUTE?id={id}",
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getString("id")
                val actualProfileId = if (profileId?.trim()?.isNotEmpty() == true) profileId else null
                val profileViewModel: ProfileViewModel = viewModel() // Standard factory ok
                ProfileFormScreen(navController = navController, profileId = actualProfileId, profileViewModel = profileViewModel)
            }
        }
    } else {
        // Optional: Loading indicator
    }
}

