package com.pillora.pillora.viewmodel

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.util.Log
import android.widget.DatePicker
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth // Import FirebaseAuth
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.repository.ConsultationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ConsultationViewModel : ViewModel() {

    private val tag = "ConsultationViewModel"
    private val auth = FirebaseAuth.getInstance() // Get Firebase Auth instance

    // Form fields state
    val specialty = mutableStateOf("")
    val doctorName = mutableStateOf("")
    val date = mutableStateOf("") // Store as dd/MM/yyyy
    val time = mutableStateOf("") // Store as HH:mm
    val location = mutableStateOf("")
    val observations = mutableStateOf("")
    val patientName = mutableStateOf("")

    // UI state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack

    private var currentConsultationId: String? = null
    private var currentConsultationUserId: String? = null // Store userId when loading

    // --- Form Input Update Functions ---
    fun onPatientNameChange(newName: String) {
        patientName.value = newName
    }
    fun onSpecialtyChange(newSpecialty: String) {
        specialty.value = newSpecialty
    }

    fun onDoctorNameChange(newName: String) {
        doctorName.value = newName
    }

    fun onLocationChange(newLocation: String) {
        location.value = newLocation
    }

    fun onObservationsChange(newObservations: String) {
        observations.value = newObservations
    }

    // --- Date and Time Picker Logic ---
    fun showDatePicker(context: Context) {
        val calendar = Calendar.getInstance()
        // Try to parse existing date
        if (date.value.isNotEmpty()) {
            try {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                calendar.time = sdf.parse(date.value) ?: Calendar.getInstance().time
            } catch (e: Exception) {
                Log.w(tag, "Error parsing existing date: ${date.value}", e)
            }
        }

        val datePickerDialog = DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth)
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                date.value = sdf.format(selectedDate.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        // Set the minimum date to today
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000 // Subtract 1 second to ensure today is selectable

        datePickerDialog.show()
    }

    fun showTimePicker(context: Context) {
        val calendar = Calendar.getInstance()
        // Try to parse existing time
        if (time.value.isNotEmpty()) {
            try {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                calendar.time = sdf.parse(time.value) ?: Calendar.getInstance().time
            } catch (e: Exception) {
                Log.w(tag, "Error parsing existing time: ${time.value}", e)
            }
        }

        TimePickerDialog(
            context,
            { _, hourOfDay: Int, minute: Int ->
                val selectedTime = Calendar.getInstance()
                selectedTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedTime.set(Calendar.MINUTE, minute)
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                time.value = sdf.format(selectedTime.time)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true // 24 hour view
        ).show()
    }

    // --- Load and Save Logic ---

    fun loadConsultation(consultationId: String) {
        if (consultationId.isEmpty()) return // Avoid loading if ID is empty
        currentConsultationId = consultationId
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            ConsultationRepository.getConsultationById(
                consultationId = consultationId,
                onSuccess = { consultation ->
                    if (consultation != null) {
                        specialty.value = consultation.specialty
                        doctorName.value = consultation.doctorName
                        patientName.value = consultation.patientName
                        // Split dateTime back into date and time
                        val dateTimeParts = consultation.dateTime.split(" ")
                        date.value = dateTimeParts.getOrElse(0) { "" }
                        time.value = dateTimeParts.getOrElse(1) { "" }
                        location.value = consultation.location
                        observations.value = consultation.observations
                        currentConsultationUserId = consultation.userId // Store the userId for updates
                        Log.d(tag, "Consultation data loaded for ID: $consultationId")
                    } else {
                        _error.value = "Consulta não encontrada ou acesso negado."
                        Log.w(tag, "Consultation not found or access denied for ID: $consultationId")
                    }
                    _isLoading.value = false
                },
                onFailure = {
                    Log.e(tag, "Error loading consultation $consultationId", it)
                    _error.value = "Erro ao carregar consulta: ${it.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    fun saveConsultation() {
        val currentUserIdAuth = auth.currentUser?.uid // Get current user ID from Auth
        if (currentUserIdAuth == null) {
            _error.value = "Usuário não autenticado."
            return
        }

        _isLoading.value = true
        _error.value = null

        val consultation = Consultation(
            id = currentConsultationId ?: "", // Keep ID if editing
            userId = if (currentConsultationId != null) currentConsultationUserId ?: currentUserIdAuth else currentUserIdAuth, // Use stored or current userId for update, current for new
            specialty = specialty.value.trim(),
            doctorName = doctorName.value.trim(),
            patientName = patientName.value.trim(),
            dateTime = "${date.value} ${time.value}".trim(), // Combine date and time
            location = location.value.trim(),
            observations = observations.value.trim()
        )

        // Basic Validation (can be expanded)
        if (consultation.specialty.isEmpty() || date.value.isEmpty() || time.value.isEmpty()) { // Check date and time separately
            _error.value = "Especialidade, Data e Hora são obrigatórios."
            _isLoading.value = false
            return
        }

        viewModelScope.launch {
            try {
                if (currentConsultationId == null) {
                    // Add new consultation
                    ConsultationRepository.addConsultation(
                        consultation = consultation, // userId is now included
                        onSuccess = {
                            Log.d(tag, "Consultation added successfully")
                            _isLoading.value = false
                            _navigateBack.value = true // Trigger navigation back
                        },
                        onFailure = {
                            Log.e(tag, "Error adding consultation", it)
                            _error.value = "Erro ao salvar consulta: ${it.message}"
                            _isLoading.value = false
                        }
                    )
                } else {
                    // Update existing consultation
                    // Ensure the userId in the object matches the logged-in user before sending
                    if (consultation.userId != currentUserIdAuth) {
                        Log.e(tag, "Mismatch between consultation userId (${consultation.userId}) and auth userId ($currentUserIdAuth)")
                        _error.value = "Erro de autorização ao preparar atualização."
                        _isLoading.value = false
                        return@launch
                    }
                    ConsultationRepository.updateConsultation(
                        consultation = consultation, // userId is now included and verified
                        onSuccess = {
                            Log.d(tag, "Consultation updated successfully")
                            _isLoading.value = false
                            _navigateBack.value = true // Trigger navigation back
                        },
                        onFailure = {
                            Log.e(tag, "Error updating consultation", it)
                            _error.value = "Erro ao atualizar consulta: ${it.message}"
                            _isLoading.value = false
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception during save/update", e)
                _error.value = "Erro inesperado: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    // Call this when navigation is handled to reset the flag
    fun onNavigationHandled() {
        _navigateBack.value = false
    }

    // Call this after the error message is shown
    fun onErrorShown() {
        _error.value = null
    }
}

