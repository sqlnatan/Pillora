package com.pillora.pillora.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.pillora.pillora.model.Medicine
import com.pillora.pillora.repository.MedicineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeViewModel : ViewModel() {

    private val tag = "HomeViewModel_DEBUG" // Tag for logs

    private val _medicinesToday = MutableStateFlow<List<Medicine>>(emptyList())
    val medicinesToday: StateFlow<List<Medicine>> = _medicinesToday

    private val _stockAlerts = MutableStateFlow<List<Medicine>>(emptyList())
    val stockAlerts: StateFlow<List<Medicine>> = _stockAlerts

    // State to indicate loading or error (optional, but good for UI)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        // Load data on initialization
        Log.d(tag, "Initializing HomeViewModel and loading medicines.") // Use tag
        loadMedicines()
    }

    private fun loadMedicines() {
        _isLoading.value = true
        _error.value = null
        Log.d(tag, "Calling getAllMedicines...") // Use tag
        MedicineRepository.getAllMedicines(
            onSuccess = { medicinesWithIds ->
                val medicines = medicinesWithIds.map { it.second } // Extract only Medicine objects
                Log.d(tag, "getAllMedicines onSuccess. Received ${medicines.size} medicines. Processing...") // Use tag
                // Log example of the first medicine, if any
                if (medicines.isNotEmpty()) {
                    Log.d(tag, "First medicine example: ${medicines[0]}") // Use tag
                }
                processMedicines(medicines)
                _isLoading.value = false
            },
            onError = { exception ->
                Log.e(tag, "Error fetching medicines", exception) // Use tag
                _error.value = "Erro ao carregar medicamentos: ${exception.message}" // Message in Portuguese
                _isLoading.value = false
            }
        )
    }

    private fun processMedicines(medicines: List<Medicine>) {
        Log.d(tag, "Processing ${medicines.size} medicines for today and stock alerts.") // Use tag
        // Process for today's medicines
        val today = Calendar.getInstance()
        val sdfLog = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        Log.d(tag, "Current date/time for filtering: ${sdfLog.format(today.time)}") // Use tag

        _medicinesToday.value = medicines.filter { med ->
            val isActive = isMedicationActiveToday(med, today)
            Log.d(tag, "Checking medicine '${med.name}': Is active today? $isActive") // Use tag
            isActive
        }
        Log.d(tag, "Finished filtering. ${_medicinesToday.value.size} medicines active today.") // Use tag

        // Process for stock alerts
        val alertThresholdDays = 5 // Alert if stock lasts less than 5 days
        _stockAlerts.value = medicines.filter { med ->
            if (!med.trackStock || med.stockQuantity <= 0) {
                false
            } else {
                val dailyDosage = calculateDailyDosage(med)
                if (dailyDosage <= 0) {
                    false // Cannot calculate remaining days if daily dose is zero
                } else {
                    val daysLeft = med.stockQuantity / dailyDosage
                    val needsAlert = daysLeft < alertThresholdDays
                    // Log only if alert is needed to avoid too much noise
                    if(needsAlert) {
                        Log.d(tag, "Stock Alert for '${med.name}': Days left = $daysLeft (Threshold: $alertThresholdDays)") // Use tag
                    }
                    needsAlert
                }
            }
        }
        Log.d(tag, "Finished checking stock alerts. ${_stockAlerts.value.size} alerts found.") // Use tag
    }

    // --- Helper Functions (kept as before, added logging) ---

    private fun parseDate(dateStr: String): Calendar? {
        return try {
            val sdf = SimpleDateFormat("ddMMyyyy", Locale.getDefault()) // Corrected format
            sdf.isLenient = false
            val date = sdf.parse(dateStr) ?: run {
                Log.w(tag, "parseDate failed for string: '$dateStr' - SDF returned null") // Use tag
                return null
            }
            Calendar.getInstance().apply { time = date }
        } catch (e: Exception) {
            Log.e(tag, "parseDate exception for string: '$dateStr'", e) // Use tag
            null // Returns null on parsing error
        }
    }

    private fun isMedicationActiveToday(med: Medicine, today: Calendar): Boolean {
        val sdfLog = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        Log.d(tag, "isMedicationActiveToday check for '${med.name}' with start date '${med.startDate}' and duration ${med.duration}") // Use tag

        val startDateCal = parseDate(med.startDate) ?: run {
            Log.w(tag, "'${med.name}' is inactive: Invalid or missing start date.") // Use tag
            return false // Invalid medicine without start date
        }

        // Normalize 'today' to the start of the day for date comparisons
        val todayStart = (today.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        Log.d(tag, "Comparing with todayStart: ${sdfLog.format(todayStart.time)} and startDate: ${sdfLog.format(startDateCal.time)}") // Use tag

        // Check if today is before the start date
        if (todayStart.before(startDateCal)) {
            Log.d(tag, "'${med.name}' is inactive: Today is before start date.") // Use tag
            return false
        }

        // Check duration if not continuous (duration != -1)
        if (med.duration != -1) {
            val endDateCal = (startDateCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, med.duration -1) // -1 because duration includes the start day
            }
            Log.d(tag, "'${med.name}' has duration ${med.duration}. Calculated end date: ${sdfLog.format(endDateCal.time)}") // Use tag
            // Check if today is after the end date
            if (todayStart.after(endDateCal)) {
                Log.d(tag, "'${med.name}' is inactive: Today is after end date.") // Use tag
                return false
            }
        }

        // If reached here, the medicine is active today in terms of date.
        Log.d(tag, "'${med.name}' is active based on date range. Checking frequency type: ${med.frequencyType}") // Use tag

        // For "every_x_hours", we need to check if TODAY is a day to take it.
        if (med.frequencyType == "a_cada_x_horas") { // Keep original string for logic
            val interval = med.intervalHours ?: run {
                Log.w(tag, "'${med.name}' (a_cada_x_horas) is inactive: Missing intervalHours.") // Use tag
                return false // Needs interval
            }
            if (interval <= 0) {
                Log.w(tag, "'${med.name}' (a_cada_x_horas) is inactive: Invalid intervalHours <= 0.") // Use tag
                return false
            }
            Log.d(tag, "'${med.name}' frequency is a_cada_x_horas with interval ${interval}h.") // Use tag

            // Calculate the difference in days between today and the start date
            val diffMillis = todayStart.timeInMillis - startDateCal.timeInMillis
            val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)
            Log.d(tag, "Difference from start date: $diffDays days.") // Use tag

            // Check if the difference in days is a multiple of the interval (in days)
            // Only applies if interval is a full day or more
            if (interval >= 24) {
                val intervalDays = interval / 24
                Log.d(tag, "Interval >= 24h. Checking if $diffDays % $intervalDays == 0") // Use tag
                if (diffDays % intervalDays != 0L) {
                    Log.d(tag, "'${med.name}' is inactive: Today is not an interval day.") // Use tag
                    return false // Today is not a day to take it
                }
                Log.d(tag, "'${med.name}' is active: Today IS an interval day.") // Use tag
            } else {
                // If interval < 24, it means take it every day it's active.
                Log.d(tag, "Interval < 24h. Considered active for the day.") // Use tag
            }
        }

        // If it's "times_day" or passed the interval check, it's active today.
        Log.d(tag, "'${med.name}' final result: Active today = true") // Use tag
        return true
    }

    private fun calculateDailyDosage(med: Medicine): Double {
        // Try converting dose to Double, handling comma and possible errors
        val doseValue = med.dose.replace(",", ".").toDoubleOrNull() ?: 0.0
        if (doseValue <= 0.0) return 0.0

        return when (med.frequencyType) {
            "vezes_dia" -> { // Keep original string for logic
                val times = med.timesPerDay ?: 0
                doseValue * times
            }
            "a_cada_x_horas" -> { // Keep original string for logic
                val interval = med.intervalHours ?: 0
                if (interval <= 0) 0.0 else (24.0 / interval) * doseValue
            }
            else -> 0.0
        }
    }

    // TODO: Add functions to fetch consultations and vaccines for the day when implemented
}

