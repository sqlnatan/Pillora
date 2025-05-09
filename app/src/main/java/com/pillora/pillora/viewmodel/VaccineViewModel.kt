package com.pillora.pillora.viewmodel

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth // Import FirebaseAuth
import com.pillora.pillora.model.Vaccine
import com.pillora.pillora.repository.VaccineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class VaccineViewModel : ViewModel() {

    private val tag = "VaccineViewModel"

    // Form fields state
    val name = mutableStateOf("")
    val reminderDate = mutableStateOf("") // Format DD/MM/YYYY
    val reminderTime = mutableStateOf("") // Format HH:MM
    val location = mutableStateOf("")
    val notes = mutableStateOf("")
    val patientName = mutableStateOf("")

    // UI State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack.asStateFlow()

    private var currentVaccineId: String? = null
    private var originalUserId: String? = null // Store the original userId for updates
    private var isEditing: Boolean = false

    // Date format used throughout this ViewModel
    private val internalDateFormat by lazy {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
            isLenient = false
        }
    }

    // --- Field Change Handlers (Public for UI Binding/Events) ---
    fun onNameChange(newName: String) {
        name.value = newName
    }

    private fun onReminderDateChange(newDate: String) {
        reminderDate.value = newDate
    }

    private fun onReminderTimeChange(newTime: String) {
        reminderTime.value = newTime
    }

    fun onLocationChange(newLocation: String) {
        location.value = newLocation
    }

    fun onNotesChange(newNotes: String) {
        notes.value = newNotes
    }

    fun onPatientNameChange(newName: String) { // <<< ADICIONE ESTA FUNÇÃO INTEIRA
        patientName.value = newName
    }

    // --- Date/Time Picker Logic ---
    fun showDatePicker(context: Context) {
        val calendar = Calendar.getInstance()
        try {
            if (reminderDate.value.isNotEmpty()) {
                calendar.time = internalDateFormat.parse(reminderDate.value) ?: Date()
            }
        } catch (e: Exception) {
            Log.w(tag, "Error parsing date for DatePicker: ${reminderDate.value}", e)
        }

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                onReminderDateChange(internalDateFormat.format(selectedCalendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.show()
    }

    fun showTimePicker(context: Context) {
        val calendar = Calendar.getInstance()
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
            Log.w(tag, "Error parsing time for TimePicker: ${reminderTime.value}", e)
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
        if (vaccineId == currentVaccineId && name.value.isNotEmpty()) return
        resetFormFields()
        currentVaccineId = vaccineId
        isEditing = true
        _isLoading.value = true
        _error.value = null
        Log.d(tag, "Loading vaccine with ID: $vaccineId")
        VaccineRepository.getVaccineById(vaccineId, {
                vaccine ->
            if (vaccine != null) {
                Log.d(tag, "Vaccine loaded: ${vaccine.name}, UserID: ${vaccine.userId}")
                name.value = vaccine.name
                reminderDate.value = vaccine.reminderDate
                reminderTime.value = vaccine.reminderTime
                location.value = vaccine.location
                notes.value = vaccine.notes
                patientName.value = vaccine.patientName
                originalUserId = vaccine.userId // Store the original userId
            } else {
                Log.w(tag, "Vaccine with ID $vaccineId not found")
                _error.value = "Lembrete de vacina não encontrado."
                resetFormFields()
            }
            _isLoading.value = false
        }, {
                exception ->
            Log.e(tag, "Error loading vaccine", exception)
            _error.value = "Erro ao carregar lembrete: ${exception.message}"
            _isLoading.value = false
            resetFormFields()
        })
    }

    // --- Save/Update Vaccine ---
    fun saveVaccine() {
        if (!validateInputs()) {
            return
        }

        _isLoading.value = true
        _error.value = null

        // Use the stored originalUserId if editing, otherwise get current user ID for new vaccine
        val userIdToSave = if (isEditing) {
            originalUserId ?: run {
                Log.e(tag, "Update failed: Original User ID is missing.")
                _error.value = "Erro interno: ID do usuário original ausente."
                _isLoading.value = false
                return
            }
        } else {
            FirebaseAuth.getInstance().currentUser?.uid ?: run {
                Log.e(tag, "Save failed: Current User ID is missing.")
                _error.value = "Erro: Usuário não autenticado."
                _isLoading.value = false
                return
            }
        }

        val vaccine = Vaccine(
            id = currentVaccineId ?: "",
            userId = userIdToSave, // Use the determined userId
            name = name.value.trim(),
            patientName = patientName.value.trim(),
            reminderDate = reminderDate.value,
            reminderTime = reminderTime.value,
            location = location.value.trim(),
            notes = notes.value.trim()
        )

        val onSuccess = {
            Log.d(tag, "Vaccine ${if (isEditing) "updated" else "added"} successfully")
            _isLoading.value = false
            resetFormFields()
            _navigateBack.value = true
        }

        val onFailure = { exception: Exception ->
            Log.e(tag, "Error ${if (isEditing) "updating" else "adding"} vaccine", exception)
            _isLoading.value = false
            // Use the specific error message from the repository if available
            _error.value = exception.message ?: "Erro desconhecido ao salvar."
        }

        viewModelScope.launch {
            if (isEditing) {
                Log.d(tag, "Attempting to update vaccine: ${vaccine.id} with UserID: ${vaccine.userId}")
                if (vaccine.id.isNotEmpty()) {
                    VaccineRepository.updateVaccine(vaccine, onSuccess, onFailure)
                } else {
                    Log.e(tag, "Update failed: Vaccine ID is missing.")
                    onFailure(IllegalStateException("ID da vacina ausente para atualização."))
                }
            } else {
                Log.d(tag, "Attempting to add new vaccine: ${vaccine.name} with UserID: ${vaccine.userId}")
                VaccineRepository.addVaccine(vaccine, onSuccess, onFailure)
            }
        }
    }

    // --- Input Validation (Internal) ---
    private fun validateInputs(): Boolean {
        var errorMessage: String? = null
        if (name.value.isBlank()) {
            errorMessage = "Nome da vacina/lembrete é obrigatório."
        } else if (reminderDate.value.isBlank()) {
            errorMessage = "Data do lembrete é obrigatória."
        } else {
            try {
                internalDateFormat.parse(reminderDate.value)
            } catch (e: ParseException) {
                Log.w(tag, "Validation failed: Invalid date format or value: ${reminderDate.value}", e)
                errorMessage = "Data inválida. Use o formato DD/MM/AAAA."
            }
        }

        if (reminderTime.value.isNotBlank() && !reminderTime.value.matches(Regex("^\\d{2}:\\d{2}$"))) {
            Log.w(tag, "Validation failed: Invalid time format: ${reminderTime.value}")
            errorMessage = "Formato de hora inválido (HH:MM)."
        }

        _error.value = errorMessage
        if (errorMessage != null) {
            Log.w(tag, "Validation failed: $errorMessage")
        }
        return errorMessage == null
    }

    // --- State Reset Handlers ---
    fun onErrorShown() {
        _error.value = null
    }

    fun onNavigationHandled() {
        _navigateBack.value = false
    }

    private fun resetFormFields() {
        name.value = ""
        reminderDate.value = ""
        reminderTime.value = ""
        location.value = ""
        notes.value = ""
        currentVaccineId = null
        originalUserId = null // Reset originalUserId
        isEditing = false
        Log.d(tag, "Form fields reset.")
    }
}

