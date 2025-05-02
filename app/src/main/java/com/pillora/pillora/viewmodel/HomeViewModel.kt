package com.pillora.pillora.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.repository.ConsultationRepository
import com.pillora.pillora.repository.MedicineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeViewModel : ViewModel() {

    private val tag = "HomeViewModel_DEBUG" // Tag for logs

    // Input Flows from Repositories
    private val medicinesFlow = MedicineRepository.getAllMedicinesFlow()
    private val consultationsFlow = ConsultationRepository.getAllConsultationsFlow()

    // Processed States for UI
    private val _medicinesToday = MutableStateFlow<List<Medicine>>(emptyList())
    val medicinesToday: StateFlow<List<Medicine>> = _medicinesToday

    private val _stockAlerts = MutableStateFlow<List<Medicine>>(emptyList())
    val stockAlerts: StateFlow<List<Medicine>> = _stockAlerts

    private val _upcomingConsultations = MutableStateFlow<List<Consultation>>(emptyList())
    val upcomingConsultations: StateFlow<List<Consultation>> = _upcomingConsultations

    // General States
    private val _isLoadingMedicines = MutableStateFlow(true) // Separate loading states
    private val _isLoadingConsultations = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Combined Loading State for UI (optional, can expose separate ones too)
    val isLoading: StateFlow<Boolean> = combine(
        _isLoadingMedicines, _isLoadingConsultations
    ) { medLoading, conLoading ->
        medLoading || conLoading
    }.catch { e ->
        Log.e(tag, "Error combining loading states", e)
        emit(false) // Default to false on error
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)


    init {
        Log.d(tag, "Initializing HomeViewModel and starting data collection.")
        observeMedicines()
        observeConsultations()
    }

    private fun observeMedicines() {
        medicinesFlow
            .onEach { medicines ->
                Log.d(tag, "Received ${medicines.size} medicines from flow. Processing...")
                _isLoadingMedicines.value = false // Mark medicines as loaded
                _error.value = null // Clear previous medicine errors if successful
                processMedicines(medicines)
            }
            .catch { e ->
                Log.e(tag, "Error collecting medicines flow", e)
                _error.value = "Erro ao carregar medicamentos: ${e.message}"
                _isLoadingMedicines.value = false
            }
            .launchIn(viewModelScope) // Collect the flow within the viewModelScope
    }

    private fun observeConsultations() {
        consultationsFlow
            .onEach { consultations ->
                Log.d(tag, "Received ${consultations.size} consultations from flow. Processing...")
                _isLoadingConsultations.value = false // Mark consultations as loaded
                // Don't clear medicine errors here, append if needed or use separate error states
                // _error.value = null
                processConsultations(consultations)
            }
            .catch { e ->
                Log.e(tag, "Error collecting consultations flow", e)
                _error.value = (_error.value ?: "") + "\nErro ao carregar consultas: ${e.message}" // Append error
                _isLoadingConsultations.value = false
            }
            .launchIn(viewModelScope) // Collect the flow within the viewModelScope
    }

    // processMedicines remains largely the same, but now called on each flow emission
    private fun processMedicines(medicines: List<Medicine>) {
        Log.d(tag, "Processing ${medicines.size} medicines for today and stock alerts.")
        val today = Calendar.getInstance()
        // val sdfLog = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) // No longer needed for logging here
        // Log.d(tag, "Current date/time for filtering: ${sdfLog.format(today.time)}")

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

    // processConsultations remains largely the same, but now called on each flow emission
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
        // val sdfDisplay = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) // No longer needed for logging here
        // Log.d(tag, "Filtering consultations between ${sdfDisplay.format(todayStart.time)} and ${sdfDisplay.format(sevenDaysLater.time)}")

        val upcoming = mutableListOf<Pair<Calendar, Consultation>>()

        consultations.forEach { consultation ->
            try {
                val consultationDateTime = sdfParse.parse(consultation.dateTime)
                if (consultationDateTime != null) {
                    val consultationCal = Calendar.getInstance().apply { time = consultationDateTime }
                    // Log.d(tag, "Checking consultation \'${consultation.specialty}\' on ${sdfDisplay.format(consultationCal.time)}")
                    if (!consultationCal.before(todayStart) && consultationCal.before(sevenDaysLater)) {
                        // Log.d(tag, "-> ACCEPTED: Within range.")
                        upcoming.add(Pair(consultationCal, consultation))
                    } else {
                        // Log.d(tag, "-> REJECTED: Outside range.")
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

    // --- Helper Functions (unchanged) ---

    private fun parseDate(dateStr: String): Calendar? {
        return try {
            val sdf = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
            sdf.isLenient = false
            val date = sdf.parse(dateStr) ?: run {
                Log.w(tag, "parseDate failed for string: 	'$dateStr	' - SDF returned null")
                return null
            }
            Calendar.getInstance().apply { time = date }
        } catch (e: Exception) {
            Log.e(tag, "parseDate exception for string: 	'$dateStr	'", e)
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
                add(Calendar.DAY_OF_YEAR, med.duration - 1)
            }
            if (todayStart.after(endDateCal)) return false
        }
        if (med.frequencyType == "a_cada_x_horas") {
            val interval = med.intervalHours ?: return false
            if (interval <= 0) return false
            if (interval >= 24) {
                val diffMillis = todayStart.timeInMillis - startDateCal.timeInMillis
                val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)
                val intervalDays = interval / 24
                if (diffDays % intervalDays != 0L) return false
            }
        }
        return true
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

