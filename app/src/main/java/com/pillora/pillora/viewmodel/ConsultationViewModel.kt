package com.pillora.pillora.viewmodel

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.util.Log
import android.widget.DatePicker
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.model.Lembrete
import com.pillora.pillora.repository.ConsultationRepository
import com.pillora.pillora.utils.AlarmScheduler
import com.pillora.pillora.utils.DateTimeUtils
import com.pillora.pillora.data.dao.LembreteDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ConsultationViewModel : ViewModel() {

    private val tag = "ConsultationViewModel"
    private val auth = FirebaseAuth.getInstance()

    // Form fields state
    val specialty = mutableStateOf("")
    val doctorName = mutableStateOf("")
    val date = mutableStateOf("")
    val time = mutableStateOf("")
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
    private var currentConsultationUserId: String? = null

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
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000

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
        if (consultationId.isEmpty()) return
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
                        currentConsultationUserId = consultation.userId
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

    fun saveConsultation(context: Context, lembreteDao: LembreteDao) {
        val currentUserIdAuth = auth.currentUser?.uid
        if (currentUserIdAuth == null) {
            _error.value = "Usuário não autenticado."
            return
        }

        _isLoading.value = true
        _error.value = null

        val consultation = Consultation(
            id = currentConsultationId ?: "",
            userId = if (currentConsultationId != null) currentConsultationUserId ?: currentUserIdAuth else currentUserIdAuth,
            specialty = specialty.value.trim(),
            doctorName = doctorName.value.trim(),
            patientName = patientName.value.trim(),
            dateTime = "${date.value} ${time.value}".trim(),
            location = location.value.trim(),
            observations = observations.value.trim()
        )

        // Basic Validation
        if (consultation.specialty.isEmpty() || date.value.isEmpty() || time.value.isEmpty()) {
            _error.value = "Especialidade, Data e Hora são obrigatórios."
            _isLoading.value = false
            return
        }

        viewModelScope.launch {
            try {
                if (currentConsultationId == null) {
                    // Add new consultation
                    ConsultationRepository.addConsultation(
                        consultation = consultation,
                        onSuccess = { newConsultationId: String ->
                            Log.d(tag, "Consultation added successfully with ID: $newConsultationId")

                            // Agendar lembretes para a consulta (24h e 2h antes)
                            agendarLembretesConsulta(context, lembreteDao, newConsultationId, consultation)

                            _isLoading.value = false
                            _navigateBack.value = true
                        },
                        onFailure = {
                            Log.e(tag, "Error adding consultation", it)
                            _error.value = "Erro ao salvar consulta: ${it.message}"
                            _isLoading.value = false
                        }
                    )
                } else {
                    // Update existing consultation
                    if (consultation.userId != currentUserIdAuth) {
                        Log.e(tag, "Mismatch between consultation userId (${consultation.userId}) and auth userId ($currentUserIdAuth)")
                        _error.value = "Erro de autorização ao preparar atualização."
                        _isLoading.value = false
                        return@launch
                    }
                    ConsultationRepository.updateConsultation(
                        consultation = consultation,
                        onSuccess = {
                            Log.d(tag, "Consultation updated successfully")

                            // Atualizar lembretes para a consulta (24h e 2h antes)
                            atualizarLembretesConsulta(context, lembreteDao, currentConsultationId!!, consultation)

                            _isLoading.value = false
                            _navigateBack.value = true
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

    // Nova função para agendar lembretes de consulta
    private fun agendarLembretesConsulta(context: Context, lembreteDao: LembreteDao, newConsultationId: String, consultation: Consultation) {
        viewModelScope.launch {
            try {
                // Extrair data e hora da consulta
                val dateTimeParts = consultation.dateTime.split(" ")
                val consultaDate = dateTimeParts.getOrElse(0) { "" }
                val consultaTime = dateTimeParts.getOrElse(1) { "" }

                if (consultaDate.isEmpty() || consultaTime.isEmpty()) {
                    Log.e(tag, "Data ou hora da consulta inválida: ${consultation.dateTime}")
                    return@launch
                }

                // Calcular timestamps para os lembretes (24h e 2h antes)
                val timestamps = DateTimeUtils.calcularLembretesConsulta(
                    consultaDateString = consultaDate,
                    consultaTimeString = consultaTime
                )

                if (timestamps.isEmpty()) {
                    Log.e(tag, "Nenhum timestamp calculado para lembretes da consulta")
                    return@launch
                }

                Log.d(tag, "Calculados ${timestamps.size} lembretes para consulta $newConsultationId")

                // Criar e agendar lembretes
                var lembretesProcessadosComSucesso = true
                timestamps.forEachIndexed { index, timestamp ->
                    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val tipoLembrete = if (index == 0) "24 horas antes" else "2 horas antes"

                    // Criar lembrete adaptado ao modelo existente
                    val lembrete = Lembrete(
                        id = 0, // Será gerado automaticamente
                        medicamentoId = newConsultationId, // Usando medicamentoId para armazenar o ID da consulta
                        nomeMedicamento = "Consulta: ${consultation.specialty}", // Prefixo para identificar que é consulta
                        recipientName = consultation.patientName,
                        hora = calendar.get(Calendar.HOUR_OF_DAY),
                        minuto = calendar.get(Calendar.MINUTE),
                        dose = tipoLembrete, // Usando dose para armazenar o tipo de lembrete
                        observacao = "Dr(a). ${consultation.doctorName} em ${consultation.location}. ${if (consultation.observations.isNotEmpty()) "Obs: ${consultation.observations}" else ""}",
                        proximaOcorrenciaMillis = timestamp,
                        ativo = true
                    )

                    try {
                        // Salvar lembrete no banco de dados
                        val lembreteId = lembreteDao.insertLembrete(lembrete)

                        // Agendar alarme
                        AlarmScheduler.scheduleAlarm(context, lembrete.copy(id = lembreteId))

                        Log.d(tag, "Lembrete de consulta ($tipoLembrete) agendado: ID=$lembreteId, timestamp=$timestamp")
                    } catch (e: Exception) {
                        Log.e(tag, "Erro ao salvar/agendar lembrete de consulta", e)
                        lembretesProcessadosComSucesso = false
                    }
                }

                // Notificar o usuário sobre o status dos lembretes
                if (!lembretesProcessadosComSucesso) {
                    Toast.makeText(
                        context,
                        "Consulta salva, mas houve problemas ao agendar alguns lembretes.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(tag, "Erro ao processar lembretes para consulta", e)
                Toast.makeText(
                    context,
                    "Erro ao agendar lembretes: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Nova função para atualizar lembretes de consulta
    private fun atualizarLembretesConsulta(context: Context, lembreteDao: LembreteDao, consultationId: String, consultation: Consultation) {
        viewModelScope.launch {
            try {
                // Cancelar e excluir lembretes antigos
                val lembretesAntigos = lembreteDao.getLembretesByMedicamentoId(consultationId)
                if (lembretesAntigos.isNotEmpty()) {
                    for (lembreteAntigo in lembretesAntigos) {
                        // Cancelar alarme
                        AlarmScheduler.cancelAlarm(context, lembreteAntigo.id)
                        Log.d(tag, "Alarme cancelado para lembrete ID: ${lembreteAntigo.id}")
                    }

                    // Excluir lembretes antigos
                    lembreteDao.deleteLembretesByMedicamentoId(consultationId)
                    Log.d(tag, "Excluídos ${lembretesAntigos.size} lembretes antigos para consulta $consultationId")
                }

                // Agendar novos lembretes
                agendarLembretesConsulta(context, lembreteDao, consultationId, consultation)

            } catch (e: Exception) {
                Log.e(tag, "Erro ao atualizar lembretes para consulta", e)
                Toast.makeText(
                    context,
                    "Erro ao atualizar lembretes: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
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
