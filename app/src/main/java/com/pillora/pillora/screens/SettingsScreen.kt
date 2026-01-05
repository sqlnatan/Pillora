package com.pillora.pillora.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
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
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.viewmodel.SettingsViewModel
import com.pillora.pillora.viewmodel.ThemePreference

/**
 * Tela de Configurações.
 *
 * VERSÃO LIMPA: Sem testes de premium ou simulações.
 * Mantém apenas configurações legítimas: Tema e Termos de Uso.
 *
 * REMOVIDO (para aprovação do Google):
 * - Toggle de "Modo de Teste Premium"
 * - Botões de "Teste de Downgrade"
 * - Seção de "Notificações" (será implementada futuramente se necessário)
 */

// Factory para injetar Context e BillingRepository no ViewModel
class SettingsViewModelFactory(
    private val context: Context,
    private val application: PilloraApplication
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(
                context.applicationContext,
                application.billingRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val application = context.applicationContext as PilloraApplication

    // Usar a Factory para criar o ViewModel
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(context, application)
    )

    val currentThemePref by viewModel.themePreference.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
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
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ========== SEÇÃO: TEMA DO APLICATIVO ==========
            Text("Tema do Aplicativo", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .selectableGroup()
                        .padding(vertical = 8.dp)
                ) {
                    ThemeOption(
                        label = "Claro",
                        selected = currentThemePref == ThemePreference.LIGHT,
                        onClick = { viewModel.setThemePreference(ThemePreference.LIGHT) }
                    )
                    ThemeOption(
                        label = "Escuro",
                        selected = currentThemePref == ThemePreference.DARK,
                        onClick = { viewModel.setThemePreference(ThemePreference.DARK) }
                    )
                    ThemeOption(
                        label = "Automático (Sistema)",
                        selected = currentThemePref == ThemePreference.SYSTEM,
                        onClick = { viewModel.setThemePreference(ThemePreference.SYSTEM) }
                    )
                }
            }

            // ========== SEÇÃO: LEGAL ==========
            Text("Legal", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    LegalOption(
                        label = "Termos de Uso e Privacidade",
                        icon = Icons.Default.Description,
                        onClick = { navController.navigate(Screen.Terms.route) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ========== COMPONENTES AUXILIARES ==========

@Composable
fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun LegalOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
