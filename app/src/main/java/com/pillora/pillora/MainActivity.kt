package com.pillora.pillora

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pillora.pillora.ui.theme.PilloraTheme
import com.pillora.pillora.navigation.AppNavigation
import com.pillora.pillora.viewmodel.ThemePreference // Importar o Enum
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.FirebaseApp
import android.os.Build // Já deve estar lá, mas confira
import androidx.compose.runtime.LaunchedEffect // Para executar a lógica de permissão
import com.google.accompanist.permissions.ExperimentalPermissionsApi // Necessário para Accompanist
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import android.util.Log

class MainActivity : ComponentActivity() {

    // Chave da preferência de tema
    private val themePreferenceKey = "theme_preference"

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Pillora) // Manter o tema de splash nativo
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        val analytics = Firebase.analytics
        analytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)
        enableEdgeToEdge()

        setContent {
            // Estado para guardar a preferência de tema atual
            var currentThemePreference by remember { mutableStateOf(getInitialThemePreference()) }

            // Efeito para ouvir mudanças nas SharedPreferences
            val context = LocalContext.current
            DisposableEffect(Unit) {
                val prefs = context.getSharedPreferences("pillora_prefs", MODE_PRIVATE)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    if (key == themePreferenceKey) {
                        currentThemePreference = getThemePreferenceFromPrefs(sharedPreferences)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                // Limpar o listener quando o efeito for descartado
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            // Determinar se deve usar tema escuro com base no estado atual
            val useDarkTheme = shouldUseDarkTheme(currentThemePreference)

            PilloraTheme(darkTheme = useDarkTheme) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
                    val notificationPermissionState = rememberPermissionState(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )

                    LaunchedEffect(notificationPermissionState.status) { // Re-executa se o status da permissão mudar
                        if (!notificationPermissionState.status.isGranted && !notificationPermissionState.status.shouldShowRationale) {
                            // Se a permissão não foi concedida e não devemos mostrar uma justificativa (primeira vez ou "não perguntar novamente")
                            notificationPermissionState.launchPermissionRequest()
                        }
                    }

                    // Opcional: Mostrar uma UI se a permissão foi negada e você deve mostrar uma justificativa
                    if (notificationPermissionState.status.shouldShowRationale) {
                        // Aqui você pode mostrar um diálogo ou um Snackbar explicando por que a notificação é importante
                        // e talvez um botão para tentar solicitar a permissão novamente.
                        // Exemplo:
                        // AlertDialog(
                        //     onDismissRequest = { /* Não faz nada ou controla estado de visibilidade */ },
                        //     title = { Text("Permissão Necessária") },
                        //     text = { Text("Para receber lembretes importantes, por favor, permita as notificações.") },
                        //     confirmButton = {
                        //         Button(onClick = { notificationPermissionState.launchPermissionRequest() }) {
                        //             Text("Permitir")
                        //         }
                        //     },
                        //     dismissButton = {
                        //         Button(onClick = { /* O usuário optou por não permitir agora */ }) {
                        //             Text("Agora não")
                        //         }
                        //     }
                        // )
                        // Por simplicidade, vamos apenas logar por enquanto ou você pode adicionar sua UI aqui.
                        Log.d("Permissions", "Deveria mostrar justificativa para permissão de notificação.")
                    } else if (!notificationPermissionState.status.isGranted) {
                        // Permissão ainda não concedida, e não devemos mostrar justificativa (pode ter sido negada permanentemente)
                        // Você pode querer mostrar uma mensagem mais sutil ou um indicador na UI de configurações do app.
                        Log.d("Permissions", "Permissão de notificação não concedida.")
                    }
                }

                AppNavigation()
            }
        }
    }

    // Função para obter a preferência inicial (fora do Composable)
    private fun getInitialThemePreference(): ThemePreference {
        val prefs = getSharedPreferences("pillora_prefs", MODE_PRIVATE)
        return getThemePreferenceFromPrefs(prefs)
    }

    // Função auxiliar para ler a preferência das SharedPreferences
    private fun getThemePreferenceFromPrefs(prefs: SharedPreferences): ThemePreference {
        return when (prefs.getString(themePreferenceKey, ThemePreference.SYSTEM.name)) {
            ThemePreference.LIGHT.name -> ThemePreference.LIGHT
            ThemePreference.DARK.name -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
    }
}

// Composable auxiliar para determinar o booleano darkTheme
@Composable
private fun shouldUseDarkTheme(preference: ThemePreference): Boolean {
    return when (preference) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        else -> isSystemInDarkTheme() // Usar o tema do sistema como padrão
    }
}

