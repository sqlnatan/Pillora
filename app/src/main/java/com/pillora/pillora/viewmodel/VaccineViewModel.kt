package com.pillora.pillora.viewmodel

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pillora.pillora.model.Vaccine
import com.pillora.pillora.repository.VaccineRepository
import com.pillora.pillora.utils.DateValidator // Assuming DateValidator exists and is suitable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class VaccineViewModel : ViewModel() {

    private val TAG = "VaccineViewModel"

    // Form fields state
    val name = mutableStateOf("")
    val reminderDate = mutableStateOf("") // Format DD/MM/YYYY
    val reminderTime = mutableStateOf("") // Format HH:MM
    val location = mutableStateOf("")
    val notes = mutableStateOf("")

    // UI State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack.asStateFlow()

    private var currentVaccineId: String? = null
    private var isEditing: Boolean = false

    // --- Field Change Handlers ---
    fun onNameChange(newName: String) {
        name.value = newName
    }

    fun onReminderDateChange(newDate: String) {
        reminderDate.value = newDate
    }

    fun onReminderTimeChange(newTime: String) {
        reminderTime.value = newTime
    }

    fun onLocationChange(newLocation: String) {
        location.value = newLocation
    }

    fun onNotesChange(newNotes: String) {
        notes.value = newNotes
    }

    // --- Date/Time Picker Logic (Similar to ConsultationViewModel) ---
    fun showDatePicker(context: Context) {
        val calendar = Calendar.getInstance()
        // Try to parse existing date to pre-select in picker
        try {
            if (reminderDate.value.isNotEmpty()) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                calendar.time = sdf.parse(reminderDate.value) ?: Date()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing date for DatePicker: ${reminderDate.value}", e)
            // Use current date if parsing fails
        }

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                onReminderDateChange(format.format(selectedCalendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            // Optional: Set min date if needed (e.g., today)
            datePicker.minDate = System.currentTimeMillis() - 1000
        }.show()
    }

    fun showTimePicker(context: Context) {
        val calendar = Calendar.getInstance()
        // Try to parse existing time
        var initialHour = calendar.get(Calendar.HOUR_OF_DAY)
        var initialMinute = calendar.get(Calendar.MINUTE)
        try {
            if (reminderTime.value.isNotEmpty()) {
                val timeParts = reminderTime.value.split(":")
                if (timeParts.size == 2) {
                    initialHour = timeParts[0].toInt()
                    initialMinute = timeParts[1].toInt()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing time for TimePicker: ${reminderTime.value}", e)
        }

        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                onReminderTimeChange(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute))
            },
            initialHour,
            initialMinute,
            true // Use 24-hour format
        ).show()
    }

    // --- Load Existing Vaccine for Editing ---
    fun loadVaccine(vaccineId: String) {
        if (vaccineId == currentVaccineId) return // Avoid reloading if ID hasn't changed
        currentVaccineId = vaccineId
        isEditing = true
        _isLoading.value = true
        _error.value = null
        Log.d(TAG, "Loading vaccine with ID: $vaccineId")
        VaccineRepository.getVaccineById(vaccineId, {
                vaccine ->
            if (vaccine != null) {
                Log.d(TAG, "Vaccine loaded: ${vaccine.name}")
                name.value = vaccine.name
                reminderDate.value = vaccine.reminderDate
                reminderTime.value = vaccine.reminderTime
                location.value = vaccine.location
                notes.value = vaccine.notes
            } else {
                Log.w(TAG, "Vaccine with ID $vaccineId not found")
                _error.value = "Lembrete de vacina não encontrado."
                // Optionally navigate back or clear fields
            }
            _isLoading.value = false
        }, {
                exception ->
            Log.e(TAG, "Error loading vaccine", exception)
            _error.value = "Erro ao carregar lembrete: ${exception.message}"
            _isLoading.value = false
        })
    }

    // --- Save/Update Vaccine ---
    fun saveVaccine() {
        if (!validateInputs()) {
            return
        }

        _isLoading.value = true
        _error.value = null

        val vaccine = Vaccine(
            id = currentVaccineId ?: "", // ID is empty for new, set for update
            userId = "", // userId will be set by the repository
            name = name.value.trim(),
            reminderDate = reminderDate.value,
            reminderTime = reminderTime.value, // Already formatted HH:MM
            location = location.value.trim(),
            notes = notes.value.trim()
        )

        val onSuccess = {
            Log.d(TAG, "Vaccine ${if (isEditing) "updated" else "added"} successfully")
            _isLoading.value = false
            _navigateBack.value = true
        }

        val onFailure = { exception: Exception ->
            Log.e(TAG, "Error ${if (isEditing) "updating" else "adding"} vaccine", exception)
            _isLoading.value = false
            _error.value = "Erro ao salvar: ${exception.message}"
        }

        viewModelScope.launch {
            if (isEditing) {
                Log.d(TAG, "Attempting to update vaccine: ${vaccine.id}")
                VaccineRepository.updateVaccine(vaccine, onSuccess, onFailure)
            } else {
                Log.d(TAG, "Attempting to add new vaccine: ${vaccine.name}")
                VaccineRepository.addVaccine(vaccine, onSuccess, onFailure)
            }
        }
    }

    // --- Input Validation ---
    private fun validateInputs(): Boolean {
        var isValid = true
        if (name.value.isBlank()) {
            // In a real app, you might set specific error states for each field
            _error.value = "Nome da vacina/lembrete é obrigatório."
            isValid = false
        }
        if (reminderDate.value.isBlank()) {
            _error.value = "Data do lembrete é obrigatória."
            isValid = false
        } else {
            val (isValidDate, dateMessage) = DateValidator.validateDate(reminderDate.value)
            if (!isValidDate) {
                _error.value = dateMessage ?: "Data inválida."
                isValid = false
            }
        }
        if (reminderTime.value.isBlank()) {
            _error.value = "Hora do lembrete é obrigatória."
            isValid = false
        } else if (!reminderTime.value.matches(Regex("^\\d{2}:\\d{2}$"))) {
            _error.value = "Formato de hora inválido (HH:MM)."
            isValid = false
        }

        if (!isValid) {
            Log.w(TAG, "Validation failed: ${_error.value}")
        }
        return isValid
    }

    // --- State Reset Handlers ---
    fun onErrorShown() {
        _error.value = null
    }

    fun onNavigationHandled() {
        _navigateBack.value = false
        // Optionally reset form fields here if needed after navigation
        // resetFormFields()
    }

    // Optional: Function to reset fields (e.g., after successful save or on init)
    private fun resetFormFields() {
        name.value = ""
        reminderDate.value = ""
        reminderTime.value = ""
        location.value = ""
        notes.value = ""
        currentVaccineId = null
        isEditing = false
    }
}
