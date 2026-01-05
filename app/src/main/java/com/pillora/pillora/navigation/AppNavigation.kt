package com.pillora.pillora.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.repository.BillingRepository
import com.pillora.pillora.screens.AuthScreen
import com.pillora.pillora.screens.DowngradeSelectionScreen
// import com.pillora.pillora.screens.GracePeriodWarningDialog // REMOVIDO: Não é mais necessário
import com.pillora.pillora.screens.HomeScreen
import com.pillora.pillora.screens.MedicineFormScreen
import com.pillora.pillora.screens.MedicineListScreen
import com.pillora.pillora.screens.SettingsScreen
import com.pillora.pillora.screens.TermsScreen
import com.pillora.pillora.screens.ProfileScreen
import com.pillora.pillora.repository.AuthRepository
import com.pillora.pillora.screens.ConsultationFormScreen
import com.pillora.pillora.screens.ConsultationListScreen
import com.pillora.pillora.screens.RecipeFormScreen // Import RecipeFormScreen
import com.pillora.pillora.screens.RecipeListScreen // Import RecipeListScreen
import com.pillora.pillora.screens.VaccineFormScreen
import com.pillora.pillora.screens.VaccineListScreen
import com.pillora.pillora.viewmodel.ConsultationViewModel
import com.pillora.pillora.viewmodel.SubscriptionViewModel
import com.pillora.pillora.repository.TermsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pillora.pillora.screens.ReportsScreen // Import ReportsScreen
import com.pillora.pillora.screens.SubscriptionScreen // Import SubscriptionScreen
import com.pillora.pillora.screens.WelcomeScreen
import com.pillora.pillora.screens.AgeVerificationScreen


// Define routes for Recipe screens (can be added to Screen sealed class if preferred)
const val RECIPE_LIST_ROUTE = "recipe_list"
const val RECIPE_FORM_ROUTE = "recipe_form_screen"

@Composable
fun AppNavigation(
    navController: NavHostController,

    // Parâmetros para navegação direta de consulta
    openConsultationEdit: Boolean = false,
    consultationId: String? = null,
    // *** CORREÇÃO: Adicionar parâmetros para navegação direta de vacina ***
    openVaccineEdit: Boolean = false,
    vaccineId: String? = null
) {
    // val navController = rememberNavController() // REMOVIDO: Recebido como parâmetro
    val context = LocalContext.current
    val application = context.applicationContext as PilloraApplication
    val prefs = remember {
        context.getSharedPreferences("pillora_prefs", Context.MODE_PRIVATE)
    }

    // Estado para armazenar a rota inicial, começando com um valor temporário
    var startRoute by rememberSaveable { mutableStateOf<String?>(null) }

    // Flag para garantir que a inicialização só ocorra uma vez
    var hasInitialized by rememberSaveable { mutableStateOf(false) }

    // IMPORTANTE: Criar uma única instância do ConsultationViewModel no escopo do NavHost
    // Isso garante que todas as telas compartilhem a mesma instância e vejam as mesmas atualizações
    val sharedConsultationViewModel: ConsultationViewModel = viewModel()

    // ViewModel para gerenciar estado de assinatura
    val subscriptionViewModel: SubscriptionViewModel = viewModel(
        factory = SubscriptionViewModel.provideFactory(
            application = application,
            billingRepository = application.billingRepository
        )
    )

    // ✅ NOVO: Observar flag de downgrade do UserPreferences (gerenciado pelo BillingRepository)
    val needsDowngradeSelection by application.userPreferences.needsDowngradeSelection.collectAsState(initial = false)

    // Determinar a rota inicial de forma assíncrona ou síncrona - APENAS UMA VEZ
    LaunchedEffect(hasInitialized) {
        if (!hasInitialized) {
            // NOVO: Verificar se já confirmou a idade
            val hasVerifiedAge = prefs.getBoolean("has_verified_age", false)
            val hasSeenWelcome = prefs.getBoolean("has_seen_welcome", false)

            val isAuthenticated = withContext(Dispatchers.IO) {
                AuthRepository.isUserAuthenticated()
            }

            // Verificação de status premium agora é feita automaticamente pelo BillingRepository

            // CORREÇÃO: Verificar termos de uso AQUI, apenas uma vez na inicialização
            // Se o usuário está autenticado mas não aceitou os termos, vai para a tela de termos
            val hasAcceptedTerms = if (isAuthenticated) {
                val userId = withContext(Dispatchers.IO) {
                    AuthRepository.getCurrentUser()?.uid
                }
                if (userId != null) {
                    withContext(Dispatchers.IO) {
                        TermsRepository.hasAcceptedCurrentTerms(userId)
                    }
                } else {
                    false
                }
            } else {
                true // Se não está autenticado, não precisa verificar termos
            }

            // NOVO: Priorizar verificação de idade antes de tudo
            startRoute = when {
                !hasVerifiedAge -> "age_verification" // Primeira coisa: verificar idade
                isAuthenticated && !hasAcceptedTerms -> "terms" // Vai para termos se não aceitou
                isAuthenticated -> Screen.Home.route
                !hasSeenWelcome -> Screen.Welcome.route
                else -> "auth"
            }

            hasInitialized = true
        }
    }

    // ✅ NOVO: Se precisa fazer downgrade, mostrar tela de seleção
    if (needsDowngradeSelection) {
        DowngradeSelectionScreen(
            onDowngradeComplete = {
                // Marcar downgrade como completo
                application.userPreferences.setDowngradeCompleted()
            }
        )
    } else if (startRoute != null) {
        // Só exibe o NavHost quando a rota inicial for determinada e não precisa de downgrade
        NavHost(navController = navController, startDestination = startRoute!!) {
            // Remover a rota "splash" - A SplashActivity nativa cuida da transição inicial

            // NOVO: Age verification screen (primeira tela no primeiro uso)
            composable("age_verification") {
                AgeVerificationScreen(
                    navController = navController,
                    onAgeVerified = {
                        // Salvar que o usuário verificou a idade
                        prefs.edit()
                            .putBoolean("has_verified_age", true)
                            .apply()

                        // Determinar próxima tela baseado no estado
                        val hasSeenWelcome = prefs.getBoolean("has_seen_welcome", false)
                        val nextRoute = if (!hasSeenWelcome) {
                            Screen.Welcome.route
                        } else {
                            "auth"
                        }

                        navController.navigate(nextRoute) {
                            popUpTo("age_verification") { inclusive = true }
                        }
                    }
                )
            }

            // Terms screen (com parâmetro opcional para modo visualização)
            composable(
                route = "terms?viewOnly={viewOnly}",
                arguments = listOf(navArgument("viewOnly") {
                    type = NavType.BoolType
                    defaultValue = false
                })
            ) { backStackEntry ->
                val viewOnly = backStackEntry.arguments?.getBoolean("viewOnly") ?: false
                TermsScreen(navController = navController, viewOnly = viewOnly)
            }

            composable("welcome") {
                WelcomeScreen(
                    onFinish = {
                        prefs.edit()
                            .putBoolean("has_seen_welcome", true)
                            .apply()

                        navController.navigate("auth") {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    }
                )
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

            // Profile screen (Nova rota adicionada)
            composable(Screen.Profile.route) {
                ProfileScreen(navController = navController)
            }

            // Reports Screen (Nova rota adicionada)
            composable(Screen.Reports.route) {
                ReportsScreen(navController = navController)
            }

            // Subscription Screen
            composable(Screen.Subscription.route) {
                SubscriptionScreen(navController = navController)
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
