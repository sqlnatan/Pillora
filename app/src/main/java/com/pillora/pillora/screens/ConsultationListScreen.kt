package com.pillora.pillora.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.repository.ConsultationRepository
import com.pillora.pillora.navigation.Screen
import com.pillora.pillora.ads.NativeAdCard
import com.pillora.pillora.utils.FreeLimits
import androidx.compose.foundation.lazy.itemsIndexed
import com.pillora.pillora.viewmodel.ConsultationListUiState
import com.pillora.pillora.viewmodel.ConsultationViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsultationListScreen(
    navController: NavController,
    viewModel: ConsultationViewModel
) {
    val consultationListState by viewModel.consultationListUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Obter status premium do UserPreferences
    val application = context.applicationContext as PilloraApplication
    val isPremium by application.userPreferences.isPremium.collectAsState(initial = false)

    LaunchedEffect(consultationListState) {
        when (consultationListState) {
            is ConsultationListUiState.Success -> {
                val consultations =
                    (consultationListState as ConsultationListUiState.Success).consultations
                Log.d("ConsultationListScreen", "Estado atualizado: ${consultations.size} consultas")
            }
            is ConsultationListUiState.Loading ->
                Log.d("ConsultationListScreen", "Estado: Carregando")
            is ConsultationListUiState.Error ->
                Log.d(
                    "ConsultationListScreen",
                    "Estado: Erro - ${(consultationListState as ConsultationListUiState.Error).message}"
                )
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var consultationToDelete by remember { mutableStateOf<Consultation?>(null) }
    val scope = rememberCoroutineScope()

    if (showDeleteDialog && consultationToDelete != null) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirmar exclusão") },
            text = {
                Text("Deseja realmente excluir a consulta de ${consultationToDelete?.specialty}?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        consultationToDelete?.id?.let { id ->
                            scope.launch {
                                ConsultationRepository.deleteConsultation(
                                    consultationId = id,
                                    onSuccess = {
                                        Toast.makeText(
                                            context,
                                            "Consulta excluída com sucesso",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onFailure = {
                                        Toast.makeText(
                                            context,
                                            "Erro ao excluir consulta: ${it.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }
                        }
                        showDeleteDialog = false
                        consultationToDelete = null
                    }
                ) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Calcular consultas ativas
    val consultations = when (consultationListState) {
        is ConsultationListUiState.Success ->
            (consultationListState as ConsultationListUiState.Success).consultations
        else -> emptyList()
    }
    val activeConsultationsCount = consultations.count { it.isActive }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                ),
                title = {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "Consultas Médicas",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${consultations.size} ${if (consultations.size == 1) "consulta" else "consultas"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Mostrar contador de ativas se não for premium
                            if (!isPremium && consultations.isNotEmpty()) {
                                Text(
                                    text = "• $activeConsultationsCount/${FreeLimits.MAX_CONSULTATIONS_FREE} ativas",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (activeConsultationsCount >= FreeLimits.MAX_CONSULTATIONS_FREE)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                actions = {
                    FilledIconButton(
                        onClick = { navController.navigate(Screen.ConsultationForm.route) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Adicionar Consulta"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            when (consultationListState) {
                is ConsultationListUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is ConsultationListUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = (consultationListState as ConsultationListUiState.Error).message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                is ConsultationListUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Aviso de limite para usuários Free
                        if (!isPremium && activeConsultationsCount >= FreeLimits.MAX_CONSULTATIONS_FREE && consultations.size > FreeLimits.MAX_CONSULTATIONS_FREE) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "⚠️ Limite de ${FreeLimits.MAX_CONSULTATIONS_FREE} consulta ativa atingido. Consultas inativas estão marcadas.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }

                        // 2. Primeiro item da lista (se existir)
                        if (consultations.isNotEmpty()) {
                            item(key = consultations[0].id) {
                                ConsultationListItem(
                                    consultation = consultations[0],
                                    isPremium = isPremium,
                                    onEditClick = {
                                        navController.navigate(
                                            "${Screen.ConsultationForm.route}?id=${consultations[0].id}"
                                        )
                                    },
                                    onDeleteClick = {
                                        consultationToDelete = consultations[0]
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }

                        // 3. Anúncio (Sempre aparece aqui se for FREE, sendo o 2º item ou o 1º se a lista estiver vazia)
                        if (!isPremium) {
                            item(key = "ad_item") {
                                NativeAdCard(
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // 4. Restante dos itens (do índice 1 em diante)
                        if (consultations.size > 1) {
                            items(
                                items = consultations.subList(1, consultations.size),
                                key = { it.id } // Corrigido: Removido operador Elvis desnecessário
                            ) { consultation ->
                                ConsultationListItem(
                                    consultation = consultation,
                                    isPremium = isPremium,
                                    onEditClick = {
                                        navController.navigate(
                                            "${Screen.ConsultationForm.route}?id=${consultation.id}"
                                        )
                                    },
                                    onDeleteClick = {
                                        consultationToDelete = consultation
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }

                        // 5. Mensagem de lista vazia (apenas se realmente não houver nada)
                        if (consultations.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillParentMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Nenhuma consulta encontrada.",
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConsultationListItem(
    consultation: Consultation,
    isPremium: Boolean = true,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    // Estilização diferente para consultas inativas (apenas para Free)
    val isInactive = !isPremium && !consultation.isActive
    val cardAlpha = if (isInactive) 0.6f else 1f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isInactive)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = cardAlpha)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isInactive) 1.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = consultation.specialty,
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isInactive)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            else
                                MaterialTheme.colorScheme.onSurface
                        )

                        // Badge de status para consultas inativas
                        if (isInactive) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "Inativa",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${consultation.doctorName.ifEmpty { "Não informado" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (consultation.patientName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Paciente: ${consultation.patientName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Excluir",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.DateRange,
                    contentDescription = "Data e Hora",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = consultation.dateTime.ifEmpty { "Data/Hora não informada" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (consultation.location.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = "Local",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = consultation.location,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (consultation.observations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Obs: ${consultation.observations}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}
