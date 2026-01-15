package com.pillora.pillora.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pillora.pillora.utils.PermissionHelper

/**
 * Tela de solicitação e explicação de permissões.
 *
 * Exibe:
 * - Explicação sobre cada permissão necessária
 * - Status atual de cada permissão
 * - Botões para solicitar/conceder permissões
 *
 * Pode ser acessada:
 * - Durante o onboarding (primeira vez)
 * - Através das configurações
 * - Automaticamente quando permissões são negadas
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    navController: NavController,
    isOnboarding: Boolean = false
) {
    val context = LocalContext.current

    // Estados das permissões
    var hasNotificationPermission by remember {
        mutableStateOf(PermissionHelper.hasNotificationPermission(context))
    }
    var hasExactAlarmPermission by remember {
        mutableStateOf(PermissionHelper.hasExactAlarmPermission(context))
    }

    // Launcher para solicitar permissão de notificações (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    // Atualizar status das permissões quando a tela for retomada
    LaunchedEffect(Unit) {
        hasNotificationPermission = PermissionHelper.hasNotificationPermission(context)
        hasExactAlarmPermission = PermissionHelper.hasExactAlarmPermission(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isOnboarding) "Configurar Permissões" else "Permissões do App") },
                navigationIcon = {
                    if (!isOnboarding) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cabeçalho
            if (isOnboarding) {
                Text(
                    text = "Bem-vindo ao Pillora! 💊",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Para funcionar corretamente, o Pillora precisa de algumas permissões. Vamos configurá-las agora!",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Text(
                    text = "Gerencie as permissões do Pillora",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Card: Permissão de Notificações
            if (PermissionHelper.needsNotificationPermission()) {
                PermissionCard(
                    title = "Notificações",
                    icon = Icons.Default.Notifications,
                    description = PermissionHelper.getNotificationPermissionRationale(),
                    isGranted = hasNotificationPermission,
                    onRequestClick = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onOpenSettingsClick = {
                        PermissionHelper.openNotificationSettings(context)
                    }
                )
            }

            // Card: Permissão de Alarmes Exatos
            if (PermissionHelper.needsExactAlarmPermission()) {
                PermissionCard(
                    title = "Alarmes Exatos",
                    icon = Icons.Default.Alarm,
                    description = PermissionHelper.getExactAlarmPermissionRationale(),
                    isGranted = hasExactAlarmPermission,
                    onRequestClick = {
                        PermissionHelper.openExactAlarmSettings(context)
                    },
                    onOpenSettingsClick = {
                        PermissionHelper.openExactAlarmSettings(context)
                    },
                    requiresManualGrant = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botão de ação principal
            if (isOnboarding) {
                val allGranted = hasNotificationPermission && hasExactAlarmPermission

                Button(
                    onClick = {
                        // Ir para a tela principal independente das permissões
                        navController.navigate("home") {
                            popUpTo("permissions") { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (allGranted) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    }
                ) {
                    Text(
                        text = if (allGranted) "Começar a usar o Pillora" else "Continuar mesmo assim",
                        modifier = Modifier.padding(8.dp)
                    )
                }

                if (!allGranted) {
                    Text(
                        text = "⚠️ Você pode conceder as permissões depois nas Configurações",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // Botão para verificar novamente
                OutlinedButton(
                    onClick = {
                        hasNotificationPermission = PermissionHelper.hasNotificationPermission(context)
                        hasExactAlarmPermission = PermissionHelper.hasExactAlarmPermission(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verificar Permissões Novamente")
                }
            }
        }
    }
}

/**
 * Card individual para cada permissão.
 */
@Composable
private fun PermissionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    isGranted: Boolean,
    onRequestClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
    requiresManualGrant: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cabeçalho com ícone e status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Badge de status
                if (isGranted) {
                    AssistChip(
                        onClick = { /* Não faz nada */ },
                        label = { Text("Concedida") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            labelColor = MaterialTheme.colorScheme.onPrimary,
                            leadingIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                } else {
                    AssistChip(
                        onClick = { /* Não faz nada */ },
                        label = { Text("Negada") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }
            }

            // Descrição
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Botão de ação
            if (!isGranted) {
                if (requiresManualGrant) {
                    // Permissões que requerem concessão manual (SCHEDULE_EXACT_ALARM)
                    Button(
                        onClick = onRequestClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Abrir Configurações")
                    }

                    Text(
                        text = "Esta permissão precisa ser concedida manualmente nas configurações do sistema.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Permissões que podem ser solicitadas via runtime (POST_NOTIFICATIONS)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRequestClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Conceder")
                        }

                        OutlinedButton(
                            onClick = onOpenSettingsClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Config")
                        }
                    }
                }
            } else {
                // Permissão já concedida
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Permissão concedida com sucesso!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
