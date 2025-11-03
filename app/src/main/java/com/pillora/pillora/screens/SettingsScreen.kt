package com.pillora.pillora.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.pillora.pillora.viewmodel.SettingsViewModel
import com.pillora.pillora.viewmodel.ThemePreference

// Factory para injetar Context no ViewModel
class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Passar applicationContext para evitar memory leaks
            return SettingsViewModel(context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    // Usar a Factory para criar o ViewModel
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))

    val currentThemePref by viewModel.themePreference.collectAsState()
    val doseRemindersEnabled by viewModel.doseRemindersEnabled.collectAsState()
    val stockAlertsEnabled by viewModel.stockAlertsEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(48.dp),
                windowInsets = WindowInsets(0),
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp) // Apenas horizontal aqui
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp)) // Espaço no topo

            // Seção de Tema
            Text("Tema do Aplicativo", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.selectableGroup().padding(vertical = 8.dp)) {
                    ThemePreferenceOption(ThemePreference.LIGHT, currentThemePref) { viewModel.setThemePreference(ThemePreference.LIGHT) }
                    ThemePreferenceOption(ThemePreference.DARK, currentThemePref) { viewModel.setThemePreference(ThemePreference.DARK) }
                    ThemePreferenceOption(ThemePreference.SYSTEM, currentThemePref) { viewModel.setThemePreference(ThemePreference.SYSTEM) }
                }
            }

            // Seção de Notificações
            Text("Notificações", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    NotificationOption(
                        text = "Lembretes de Dose",
                        checked = doseRemindersEnabled,
                        onCheckedChange = { viewModel.setDoseRemindersEnabled(it) }
                    )
                    NotificationOption(
                        text = "Alertas de Estoque Baixo",
                        checked = stockAlertsEnabled,
                        onCheckedChange = { viewModel.setStockAlertsEnabled(it) }
                    )
                    // TODO: Adicionar Switches para outros tipos de notificação (Consultas, Vacinas)
                }
            }

            Spacer(modifier = Modifier.height(16.dp)) // Espaço no final
        }
    }
}

@Composable
fun ThemePreferenceOption(preference: ThemePreference, currentSelection: ThemePreference, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .selectable(
                selected = (preference == currentSelection),
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = (preference == currentSelection),
            onClick = null // null recomendado para acessibilidade
        )
        Text(
            text = when (preference) {
                ThemePreference.LIGHT -> "Claro"
                ThemePreference.DARK -> "Escuro"
                ThemePreference.SYSTEM -> "Padrão do Sistema"
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
fun NotificationOption(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

