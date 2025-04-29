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
import com.pillora.pillora.screens.AuthScreen
import com.pillora.pillora.screens.HomeScreen
import com.pillora.pillora.screens.MedicineFormScreen
import com.pillora.pillora.screens.MedicineListScreen
// Removido import SplashScreen
import com.pillora.pillora.screens.TermsScreen
import com.pillora.pillora.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            /*
            composable("splash") {
                // Este Composable não é mais necessário
            }
            */

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
        }
    } else {
        // Opcional: Mostrar um indicador de carregamento ou tela vazia enquanto determina a rota inicial
        // Ex: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        // No entanto, a SplashActivity nativa já deve estar cobrindo este tempo.
    }
}

