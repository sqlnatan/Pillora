package com.pillora.pillora.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Data class to hold timestamp and its type
data class LembreteInfo(val timestamp: Long, val tipo: String)

object DateTimeUtils {

    const val TIPO_24H_ANTES = "24 horas antes"
    const val TIPO_2H_ANTES = "2 horas antes"
    const val TIPO_3H_DEPOIS = "3 horas depois"

    // Função existente para medicamentos (mantida como está)
    fun calcularProximasOcorrenciasIntervalo(
        startDateString: String, // Formato "dd/MM/yyyy" ou "ddMMyyyy"
        startTimeString: String, // Formato "HH:mm"
        intervalHours: Int,
        durationDays: Int // -1 para contínuo
    ): List<Long> {
        val ocorrencias = mutableListOf<Long>()
        Log.d("DateTimeUtils", "Calculando ocorrências: startDate=$startDateString, startTime=$startTimeString, intervalHours=$intervalHours, durationDays=$durationDays")
        if (intervalHours <= 0) {
            Log.e("DateTimeUtils", "Intervalo de horas inválido: $intervalHours")
            return ocorrencias
        }
        var formattedStartDate = startDateString
        if (!startDateString.contains("/") && startDateString.length == 8 && startDateString.all { it.isDigit() }) {
            formattedStartDate = "${startDateString.substring(0, 2)}/${startDateString.substring(2, 4)}/${startDateString.substring(4)}"
        }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        val startDateTimeString = "$formattedStartDate $startTimeString"
        try {
            val initialCalendar = Calendar.getInstance().apply {
                time = dateFormat.parse(startDateTimeString) ?: run {
                    Log.e("DateTimeUtils", "Falha ao parsear data/hora de início: $startDateTimeString")
                    return emptyList()
                }
            }
            val now = Calendar.getInstance()
            val initialTimestamp = initialCalendar.timeInMillis
            val currentTimestamp = now.timeInMillis
            if (initialTimestamp > currentTimestamp) {
                ocorrencias.add(initialTimestamp)
            } else {
                val intervalMillis = intervalHours * 60 * 60 * 1000L
                val elapsedMillis = currentTimestamp - initialTimestamp
                val completedIntervals = elapsedMillis / intervalMillis
                val nextCalendar = Calendar.getInstance().apply {
                    timeInMillis = initialTimestamp
                    add(Calendar.HOUR_OF_DAY, (completedIntervals + 1).toInt() * intervalHours)
                }
                if (nextCalendar.timeInMillis <= currentTimestamp) {
                    nextCalendar.add(Calendar.HOUR_OF_DAY, intervalHours)
                }
                ocorrencias.add(nextCalendar.timeInMillis)
            }
            val calendar = Calendar.getInstance().apply {
                timeInMillis = ocorrencias[0]
            }
            val dataLimiteCalculo = Calendar.getInstance().apply {
                time = calendar.time
                if (durationDays == -1) {
                    add(Calendar.DAY_OF_MONTH, 30)
                } else if (durationDays > 0) {
                    add(Calendar.DAY_OF_MONTH, durationDays)
                } else {
                    return emptyList()
                }
            }
            while (true) {
                calendar.add(Calendar.HOUR_OF_DAY, intervalHours)
                if (calendar.timeInMillis < dataLimiteCalculo.timeInMillis) {
                    ocorrencias.add(calendar.timeInMillis)
                } else {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("DateTimeUtils", "Erro ao calcular ocorrências para $startDateTimeString com intervalo $intervalHours", e)
            return emptyList()
        }
        Log.d("DateTimeUtils", "Total de ${ocorrencias.size} ocorrências calculadas")
        return ocorrencias
    }

    // Função para calcular lembretes de consultas (retorna lista de LembreteInfo)
    fun calcularLembretesConsulta(
        consultaDateString: String, // Formato "dd/MM/yyyy" ou "ddMMyyyy"
        consultaTimeString: String  // Formato "HH:mm"
    ): List<LembreteInfo> {
        val lembretes = mutableListOf<LembreteInfo>()
        Log.d("DateTimeUtils", "Calculando lembretes para consulta: data=$consultaDateString, hora=$consultaTimeString")

        var formattedConsultaDate = consultaDateString
        if (!consultaDateString.contains("/") && consultaDateString.length == 8 && consultaDateString.all { it.isDigit() }) {
            formattedConsultaDate = "${consultaDateString.substring(0, 2)}/${consultaDateString.substring(2, 4)}/${consultaDateString.substring(4)}"
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        val consultaDateTimeString = "$formattedConsultaDate $consultaTimeString"

        try {
            val consultaCalendar = Calendar.getInstance().apply {
                time = dateFormat.parse(consultaDateTimeString) ?: run {
                    Log.e("DateTimeUtils", "Falha ao parsear data/hora da consulta: $consultaDateTimeString")
                    return emptyList()
                }
            }
            val consultaTimestamp = consultaCalendar.timeInMillis
            Log.d("DateTimeUtils", "Data/hora da consulta parseada: ${dateFormat.format(consultaCalendar.time)}, timestamp: $consultaTimestamp")

            val now = Calendar.getInstance().timeInMillis

            // Calcular timestamp para 24 horas antes
            val lembrete24hTs = consultaTimestamp - (24 * 60 * 60 * 1000L)
            if (lembrete24hTs > now) {
                lembretes.add(LembreteInfo(lembrete24hTs, TIPO_24H_ANTES))
                Log.d("DateTimeUtils", "Lembrete 24h antes adicionado: ${dateFormat.format(Date(lembrete24hTs))}, timestamp: $lembrete24hTs")
            } else {
                Log.d("DateTimeUtils", "Lembrete 24h antes já passou: ${dateFormat.format(Date(lembrete24hTs))}")
            }

            // Calcular timestamp para 2 horas antes
            val lembrete2hTs = consultaTimestamp - (2 * 60 * 60 * 1000L)
            if (lembrete2hTs > now) {
                lembretes.add(LembreteInfo(lembrete2hTs, TIPO_2H_ANTES))
                Log.d("DateTimeUtils", "Lembrete 2h antes adicionado: ${dateFormat.format(Date(lembrete2hTs))}, timestamp: $lembrete2hTs")
            } else {
                Log.d("DateTimeUtils", "Lembrete 2h antes já passou: ${dateFormat.format(Date(lembrete2hTs))}")
            }

            // Calcular timestamp para 3 horas depois
            val lembrete3hDepoisTs = consultaTimestamp + (3 * 60 * 60 * 1000L)
            if (lembrete3hDepoisTs > now) {
                lembretes.add(LembreteInfo(lembrete3hDepoisTs, TIPO_3H_DEPOIS))
                Log.d("DateTimeUtils", "Lembrete 3h depois adicionado: ${dateFormat.format(Date(lembrete3hDepoisTs))}, timestamp: $lembrete3hDepoisTs")
            } else {
                Log.d("DateTimeUtils", "Lembrete 3h depois já passou: ${dateFormat.format(Date(lembrete3hDepoisTs))}")
            }

        } catch (e: Exception) {
            Log.e("DateTimeUtils", "Erro ao calcular lembretes para consulta $consultaDateTimeString", e)
            return emptyList()
        }

        Log.d("DateTimeUtils", "Total de ${lembretes.size} lembretes calculados para a consulta")
        // Ordenar por timestamp para garantir a ordem correta
        return lembretes.sortedBy { it.timestamp }
    }
}

