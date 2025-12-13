package com.pillora.pillora.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.model.Recipe // Import Recipe model
import com.pillora.pillora.model.Vaccine // Importar Vaccine
import com.pillora.pillora.repository.ConsultationRepository
import com.pillora.pillora.repository.DataResult // *** ADICIONADO IMPORT NECESSÁRIO ***
import com.pillora.pillora.repository.MedicineRepository
import com.pillora.pillora.repository.RecipeRepository // Import RecipeRepository
import com.pillora.pillora.repository.VaccineRepository // Importar VaccineRepository
import com.pillora.pillora.data.local.AppDatabase // Adicionado
import com.pillora.pillora.utils.AlarmScheduler // Adicionado
import kotlinx.coroutines.Dispatchers // Adicionado
import kotlinx.coroutines.launch // Adicionado
import kotlinx.coroutines.withContext // Adicionado para mudar contexto de thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted // Importar SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context
// import java.util.concurrent.TimeUnit // Importar TimeUnit

class HomeViewModel : ViewModel() {

    private val tag = "HomeViewModel_DEBUG" // Tag for logs
    // Date formatters for parsing
    private val sdfWithSlashes = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false }
    private val sdfWithoutSlashes = SimpleDateFormat("ddMMyyyy", Locale.getDefault()).apply { isLenient = false }

    // Input Flows from Repositories
    private val medicinesFlow = MedicineRepository.getAllMedicinesFlow()
    private val consultationsFlow = ConsultationRepository.getAllConsultationsFlow()
    private val vaccinesFlow = VaccineRepository.getAllVaccinesFlow() // Retorna Flow<DataResult<List<Vaccine>>>
    private val recipesFlow = RecipeRepository.getAllRecipesFlow() // <<< ADDED: Recipe flow

    // Processed States for UI
    private val _medicinesToday = MutableStateFlow<List<Medicine>>(emptyList())
    val medicinesToday: StateFlow<List<Medicine>> = _medicinesToday

    private val _stockAlerts = MutableStateFlow<List<Medicine>>(emptyList())
    val stockAlerts: StateFlow<List<Medicine>> = _stockAlerts

    private val _upcomingConsultations = MutableStateFlow<List<Consultation>>(emptyList())
    val upcomingConsultations: StateFlow<List<Consultation>> = _upcomingConsultations

    private val _upcomingVaccines = MutableStateFlow<List<Vaccine>>(emptyList())
    val upcomingVaccines: StateFlow<List<Vaccine>> = _upcomingVaccines

    private val _expiringRecipes = MutableStateFlow<List<Recipe>>(emptyList()) // <<< ADDED: State for expiring recipes
    val expiringRecipes: StateFlow<List<Recipe>> = _expiringRecipes

    private val _allRecipes = MutableStateFlow<List<Recipe>>(emptyList()) // <<< NEW: State for ALL recipes
    val allRecipes: StateFlow<List<Recipe>> = _allRecipes

    // General States
    private val _isLoadingMedicines = MutableStateFlow(true)
    private val _isLoadingConsultations = MutableStateFlow(true)
    private val _isLoadingVaccines = MutableStateFlow(true)
    private val _isLoadingRecipes = MutableStateFlow(true) // <<< ADDED: Loading state for recipes
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Combined Loading State for UI
    val isLoading: StateFlow<Boolean> = combine(
        _isLoadingMedicines, _isLoadingConsultations, _isLoadingVaccines, _isLoadingRecipes // <<< UPDATED: Include recipes loading
    ) { medLoading, conLoading, vacLoading, recLoading ->
        medLoading || conLoading || vacLoading || recLoading
    }.catch { e ->
        Log.e(tag, "Error combining loading states", e)
        emit(false) // Emit false on error
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    init {
        Log.d(tag, "Initializing HomeViewModel and starting data collection.")
        observeMedicines()
        observeConsultations()
        observeVaccines() // *** ESTA FUNÇÃO FOI ALTERADA ***
        observeRecipes() // <<< ADDED: Start observing recipes
    }

    // --- Funções de observação (Medicamentos, Consultas, Receitas) - SEM ALTERAÇÕES ---
    private fun observeMedicines() {
        medicinesFlow
            .onEach { medicines ->
                Log.d(tag, "Received ${medicines.size} medicines from flow. Processing...")
                _isLoadingMedicines.value = false // Mark medicines as loaded
                processMedicines(medicines)
            }
            .catch { e ->
                Log.e(tag, "Error collecting medicines flow", e)
                _error.value = appendError("Erro ao carregar medicamentos: ${e.message}") // Use appendError
                _isLoadingMedicines.value = false
            }
            .launchIn(viewModelScope) // Collect the flow within the viewModelScope
    }

    private fun observeConsultations() {
        consultationsFlow
            .onEach { consultations ->
                Log.d(tag, "Received ${consultations.size} consultations from flow. Processing...")
                _isLoadingConsultations.value = false // Mark consultations as loaded
                processConsultations(consultations)
            }
            .catch { e ->
                Log.e(tag, "Error collecting consultations flow", e)
                _error.value = appendError("Erro ao carregar consultas: ${e.message}") // Use appendError
                _isLoadingConsultations.value = false
            }
            .launchIn(viewModelScope) // Collect the flow within the viewModelScope
    }

    private fun observeRecipes() {
        recipesFlow
            .onEach { recipes ->
                Log.d(tag, "Received ${recipes.size} recipes from flow. Processing...")
                _isLoadingRecipes.value = false // Mark recipes as loaded
                _allRecipes.value = recipes // <<< UPDATED: Populate all recipes list
                processExpiringRecipes(recipes)
            }
            .catch { e ->
                Log.e(tag, "Error collecting recipes flow", e)
                _error.value = appendError("Erro ao carregar receitas: ${e.message}")
                _isLoadingRecipes.value = false
            }
            .launchIn(viewModelScope)
    }
    // --- FIM Funções de observação (Medicamentos, Consultas, Receitas) ---

    // *** FUNÇÃO ALTERADA PARA TRATAR DataResult - APENAS ESTA FOI MODIFICADA ***
    private fun observeVaccines() {
        vaccinesFlow // Este flow agora emite DataResult<List<Vaccine>>
            .onEach { result -> // O parâmetro agora é 'result' do tipo DataResult
                when (result) {
                    is DataResult.Loading -> {
                        Log.d(tag, "Loading vaccines...")
                        _isLoadingVaccines.value = true // Atualiza o estado de carregamento
                    }
                    is DataResult.Success -> {
                        val vaccines = result.data // Extrai a lista de dentro do Success
                        Log.d(tag, "Received ${vaccines.size} vaccines from flow (Success). Processing...")
                        _isLoadingVaccines.value = false // Marca como carregado
                        processUpcomingVaccines(vaccines) // Chama o processamento com a lista extraída
                    }
                    is DataResult.Error -> {
                        Log.e(tag, "Error collecting vaccines flow: ${result.message}")
                        _error.value = appendError("Erro ao carregar vacinas: ${result.message}")
                        _isLoadingVaccines.value = false // Marca como não carregando (mesmo com erro)
                    }
                }
            }
            .catch { e -> // Captura exceções na coleta do Flow (raro se o catch já existe no repo)
                Log.e(tag, "Exception in vaccinesFlow collection", e)
                _error.value = appendError("Erro crítico ao observar vacinas: ${e.message}")
                _isLoadingVaccines.value = false
            }
            .launchIn(viewModelScope)
    }
    // *** FIM DA FUNÇÃO ALTERADA ***

    // --- Funções de processamento e helpers - SEM ALTERAÇÕES ---
    private fun processMedicines(medicines: List<Medicine>) {
        Log.d(tag, "Processing ${medicines.size} medicines for today and stock alerts.")
        val today = Calendar.getInstance()

        val activeToday = medicines.filter { med ->
            isMedicationActiveToday(med, today)
        }
        _medicinesToday.value = activeToday
        Log.d(tag, "Finished filtering medicines. ${_medicinesToday.value.size} active today.")

        val alertThresholdDays = 5
        _stockAlerts.value = medicines.filter { med ->
            if (!med.trackStock || med.stockQuantity <= 0) {
                false
            } else {
                val dailyDosage = calculateDailyDosage(med)
                if (dailyDosage <= 0) {
                    false
                } else {
                    val daysLeft = med.stockQuantity / dailyDosage
                    val needsAlert = daysLeft < alertThresholdDays
                    if(needsAlert) {
                        Log.d(tag, "Stock Alert for \'${med.name}\': Days left = $daysLeft (Threshold: $alertThresholdDays)")
                    }
                    needsAlert
                }
            }
        }
        Log.d(tag, "Finished checking stock alerts. ${_stockAlerts.value.size} alerts found.")
    }

    private fun processConsultations(consultations: List<Consultation>) {
        Log.d(tag, "Processing ${consultations.size} consultations for upcoming 7 days.")
        val todayStart = getStartOfDay(Calendar.getInstance())
        val sevenDaysLater = (todayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 7) }
        val sdfParse = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply { isLenient = false }

        val upcoming = mutableListOf<Pair<Calendar, Consultation>>()

        consultations.forEach { consultation ->
            try {
                sdfParse.parse(consultation.dateTime)?.let { dateTime ->
                    val consultationCal = Calendar.getInstance().apply { time = dateTime }
                    if (!consultationCal.before(todayStart) && consultationCal.before(sevenDaysLater)) {
                        upcoming.add(Pair(consultationCal, consultation))
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error parsing dateTime for consultation ID ${consultation.id}: \'${consultation.dateTime}\'", e)
            }
        }

        _upcomingConsultations.value = upcoming.sortedBy { it.first }.map { it.second }
        Log.d(tag, "Finished filtering consultations. ${_upcomingConsultations.value.size} upcoming in next 7 days.")
    }

    private fun processUpcomingVaccines(vaccines: List<Vaccine>) {
        Log.d(tag, "Processing ${vaccines.size} vaccines for upcoming 15 days.")
        val todayStart = getStartOfDay(Calendar.getInstance())
        val fifteenDaysLater = (todayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 15) }

        val upcoming = mutableListOf<Pair<Calendar, Vaccine>>()

        vaccines.forEach { vaccine ->
            parseDate(vaccine.reminderDate)?.let { vaccineCal -> // Use flexible parseDate
                val vaccineDayStart = getStartOfDay(vaccineCal)
                if (!vaccineDayStart.before(todayStart) && vaccineDayStart.before(fifteenDaysLater)) {
                    // Try to add time for sorting, default to start of day if invalid/missing
                    val timeCal = parseTime(vaccine.reminderTime)
                    if (timeCal != null) {
                        vaccineCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                        vaccineCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                    } else {
                        vaccineCal.set(Calendar.HOUR_OF_DAY, 0)
                        vaccineCal.set(Calendar.MINUTE, 0)
                    }
                    upcoming.add(Pair(vaccineCal, vaccine))
                }
            } ?: Log.e(tag, "Error parsing date for vaccine ID ${vaccine.id}: \'${vaccine.reminderDate}\'")
        }

        _upcomingVaccines.value = upcoming.sortedBy { it.first }.map { it.second }
        Log.d(tag, "Finished filtering vaccines. ${_upcomingVaccines.value.size} upcoming in next 15 days.")
    }

    private fun processExpiringRecipes(recipes: List<Recipe>) {
        val daysThreshold = 15 // Show recipes expiring in the next 15 days
        Log.d(tag, "Processing ${recipes.size} recipes for expiration within $daysThreshold days.")
        val todayStart = getStartOfDay(Calendar.getInstance())
        val thresholdDate = (todayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, daysThreshold) }

        val expiring = mutableListOf<Pair<Calendar, Recipe>>()

        recipes.forEach { recipe ->
            if (recipe.validityDate.isNotBlank()) {
                parseDate(recipe.validityDate)?.let { validityCal ->
                    val validityDayStart = getStartOfDay(validityCal)
                    // Check if validity date is today or in the future, AND before the threshold date
                    if (!validityDayStart.before(todayStart) && validityDayStart.before(thresholdDate)) {
                        expiring.add(Pair(validityDayStart, recipe))
                    }
                } ?: Log.e(tag, "Error parsing validityDate for recipe ID ${recipe.id}: \'${recipe.validityDate}\'")
            }
        }

        _expiringRecipes.value = expiring.sortedBy { it.first }.map { it.second }
        Log.d(tag, "Finished filtering recipes. ${_expiringRecipes.value.size} expiring in next $daysThreshold days.")
    }

    private fun appendError(newMessage: String): String {
        val currentError = _error.value
        return if (currentError.isNullOrBlank()) newMessage else "$currentError\n$newMessage"
    }

    // *** FUNÇÃO ADICIONADA PARA CORRIGIR ERRO NA HOMESCREEN ***
    fun onErrorShown() {
        _error.value = null
    }
    // *** FIM DA FUNÇÃO ADICIONADA ***

    private fun getStartOfDay(calendar: Calendar): Calendar {
        return (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun parseDate(dateStr: String): Calendar? {
        val trimmedDateStr = dateStr.trim()
        if (trimmedDateStr.isBlank()) return null
        try {
            sdfWithSlashes.parse(trimmedDateStr)?.let { return Calendar.getInstance().apply { time = it } }
        } catch (e: ParseException) { /* Try next format */ }
        try {
            sdfWithoutSlashes.parse(trimmedDateStr)?.let { return Calendar.getInstance().apply { time = it } }
        } catch (e: ParseException) { /* Failed both */ }
        Log.e(tag, "parseDate failed for string: \'$trimmedDateStr\' with both formats.")
        return null
    }

    private fun parseTime(timeStr: String?): Calendar? { // <<< Made timeStr nullable
        val trimmedTimeStr = timeStr?.trim()
        if (trimmedTimeStr.isNullOrBlank()) return null // <<< Check for null or blank
        return try {
            val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { isLenient = false }
            sdfTime.parse(trimmedTimeStr)?.let { Calendar.getInstance().apply { time = it } }
        } catch (e: Exception) {
            Log.w(tag, "Could not parse time string: \'$trimmedTimeStr\'" )
            null
        }
    }

    private fun isMedicationActiveToday(med: Medicine, today: Calendar): Boolean {
        val medInfo = "Med \'${med.name}\' (ID: ${med.id})"
        val startDateCal = parseDate(med.startDate) ?: run {
            Log.w(tag, "[$medInfo] Could not parse start date \'${med.startDate}\'. Result: INACTIVE")
            return false
        }
        val todayStart = getStartOfDay(today)

        if (todayStart.before(startDateCal)) {
            return false
        }

        if (med.duration > 0) { // Only check if duration is positive
            val endDateCal = (startDateCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, med.duration) // End date is exclusive
            }
            if (!todayStart.before(endDateCal)) {
                return false // Today is on or after the end date
            }
        }
        return true
    }

    private fun calculateDailyDosage(med: Medicine): Double {
        val doseValue = med.dose.toDoubleOrNull() ?: 0.0 // Removido safe call desnecessário
        if (doseValue <= 0.0) return 0.0

        return when (med.frequencyType) {
            "vezes_dia" -> {
                (med.timesPerDay ?: 0) * doseValue
            }
            "a_cada_x_horas" -> {
                val interval = med.intervalHours ?: 0
                if (interval > 0) {
                    (24.0 / interval) * doseValue
                } else {
                    0.0
                }
            }
            else -> 0.0
        }
    }
    // --- FIM Funções de processamento e helpers ---

    // --- Funções de Exclusão ---

    fun deleteMedicine(
        context: Context,
        medicineId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Buscar todos os lembretes associados ao medicamento no Room
            val lembreteDao = AppDatabase.getDatabase(context).lembreteDao()
            val lembretes = lembreteDao.getLembretesByMedicamentoId(medicineId)

            // 2. Cancelar alarmes e excluir lembretes localmente
            lembretes.forEach { lembrete ->
                // Cancelar o alarme no AlarmManager
                AlarmScheduler.cancelAlarm(context, lembrete.id)
                // Excluir o lembrete do Room (opcional, mas mais limpo)
                lembreteDao.deleteLembrete(lembrete)
                Log.d(tag, "Lembrete ID ${lembrete.id} (Medicamento $medicineId) cancelado e excluído localmente.")
            }

            // 3. Excluir o medicamento do Firestore
            MedicineRepository.deleteMedicine(
                medicineId = medicineId,
                onSuccess = {
                    Log.d(tag, "Medicamento $medicineId excluído do Firestore com sucesso.")
                    onSuccess()
                },
                onError = { exception ->
                    Log.e(tag, "Erro ao excluir medicamento $medicineId do Firestore.", exception)
                    // Se a exclusão do Firestore falhar, os lembretes locais já foram limpos,
                    // mas o medicamento pode reaparecer na UI se o Firestore for a fonte primária.
                    // O erro deve ser reportado.
                    onError(exception)
                }
            )
        }
    }

    // --- Função para ativar/desativar alarmes de um medicamento específico ---
    fun toggleMedicineAlarms(
        context: Context,
        medicineId: String,
        alarmsEnabled: Boolean,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Buscar o medicamento atual do Firestore
                MedicineRepository.getMedicineById(
                    medicineId = medicineId,
                    onSuccess = { medicine ->
                        if (medicine == null) {
                            onError(Exception("Medicamento não encontrado"))
                            return@getMedicineById
                        }

                        // 2. Atualizar o campo alarmsEnabled
                        val updatedMedicine = medicine.copy(alarmsEnabled = alarmsEnabled)

                        // 3. Salvar no Firestore
                        MedicineRepository.updateMedicine(
                            medicineId = medicineId,
                            medicine = updatedMedicine,
                            onSuccess = {
                                Log.d(tag, "Alarmes do medicamento $medicineId ${if (alarmsEnabled) "ativados" else "desativados"}")

                                // 4. Gerenciar alarmes locais
                                viewModelScope.launch(Dispatchers.IO) {
                                    val lembreteDao = AppDatabase.getDatabase(context).lembreteDao()
                                    val lembretes = lembreteDao.getLembretesByMedicamentoId(medicineId)

                                    if (alarmsEnabled) {
                                        // Reativar alarmes
                                        lembretes.forEach { lembrete ->
                                            AlarmScheduler.scheduleAlarm(context, lembrete)
                                            Log.d(tag, "Alarme reativado para lembrete ID ${lembrete.id}")
                                        }
                                    } else {
                                        // Desativar alarmes
                                        lembretes.forEach { lembrete ->
                                            AlarmScheduler.cancelAlarm(context, lembrete.id)
                                            Log.d(tag, "Alarme desativado para lembrete ID ${lembrete.id}")
                                        }
                                    }

                                    // Chamar onSuccess na thread principal (UI thread)
                                    withContext(Dispatchers.Main) {
                                        onSuccess()
                                    }
                                }
                            },
                            onError = { exception ->
                                Log.e(tag, "Erro ao atualizar alarmes do medicamento $medicineId", exception)
                                // Chamar onError na thread principal (UI thread)
                                viewModelScope.launch(Dispatchers.Main) {
                                    onError(exception)
                                }
                            }
                        )
                    },
                    onError = { exception ->
                        Log.e(tag, "Erro ao buscar medicamento $medicineId", exception)
                        // Chamar onError na thread principal (UI thread)
                        viewModelScope.launch(Dispatchers.Main) {
                            onError(exception)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(tag, "Erro ao alternar alarmes do medicamento $medicineId", e)
                // Chamar onError na thread principal (UI thread)
                viewModelScope.launch(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }
}

