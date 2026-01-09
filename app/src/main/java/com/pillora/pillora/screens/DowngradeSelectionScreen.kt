package com.pillora.pillora.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.repository.ConsultationRepository
import com.pillora.pillora.repository.MedicineRepository
import com.pillora.pillora.utils.FreeLimits
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tela obrigatória de seleção de itens para downgrade.
 * O usuário deve selecionar até 3 medicamentos e 1 consulta para manter ativos.
 * Esta tela não pode ser fechada sem completar a seleção.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DowngradeSelectionScreen(
    onDowngradeComplete: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as PilloraApplication
    val userPreferences = application.userPreferences
    val scope = rememberCoroutineScope()

    // Estados para medicamentos e consultas
    var medicines by remember { mutableStateOf<List<Medicine>>(emptyList()) }
    var consultations by remember { mutableStateOf<List<Consultation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Estados de seleção
    var selectedMedicineIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedConsultationId by remember { mutableStateOf<String?>(null) }

    // Estado para controlar se está salvando
    var isSaving by remember { mutableStateOf(false) }

    // Bloquear botão voltar do sistema
    BackHandler(enabled = true) {
        Toast.makeText(
            context,
            "Você precisa selecionar os itens para continuar usando o app",
            Toast.LENGTH_LONG
        ).show()
    }

    // Carregar dados
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null

        // Carregar medicamentos
        MedicineRepository.getAllMedicinesFlow()
            .catch { exception ->
                errorMessage = "Erro ao carregar medicamentos: ${exception.message}"
            }
            .collect { medicinesList ->
                medicines = medicinesList
                // Pré-selecionar medicamentos já ativos (até o limite)
                val activeIds = medicinesList
                    .filter { it.alarmsEnabled }
                    .take(FreeLimits.MAX_MEDICINES_FREE)
                    .mapNotNull { it.id }
                    .toSet()
                selectedMedicineIds = activeIds
            }
    }

    LaunchedEffect(Unit) {
        // Carregar consultas
        ConsultationRepository.getAllConsultationsFlow()
            .catch { exception ->
                errorMessage = "Erro ao carregar consultas: ${exception.message}"
            }
            .collect { consultationsList ->
                consultations = consultationsList
                // Pré-selecionar primeira consulta ativa
                val activeConsultation = consultationsList.firstOrNull { it.isActive }
                selectedConsultationId = activeConsultation?.id
                isLoading = false
            }
    }

    // Validação
    val canContinue = selectedMedicineIds.size <= FreeLimits.MAX_MEDICINES_FREE &&
            (consultations.isEmpty() || selectedConsultationId != null || consultations.size <= FreeLimits.MAX_CONSULTATIONS_FREE)

    val medicinesOverLimit = medicines.size > FreeLimits.MAX_MEDICINES_FREE
    val consultationsOverLimit = consultations.size > FreeLimits.MAX_CONSULTATIONS_FREE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Ajuste seu Plano",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    titleContentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cabeçalho explicativo
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Sua assinatura Premium expirou",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            Text(
                                text = "Seu plano Premium terminou. Para continuar usando o Pillora no plano gratuito, selecione quais itens deseja manter ativos.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f))

                            Text(
                                text = "Limites do Plano Gratuito:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                LimitChip(
                                    label = "Medicamentos",
                                    limit = FreeLimits.MAX_MEDICINES_FREE,
                                    current = selectedMedicineIds.size,
                                    total = medicines.size
                                )
                                LimitChip(
                                    label = "Consultas",
                                    limit = FreeLimits.MAX_CONSULTATIONS_FREE,
                                    current = if (selectedConsultationId != null) 1 else 0,
                                    total = consultations.size
                                )
                            }
                        }
                    }
                }

                // Seção de Medicamentos
                if (medicinesOverLimit) {
                    item {
                        Text(
                            text = "Selecione até ${FreeLimits.MAX_MEDICINES_FREE} medicamentos para manter ativos:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    item {
                        Text(
                            text = "Os medicamentos não selecionados terão seus alarmes desativados, mas continuarão salvos no app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(medicines, key = { it.id ?: it.hashCode() }) { medicine ->
                        MedicineSelectionItem(
                            medicine = medicine,
                            isSelected = selectedMedicineIds.contains(medicine.id),
                            canSelect = selectedMedicineIds.size < FreeLimits.MAX_MEDICINES_FREE ||
                                    selectedMedicineIds.contains(medicine.id),
                            onSelectionChange = { isSelected ->
                                medicine.id?.let { id ->
                                    selectedMedicineIds = if (isSelected) {
                                        if (selectedMedicineIds.size < FreeLimits.MAX_MEDICINES_FREE) {
                                            selectedMedicineIds + id
                                        } else {
                                            selectedMedicineIds
                                        }
                                    } else {
                                        selectedMedicineIds - id
                                    }
                                }
                            }
                        )
                    }
                } else if (medicines.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Você tem ${medicines.size} medicamento(s), dentro do limite gratuito. Todos serão mantidos ativos.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // Seção de Consultas
                if (consultationsOverLimit) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Selecione ${FreeLimits.MAX_CONSULTATIONS_FREE} consulta para manter ativa:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    item {
                        Text(
                            text = "As consultas não selecionadas serão desativadas, mas continuarão salvas no app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(consultations, key = { it.id }) { consultation ->
                        ConsultationSelectionItem(
                            consultation = consultation,
                            isSelected = selectedConsultationId == consultation.id,
                            onSelectionChange = { isSelected ->
                                selectedConsultationId = if (isSelected) consultation.id else null
                            }
                        )
                    }
                } else if (consultations.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Você tem ${consultations.size} consulta(s), dentro do limite gratuito. Todas serão mantidas ativas.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // Botão Continuar
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    val buttonEnabled = !isSaving && (
                            (!medicinesOverLimit || selectedMedicineIds.size <= FreeLimits.MAX_MEDICINES_FREE) &&
                                    (!consultationsOverLimit || selectedConsultationId != null)
                            )

                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                errorMessage = null

                                // Calcular quantas operações precisamos fazer
                                val medicinesToUpdate = if (medicinesOverLimit) {
                                    medicines.filter { medicine ->
                                        medicine.id != null && medicine.alarmsEnabled != selectedMedicineIds.contains(medicine.id)
                                    }
                                } else emptyList()

                                val consultationsToUpdate = if (consultationsOverLimit) {
                                    consultations.filter { consultation ->
                                        // CORREÇÃO: Filtrar apenas consultas com ID válido E que precisam ser atualizadas
                                        consultation.id.isNotEmpty() &&
                                                consultation.isActive != (consultation.id == selectedConsultationId)
                                    }
                                } else emptyList()

                                val totalOperations = medicinesToUpdate.size + consultationsToUpdate.size

                                // Se não há nada para atualizar, apenas finaliza
                                if (totalOperations == 0) {
                                    userPreferences.setDowngradeCompleted()
                                    Toast.makeText(
                                        context,
                                        "Configurações salvas com sucesso!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    isSaving = false
                                    onDowngradeComplete()
                                    return@launch
                                }

                                // Contadores thread-safe para rastrear operações
                                val completedOperations = AtomicInteger(0)
                                val allSuccessful = AtomicBoolean(true)

                                val checkCompletion: () -> Unit = {
                                    if (completedOperations.get() == totalOperations) {
                                        scope.launch {
                                            if (allSuccessful.get()) {
                                                userPreferences.setDowngradeCompleted()
                                                Toast.makeText(
                                                    context,
                                                    "Configurações salvas com sucesso!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                onDowngradeComplete()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Algumas atualizações falharam. Tente novamente.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                            isSaving = false
                                        }
                                    }
                                }

                                // Desativar medicamentos não selecionados
                                medicinesToUpdate.forEach { medicine ->
                                    medicine.id?.let { id ->
                                        val shouldBeActive = selectedMedicineIds.contains(id)
                                        MedicineRepository.updateMedicineAlarmsEnabled(
                                            medicineId = id,
                                            alarmsEnabled = shouldBeActive,
                                            onSuccess = {
                                                completedOperations.incrementAndGet()
                                                checkCompletion()
                                            },
                                            onError = { e ->
                                                allSuccessful.set(false)
                                                errorMessage = "Erro ao atualizar medicamento: ${e.message}"
                                                completedOperations.incrementAndGet()
                                                checkCompletion()
                                            }
                                        )
                                    }
                                }

                                // Desativar consultas não selecionadas
                                consultationsToUpdate.forEach { consultation ->
                                    // CORREÇÃO: Verificar se o ID não está vazio antes de atualizar
                                    if (consultation.id.isNotEmpty()) {
                                        val shouldBeActive = consultation.id == selectedConsultationId
                                        ConsultationRepository.updateConsultationActiveStatus(
                                            consultationId = consultation.id,
                                            isActive = shouldBeActive,
                                            onSuccess = {
                                                completedOperations.incrementAndGet()
                                                checkCompletion()
                                            },
                                            onFailure = { e ->
                                                allSuccessful.set(false)
                                                errorMessage = "Erro ao atualizar consulta: ${e.message}"
                                                completedOperations.incrementAndGet()
                                                checkCompletion()
                                            }
                                        )
                                    } else {
                                        // Se o ID está vazio, pular esta consulta e contar como completada
                                        Log.w("DowngradeSelection", "Consulta com ID vazio encontrada, pulando atualização")
                                        completedOperations.incrementAndGet()
                                        checkCompletion()
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = buttonEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                text = "Continuar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Mensagem de erro se houver
                    if (!buttonEnabled && !isSaving) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                medicinesOverLimit && selectedMedicineIds.size > FreeLimits.MAX_MEDICINES_FREE ->
                                    "Selecione no máximo ${FreeLimits.MAX_MEDICINES_FREE} medicamentos"
                                consultationsOverLimit && selectedConsultationId == null ->
                                    "Selecione 1 consulta para manter ativa"
                                else -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Mostrar mensagem de erro se houver
                    errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Opção de renovar assinatura
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            // TODO: Navegar para tela de assinatura
                            Toast.makeText(
                                context,
                                "Funcionalidade de renovação será implementada",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Renovar Assinatura Premium")
                    }
                }

                // Espaço extra no final
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun LimitChip(
    label: String,
    limit: Int,
    current: Int,
    total: Int
) {
    val isOverLimit = total > limit
    val backgroundColor = if (isOverLimit) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    }
    val textColor = if (isOverLimit) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
            Text(
                text = if (isOverLimit) "$current/$limit" else "$total/$limit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            if (isOverLimit) {
                Text(
                    text = "(total: $total)",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun MedicineSelectionItem(
    medicine: Medicine,
    isSelected: Boolean,
    canSelect: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        !canSelect -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = canSelect || isSelected) {
                onSelectionChange(!isSelected)
            },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medicine.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (medicine.recipientName.isNotBlank()) {
                    Text(
                        text = "Para: ${medicine.recipientName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "Dose: ${medicine.dose}${medicine.doseUnit?.let { " $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val frequencyText = when (medicine.frequencyType) {
                    "vezes_dia" -> "${medicine.timesPerDay ?: "?"}x ao dia"
                    "a_cada_x_horas" -> "A cada ${medicine.intervalHours ?: "?"} horas"
                    else -> "Frequência não definida"
                }
                Text(
                    text = frequencyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isSelected,
                onCheckedChange = { onSelectionChange(it) },
                enabled = canSelect || isSelected,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun ConsultationSelectionItem(
    consultation: Consultation,
    isSelected: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelectionChange(!isSelected) },
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = consultation.specialty,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Dr(a). ${consultation.doctorName.ifEmpty { "Não informado" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (consultation.patientName.isNotBlank()) {
                    Text(
                        text = "Paciente: ${consultation.patientName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = consultation.dateTime.ifEmpty { "Data não definida" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (consultation.location.isNotBlank()) {
                    Text(
                        text = consultation.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Radio button para seleção única
            RadioButton(
                selected = isSelected,
                onClick = { onSelectionChange(!isSelected) },
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.outline
                )
            )
        }
    }
}
