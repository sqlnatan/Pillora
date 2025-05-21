package com.pillora.pillora.utils

import android.util.Log // Import necessário para Log.e
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    fun calcularProximasOcorrenciasIntervalo(
        startDateString: String, // Formato "dd/MM/yyyy" ou "ddMMyyyy"
        startTimeString: String, // Formato "HH:mm"
        intervalHours: Int,
        durationDays: Int // -1 para contínuo
    ): List<Long> {
        val ocorrencias = mutableListOf<Long>()

        Log.e("PILLORA_DEBUG", "Calculando ocorrências: startDate=$startDateString, startTime=$startTimeString, intervalHours=$intervalHours, durationDays=$durationDays")

        if (intervalHours <= 0) {
            Log.e("PILLORA_DEBUG", "Intervalo de horas inválido: $intervalHours")
            return ocorrencias
        }

        // Formatar a data corretamente, independente do formato de entrada
        var formattedStartDate = startDateString

        // Se a data não contém barras e tem 8 dígitos, adicionar as barras
        if (!startDateString.contains("/") && startDateString.length == 8 && startDateString.all { it.isDigit() }) {
            formattedStartDate = "${startDateString.substring(0, 2)}/${startDateString.substring(2, 4)}/${startDateString.substring(4)}"
            Log.e("PILLORA_DEBUG", "Data reformatada: $startDateString -> $formattedStartDate")
        }

        // Usar Locale.US para garantir consistência no parsing do formato de data/hora
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        val startDateTimeString = "$formattedStartDate $startTimeString"

        try {
            // Obter o timestamp da data/hora inicial configurada
            val initialCalendar = Calendar.getInstance().apply {
                time = dateFormat.parse(startDateTimeString) ?: run {
                    Log.e("PILLORA_DEBUG", "Falha ao parsear data/hora de início: $startDateTimeString")
                    return emptyList()
                }
            }

            Log.e("PILLORA_DEBUG", "Data/hora inicial parseada: ${dateFormat.format(initialCalendar.time)}")

            // Obter o timestamp atual
            val now = Calendar.getInstance()
            Log.e("PILLORA_DEBUG", "Data/hora atual: ${dateFormat.format(now.time)}")

            // Calcular quantos intervalos já se passaram desde a data inicial até agora
            val initialTimestamp = initialCalendar.timeInMillis
            val currentTimestamp = now.timeInMillis
            val elapsedMillis = currentTimestamp - initialTimestamp

            // Se a data inicial é futura, começamos dela
            if (initialTimestamp > currentTimestamp) {
                Log.e("PILLORA_DEBUG", "Data inicial é futura, começando dela")
                ocorrencias.add(initialTimestamp)
            } else {
                // Calcular quantos intervalos completos já se passaram
                val intervalMillis = intervalHours * 60 * 60 * 1000L
                val completedIntervals = elapsedMillis / intervalMillis

                Log.e("PILLORA_DEBUG", "Já se passaram $completedIntervals intervalos completos desde a data inicial")

                // Calcular o próximo horário a partir da data inicial + intervalos completos + 1
                val nextCalendar = Calendar.getInstance().apply {
                    timeInMillis = initialTimestamp
                    add(Calendar.HOUR_OF_DAY, (completedIntervals + 1).toInt() * intervalHours)
                }

                // Verificar se o próximo horário calculado é futuro
                if (nextCalendar.timeInMillis <= currentTimestamp) {
                    // Se ainda não for futuro, adicionar mais um intervalo
                    nextCalendar.add(Calendar.HOUR_OF_DAY, intervalHours)
                }

                Log.e("PILLORA_DEBUG", "Próximo horário calculado: ${dateFormat.format(nextCalendar.time)}")
                ocorrencias.add(nextCalendar.timeInMillis)
            }

            // Calcular os próximos horários a partir do primeiro
            val calendar = Calendar.getInstance().apply {
                timeInMillis = ocorrencias[0]
            }

            // Definir o limite de cálculo
            val dataLimiteCalculo = Calendar.getInstance().apply {
                time = calendar.time // Começa do primeiro horário calculado
                if (durationDays == -1) {
                    // Para tratamento contínuo, calculamos para um período padrão (ex: 30 dias)
                    add(Calendar.DAY_OF_MONTH, 30)
                    Log.e("PILLORA_DEBUG", "Tratamento contínuo, calculando para os próximos 30 dias.")
                } else if (durationDays > 0) {
                    add(Calendar.DAY_OF_MONTH, durationDays)
                    Log.e("PILLORA_DEBUG", "Calculando para duração de $durationDays dias.")
                } else {
                    Log.e("PILLORA_DEBUG", "Duração em dias inválida ($durationDays), retornando lista vazia.")
                    return emptyList()
                }
            }

            // Já adicionamos o primeiro horário, agora calculamos os próximos
            Log.e("PILLORA_DEBUG", "Primeira ocorrência adicionada: ${dateFormat.format(Date(ocorrencias[0]))}, timestamp: ${ocorrencias[0]}")

            // Calcular os próximos horários
            while (true) {
                calendar.add(Calendar.HOUR_OF_DAY, intervalHours)
                if (calendar.timeInMillis < dataLimiteCalculo.timeInMillis) {
                    ocorrencias.add(calendar.timeInMillis)
                    Log.e("PILLORA_DEBUG", "Próxima ocorrência adicionada: ${dateFormat.format(calendar.time)}, timestamp: ${calendar.timeInMillis}")
                } else {
                    Log.e("PILLORA_DEBUG", "Limite de cálculo atingido: ${dateFormat.format(dataLimiteCalculo.time)}")
                    break
                }
            }

        } catch (e: Exception) {
            Log.e("PILLORA_DEBUG", "Erro ao calcular ocorrências para $startDateTimeString com intervalo $intervalHours", e)
            return emptyList() // Retorna lista vazia em caso de erro
        }

        Log.e("PILLORA_DEBUG", "Total de ${ocorrencias.size} ocorrências calculadas")
        return ocorrencias
    }
}
