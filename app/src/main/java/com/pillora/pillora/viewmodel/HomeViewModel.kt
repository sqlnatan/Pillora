package com.pillora.pillora.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.model.Recipe // Import Recipe model
import com.pillora.pillora.model.Vaccine // Importar Vaccine
import com.pillora.pillora.repository.ConsultationRepository
import com.pillora.pillora.repository.MedicineRepository
import com.pillora.pillora.repository.RecipeRepository // Import RecipeRepository
import com.pillora.pillora.repository.VaccineRepository // Importar VaccineRepository
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
import java.util.concurrent.TimeUnit // Importar TimeUnit

class HomeViewModel : ViewModel() {

    private val tag = "HomeViewModel_DEBUG" // Tag for logs
    private val sdfLog = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) // Formatter for logs

    // Date formatters for parsing
    private val sdfWithSlashes = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false }
    private val sdfWithoutSlashes = SimpleDateFormat("ddMMyyyy", Locale.getDefault()).apply { isLenient = false }

    // Input Flows from Repositories
    private val medicinesFlow = MedicineRepository.getAllMedicinesFlow()
    private val consultationsFlow = ConsultationRepository.getAllConsultationsFlow()
    private val vaccinesFlow = VaccineRepository.getAllVaccinesFlow()
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
        observeVaccines()
        observeRecipes() // <<< ADDED: Start observing recipes
    }

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

    private fun observeVaccines() {
        vaccinesFlow
            .onEach { vaccines ->
                Log.d(tag, "Received ${vaccines.size} vaccines from flow. Processing...")
                _isLoadingVaccines.value = false // Marcar vacinas como carregadas
                processUpcomingVaccines(vaccines)
            }
            .catch { e ->
                Log.e(tag, "Error collecting vaccines flow", e)
                _error.value = appendError("Erro ao carregar vacinas: ${e.message}") // Use appendError
                _isLoadingVaccines.value = false
            }
            .launchIn(viewModelScope)
    }

    // <<< ADDED: Function to observe recipes >>>
    private fun observeRecipes() {
        recipesFlow
            .onEach { recipes ->
                Log.d(tag, "Received ${recipes.size} recipes from flow. Processing...")
                _isLoadingRecipes.value = false // Mark recipes as loaded
                processExpiringRecipes(recipes)
            }
            .catch { e ->
                Log.e(tag, "Error collecting recipes flow", e)
                _error.value = appendError("Erro ao carregar receitas: ${e.message}")
                _isLoadingRecipes.value = false
            }
            .launchIn(viewModelScope)
    }

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
            } ?: Log.e(tag, "Error parsing date for vaccine ID ${vaccine.id}: \'${vaccine.reminderDate}\"")
        }

        _upcomingVaccines.value = upcoming.sortedBy { it.first }.map { it.second }
        Log.d(tag, "Finished filtering vaccines. ${_upcomingVaccines.value.size} upcoming in next 15 days.")
    }

    // <<< ADDED: Function to process expiring recipes >>>
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
                } ?: Log.e(tag, "Error parsing validityDate for recipe ID ${recipe.id}: \'${recipe.validityDate}\"")
            }
        }

        _expiringRecipes.value = expiring.sortedBy { it.first }.map { it.second }
        Log.d(tag, "Finished filtering recipes. ${_expiringRecipes.value.size} expiring in next $daysThreshold days.")
    }

    // --- Helper Functions ---

    // Helper to append errors without overwriting
    private fun appendError(newMessage: String): String {
        val currentError = _error.value
        return if (currentError.isNullOrBlank()) newMessage else "$currentError\n$newMessage"
    }

    // Helper to get the start of a given day
    private fun getStartOfDay(calendar: Calendar): Calendar {
        return (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    // Flexible date parser (kept from previous version)
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

    // Helper to parse time (kept from previous version)
    private fun parseTime(timeStr: String?): Calendar? { // <<< Made timeStr nullable
        val trimmedTimeStr = timeStr?.trim()
        if (trimmedTimeStr.isNullOrBlank()) return null // <<< Check for null or blank
        return try {
            val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { isLenient = false }
            sdfTime.parse(trimmedTimeStr)?.let { Calendar.getInstance().apply { time = it } }
        } catch (e: Exception) {
            Log.w(tag, "Could not parse time string: \'$trimmedTimeStr\'")
            null
        }
    }

    // <<< UPDATED: isMedicationActiveToday to match Medicine model >>>
    private fun isMedicationActiveToday(med: Medicine, today: Calendar): Boolean {
        val medInfo = "Med \'${med.name}\' (ID: ${med.id})"
        // val todayFormatted = sdfLog.format(today.time) // Unused variable
        // Log.d(tag, "[$medInfo] Checking activity for today: $todayFormatted. StartDate: ${med.startDate}, Duration: ${med.duration}, FreqType: ${med.frequencyType}, Interval: ${med.intervalHours}")

        val startDateCal = parseDate(med.startDate) ?: run {
            Log.w(tag, "[$medInfo] Could not parse start date \'${med.startDate}\'. Result: INACTIVE")
            return false
        }

        val todayStart = getStartOfDay(today)

        // Check 1: Is today before the start date?
        if (todayStart.before(startDateCal)) {
            return false
        }

        // Check 2: If there's a duration, has it ended?
        // Duration is in days. 0 or -1 means indefinite in the original model logic (adjusting here)
        if (med.duration > 0) { // Only check if duration is positive
            val endDateCal = (startDateCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, med.duration) // End date is exclusive
            }
            if (!todayStart.before(endDateCal)) {
                return false // Today is on or after the end date
            }
        }

        // Check 3: Based on frequency type (Only 'vezes_dia' and 'a_cada_x_horas' exist in model)
        // For simplicity, if frequencyType is not one of the known types, assume it's active if within date range.
        // A more robust approach might involve logging an error or handling unknown types differently.
        when (med.frequencyType) {
            "vezes_dia" -> {
                // Active if within date range
                return true
            }
            "a_cada_x_horas" -> {
                // Active if interval is set and within date range
                // Handle nullable intervalHours safely
                return (med.intervalHours ?: 0) > 0
            }
            // Removed cases for DIAS_ESPECIFICOS and INTERVALO_DIAS as fields are missing in model
            else -> {
                Log.w(tag, "[$medInfo] Unknown or unsupported frequency type: ${med.frequencyType}. Assuming ACTIVE if within date range.")
                // Assume active if start/end dates match, as frequency logic is unclear/unsupported
                return true
            }
        }
    }

    // <<< UPDATED: calculateDailyDosage to match Medicine model >>>
    private fun calculateDailyDosage(med: Medicine): Double {
        return when (med.frequencyType) {
            "vezes_dia" -> {
                // Use timesPerDay if available, default to 1 if null/zero (conservative estimate)
                (med.timesPerDay ?: 1).toDouble().coerceAtLeast(1.0)
            }
            "a_cada_x_horas" -> {
                // Use intervalHours if available and positive
                val interval = med.intervalHours ?: 0
                if (interval > 0) (24.0 / interval) else 0.0 // Avoid division by zero
            }
            // Removed cases for DIAS_ESPECIFICOS and INTERVALO_DIAS
            else -> {
                Log.w(tag, "Cannot calculate daily dosage for unknown frequency type: ${med.frequencyType}. Returning 0.")
                0.0 // Cannot determine dosage
            }
        }
    }

    // Call this if you implement error dismissal in the UI
    fun onErrorShown() {
        _error.value = null
    }
}

