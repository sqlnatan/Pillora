package com.pillora.pillora.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pillora.pillora.screens.AuthScreen
import com.pillora.pillora.screens.HomeScreen
import com.pillora.pillora.screens.MedicineFormScreen
import com.pillora.pillora.screens.MedicineListScreen
import com.pillora.pillora.screens.SettingsScreen
import com.pillora.pillora.screens.TermsScreen
import com.pillora.pillora.repository.AuthRepository
import com.pillora.pillora.screens.ConsultationFormScreen
import com.pillora.pillora.screens.ConsultationListScreen
import com.pillora.pillora.screens.RecipeFormScreen // Import RecipeFormScreen
import com.pillora.pillora.screens.RecipeListScreen // Import RecipeListScreen
import com.pillora.pillora.screens.VaccineFormScreen
import com.pillora.pillora.screens.VaccineListScreen
import com.pillora.pillora.viewmodel.ConsultationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Define routes for Recipe screens (can be added to Screen sealed class if preferred)
const val RECIPE_LIST_ROUTE = "recipe_list"
const val RECIPE_FORM_ROUTE = "recipe_form_screen"

@Composable
fun AppNavigation(
    // Parâmetros para navegação direta de consulta
    openConsultationEdit: Boolean = false,
    consultationId: String? = null,
    // *** CORREÇÃO: Adicionar parâmetros para navegação direta de vacina ***
    openVaccineEdit: Boolean = false,
    vaccineId: String? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("pillora_prefs", Context.MODE_PRIVATE)
    }

    // Estado para armazenar a rota inicial, começando com um valor temporário
    var startRoute by remember { mutableStateOf<String?>(null) }

    // IMPORTANTE: Criar uma única instância do ConsultationViewModel no escopo do NavHost
    // Isso garante que todas as telas compartilhem a mesma instância e vejam as mesmas atualizações
    val sharedConsultationViewModel: ConsultationViewModel = viewModel()

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
                // Passar o ViewModel compartilhado para a tela de lista de consultas
                ConsultationListScreen(
                    navController = navController,
                    viewModel = sharedConsultationViewModel
                )
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
                val consultationIdFromRoute = backStackEntry.arguments?.getString("id")
                ConsultationFormScreen(
                    navController = navController,
                    consultationId = consultationIdFromRoute,
                    // Opcional: passar o mesmo ViewModel se a tela de formulário também precisar dele
                    // viewModel = sharedConsultationViewModel
                )
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
                val vaccineIdFromRoute = backStackEntry.arguments?.getString("id")
                VaccineFormScreen(navController = navController, vaccineId = vaccineIdFromRoute)
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
        }

        // *** CORREÇÃO: Navegar para a tela de edição de consulta OU vacina se necessário ***
        if (openConsultationEdit && consultationId != null) {
            LaunchedEffect(key1 = "consultation_$consultationId") { // Use uma chave única
                navController.navigate("${Screen.ConsultationForm.route}?id=$consultationId")
            }
        } else if (openVaccineEdit && vaccineId != null) {
            LaunchedEffect(key1 = "vaccine_$vaccineId") { // Use uma chave única
                navController.navigate("${Screen.VaccineForm.route}?id=$vaccineId")
            }
        }

    } else {
        // Opcional: Mostrar um indicador de carregamento ou tela vazia enquanto determina a rota inicial
        // No entanto, a SplashActivity nativa já deve estar cobrindo este tempo.
    }
}

