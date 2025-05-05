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
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit // Importar TimeUnit

class HomeViewModel : ViewModel() {

    private val tag = "HomeViewModel_DEBUG" // Tag for logs
    private val sdfLog = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) // Formatter for logs

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

        val activeToday = medicines.filter { med ->
            val isActive = isMedicationActiveToday(med, today)
            // Log moved inside isMedicationActiveToday for context
            isActive
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
        } catch (e: ParseException) {
            Log.e(tag, "parseDate exception for string: \'$dateStr\'", e)
            null
        } catch (e: Exception) {
            Log.e(tag, "parseDate unexpected exception for string: \'$dateStr\'", e)
            null
        }
    }

    // Refactored function to check if medication is active today with detailed logs
    private fun isMedicationActiveToday(med: Medicine, today: Calendar): Boolean {
        val medInfo = "Med \'${med.name}\' (ID: ${med.id})"
        val todayFormatted = sdfLog.format(today.time)
        Log.d(tag, "[$medInfo] Checking activity for today: $todayFormatted. StartDate: ${med.startDate}, Duration: ${med.duration}, FreqType: ${med.frequencyType}, Interval: ${med.intervalHours}")

        val startDateCal = parseDate(med.startDate) ?: run {
            Log.w(tag, "[$medInfo] Invalid start date format \'${med.startDate}\'. Result: INACTIVE")
            return false
        }
        val startDateFormatted = sdfLog.format(startDateCal.time)
        Log.d(tag, "[$medInfo] Parsed Start Date: $startDateFormatted")

        // Normalize 'today' to the start of the day for consistent comparisons
        val todayStart = (today.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStartFormatted = sdfLog.format(todayStart.time)
        Log.d(tag, "[$medInfo] Normalized Today Start: $todayStartFormatted")

        // Check 1: Is today before the start date?
        if (todayStart.before(startDateCal)) {
            Log.d(tag, "[$medInfo] Check 1 Failed: Today ($todayStartFormatted) is before Start Date ($startDateFormatted). Result: INACTIVE")
            return false
        }
        Log.d(tag, "[$medInfo] Check 1 Passed: Today is on or after Start Date.")

        // Check 2: If there's a duration, has it ended?
        // Duration is in days. -1 means indefinite.
        if (med.duration != -1) {
            Log.d(tag, "[$medInfo] Check 2: Duration is ${med.duration} days.")
            val endDateCal = (startDateCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, med.duration) // End date is exclusive
            }
            val endDateFormatted = sdfLog.format(endDateCal.time)
            Log.d(tag, "[$medInfo] Calculated End Date (exclusive): $endDateFormatted")
            // If todayStart is on or after the calculated end date, it's no longer active.
            if (!todayStart.before(endDateCal)) {
                Log.d(tag, "[$medInfo] Check 2 Failed: Today ($todayStartFormatted) is on or after End Date ($endDateFormatted). Result: INACTIVE")
                return false
            }
            Log.d(tag, "[$medInfo] Check 2 Passed: Today is before End Date.")
        } else {
            Log.d(tag, "[$medInfo] Check 2 Skipped: Duration is indefinite (-1).")
        }

        // Check 3: Based on frequency type
        Log.d(tag, "[$medInfo] Check 3: Frequency Type is '${med.frequencyType}'.")
        when (med.frequencyType) {
            "vezes_dia" -> {
                Log.d(tag, "[$medInfo] Frequency 'vezes_dia'. Result: ACTIVE")
                return true
            }
            "a_cada_x_horas" -> {
                val interval = med.intervalHours ?: run {
                    Log.w(tag, "[$medInfo] Frequency 'a_cada_x_horas' but interval is null. Result: INACTIVE")
                    return false
                }
                Log.d(tag, "[$medInfo] Frequency 'a_cada_x_horas' with interval: $interval hours.")
                if (interval <= 0) {
                    Log.w(tag, "[$medInfo] Interval is non-positive ($interval). Result: INACTIVE")
                    return false
                }

                if (interval < 24) {
                    Log.d(tag, "[$medInfo] Interval ($interval) < 24 hours. Result: ACTIVE")
                    return true
                }
                else {
                    val intervalDays = interval / 24
                    Log.d(tag, "[$medInfo] Interval ($interval) >= 24 hours. Calculated Interval Days: $intervalDays.")
                    // Calculate the difference in days since the start date
                    val diffMillis = todayStart.timeInMillis - startDateCal.timeInMillis
                    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)
                    Log.d(tag, "[$medInfo] Millis diff: $diffMillis. Days diff: $diffDays.")

                    // Check if the difference in days is a multiple of the interval in days.
                    val isActive = diffDays % intervalDays == 0L
                    Log.d(tag, "[$medInfo] Check: $diffDays % $intervalDays == 0? $isActive. Result: ${if(isActive) "ACTIVE" else "INACTIVE"}")
                    return isActive
                }
            }
            "dias_especificos" -> {
                Log.w(tag, "[$medInfo] Frequency 'dias_especificos' not implemented. Result: INACTIVE")
                return false // Placeholder
            }
            else -> {
                Log.w(tag, "[$medInfo] Unknown frequency type '${med.frequencyType}'. Result: INACTIVE")
                return false
            }
        }
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

