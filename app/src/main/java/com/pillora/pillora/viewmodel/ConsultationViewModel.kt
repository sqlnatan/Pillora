package com.pillora.pillora.viewmodel

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.DatePicker
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.pillora.pillora.data.dao.LembreteDao
import com.pillora.pillora.model.Consultation
import com.pillora.pillora.model.Lembrete
import com.pillora.pillora.receiver.AlarmReceiver
import com.pillora.pillora.repository.ConsultationRepository
import com.pillora.pillora.utils.AlarmScheduler
import com.pillora.pillora.utils.DateTimeUtils
import com.pillora.pillora.workers.NotificationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface ConsultationListUiState {
    data object Loading : ConsultationListUiState
    data class Success(val consultations: List<Consultation>) : ConsultationListUiState
    data class Error(val message: String) : ConsultationListUiState
}

class ConsultationViewModel : ViewModel() {

    private val tag = "ConsultationViewModel"
    private val auth = FirebaseAuth.getInstance()

    // CoroutineScope independente que não é cancelado ao sair da tela
    private val independentScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Campos do formulário
    val specialty = mutableStateOf("")
    val doctorName = mutableStateOf("")
    val date = mutableStateOf("")
    val time = mutableStateOf("")
    val location = mutableStateOf("")
    val observations = mutableStateOf("")
    val patientName = mutableStateOf("")

    // NOVOS: toque personalizado e modo silencioso
    val isSilencioso = mutableStateOf(false)
    val toqueAlarmeUri = mutableStateOf<String?>(null)

    // UI state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack

    private var currentConsultationId: String? = null
    private var currentConsultationUserId: String? = null
    private var currentConsultationIsActive: Boolean = true

    val consultationListUiState: StateFlow<ConsultationListUiState> = ConsultationRepository.getAllConsultationsFlow()
        .map<List<Consultation>, ConsultationListUiState> { consultations ->
            ConsultationListUiState.Success(consultations.sortedBy { it.dateTime })
        }
        .catch { e ->
            emit(ConsultationListUiState.Error(e.localizedMessage ?: "Erro desconhecido ao carregar consultas"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConsultationListUiState.Loading
        )

    // --- Form Input Update Functions ---
    fun onPatientNameChange(newName: String) { patientName.value = newName }
    fun onSpecialtyChange(newSpecialty: String) { specialty.value = newSpecialty }
    fun onDoctorNameChange(newName: String) { doctorName.value = newName }
    fun onLocationChange(newLocation: String) { location.value = newLocation }
    fun onObservationsChange(newObservations: String) { observations.value = newObservations }
    fun setSilencioso(valor: Boolean) { isSilencioso.value = valor }

    fun setToqueAlarmeUri(uri: String?) { toqueAlarmeUri.value = uri }


    // --- Date and Time Picker ---
    fun showDatePicker(context: Context) {
        val calendar = Calendar.getInstance()
        if (date.value.isNotEmpty()) {
            try {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                calendar.time = sdf.parse(date.value) ?: Calendar.getInstance().time
            } catch (e: Exception) { Log.w(tag, "Error parsing existing date: ${date.value}", e) }
        }
        DatePickerDialog(
            context,
            { _: DatePicker, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth)
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                date.value = sdf.format(selectedDate.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
    }

    fun showTimePicker(context: Context) {
        val calendar = Calendar.getInstance()
        if (time.value.isNotEmpty()) {
            try {
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                calendar.time = sdf.parse(time.value) ?: Calendar.getInstance().time
            } catch (e: Exception) { Log.w(tag, "Error parsing existing time: ${time.value}", e) }
        }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val selectedTime = Calendar.getInstance()
                selectedTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedTime.set(Calendar.MINUTE, minute)
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                time.value = sdf.format(selectedTime.time)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    // --- Load Consultation ---
    fun loadConsultation(consultationId: String) {
        if (consultationId.isEmpty()) return
        currentConsultationId = consultationId
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            ConsultationRepository.getConsultationById(
                consultationId,
                onSuccess = { consultation ->
                    if (consultation != null) {
                        specialty.value = consultation.specialty
                        doctorName.value = consultation.doctorName
                        patientName.value = consultation.patientName
                        val dateTimeParts = consultation.dateTime.split(" ")
                        date.value = dateTimeParts.getOrElse(0) { "" }
                        time.value = dateTimeParts.getOrElse(1) { "" }
                        location.value = consultation.location
                        observations.value = consultation.observations
                        currentConsultationUserId = consultation.userId
                        currentConsultationIsActive = consultation.isActive
                        isSilencioso.value = consultation.isSilencioso
                        toqueAlarmeUri.value = consultation.toqueAlarmeUri
                    } else {
                        _error.value = "Consulta não encontrada ou acesso negado."
                    }
                    _isLoading.value = false
                },
                onFailure = {
                    _error.value = "Erro ao carregar consulta: ${it.message}"
                    _isLoading.value = false
                }
            )
        }
    }

    // --- Save / Update Consultation ---
    fun saveConsultation(context: Context, lembreteDao: LembreteDao) {
        Log.d(tag, "=== saveConsultation CHAMADO ===")
        val currentUserIdAuth = auth.currentUser?.uid
        if (currentUserIdAuth == null) {
            _error.value = "Usuário não autenticado."
            Log.e(tag, "Erro: Usuário não autenticado")
            return
        }
        _isLoading.value = true
        _error.value = null
        Log.d(tag, "currentConsultationId: $currentConsultationId")

        val consultation = Consultation(
            id = currentConsultationId ?: "",
            userId = if (currentConsultationId != null) currentConsultationUserId ?: currentUserIdAuth else currentUserIdAuth,
            specialty = specialty.value.trim(),
            doctorName = doctorName.value.trim(),
            patientName = patientName.value.trim(),
            dateTime = "${date.value} ${time.value}".trim(),
            location = location.value.trim(),
            observations = observations.value.trim(),
            isSilencioso = isSilencioso.value,
            toqueAlarmeUri = toqueAlarmeUri.value,
            isActive = if (currentConsultationId != null) currentConsultationIsActive else true
        )

        if (consultation.specialty.isEmpty() || date.value.isEmpty() || time.value.isEmpty()) {
            _error.value = "Especialidade, Data e Hora são obrigatórios."
            _isLoading.value = false
            Log.e(tag, "Erro: Campos obrigatórios vazios")
            return
        }
        Log.d(tag, "Dados da consulta: specialty=${consultation.specialty}, dateTime=${consultation.dateTime}")

        viewModelScope.launch {
            try {
                if (currentConsultationId == null) {
                    Log.d(tag, "Criando NOVA consulta...")
                    ConsultationRepository.addConsultation(consultation,
                        onSuccess = { newConsultationId ->
                            Log.d(tag, "Consulta criada com sucesso! ID: $newConsultationId")
                            Log.d(tag, "Chamando agendarLembretesConsulta...")
                            independentScope.launch {
                                agendarLembretesConsulta(context, lembreteDao, newConsultationId, consultation)
                                _isLoading.value = false
                                _navigateBack.value = true
                            }
                        },
                        onFailure = {
                            _error.value = "Erro ao salvar consulta: ${it.message}"
                            _isLoading.value = false
                        }
                    )
                } else {
                    Log.d(tag, "Atualizando consulta existente: $currentConsultationId")
                    if (consultation.userId != currentUserIdAuth) {
                        _error.value = "Erro de autorização ao preparar atualização."
                        _isLoading.value = false
                        Log.e(tag, "Erro de autorização")
                        return@launch
                    }
                    ConsultationRepository.updateConsultation(consultation,
                        onSuccess = {
                            Log.d(tag, "Consulta atualizada com sucesso!")
                            Log.d(tag, "Chamando atualizarLembretesConsulta...")
                            independentScope.launch {
                                atualizarLembretesConsulta(context, lembreteDao, currentConsultationId!!, consultation)
                                _isLoading.value = false
                                _navigateBack.value = true
                            }
                        },
                        onFailure = {
                            _error.value = "Erro ao atualizar consulta: ${it.message}"
                            _isLoading.value = false
                        }
                    )
                }
            } catch (e: Exception) {
                _error.value = "Erro inesperado: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    // --- Agendamento de Lembretes ---
    private suspend fun agendarLembretesConsulta(context: Context, lembreteDao: LembreteDao, consultationId: String, consultation: Consultation) {
        Log.d(tag, "=== agendarLembretesConsulta CHAMADO ===")
        Log.d(tag, "consultationId: $consultationId, dateTime: ${consultation.dateTime}")
        val dateTimeParts = consultation.dateTime.split(" ")
        val consultaDate = dateTimeParts.getOrElse(0) { "" }
        val consultaTime = dateTimeParts.getOrElse(1) { "" }
        Log.d(tag, "consultaDate: $consultaDate, consultaTime: $consultaTime")
        if (consultaDate.isEmpty() || consultaTime.isEmpty()) {
            Log.e(tag, "ERRO: Data ou hora da consulta vazia!")
            return
        }

        // CORREÇÃO: Extrair hora e minuto da consulta real para passar ao NotificationWorker
        val consultaTimeParts = consultaTime.split(":")
        val horaConsultaReal = consultaTimeParts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
        val minutoConsultaReal = consultaTimeParts.getOrElse(1) { "0" }.toIntOrNull() ?: 0

        val lembretesInfo = DateTimeUtils.calcularLembretesConsulta(consultaDate, consultaTime)
        Log.d(tag, "DateTimeUtils retornou ${lembretesInfo.size} lembretes")
        lembretesInfo.forEach { lembreteInfo ->
            Log.d(tag, "Criando lembrete tipo: ${lembreteInfo.tipo}, timestamp: ${lembreteInfo.timestamp}")
            val calendar = Calendar.getInstance().apply { timeInMillis = lembreteInfo.timestamp }

            val lembrete = Lembrete(
                id = 0,
                medicamentoId = consultationId,
                nomeMedicamento = "Consulta: ${consultation.specialty}",
                recipientName = consultation.patientName,
                hora = calendar.get(Calendar.HOUR_OF_DAY),
                minuto = calendar.get(Calendar.MINUTE),
                dose = lembreteInfo.tipo,
                observacao = "Dr(a). ${consultation.doctorName} em ${consultation.location}. ${if (consultation.observations.isNotEmpty()) "Obs: ${consultation.observations}" else ""}",
                proximaOcorrenciaMillis = lembreteInfo.timestamp,
                ativo = true,
                isConsulta = true,
                // CORREÇÃO: Marcar como confirmação se for o lembrete de 3h depois
                isConfirmacao = lembreteInfo.tipo == DateTimeUtils.TIPO_3H_DEPOIS,
                isSilencioso = consultation.isSilencioso,
                toqueAlarmeUri = consultation.toqueAlarmeUri
            )
            try {
                Log.d(tag, "Inserindo lembrete no banco...")
                val lembreteId = lembreteDao.insertLembrete(lembrete)
                Log.d(tag, "Lembrete salvo no DB com ID: $lembreteId")
                val lembreteComId = lembrete.copy(id = lembreteId)

                when (lembreteInfo.tipo) {
                    DateTimeUtils.TIPO_3H_DEPOIS -> {
                        Log.d(tag, "Agendando WorkManager para confirmação (3h depois)...")
                        try {
                            agendarNotificacaoPosConsulta(context, lembreteComId, horaConsultaReal, minutoConsultaReal)
                            Log.d(tag, "WorkManager agendado com sucesso")
                        } catch (e: Exception) {
                            Log.e(tag, "Erro ao agendar WorkManager", e)
                            throw e
                        }
                    }
                    DateTimeUtils.TIPO_24H_ANTES, DateTimeUtils.TIPO_2H_ANTES -> {
                        Log.d(tag, "Agendando AlarmManager para lembrete (${lembreteInfo.tipo})...")
                        try {
                            agendarAlarmeConsulta(context, lembreteComId, horaConsultaReal, minutoConsultaReal)
                            Log.d(tag, "AlarmManager agendado com sucesso")
                        } catch (e: Exception) {
                            Log.e(tag, "Erro ao agendar AlarmManager", e)
                            throw e
                        }
                    }
                }
                Log.d(tag, "Lembrete ${lembreteInfo.tipo} criado e agendado com sucesso!")
            } catch (e: Exception) {
                Log.e(tag, "Erro ao salvar/agendar lembrete", e)
                Log.e(tag, "Stack trace: ${e.stackTraceToString()}")
            }
        }
    }

    /**
     * CORREÇÃO: Adicionado parâmetros horaConsultaReal e minutoConsultaReal
     */
    private fun agendarNotificacaoPosConsulta(context: Context, lembrete: Lembrete, horaConsultaReal: Int, minutoConsultaReal: Int) {
        val workData = Data.Builder()
            .putLong(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
            .putString(NotificationWorker.EXTRA_MEDICAMENTO_ID, lembrete.medicamentoId)
            .putString(NotificationWorker.EXTRA_CONSULTA_ID, lembrete.medicamentoId) // CORREÇÃO: Passar o consultaId
            .putString(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
            .putString(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.observacao)
            .putString(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
            .putLong(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
            .putInt(NotificationWorker.EXTRA_HORA, lembrete.hora)
            .putInt(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
            // CORREÇÃO: Passar a hora e minuto REAL da consulta
            .putInt(NotificationWorker.EXTRA_HORA_CONSULTA, horaConsultaReal)
            .putInt(NotificationWorker.EXTRA_MINUTO_CONSULTA, minutoConsultaReal)
            .putBoolean(NotificationWorker.EXTRA_IS_CONSULTA, true)
            .putBoolean(NotificationWorker.EXTRA_IS_CONFIRMACAO, true)
            // CORREÇÃO: Passar o tipo do lembrete
            .putString(NotificationWorker.EXTRA_TIPO_LEMBRETE, lembrete.dose)
            .build()


        var delay = lembrete.proximaOcorrenciaMillis - System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        Log.d(tag, "Agendando WorkManager para confirmação de consulta: Lembrete ID=${lembrete.id}")
        Log.d(tag, "Timestamp do WorkManager: ${lembrete.proximaOcorrenciaMillis} (${dateFormat.format(Date(lembrete.proximaOcorrenciaMillis))})")
        Log.d(tag, "Timestamp atual: ${System.currentTimeMillis()} (${dateFormat.format(Date(System.currentTimeMillis()))})")
        Log.d(tag, "Delay inicial: ${delay / 1000 / 60} minutos")
        // Se o delay for negativo ou muito pequeno, agendar para daqui a 5 segundos
        if (delay <= 0) {
            delay = 5000 // 5 segundos
            Log.d(tag, "Delay era <= 0 para confirmação de consulta. Ajustando para 5 segundos. Lembrete ID: ${lembrete.id}")
        }

        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInputData(workData)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag("consulta_${lembrete.medicamentoId}_${lembrete.id}")
            .build()

        WorkManager.getInstance(context).enqueue(request)
        Log.d(tag, "WorkManager agendado para confirmação de consulta (3h depois). Lembrete ID: ${lembrete.id}, delay: ${delay}ms")
    }

    /**
     * Agenda alarme para consulta usando setAndAllowWhileIdle (alarme inexato).
     * Consultas não precisam de alarmes exatos.
     *
     * CORREÇÃO: Adicionado parâmetros horaConsultaReal e minutoConsultaReal
     */
    private fun agendarAlarmeConsulta(context: Context, lembrete: Lembrete, horaConsultaReal: Int, minutoConsultaReal: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(NotificationWorker.EXTRA_LEMBRETE_ID, lembrete.id)
            putExtra(NotificationWorker.EXTRA_MEDICAMENTO_ID, lembrete.medicamentoId)
            putExtra(NotificationWorker.EXTRA_CONSULTA_ID, lembrete.medicamentoId) // CORREÇÃO: Passar o consultaId
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_TITLE, lembrete.nomeMedicamento)
            putExtra(NotificationWorker.EXTRA_NOTIFICATION_MESSAGE, lembrete.observacao)
            putExtra(NotificationWorker.EXTRA_RECIPIENT_NAME, lembrete.recipientName)
            putExtra(NotificationWorker.EXTRA_PROXIMA_OCORRENCIA_MILLIS, lembrete.proximaOcorrenciaMillis)
            putExtra(NotificationWorker.EXTRA_HORA, lembrete.hora)
            putExtra(NotificationWorker.EXTRA_MINUTO, lembrete.minuto)
            // CORREÇÃO: Passar a hora e minuto REAL da consulta
            putExtra(NotificationWorker.EXTRA_HORA_CONSULTA, horaConsultaReal)
            putExtra(NotificationWorker.EXTRA_MINUTO_CONSULTA, minutoConsultaReal)
            putExtra(NotificationWorker.EXTRA_IS_CONSULTA, true)
            // CORREÇÃO: Passar o tipo do lembrete
            putExtra(NotificationWorker.EXTRA_TIPO_LEMBRETE, lembrete.dose)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            lembrete.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = lembrete.proximaOcorrenciaMillis
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        Log.d(tag, "Agendando alarme de consulta: Lembrete ID=${lembrete.id}, Tipo=${lembrete.dose}")
        Log.d(tag, "Timestamp do alarme: $triggerAtMillis (${dateFormat.format(Date(triggerAtMillis))})")
        Log.d(tag, "Timestamp atual: $now (${dateFormat.format(Date(now))})")
        Log.d(tag, "Diferença: ${(triggerAtMillis - now) / 1000 / 60} minutos")
        if (triggerAtMillis < now) {
            Log.w(tag, "AVISO: Timestamp do alarme está no passado! Alarme NÃO será agendado.")
            return
        }

        try {
            // CONSULTAS usam setAndAllowWhileIdle (alarme inexato)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.d(tag, "Alarme INEXATO agendado para CONSULTA (Lembrete ID: ${lembrete.id})")
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.d(tag, "Alarme agendado para CONSULTA (API < 23, Lembrete ID: ${lembrete.id})")
            }
        } catch (e: Exception) {
            Log.e(tag, "Erro ao agendar alarme de consulta", e)
        }
    }

    private suspend fun atualizarLembretesConsulta(context: Context, lembreteDao: LembreteDao, consultationId: String, consultation: Consultation) {
        Log.d(tag, "=== atualizarLembretesConsulta CHAMADO ===")
        Log.d(tag, "consultationId: $consultationId")
        try {
            Log.d(tag, "Buscando lembretes antigos...")
            val lembretesAntigos = lembreteDao.getLembretesByMedicamentoId(consultationId)
            Log.d(tag, "Encontrados ${lembretesAntigos.size} lembretes antigos para cancelar")

            lembretesAntigos.forEach { lembreteAntigo ->
                Log.d(tag, "Cancelando lembrete antigo ID: ${lembreteAntigo.id}")
                try {
                    AlarmScheduler.cancelAlarm(context, lembreteAntigo.id)
                    WorkManager.getInstance(context).cancelAllWorkByTag("consulta_${consultationId}_${lembreteAntigo.id}")
                    Log.d(tag, "Lembrete ${lembreteAntigo.id} cancelado com sucesso")
                } catch (e: Exception) {
                    Log.e(tag, "Erro ao cancelar lembrete ${lembreteAntigo.id}", e)
                }
            }

            Log.d(tag, "Deletando lembretes antigos do banco...")
            lembreteDao.deleteLembretesByMedicamentoId(consultationId)
            Log.d(tag, "Lembretes antigos deletados. Criando novos...")

            agendarLembretesConsulta(context, lembreteDao, consultationId, consultation)
            Log.d(tag, "atualizarLembretesConsulta concluído com sucesso")
        } catch (e: Exception) {
            Log.e(tag, "ERRO FATAL em atualizarLembretesConsulta", e)
            Log.e(tag, "Stack trace: ${e.stackTraceToString()}")
        }
    }

    fun onNavigationHandled() { _navigateBack.value = false }
    fun onErrorShown() { _error.value = null }
}
