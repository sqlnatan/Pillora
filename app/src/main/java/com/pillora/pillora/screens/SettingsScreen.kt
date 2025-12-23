package com.pillora.pillora.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
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
import com.pillora.pillora.data.SubscriptionStatus
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.viewmodel.SettingsViewModel
import com.pillora.pillora.viewmodel.SubscriptionViewModel
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
    val application = context.applicationContext as PilloraApplication

    // Usar a Factory para criar o ViewModel
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(context))

    // ViewModel de assinatura para testes de downgrade
    val subscriptionViewModel: SubscriptionViewModel = viewModel(
        factory = SubscriptionViewModel.provideFactory(
            application = application,
            userPreferences = application.userPreferences
        )
    )

    val currentThemePref by viewModel.themePreference.collectAsState()
    val doseRemindersEnabled by viewModel.doseRemindersEnabled.collectAsState()
    val stockAlertsEnabled by viewModel.stockAlertsEnabled.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()

    // Estados de assinatura
    val subscriptionStatus by subscriptionViewModel.subscriptionStatus.collectAsState()
    val gracePeriodDaysRemaining by subscriptionViewModel.gracePeriodDaysRemaining.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
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

            // Seção de Teste Premium
            Text("Modo de Teste", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    PremiumTestOption(
                        isPremium = isPremium,
                        onPremiumChange = { newValue ->
                            viewModel.setPremiumStatus(newValue)
                            if (newValue) {
                                // Resetar dados de downgrade quando ativa premium
                                subscriptionViewModel.resetDowngradeState()
                            }
                        }
                    )
                }
            }

            // Seção de Teste de Downgrade (apenas para desenvolvimento)
            Text("Teste de Downgrade", style = MaterialTheme.typography.titleMedium)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status atual
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Status: ${getSubscriptionStatusText(subscriptionStatus)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (gracePeriodDaysRemaining > 0) {
                        Text(
                            text = "Dias restantes no período de carência: $gracePeriodDaysRemaining",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    HorizontalDivider()

                    // Botão para simular expiração (inicia período de carência)
                    OutlinedButton(
                        onClick = {
                            subscriptionViewModel.simulateExpiration()
                            Toast.makeText(
                                context,
                                "Assinatura expirada! Período de carência iniciado.",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Simular Expiração (Inicia Carência)")
                    }

                    // Botão para simular fim do período de carência
                    OutlinedButton(
                        onClick = {
                            subscriptionViewModel.simulateGracePeriodExpired()
                            Toast.makeText(
                                context,
                                "Período de carência expirado! Downgrade obrigatório.",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Simular Fim da Carência (Força Downgrade)")
                    }

                    // Botão para ir à tela de downgrade manualmente
                    OutlinedButton(
                        onClick = {
                            navController.navigate(Screen.DowngradeSelection.route)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Abrir Tela de Downgrade")
                    }

                    // Botão para resetar tudo
                    Button(
                        onClick = {
                            subscriptionViewModel.resetDowngradeState()
                            viewModel.setPremiumStatus(true)
                            Toast.makeText(
                                context,
                                "Estado resetado para Premium ativo!",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Resetar para Premium")
                    }

                    Text(
                        text = "⚠️ Estas opções são apenas para teste durante o desenvolvimento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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

            // Seção de Termos e Privacidade
            Text("Legal", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate("terms?viewOnly=true") }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Termos",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Termos de Uso e Privacidade",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp)) // Espaço no final
        }
    }
}

@Composable
private fun getSubscriptionStatusText(status: SubscriptionStatus): String {
    return when (status) {
        is SubscriptionStatus.PREMIUM_ACTIVE -> "Premium Ativo"
        is SubscriptionStatus.FREE -> "Plano Gratuito"
        is SubscriptionStatus.GRACE_PERIOD -> "Período de Carência (${status.daysRemaining} dias)"
        is SubscriptionStatus.DOWNGRADE_REQUIRED -> "Downgrade Obrigatório"
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

@Composable
fun PremiumTestOption(isPremium: Boolean, onPremiumChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = if (isPremium) "Modo Premium" else "Modo Free",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Alterne para testar funcionalidades",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isPremium,
            onCheckedChange = onPremiumChange
        )
    }
}
