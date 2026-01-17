package com.pillora.pillora.viewmodel

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.pillora.pillora.data.dao.LembreteDao
import com.pillora.pillora.model.Lembrete
import com.pillora.pillora.model.PrescribedMedication
import com.pillora.pillora.model.Recipe
import com.pillora.pillora.repository.RecipeRepository
import com.pillora.pillora.utils.DateTimeUtils
import com.pillora.pillora.workers.NotificationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// Represents the different states for the Recipe List screen
sealed class RecipeListUiState {
    data object Loading : RecipeListUiState()
    data class Success(val recipes: List<Recipe>) : RecipeListUiState()
    data class Error(val message: String) : RecipeListUiState()
}

// Represents the different states for the Recipe Form/Detail screen
sealed class RecipeDetailUiState {
    data object Loading : RecipeDetailUiState()
    data class Success(val recipe: Recipe) : RecipeDetailUiState() // State for when data is loaded successfully
    data class Error(val message: String) : RecipeDetailUiState()
    data object OperationSuccess : RecipeDetailUiState() // State for when save/update/delete is successful
    data object Idle : RecipeDetailUiState() // Initial state or after an operation
}

class RecipeViewModel : ViewModel() {

    private val tag = "RecipeViewModel"
    private val auth = FirebaseAuth.getInstance()

    private val _recipeListState = MutableStateFlow<RecipeListUiState>(RecipeListUiState.Loading)
    val recipeListState: StateFlow<RecipeListUiState> = _recipeListState.asStateFlow()

    private val _recipeDetailState = MutableStateFlow<RecipeDetailUiState>(RecipeDetailUiState.Idle)
    val recipeDetailState: StateFlow<RecipeDetailUiState> = _recipeDetailState.asStateFlow()

    var recipeUiState by mutableStateOf(RecipeFormData())
        private set

    var prescribedMedicationsState by mutableStateOf<List<PrescribedMedication>>(emptyList())
        private set

    var currentPrescribedMedicationState by mutableStateOf(PrescribedMedication())
        private set

    var editingMedicationIndex by mutableIntStateOf(-1)
        private set

    init {
        loadRecipes()
    }

    private fun loadRecipes() {
        viewModelScope.launch {
            RecipeRepository.getAllRecipesFlow()
                .catch { e ->
                    _recipeListState.value = RecipeListUiState.Error("Erro ao carregar receitas: ${e.message}")
                }
                .collect { recipes ->
                    _recipeListState.value = RecipeListUiState.Success(recipes.sortedByDescending { parseDate(it.prescriptionDate) })
                }
        }
    }

    private fun parseDate(dateString: String): Long {
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            sdf.isLenient = false
            sdf.parse(dateString)?.time ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun loadRecipeDetails(recipeId: String) {
        if (recipeId.isBlank()) {
            resetFormState()
            _recipeDetailState.value = RecipeDetailUiState.Idle
            return
        }

        _recipeDetailState.value = RecipeDetailUiState.Loading
        RecipeRepository.getRecipeById(
            recipeId = recipeId,
            onSuccess = { recipe ->
                if (recipe != null) {
                    updateFormState(recipe)
                    _recipeDetailState.value = RecipeDetailUiState.Success(recipe)
                } else {
                    _recipeDetailState.value = RecipeDetailUiState.Error("Receita não encontrada.")
                }
            },
            onError = { exception ->
                _recipeDetailState.value = RecipeDetailUiState.Error("Erro ao carregar detalhes da receita: ${exception.message}")
            }
        )
    }

    fun resetDetailState() {
        _recipeDetailState.value = RecipeDetailUiState.Idle
    }

    // *** CORRIGIDO: Chamadas de agendamento/cancelamento dentro de viewModelScope.launch ***
    fun saveOrUpdateRecipe(context: Context, lembreteDao: LembreteDao) {
        val recipeToSave = createRecipeFromFormState()
        if (!validateRecipe(recipeToSave)) return

        _recipeDetailState.value = RecipeDetailUiState.Loading

        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            _recipeDetailState.value = RecipeDetailUiState.Error("Usuário não autenticado.")
            return
        }

        val finalRecipe = recipeToSave.copy(userId = recipeUiState.userId ?: currentUserId)

        if (recipeUiState.id == null) {
            // Adicionando nova receita
            RecipeRepository.saveRecipe(
                recipe = finalRecipe,
                onSuccess = { newRecipeId -> // Recebe o ID da nova receita
                    if (newRecipeId != null) {
                        Log.d(tag, "Receita adicionada com ID: $newRecipeId")
                        // *** CORRIGIDO: Chamar função suspend dentro de launch ***
                        viewModelScope.launch {
                            agendarOuAtualizarLembretesReceita(context, lembreteDao, newRecipeId, finalRecipe)
                        }
                        _recipeDetailState.value = RecipeDetailUiState.OperationSuccess
                        resetFormState()
                    } else {
                        Log.e(tag, "Erro: ID da nova receita não retornado pelo repositório.")
                        _recipeDetailState.value = RecipeDetailUiState.Error("Erro ao salvar receita (ID não retornado).")
                    }
                },
                onError = { exception ->
                    _recipeDetailState.value = RecipeDetailUiState.Error("Erro ao salvar receita: ${exception.message}")
                }
            )
        } else {
            // Atualizando receita existente
            val currentRecipeId = recipeUiState.id!!
            if (finalRecipe.userId != currentUserId) {
                _recipeDetailState.value = RecipeDetailUiState.Error("Erro de permissão ao atualizar receita.")
                return
            }
            RecipeRepository.updateRecipe(
                recipeId = currentRecipeId,
                recipe = finalRecipe,
                onSuccess = { // onSuccess não recebe parâmetros aqui
                    Log.d(tag, "Receita atualizada com ID: $currentRecipeId")
                    // *** CORRIGIDO: Chamar função suspend dentro de launch ***
                    viewModelScope.launch {
                        agendarOuAtualizarLembretesReceita(context, lembreteDao, currentRecipeId, finalRecipe)
                    }
                    _recipeDetailState.value = RecipeDetailUiState.OperationSuccess
                    resetFormState()
                },
                onError = { exception ->
                    _recipeDetailState.value = RecipeDetailUiState.Error("Erro ao atualizar receita: ${exception.message}")
                }
            )
        }
    }

    // *** CORRIGIDO: Chamada de cancelamento dentro de viewModelScope.launch ***
    fun deleteRecipe(context: Context, lembreteDao: LembreteDao, recipeId: String) {
        _recipeDetailState.value = RecipeDetailUiState.Loading
        RecipeRepository.deleteRecipe(
            recipeId = recipeId,
            onSuccess = { // onSuccess não recebe parâmetros aqui
                Log.d(tag, "Receita deletada com ID: $recipeId")
                // *** CORRIGIDO: Chamar função suspend dentro de launch ***
                viewModelScope.launch {
                    cancelarLembretesReceita(context, lembreteDao, recipeId)
                }
                _recipeDetailState.value = RecipeDetailUiState.OperationSuccess
                resetFormState()
            },
            onError = { exception ->
                _recipeDetailState.value = RecipeDetailUiState.Error("Erro ao deletar receita: ${exception.message}")
            }
        )
    }

    // --- Form State Management (sem alterações) ---
    fun updatePatientName(name: String) { recipeUiState = recipeUiState.copy(patientName = name) }
    fun updateDoctorName(name: String) { recipeUiState = recipeUiState.copy(doctorName = name) }
    fun updateCrm(crm: String) { recipeUiState = recipeUiState.copy(crm = crm) }
    fun updatePrescriptionDate(date: String) { recipeUiState = recipeUiState.copy(prescriptionDate = date) }
    fun updateGeneralInstructions(instructions: String) { recipeUiState = recipeUiState.copy(generalInstructions = instructions) }
    fun updateNotes(notes: String) { recipeUiState = recipeUiState.copy(notes = notes) }
    fun updateValidityDate(date: String) { recipeUiState = recipeUiState.copy(validityDate = date) }

    // --- Prescribed Medication Form Management (sem alterações) ---
    fun updateCurrentMedName(name: String) { currentPrescribedMedicationState = currentPrescribedMedicationState.copy(name = name) }
    fun updateCurrentMedDose(dose: String) { currentPrescribedMedicationState = currentPrescribedMedicationState.copy(dose = dose) }
    fun updateCurrentMedInstructions(instructions: String) { currentPrescribedMedicationState = currentPrescribedMedicationState.copy(instructions = instructions) }

    fun startEditingPrescribedMedication(index: Int) {
        if (index in prescribedMedicationsState.indices) {
            editingMedicationIndex = index
            currentPrescribedMedicationState = prescribedMedicationsState[index]
        }
    }

    fun cancelEditingPrescribedMedication() {
        editingMedicationIndex = -1
        currentPrescribedMedicationState = PrescribedMedication() // Reset form
    }

    fun saveOrUpdateCurrentPrescribedMedication() {
        if (currentPrescribedMedicationState.name.isBlank()) return // Basic validation

        val currentList = prescribedMedicationsState.toMutableList()
        if (editingMedicationIndex != -1 && editingMedicationIndex in currentList.indices) {
            currentList[editingMedicationIndex] = currentPrescribedMedicationState
        } else {
            currentList.add(currentPrescribedMedicationState)
        }
        prescribedMedicationsState = currentList.toList()
        cancelEditingPrescribedMedication()
    }

    fun removePrescribedMedication(index: Int) {
        if (index in prescribedMedicationsState.indices) {
            val currentList = prescribedMedicationsState.toMutableList()
            currentList.removeAt(index)
            prescribedMedicationsState = currentList.toList()
            if (index == editingMedicationIndex) {
                cancelEditingPrescribedMedication()
            }
        }
    }

    // --- Helper Functions (sem alterações) ---
    private fun resetFormState() {
        recipeUiState = RecipeFormData()
        prescribedMedicationsState = emptyList()
        cancelEditingPrescribedMedication()
    }

    private fun updateFormState(recipe: Recipe) {
        recipeUiState = RecipeFormData(
            id = recipe.id,
            userId = recipe.userId,
            patientName = recipe.patientName,
            doctorName = recipe.doctorName,
            crm = recipe.crm,
            prescriptionDate = recipe.prescriptionDate,
            validityDate = recipe.validityDate,
            generalInstructions = recipe.generalInstructions,
            notes = recipe.notes,
            imageUri = recipe.imageUri
        )
        prescribedMedicationsState = recipe.prescribedMedications
        cancelEditingPrescribedMedication()
    }

    private fun createRecipeFromFormState(): Recipe {
        return Recipe(
            id = recipeUiState.id,
            userId = recipeUiState.userId,
            patientName = recipeUiState.patientName.trim(),
            doctorName = recipeUiState.doctorName.trim(),
            crm = recipeUiState.crm.trim(),
            prescriptionDate = recipeUiState.prescriptionDate.trim(),
            validityDate = recipeUiState.validityDate.trim(),
            prescribedMedications = prescribedMedicationsState,
            generalInstructions = recipeUiState.generalInstructions.trim(),
            notes = recipeUiState.notes.trim(),
            imageUri = recipeUiState.imageUri
        )
    }

    private fun validateRecipe(recipe: Recipe): Boolean {
        var isValid = true
        var errorMessage = ""

        if (recipe.doctorName.isBlank()) {
            errorMessage += "Nome do médico não pode estar vazio.\n"
            isValid = false
        }
        if (recipe.prescriptionDate.isBlank()) {
            errorMessage += "Data da prescrição não pode estar vazia.\n"
            isValid = false
        } else if (!isValidDate(recipe.prescriptionDate)) {
            errorMessage += "Formato da data da prescrição inválido (use DD/MM/YYYY).\n"
            isValid = false
        }

        if (recipe.validityDate.isBlank()) {
            errorMessage += "Data de validade não pode estar vazia.\n"
            isValid = false
        } else if (!isValidDate(recipe.validityDate)) {
            errorMessage += "Formato da data de validade inválido (use DD/MM/YYYY).\n"
            isValid = false
        }

        if (recipe.prescribedMedications.isEmpty()) {
            errorMessage += "Adicione pelo menos um medicamento prescrito.\n"
            isValid = false
        }

        if (!isValid) {
            _recipeDetailState.value = RecipeDetailUiState.Error(errorMessage.trim())
        }

        return isValid
    }

    private fun isValidDate(dateStr: String): Boolean {
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            sdf.isLenient = false
            sdf.parse(dateStr)
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- Recipe Reminder Scheduling Logic ---

    // *** CORRIGIDO: Marcada como suspend pois chama DAO (suspend) ***
    private suspend fun agendarOuAtualizarLembretesReceita(context: Context, lembreteDao: LembreteDao, recipeId: String, recipe: Recipe) {
        // Executar em background thread
        withContext(Dispatchers.IO) {
            try {
                // 1. Cancelar e excluir lembretes antigos
                cancelarLembretesReceita(context, lembreteDao, recipeId)

                // 2. Calcular novos lembretes
                val lembretesInfo = DateTimeUtils.calcularLembretesReceita(recipe.validityDate)
                Log.d(tag, "Calculados ${lembretesInfo.size} lembretes para receita $recipeId com validade ${recipe.validityDate}")

                if (lembretesInfo.isEmpty()) {
                    Log.w(tag, "Nenhum lembrete futuro calculado para receita $recipeId.")
                    return@withContext
                }

                // 3. Salvar e agendar novos lembretes
                var lembretesAgendados = 0
                lembretesInfo.forEach { lembreteInfo ->
                    val calendar = Calendar.getInstance().apply { timeInMillis = lembreteInfo.timestamp }
                    val isConfirmacao = lembreteInfo.tipo == DateTimeUtils.TIPO_RECEITA_CONFIRMACAO

                    val lembrete = Lembrete(
                        medicamentoId = recipeId,
                        nomeMedicamento = "Receita Dr(a). ${recipe.doctorName}",
                        recipientName = recipe.patientName.ifBlank { null },
                        hora = calendar.get(Calendar.HOUR_OF_DAY),
                        minuto = calendar.get(Calendar.MINUTE),
                        dose = lembreteInfo.tipo,
                        observacao = "Validade: ${recipe.validityDate}",
                        proximaOcorrenciaMillis = lembreteInfo.timestamp,
                        ativo = true,
                        isReceita = true,
                        isConfirmacao = isConfirmacao
                    )

                    try {
                        val lembreteId = lembreteDao.insertLembrete(lembrete)
                        Log.d(tag, "Lembrete de receita salvo no DB: ID=$lembreteId, Tipo=${lembrete.dose}")
                        agendarNotificacaoReceita(context, lembrete.copy(id = lembreteId))
                        lembretesAgendados++
                    } catch (e: Exception) {
                        Log.e(tag, "Erro ao salvar/agendar lembrete de receita (Tipo: ${lembreteInfo.tipo})", e)
                    }
                }
                Log.d(tag, "Total de $lembretesAgendados lembretes de receita agendados para $recipeId.")

            } catch (e: Exception) {
                Log.e(tag, "Erro geral ao agendar/atualizar lembretes para receita $recipeId", e)
                // Usar Main dispatcher para mostrar Toast
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erro ao agendar lembretes da receita.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // *** CORRIGIDO: Marcada como suspend pois chama DAO (suspend) ***
    private suspend fun cancelarLembretesReceita(context: Context, lembreteDao: LembreteDao, recipeId: String) {
        // Já está em Dispatchers.IO por causa da chamada
        try {
            val lembretesAntigos = lembreteDao.getLembretesByMedicamentoId(recipeId).filter { it.isReceita }
            if (lembretesAntigos.isNotEmpty()) {
                Log.d(tag, "Cancelando ${lembretesAntigos.size} lembretes antigos para receita $recipeId.")
                lembretesAntigos.forEach { lembreteAntigo ->
                    val workTag = "receita_${recipeId}_${lembreteAntigo.id}"
                    WorkManager.getInstance(context).cancelAllWorkByTag(workTag)
                    Log.d(tag, "WorkManager job cancelado para tag: $workTag (Lembrete ID: ${lembreteAntigo.id})")
                }
                lembreteDao.deleteLembretes(lembretesAntigos)
                Log.d(tag, "Excluídos ${lembretesAntigos.size} lembretes antigos de receita do DB para $recipeId")
            }
        } catch (e: Exception) {
            Log.e(tag, "Erro ao cancelar lembretes antigos para receita $recipeId", e)
        }
    }

    private fun agendarNotificacaoReceita(context: Context, lembrete: Lembrete) {
        // Já está em Dispatchers.IO
        try {
            val agora = System.currentTimeMillis()
            var atraso = lembrete.proximaOcorrenciaMillis - agora

            // Se o atraso for negativo ou muito pequeno, agendar para daqui a 5 segundos
            if (atraso <= 0) {
                atraso = 5000 // 5 segundos
                Log.d(tag, "Atraso era <= 0 para notificação de receita. Ajustando para 5 segundos. Lembrete ID: ${lembrete.id}")
            }
            Log.d(tag, "Agendando WorkManager para Lembrete Receita ID: ${lembrete.id}, Tipo: ${lembrete.dose}, Atraso: $atraso ms")

            val workData = Data.Builder()
                .putLong(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
                .putString(NotificationWorker.EXTRA_MEDICAMENTO_ID, lembrete.medicamentoId) // Guarda o Recipe ID
                .putString(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
                .putString(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.observacao ?: "")
                .putString(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName ?: "")
                .putLong(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
                .putInt(NotificationWorker.EXTRA_HORA, lembrete.hora)
                .putInt(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
                .putBoolean(NotificationWorker.EXTRA_IS_RECEITA, true)
                .putBoolean(NotificationWorker.EXTRA_IS_CONFIRMACAO, lembrete.isConfirmacao)
                .putString(NotificationWorker.EXTRA_TIPO_LEMBRETE, lembrete.dose)
                .build()

            val workTag = "receita_${lembrete.medicamentoId}_${lembrete.id}"

            val notificationWorkRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInputData(workData)
                .setInitialDelay(atraso, TimeUnit.MILLISECONDS)
                .addTag(workTag)
                .build()

            WorkManager.getInstance(context).enqueue(notificationWorkRequest)
            val dataFormatada = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(lembrete.proximaOcorrenciaMillis))
            Log.d(tag, "WorkManager agendado para Lembrete Receita ID ${lembrete.id} em $dataFormatada com tag $workTag")

        } catch (e: Exception) {
            Log.e(tag, "Erro ao agendar notificação de receita via WorkManager para Lembrete ID: ${lembrete.id}", e)
        }
    }

}

data class RecipeFormData(
    val id: String? = null,
    val userId: String? = null,
    val patientName: String = "",
    val doctorName: String = "",
    val crm: String = "",
    val prescriptionDate: String = "",
    val validityDate: String = "", // Crucial para lembretes
    val generalInstructions: String = "",
    val notes: String = "",
    val imageUri: String? = null
)

