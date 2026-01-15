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
import androidx.compose.material.icons.filled.*
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
import com.pillora.pillora.utils.PermissionHelper

/**
 * Tela de Configurações.
 *
 * VERSÃO ATUALIZADA: Inclui seção de Permissões
 * - Tema do Aplicativo
 * - Permissões (Notificações e Alarmes Exatos)
 * - Termos de Uso
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

    // Estados das permissões
    var hasNotificationPermission by remember {
        mutableStateOf(PermissionHelper.hasNotificationPermission(context))
    }
    var hasExactAlarmPermission by remember {
        mutableStateOf(PermissionHelper.hasExactAlarmPermission(context))
    }

    // Atualizar permissões quando a tela for retomada
    LaunchedEffect(Unit) {
        hasNotificationPermission = PermissionHelper.hasNotificationPermission(context)
        hasExactAlarmPermission = PermissionHelper.hasExactAlarmPermission(context)
    }

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

            // ========== SEÇÃO: PERMISSÕES ==========
            Text("Permissões", style = MaterialTheme.typography.titleMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    // Opção para gerenciar todas as permissões
                    PermissionOption(
                        label = "Gerenciar Permissões",
                        icon = Icons.Default.Security,
                        subtitle = if (hasNotificationPermission && hasExactAlarmPermission) {
                            "Todas as permissões concedidas"
                        } else {
                            "Algumas permissões estão faltando"
                        },
                        hasIssue = !hasNotificationPermission || !hasExactAlarmPermission,
                        onClick = {
                            navController.navigate(Screen.Permissions.route + "?isOnboarding=false")
                        }
                    )

                    HorizontalDivider()

                    // Status individual: Notificações
                    if (PermissionHelper.needsNotificationPermission()) {
                        PermissionStatusItem(
                            label = "Notificações",
                            icon = Icons.Default.Notifications,
                            isGranted = hasNotificationPermission,
                            onClick = {
                                if (!hasNotificationPermission) {
                                    navController.navigate(Screen.Permissions.route + "?isOnboarding=false")
                                }
                            }
                        )
                    }

                    // Status individual: Alarmes Exatos
                    if (PermissionHelper.needsExactAlarmPermission()) {
                        PermissionStatusItem(
                            label = "Alarmes Exatos",
                            icon = Icons.Default.Alarm,
                            isGranted = hasExactAlarmPermission,
                            onClick = {
                                if (!hasExactAlarmPermission) {
                                    navController.navigate(Screen.Permissions.route + "?isOnboarding=false")
                                }
                            }
                        )
                    }
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

@Composable
fun PermissionOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String,
    hasIssue: Boolean,
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
            tint = if (hasIssue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (hasIssue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (hasIssue) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PermissionStatusItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, enabled = !isGranted)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Concedida",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = "Negada",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
