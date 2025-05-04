package com.pillora.pillora.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.model.Vaccine // Importar Vaccine
import com.pillora.pillora.repository.ConsultationRepository
import com.pillora.pillora.repository.MedicineRepository
import com.pillora.pillora.repository.VaccineRepository // Importar VaccineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted // Importar SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit // Importar TimeUnit

class HomeViewModel : ViewModel() {

    private val tag = "HomeViewModel_DEBUG" // Tag for logs

    // Input Flows from Repositories
    private val medicinesFlow = MedicineRepository.getAllMedicinesFlow()
    private val consultationsFlow = ConsultationRepository.getAllConsultationsFlow()
    private val vaccinesFlow = VaccineRepository.getAllVaccinesFlow() // Adicionar fluxo de vacinas

    // Processed States for UI
    private val _medicinesToday = MutableStateFlow<List<Medicine>>(emptyList())
    val medicinesToday: StateFlow<List<Medicine>> = _medicinesToday

    private val _stockAlerts = MutableStateFlow<List<Medicine>>(emptyList())
    val stockAlerts: StateFlow<List<Medicine>> = _stockAlerts

    private val _upcomingConsultations = MutableStateFlow<List<Consultation>>(emptyList())
    val upcomingConsultations: StateFlow<List<Consultation>> = _upcomingConsultations

    private val _upcomingVaccines = MutableStateFlow<List<Vaccine>>(emptyList()) // Adicionar StateFlow para vacinas
    val upcomingVaccines: StateFlow<List<Vaccine>> = _upcomingVaccines

    // General States
    private val _isLoadingMedicines = MutableStateFlow(true)
    private val _isLoadingConsultations = MutableStateFlow(true)
    private val _isLoadingVaccines = MutableStateFlow(true) // Adicionar estado de loading para vacinas
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Combined Loading State for UI
    val isLoading: StateFlow<Boolean> = combine(
        _isLoadingMedicines, _isLoadingConsultations, _isLoadingVaccines // Incluir loading de vacinas
    ) { medLoading, conLoading, vacLoading ->
        medLoading || conLoading || vacLoading
    }.catch { e ->
        Log.e(tag, "Error combining loading states", e)
        emit(false) // Emit false on error
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    init {
        Log.d(tag, "Initializing HomeViewModel and starting data collection.")
        observeMedicines()
        observeConsultations()
        observeVaccines() // Iniciar observação de vacinas
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
                _error.value = (_error.value ?: "") + "\nErro ao carregar medicamentos: ${e.message}" // Append error
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
                _error.value = (_error.value ?: "") + "\nErro ao carregar consultas: ${e.message}" // Append error
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
                _error.value = (_error.value ?: "") + "\nErro ao carregar vacinas: ${e.message}" // Append error
                _isLoadingVaccines.value = false
            }
            .launchIn(viewModelScope)
    }

    private fun processMedicines(medicines: List<Medicine>) {
        Log.d(tag, "Processing ${medicines.size} medicines for today and stock alerts.")
        val today = Calendar.getInstance()

        _medicinesToday.value = medicines.filter { med ->
            isMedicationActiveToday(med, today)
        }
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
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val sevenDaysLater = (todayStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 7)
        }
        val sdfParse = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sdfParse.isLenient = false

        val upcoming = mutableListOf<Pair<Calendar, Consultation>>()

        consultations.forEach { consultation ->
            try {
                val consultationDateTime = sdfParse.parse(consultation.dateTime)
                if (consultationDateTime != null) {
                    val consultationCal = Calendar.getInstance().apply { time = consultationDateTime }
                    if (!consultationCal.before(todayStart) && consultationCal.before(sevenDaysLater)) {
                        upcoming.add(Pair(consultationCal, consultation))
                    }
                } else {
                    Log.w(tag, "Could not parse dateTime for consultation ID ${consultation.id}: \'${consultation.dateTime}\'")
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
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val fifteenDaysLater = (todayStart.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 15) // Adiciona 15 dias
        }
        val sdfParseDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        sdfParseDate.isLenient = false

        val upcoming = mutableListOf<Pair<Calendar, Vaccine>>()

        vaccines.forEach { vaccine ->
            try {
                val vaccineDate = sdfParseDate.parse(vaccine.reminderDate)
                if (vaccineDate != null) {
                    val vaccineCal = Calendar.getInstance().apply { time = vaccineDate }
                    vaccineCal.set(Calendar.HOUR_OF_DAY, 0)
                    vaccineCal.set(Calendar.MINUTE, 0)
                    vaccineCal.set(Calendar.SECOND, 0)
                    vaccineCal.set(Calendar.MILLISECOND, 0)

                    if (!vaccineCal.before(todayStart) && vaccineCal.before(fifteenDaysLater)) {
                        val sdfParseTime = SimpleDateFormat("HH:mm", Locale.getDefault())
                        try {
                            if (vaccine.reminderTime.isNotEmpty()) { // Only parse if time exists
                                val time = sdfParseTime.parse(vaccine.reminderTime)
                                if (time != null) {
                                    val timeCal = Calendar.getInstance().apply { this.time = time }
                                    vaccineCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                                    vaccineCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                                }
                            }
                        } catch (timeEx: Exception) {
                            Log.w(tag, "Could not parse time for vaccine ${vaccine.id}, using date only for sorting")
                        }
                        upcoming.add(Pair(vaccineCal, vaccine))
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error parsing date for vaccine ID ${vaccine.id}: \'${vaccine.reminderDate}\'", e)
            }
        }

        _upcomingVaccines.value = upcoming.sortedBy { it.first }.map { it.second }
        Log.d(tag, "Finished filtering vaccines. ${_upcomingVaccines.value.size} upcoming in next 15 days.")
    }

    // --- Helper Functions ---

    private fun parseDate(dateStr: String): Calendar? {
        // Assuming format DD/MM/YYYY based on other parts of the code
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            sdf.isLenient = false
            val date = sdf.parse(dateStr) ?: run {
                Log.w(tag, "parseDate failed for string: \'$dateStr\' - SDF returned null")
                return null
            }
            Calendar.getInstance().apply { time = date }
        } catch (e: Exception) {
            Log.e(tag, "parseDate exception for string: \'$dateStr\'", e)
            null
        }
    }

    private fun isMedicationActiveToday(med: Medicine, today: Calendar): Boolean {
        val startDateCal = parseDate(med.startDate) ?: return false
        val todayStart = (today.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (todayStart.before(startDateCal)) return false

        if (med.duration != -1) {
            val endDateCal = (startDateCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, med.duration)
            }
            if (!todayStart.before(endDateCal)) return false
        }

        if (med.frequencyType == "a_cada_x_horas") {
            val interval = med.intervalHours ?: return false // Interval must exist
            if (interval <= 0) return false // Interval must be positive

            // Logic for intervals >= 24 hours (every X days)
            if (interval >= 24) {
                val intervalDays = interval / 24 // Integer division gives the number of full days
                // Ensure intervalDays is at least 1 (already guaranteed by interval >= 24)
                // Calculate the difference in days since the start date
                val diffMillis = todayStart.timeInMillis - startDateCal.timeInMillis
                val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

                // Check if today is a day the medication should be taken
                // If diffDays is 0, 1*intervalDays, 2*intervalDays, etc., then take today.
                if (diffDays % intervalDays != 0L) {
                    // Log.d(tag, "Med ${med.name} not active today (every $intervalDays days, diff $diffDays days)")
                    return false // Not a day to take the medication
                }
            }
            // If interval < 24 hours, assume it's taken every day during the active period.
            // More complex logic could be added here if needed (e.g., first dose time).
        }
        // Add logic for "dias_especificos" if needed

        return true // Active today if no checks failed
    }

    private fun calculateDailyDosage(med: Medicine): Double {
        val doseValue = med.dose.replace(",", ".").toDoubleOrNull() ?: 0.0
        if (doseValue <= 0.0) return 0.0
        return when (med.frequencyType) {
            "vezes_dia" -> {
                val times = med.timesPerDay ?: 0
                doseValue * times
            }
            "a_cada_x_horas" -> {
                val interval = med.intervalHours ?: 0
                if (interval <= 0) 0.0 else (24.0 / interval) * doseValue
            }
            else -> 0.0
        }
    }
}

