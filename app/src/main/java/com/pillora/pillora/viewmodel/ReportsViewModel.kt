package com.pillora.pillora.viewmodel

import android.app.Application
import android.os.Environment
import android.util.Log
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.pillora.pillora.R
import com.pillora.pillora.data.UserPreferences
import com.pillora.pillora.repository.MedicineRepository
import com.pillora.pillora.repository.ConsultationRepository
import com.pillora.pillora.repository.VaccineRepository
import com.pillora.pillora.repository.DataResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

data class ReportFile(
    val name: String,
    val path: String,
    val lastModified: Long,
    val size: Long
)

class ReportsViewModel(
    application: Application,
    userPreferences: UserPreferences,
    private val currentUserId: String?
) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>().applicationContext

    private val _reportFiles = MutableStateFlow<List<ReportFile>>(emptyList())
    val reportFiles: StateFlow<List<ReportFile>> = _reportFiles

    val isPremium: StateFlow<Boolean> = userPreferences.isPremium.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    /** Diretório interno correto e compatível com o FileProvider (files/reports/UID) */
    private val reportsDir: File by lazy {
        val userId = currentUserId ?: "guest"
        File(context.filesDir, "reports/$userId").apply {
            if (!exists()) mkdirs()
        }
    }

    init {
        loadReportFiles()
    }

    /** Carrega a lista de relatórios armazenados localmente */
    fun loadReportFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val files = reportsDir.listFiles { _, name -> name.endsWith(".pdf") }
                ?.map { file ->
                    ReportFile(
                        name = file.name,
                        path = file.absolutePath,
                        lastModified = file.lastModified(),
                        size = file.length()
                    )
                }
                ?.sortedByDescending { it.lastModified }
                ?: emptyList()

            _reportFiles.value = files
            Log.d("ReportsViewModel", "✅ Carregados ${files.size} relatórios.")
        }
    }

    /** Exclui um relatório do diretório interno */
    fun deleteReport(reportFile: ReportFile) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(reportFile.path)
            if (file.exists() && file.delete()) {
                Log.d("ReportsViewModel", "🗑️ Relatório excluído: ${reportFile.name}")
                loadReportFiles()
            } else {
                Log.e("ReportsViewModel", "⚠️ Falha ao excluir: ${reportFile.name}")
            }
        }
    }

    /** Busca todos os nomes únicos de pacientes cadastrados */
    suspend fun getAllPatientNames(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val medicines = MedicineRepository.getAllMedicinesFlow().first()
                val consultations = ConsultationRepository.getAllConsultationsFlow().first()

                // Aguardar até receber DataResult.Success (pular Loading)
                val vaccines = VaccineRepository.getAllVaccinesFlow()
                    .first { it is DataResult.Success || it is DataResult.Error }
                    .let { result ->
                        when (result) {
                            is DataResult.Success -> {
                                Log.d("ReportsViewModel", "✅ Vacinas carregadas: ${result.data.size}")
                                result.data
                            }
                            is DataResult.Error -> {
                                Log.e("ReportsViewModel", "❌ Erro ao carregar vacinas: ${result.message}")
                                emptyList()
                            }
                            is DataResult.Loading -> emptyList() // Não deve acontecer devido ao first{}
                        }
                    }

                // Buscar nome do usuário logado
                val currentUser = Firebase.auth.currentUser
                val userName = currentUser?.displayName ?: "Usuário"

                // Coletar todos os nomes únicos
                val names = mutableSetOf<String>()

                // Adicionar nome do usuário como "Eu (Nome)"
                names.add("Eu ($userName)")

                // Adicionar nomes de medicamentos
                medicines.forEach { medicine ->
                    if (medicine.recipientName.isNotBlank()) {
                        names.add(medicine.recipientName)
                    }
                }

                // Adicionar nomes de consultas
                consultations.forEach { consultation ->
                    if (consultation.patientName.isNotBlank()) {
                        names.add(consultation.patientName)
                    }
                }

                // Adicionar nomes de vacinas
                Log.d("ReportsViewModel", "💉 Processando ${vaccines.size} vacinas")
                vaccines.forEach { vaccine ->
                    Log.d("ReportsViewModel", "  - Vacina: ${vaccine.name}, Paciente: '${vaccine.patientName}'")
                    if (vaccine.patientName.isNotBlank()) {
                        names.add(vaccine.patientName)
                        Log.d("ReportsViewModel", "    ✅ Nome adicionado: ${vaccine.patientName}")
                    } else {
                        Log.d("ReportsViewModel", "    ⚠️ Nome vazio, não adicionado")
                    }
                }

                // Retornar lista ordenada
                Log.d("ReportsViewModel", "📋 Total de nomes únicos encontrados: ${names.size}")
                Log.d("ReportsViewModel", "   Nomes: ${names.joinToString(", ")}")
                names.sorted()
            } catch (e: Exception) {
                Log.e("ReportsViewModel", "Erro ao buscar nomes de pacientes", e)
                emptyList()
            }
        }
    }

    /** Gera um novo relatório PDF aprimorado */
    fun generateReport(reportName: String, selectedPatient: String? = null) {
        if (!isPremium.value) {
            Log.w("ReportsViewModel", "⚠️ Tentativa de gerar relatório sem Premium.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Formato de data e hora para o nome do arquivo: DD-MM-YYYY_HH-mm-ss
            val dateTimeStr = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())

            try {
                // Buscar dados
                val allMedicines = MedicineRepository.getAllMedicinesFlow().first()
                val allConsultations = ConsultationRepository.getAllConsultationsFlow().first()

                // Aguardar até receber DataResult.Success (pular Loading)
                val allVaccines = VaccineRepository.getAllVaccinesFlow()
                    .first { it is DataResult.Success || it is DataResult.Error }
                    .let { result ->
                        when (result) {
                            is DataResult.Success -> result.data
                            else -> emptyList()
                        }
                    }

                // Buscar nome do usuário
                val currentUser = Firebase.auth.currentUser
                val userName = currentUser?.displayName ?: "Usuário"

                // Filtrar dados por paciente selecionado
                val medicines = if (selectedPatient != null) {
                    if (selectedPatient.startsWith("Eu (")) {
                        // Filtrar registros sem recipientName OU com recipientName igual ao userName (do próprio usuário)
                        allMedicines.filter { it.recipientName.isBlank() || it.recipientName == userName }
                    } else {
                        // Filtrar por nome específico
                        allMedicines.filter { it.recipientName == selectedPatient }
                    }
                } else {
                    allMedicines
                }

                val consultations = if (selectedPatient != null) {
                    if (selectedPatient.startsWith("Eu (")) {
                        // Filtrar registros sem patientName OU com patientName igual ao userName (do próprio usuário)
                        allConsultations.filter { it.patientName.isBlank() || it.patientName == userName }
                    } else {
                        allConsultations.filter { it.patientName == selectedPatient }
                    }
                } else {
                    allConsultations
                }

                val vaccines = if (selectedPatient != null) {
                    if (selectedPatient.startsWith("Eu (")) {
                        // Filtrar registros sem patientName OU com patientName igual ao userName (do próprio usuário)
                        allVaccines.filter { it.patientName.isBlank() || it.patientName == userName }
                    } else {
                        allVaccines.filter { it.patientName == selectedPatient }
                    }
                } else {
                    allVaccines
                }

                Log.d("ReportsViewModel", "📊 Gerando relatório para: $selectedPatient")
                Log.d("ReportsViewModel", "   Total de vacinas (todas): ${allVaccines.size}")
                Log.d("ReportsViewModel", "   Vacinas filtradas: ${vaccines.size}")
                vaccines.forEach { v ->
                    Log.d("ReportsViewModel", "     - ${v.name} (Paciente: '${v.patientName}')")
                }

                // Nome do paciente para o relatório
                val patientNameForReport = if (selectedPatient != null) {
                    if (selectedPatient.startsWith("Eu (")) userName else selectedPatient
                } else {
                    userName
                }

                // Criar nome do arquivo no formato: Relatório Pillora (Nome) (DATA_HORA).pdf
                val fileName = "Relatório Pillora ($patientNameForReport) ($dateTimeStr).pdf"
                val newFile = File(reportsDir, fileName)

                // Criar documento PDF
                val document = PdfDocument()
                val pageWidth = 595 // A4 width in points
                val pageHeight = 842 // A4 height in points
                var pageNumber = 1

                var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                var page = document.startPage(pageInfo)
                var canvas = page.canvas
                var y = 25f

                val paint = Paint()
                val leftMargin = 40f
                val rightMargin = pageWidth - 40f
                val lineHeight = 18f

                // ==================== CABEÇALHO EM DUAS COLUNAS ====================
                val headerStartY = y
                val currentDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

                // ===== LADO ESQUERDO: LOGO, NOME E SLOGAN =====
                var leftY = headerStartY
                try {
                    val logo = BitmapFactory.decodeResource(context.resources, R.drawable.app_logo)
                    val logoWidth = 50f
                    val logoHeight = 50f

                    // Desenhar logo no canto esquerdo
                    canvas.drawBitmap(
                        logo,
                        null,
                        android.graphics.RectF(leftMargin, leftY, leftMargin + logoWidth, leftY + logoHeight),
                        paint
                    )

                    // Desenhar texto "Pillora" ao lado do logo
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textSize = 28f
                    paint.color = Color.BLACK
                    paint.textAlign = Paint.Align.LEFT
                    val textX = leftMargin + logoWidth + 12f
                    val textY = leftY + (logoHeight / 2f) + (paint.textSize / 3f)
                    canvas.drawText("Pillora", textX, textY, paint)

                    leftY += logoHeight + 8f
                } catch (e: Exception) {
                    Log.e("ReportsViewModel", "Erro ao carregar logo", e)
                }

                // Slogan abaixo do logo (alinhado à esquerda)
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                paint.textSize = 10f
                paint.color = Color.GRAY
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("Cuidando da sua saúde, um lembrete de cada vez", leftMargin, leftY, paint)
                leftY += 15f

                // ===== LADO DIREITO: CARD DE IDENTIFICAÇÃO =====
                val cardPadding = 10f
                val titleHeight = 18f
                val cardWidth = 250f
                val cardHeight = titleHeight + (lineHeight * 2) + (cardPadding * 2)
                val cornerRadius = 8f
                val cardLeft = rightMargin - cardWidth
                val cardTop = headerStartY

                // Desenhar fundo azul muito claro (card) com cantos arredondados
                paint.color = Color.rgb(245, 250, 255)
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(
                    cardLeft,
                    cardTop,
                    rightMargin,
                    cardTop + cardHeight,
                    cornerRadius,
                    cornerRadius,
                    paint
                )

                // Desenhar borda do card
                paint.color = Color.rgb(200, 220, 240)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(
                    cardLeft,
                    cardTop,
                    rightMargin,
                    cardTop + cardHeight,
                    cornerRadius,
                    cornerRadius,
                    paint
                )

                // Conteúdo do card
                paint.style = Paint.Style.FILL
                paint.textAlign = Paint.Align.LEFT
                var cardY = cardTop + cardPadding + 12f

                // Título dentro do card
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 12f
                paint.color = Color.BLACK
                canvas.drawText("📋 IDENTIFICAÇÃO DO PACIENTE", cardLeft + 10f, cardY, paint)
                cardY += titleHeight

                // Dados do paciente
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 10f
                canvas.drawText("Nome: $patientNameForReport", cardLeft + 10f, cardY, paint)
                cardY += lineHeight
                canvas.drawText("Data: $currentDate", cardLeft + 10f, cardY, paint)

                // Avançar Y para depois da seção mais alta (card ou logo+slogan)
                y = Math.max(leftY, cardTop + cardHeight) + 10f

                // Linha separadora
                paint.strokeWidth = 1f
                paint.color = Color.LTGRAY
                canvas.drawLine(leftMargin, y, rightMargin, y, paint)
                y += 20f

                // ==================== MEDICAMENTOS ====================
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 16f
                paint.color = Color.BLACK
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("💊 MEDICAMENTOS", leftMargin, y, paint)
                y += 25f

                if (medicines.isEmpty()) {
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    paint.textSize = 12f
                    paint.color = Color.GRAY
                    canvas.drawText("Nenhum medicamento cadastrado.", leftMargin + 10f, y, paint)
                    y += 30f
                } else {
                    medicines.forEachIndexed { index, medicine ->
                        // Verificar se precisa de nova página
                        if (y > pageHeight - 100) {
                            document.finishPage(page)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            page = document.startPage(pageInfo)
                            canvas = page.canvas
                            y = 40f
                        }

                        paint.color = Color.BLACK
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        paint.textSize = 13f

                        // Nome do paciente (se diferente do usuário) ou nome do medicamento
                        val patientPrefix = if (medicine.recipientName.isNotBlank())
                            "${medicine.recipientName} - "
                        else
                            ""
                        canvas.drawText("${index + 1}. ${patientPrefix}${medicine.name}", leftMargin + 10f, y, paint)
                        y += lineHeight + 3f

                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        paint.textSize = 11f

                        // Dose
                        val doseText = if (medicine.doseUnit != null) {
                            "${medicine.dose} ${medicine.doseUnit}"
                        } else {
                            medicine.dose
                        }
                        canvas.drawText("   Dose: $doseText", leftMargin + 15f, y, paint)
                        y += lineHeight

                        // Frequência
                        val frequencyText = when (medicine.frequencyType) {
                            "vezes_dia" -> {
                                val times = medicine.timesPerDay ?: 0
                                val horarios = medicine.horarios?.joinToString(", ") ?: ""
                                "$times vez(es) ao dia${if (horarios.isNotEmpty()) " - Horários: $horarios" else ""}"
                            }
                            "a_cada_x_horas" -> {
                                val interval = medicine.intervalHours ?: 0
                                val startTime = medicine.startTime ?: ""
                                "A cada $interval horas${if (startTime.isNotEmpty()) " (início: $startTime)" else ""}"
                            }
                            else -> medicine.frequencyType
                        }
                        canvas.drawText("   Frequência: $frequencyText", leftMargin + 15f, y, paint)
                        y += lineHeight

                        // Início e duração
                        val durationText = if (medicine.duration == -1) {
                            "Contínuo"
                        } else {
                            "${medicine.duration} dias"
                        }
                        // Formatar data de início corretamente
                        val formattedStartDate = try {
                            val inputFormat = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
                            val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                            val date = inputFormat.parse(medicine.startDate)
                            if (date != null) outputFormat.format(date) else medicine.startDate
                        } catch (e: Exception) {
                            medicine.startDate // Se já estiver formatado ou outro formato, usar como está
                        }
                        canvas.drawText("   Início: $formattedStartDate | Duração: $durationText", leftMargin + 15f, y, paint)
                        y += lineHeight

                        // Observações
                        if (medicine.notes.isNotEmpty()) {
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                            paint.color = Color.DKGRAY

                            // Quebrar texto longo em múltiplas linhas
                            val maxWidth = rightMargin - (leftMargin + 15f)
                            val words = medicine.notes.split(" ")
                            var currentLine = "   Obs: "

                            words.forEach { word ->
                                val testLine = "$currentLine $word"
                                if (paint.measureText(testLine) > maxWidth && currentLine != "   Obs: ") {
                                    canvas.drawText(currentLine, leftMargin + 15f, y, paint)
                                    y += lineHeight
                                    currentLine = "        $word"
                                } else {
                                    currentLine = if (currentLine == "   Obs: ") "$currentLine$word" else "$currentLine $word"
                                }
                            }
                            if (currentLine.isNotEmpty()) {
                                canvas.drawText(currentLine, leftMargin + 15f, y, paint)
                                y += lineHeight
                            }

                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                            paint.color = Color.BLACK
                        }

                        y += 8f // Espaço entre medicamentos
                    }
                }

                y += 15f

                // ==================== VACINAS ====================
                // Verificar se precisa de nova página
                if (y > pageHeight - 100) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = 40f
                }

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 16f
                paint.color = Color.BLACK
                canvas.drawText("💉 VACINAS", leftMargin, y, paint)
                y += 25f

                Log.d("ReportsViewModel", "📝 Renderizando seção de vacinas no PDF (${vaccines.size} vacinas)")
                if (vaccines.isEmpty()) {
                    Log.d("ReportsViewModel", "   ⚠️ Nenhuma vacina para renderizar")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    paint.textSize = 12f
                    paint.color = Color.GRAY
                    canvas.drawText("Nenhuma vacina cadastrada.", leftMargin + 10f, y, paint)
                    y += 30f
                } else {
                    Log.d("ReportsViewModel", "   ✅ Renderizando ${vaccines.size} vacinas")
                    vaccines.forEachIndexed { index, vaccine ->
                        if (y > pageHeight - 100) {
                            document.finishPage(page)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            page = document.startPage(pageInfo)
                            canvas = page.canvas
                            y = 40f
                        }

                        paint.color = Color.BLACK
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        paint.textSize = 13f

                        val patientPrefix = if (vaccine.patientName.isNotBlank())
                            "${vaccine.patientName} - "
                        else
                            ""
                        canvas.drawText("${index + 1}. ${patientPrefix}${vaccine.name}", leftMargin + 10f, y, paint)
                        y += lineHeight + 3f

                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        paint.textSize = 11f

                        val dateTime = "${vaccine.reminderDate}${if (vaccine.reminderTime.isNotEmpty()) " às ${vaccine.reminderTime}" else ""}"
                        canvas.drawText("   Data: $dateTime", leftMargin + 15f, y, paint)
                        y += lineHeight

                        if (vaccine.location.isNotEmpty()) {
                            canvas.drawText("   Local: ${vaccine.location}", leftMargin + 15f, y, paint)
                            y += lineHeight
                        }

                        if (vaccine.notes.isNotEmpty()) {
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                            paint.color = Color.DKGRAY
                            canvas.drawText("   Obs: ${vaccine.notes}", leftMargin + 15f, y, paint)
                            y += lineHeight
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                            paint.color = Color.BLACK
                        }

                        y += 8f
                    }
                }

                y += 15f

                // ==================== CONSULTAS ====================
                if (y > pageHeight - 100) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = 40f
                }

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 16f
                paint.color = Color.BLACK
                canvas.drawText("🩺 CONSULTAS", leftMargin, y, paint)
                y += 25f

                if (consultations.isEmpty()) {
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    paint.textSize = 12f
                    paint.color = Color.GRAY
                    canvas.drawText("Nenhuma consulta cadastrada.", leftMargin + 10f, y, paint)
                    y += 30f
                } else {
                    consultations.forEachIndexed { index, consultation ->
                        if (y > pageHeight - 100) {
                            document.finishPage(page)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            page = document.startPage(pageInfo)
                            canvas = page.canvas
                            y = 40f
                        }

                        paint.color = Color.BLACK
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        paint.textSize = 13f

                        val patientPrefix = if (consultation.patientName.isNotBlank())
                            "${consultation.patientName} - "
                        else
                            ""
                        canvas.drawText("${index + 1}. ${patientPrefix}${consultation.specialty}", leftMargin + 10f, y, paint)
                        y += lineHeight + 3f

                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        paint.textSize = 11f

                        canvas.drawText("   Médico: ${consultation.doctorName}", leftMargin + 15f, y, paint)
                        y += lineHeight

                        canvas.drawText("   Data/Hora: ${consultation.dateTime}", leftMargin + 15f, y, paint)
                        y += lineHeight

                        if (consultation.location.isNotEmpty()) {
                            canvas.drawText("   Local: ${consultation.location}", leftMargin + 15f, y, paint)
                            y += lineHeight
                        }

                        if (consultation.observations.isNotEmpty()) {
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                            paint.color = Color.DKGRAY
                            canvas.drawText("   Obs: ${consultation.observations}", leftMargin + 15f, y, paint)
                            y += lineHeight
                            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                            paint.color = Color.BLACK
                        }

                        y += 8f
                    }
                }

                // ==================== RODAPÉ OTIMIZADO ====================
                // Ir para o final da página ou criar nova se necessário
                val disclaimerHeight = 65f
                if (y > pageHeight - disclaimerHeight - 40f) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    y = pageHeight - disclaimerHeight - 20f
                } else {
                    y = pageHeight - disclaimerHeight - 20f
                }

                // Linha separadora
                paint.strokeWidth = 1f
                paint.color = Color.LTGRAY
                canvas.drawLine(leftMargin, y, rightMargin, y, paint)
                y += 12f

                // Calcular largura das 2 colunas (mais largas)
                val totalWidth = rightMargin - leftMargin
                val columnSpacing = 20f
                val columnWidth = (totalWidth - columnSpacing) / 2f
                val col1X = leftMargin
                val col2X = leftMargin + columnWidth + columnSpacing
                val footerStartY = y

                // ===== COLUNA 1: AVISO IMPORTANTE =====
                var col1Y = footerStartY
                paint.color = Color.DKGRAY
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 8f
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("⚠️ AVISO IMPORTANTE", col1X, col1Y, paint)
                col1Y += 12f

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 7f

                val disclaimer1 = """
                Este relatório é gerado automaticamente pelo
                aplicativo Pillora e destina-se exclusivamente
                ao controle pessoal de medicamentos, consultas
                e vacinas.
                """.trimIndent()

                disclaimer1.lines().forEach { line ->
                    canvas.drawText(line.trim(), col1X, col1Y, paint)
                    col1Y += 9f
                }

                // ===== COLUNA 2: RESPONSABILIDADE MÉDICA =====
                var col2Y = footerStartY
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 8f
                canvas.drawText("👨‍⚕️ RESPONSABILIDADE MÉDICA", col2X, col2Y, paint)
                col2Y += 12f

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 7f

                val disclaimer2 = """
                NÃO INCENTIVAMOS O USO DE QUALQUER
                MEDICAMENTO SEM PRESCRIÇÃO MÉDICA.
                Sempre consulte um profissional de saúde
                qualificado antes de iniciar, alterar ou
                interromper qualquer tratamento médico.
                """.trimIndent()

                disclaimer2.lines().forEach { line ->
                    canvas.drawText(line.trim(), col2X, col2Y, paint)
                    col2Y += 9f
                }

                // Avançar para depois dos avisos
                y = Math.max(col1Y, col2Y) + 8f

                // ===== ASSINATURA NO FINAL (CENTRALIZADA) =====
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                paint.textSize = 7f
                paint.color = Color.GRAY
                paint.textAlign = Paint.Align.CENTER

                val appVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) {
                    "1.0.0"
                }

                canvas.drawText("Relatório gerado automaticamente pelo aplicativo Pillora - versão $appVersion © 2025", pageWidth / 2f, y, paint)

                // Finalizar documento
                document.finishPage(page)
                newFile.outputStream().use { document.writeTo(it) }
                document.close()

                Log.d("ReportsViewModel", "✅ PDF criado: ${newFile.absolutePath}")
                loadReportFiles()
            } catch (e: Exception) {
                Log.e("ReportsViewModel", "❌ Erro ao gerar PDF", e)
            }
        }
    }

    /** Copia o relatório para a pasta Downloads (para o botão "Baixar") */
    fun downloadReport(reportFile: ReportFile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()

                val sourceFile = File(reportFile.path)
                val destFile = File(downloadsDir, reportFile.name)

                FileInputStream(sourceFile).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                Log.d("ReportsViewModel", "📁 Relatório baixado em: ${destFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("ReportsViewModel", "❌ Erro ao salvar relatório", e)
            }
        }
    }

    companion object {
        fun provideFactory(
            application: Application,
            userPreferences: UserPreferences,
            currentUserId: String?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReportsViewModel(
                    application,
                    userPreferences,
                    currentUserId
                ) as T
            }
        }
    }
}
