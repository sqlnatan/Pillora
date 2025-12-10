package com.pillora.pillora.viewmodel

import android.app.Application
import android.os.Environment
import android.util.Log
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.graphics.Typeface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pillora.pillora.data.UserPreferences
import com.pillora.pillora.repository.MedicineRepository
import kotlinx.coroutines.Dispatchers
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

data class ReportFile(
    val name: String,
    val path: String,
    val lastModified: Long,
    val size: Long
)

class ReportsViewModel(
    application: Application,
    userPreferences: UserPreferences,
    private val medicineRepository: MedicineRepository,
    private val currentUserId: String? // Adicionado o UID do usuário
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
        val userId = currentUserId ?: "guest" // Usa 'guest' se o UID for nulo
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

    /** Gera um novo relatório PDF */
    fun generateReport(reportName: String) {
        if (!isPremium.value) {
            Log.w("ReportsViewModel", "⚠️ Tentativa de gerar relatório sem Premium.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${reportName}_$timestamp.pdf"
            val newFile = File(reportsDir, fileName)

            try {
                val medicines = medicineRepository.getAllMedicinesFlow().first()
                val document = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = document.startPage(pageInfo)
                var canvas = page.canvas
                val paint = Paint()

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 24f
                canvas.drawText("Relatório de Medicamentos Pillora", 40f, 60f, paint)

                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.textSize = 12f
                canvas.drawText(
                    "Gerado em: ${
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                    }",
                    40f,
                    80f,
                    paint
                )

                var y = 120f
                paint.textSize = 14f

                if (medicines.isEmpty()) {
                    canvas.drawText("Nenhum medicamento cadastrado.", 40f, y, paint)
                } else {
                    medicines.forEachIndexed { index, medicine ->
                        if (y > 800) {
                            document.finishPage(page)
                            val newPageInfo = PdfDocument.PageInfo.Builder(595, 842, index / 10 + 1).create()
                            val newPage = document.startPage(newPageInfo)
                            canvas = newPage.canvas
                            y = 60f
                        }

                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        canvas.drawText("${index + 1}. ${medicine.name}", 40f, y, paint)
                        y += 20f

                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        canvas.drawText("Dose: ${medicine.dose} ${medicine.doseUnit}", 60f, y, paint)
                        y += 20f
                        canvas.drawText("Frequência: ${medicine.frequencyType}", 60f, y, paint)
                        y += 20f
                        canvas.drawText(
                            "Duração: ${medicine.duration} dias (Início: ${medicine.startDate})",
                            60f,
                            y,
                            paint
                        )
                        y += 30f
                    }
                }

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
            medicineRepository: MedicineRepository,
            currentUserId: String? // Adicionado
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ReportsViewModel(application, userPreferences, medicineRepository, currentUserId) as T
            }
        }
    }
}
