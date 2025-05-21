package com.pillora.pillora.utils

import android.util.Log // Import necessário para Log.e
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateTimeUtils {

    fun calcularProximasOcorrenciasIntervalo(
        startDateString: String, // Formato "dd/MM/yyyy"
        startTimeString: String, // Formato "HH:mm"
        intervalHours: Int,
        durationDays: Int // -1 para contínuo
    ): List<Long> {
        val ocorrencias = mutableListOf<Long>()
        if (intervalHours <= 0) {
            Log.e("DateTimeUtils", "Intervalo de horas inválido: $intervalHours")
            return ocorrencias
        }

        // Usar Locale.US para garantir consistência no parsing do formato de data/hora
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        val startDateTimeString = "$startDateString $startTimeString"

        try {
            val calendar = Calendar.getInstance().apply {
                time = dateFormat.parse(startDateTimeString) ?: run {
                    Log.e("DateTimeUtils", "Falha ao parsear data/hora de início: $startDateTimeString")
                    return emptyList()
                }
            }

            val dataLimiteCalculo = Calendar.getInstance().apply {
                time = calendar.time // Começa da data de início
                if (durationDays == -1) {
                    // Para tratamento contínuo, calculamos para um período padrão (ex: 30 dias)
                    // TODO: Considerar uma estratégia de longo prazo para tratamentos contínuos (ex: reagendar periodicamente)
                    add(Calendar.DAY_OF_MONTH, 30)
                    Log.d("DateTimeUtils", "Tratamento contínuo, calculando para os próximos 30 dias.")
                } else if (durationDays > 0) {
                    add(Calendar.DAY_OF_MONTH, durationDays)
                } else {
                    Log.w("DateTimeUtils", "Duração em dias inválida ($durationDays), retornando lista vazia.")
                    return emptyList()
                }
            }

            // Adiciona a primeira ocorrência.
            // O AlarmScheduler deve ser inteligente o suficiente para não agendar alarmes no passado.
            ocorrencias.add(calendar.timeInMillis)
            Log.d("DateTimeUtils", "Primeira ocorrência adicionada: ${dateFormat.format(calendar.time)}")

            while (true) {
                calendar.add(Calendar.HOUR_OF_DAY, intervalHours)
                if (calendar.timeInMillis < dataLimiteCalculo.timeInMillis) {
                    ocorrencias.add(calendar.timeInMillis)
                    Log.d("DateTimeUtils", "Próxima ocorrência adicionada: ${dateFormat.format(calendar.time)}")
                } else {
                    Log.d("DateTimeUtils", "Limite de cálculo atingido: ${dateFormat.format(dataLimiteCalculo.time)}")
                    break
                }
            }

        } catch (e: Exception) {
            Log.e("DateTimeUtils", "Erro ao calcular ocorrências para 	$startDateTimeString	 com intervalo $intervalHours", e)
            return emptyList() // Retorna lista vazia em caso de erro
        }
        Log.d("DateTimeUtils", "Total de ${ocorrencias.size} ocorrências calculadas.")
        return ocorrencias
    }
}
