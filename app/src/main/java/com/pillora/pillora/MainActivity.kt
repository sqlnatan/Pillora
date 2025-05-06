package com.pillora.pillora

import android.app.Application // Import Application
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
import androidx.lifecycle.ViewModelProvider // Import ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel // Import viewModel
import com.pillora.pillora.ui.theme.PilloraTheme
import com.pillora.pillora.navigation.AppNavigation
import com.pillora.pillora.viewmodel.ThemePreference // Importar o Enum
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.FirebaseApp
import com.pillora.pillora.viewmodel.AppViewModel // *** IMPORT AppViewModel ***

class MainActivity : ComponentActivity() {

    // Chave da preferência de tema
    private val themePreferenceKey = "theme_preference"

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Pillora) // Manter o tema de splash nativo
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        val analytics = Firebase.analytics
        analytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)
        enableEdgeToEdge()

        setContent {
            // *** Instantiate AppViewModel ***
            val appViewModel: AppViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(LocalContext.current.applicationContext as Application)
            )

            // Estado para guardar a preferência de tema atual
            var currentThemePreference by remember { mutableStateOf(getInitialThemePreference()) }

            // Efeito para ouvir mudanças nas SharedPreferences (mantido para tema)
            val context = LocalContext.current
            DisposableEffect(Unit) {
                val prefs = context.getSharedPreferences("pillora_prefs", Context.MODE_PRIVATE)
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
                // *** Pass AppViewModel to AppNavigation ***
                AppNavigation(appViewModel = appViewModel)
            }
        }
    }

    // Função para obter a preferência inicial (fora do Composable)
    private fun getInitialThemePreference(): ThemePreference {
        val prefs = getSharedPreferences("pillora_prefs", Context.MODE_PRIVATE)
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

