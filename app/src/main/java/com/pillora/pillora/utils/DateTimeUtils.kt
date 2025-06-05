package com.pillora.pillora.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Data class to hold timestamp and its type
data class LembreteInfo(val timestamp: Long, val tipo: String)

object DateTimeUtils {

    // Tipos existentes
    const val TIPO_24H_ANTES = "24 horas antes"
    const val TIPO_2H_ANTES = "2 horas antes"
    const val TIPO_3H_DEPOIS = "3 horas depois"
    const val TIPO_CONFIRMACAO = "confirmação" // Tipo específico para vacina 3h depois

    // Novos tipos para Receitas
    const val TIPO_RECEITA_3D_ANTES = "Receita: 3 dias antes"
    const val TIPO_RECEITA_1D_ANTES = "Receita: 1 dia antes"
    const val TIPO_RECEITA_VENCIMENTO = "Receita: Dia do vencimento"
    const val TIPO_RECEITA_CONFIRMACAO = "Receita: Confirmação 1 dia depois"

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
                    // Se durationDays for 0 ou negativo (exceto -1), não calcula futuras
                    return ocorrencias.filter { it >= currentTimestamp } // Retorna apenas a primeira ocorrência futura
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
        Log.d("DateTimeUtils", "Total de ${ocorrencias.size} ocorrências calculadas para medicamento")
        // Filtrar ocorrências passadas no final
        val nowMillis = System.currentTimeMillis()
        return ocorrencias.filter { it >= nowMillis }
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
                Log.d("DateTimeUtils", "Lembrete Consulta 24h antes adicionado: ${dateFormat.format(Date(lembrete24hTs))}")
            } else {
                Log.d("DateTimeUtils", "Lembrete Consulta 24h antes já passou.")
            }

            // Calcular timestamp para 2 horas antes
            val lembrete2hTs = consultaTimestamp - (2 * 60 * 60 * 1000L)
            if (lembrete2hTs > now) {
                lembretes.add(LembreteInfo(lembrete2hTs, TIPO_2H_ANTES))
                Log.d("DateTimeUtils", "Lembrete Consulta 2h antes adicionado: ${dateFormat.format(Date(lembrete2hTs))}")
            } else {
                Log.d("DateTimeUtils", "Lembrete Consulta 2h antes já passou.")
            }

            // Calcular timestamp para 3 horas depois
            val lembrete3hDepoisTs = consultaTimestamp + (3 * 60 * 60 * 1000L)
            if (lembrete3hDepoisTs > now) {
                lembretes.add(LembreteInfo(lembrete3hDepoisTs, TIPO_3H_DEPOIS))
                Log.d("DateTimeUtils", "Lembrete Consulta 3h depois adicionado: ${dateFormat.format(Date(lembrete3hDepoisTs))}")
            } else {
                Log.d("DateTimeUtils", "Lembrete Consulta 3h depois já passou.")
            }

        } catch (e: Exception) {
            Log.e("DateTimeUtils", "Erro ao calcular lembretes para consulta $consultaDateTimeString", e)
            return emptyList()
        }

        Log.d("DateTimeUtils", "Total de ${lembretes.size} lembretes calculados para a consulta")
        return lembretes.sortedBy { it.timestamp }
    }

    // Função para calcular lembretes de vacina (retorna lista de LembreteInfo)
    fun calcularLembretesVacina(
        vacinaDateString: String, // Formato "dd/MM/yyyy" ou "ddMMyyyy"
        vacinaTimeString: String  // Formato "HH:mm"
    ): List<LembreteInfo> {
        val lembretes = mutableListOf<LembreteInfo>()
        Log.d("DateTimeUtils", "Calculando lembretes para vacina: data=$vacinaDateString, hora=$vacinaTimeString")

        var formattedVacinaDate = vacinaDateString
        if (!vacinaDateString.contains("/") && vacinaDateString.length == 8 && vacinaDateString.all { it.isDigit() }) {
            formattedVacinaDate = "${vacinaDateString.substring(0, 2)}/${vacinaDateString.substring(2, 4)}/${vacinaDateString.substring(4)}"
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
        val vacinaDateTimeString = "$formattedVacinaDate $vacinaTimeString"

        try {
            val vacinaCalendar = Calendar.getInstance().apply {
                time = dateFormat.parse(vacinaDateTimeString) ?: run {
                    Log.e("DateTimeUtils", "Falha ao parsear data/hora da vacina: $vacinaDateTimeString")
                    return emptyList()
                }
            }
            val vacinaTimestamp = vacinaCalendar.timeInMillis
            Log.d("DateTimeUtils", "Data/hora da vacina parseada: ${dateFormat.format(vacinaCalendar.time)}, timestamp: $vacinaTimestamp")

            val now = Calendar.getInstance().timeInMillis

            // Calcular timestamp para 24 horas antes
            val lembrete24hTs = vacinaTimestamp - (24 * 60 * 60 * 1000L)
            if (lembrete24hTs > now) {
                lembretes.add(LembreteInfo(lembrete24hTs, TIPO_24H_ANTES))
                Log.d("DateTimeUtils", "Lembrete Vacina 24h antes adicionado: ${dateFormat.format(Date(lembrete24hTs))}")
            } else {
                Log.d("DateTimeUtils", "Lembrete Vacina 24h antes já passou.")
            }

            // Calcular timestamp para 2 horas antes
            val lembrete2hTs = vacinaTimestamp - (2 * 60 * 60 * 1000L)
            if (lembrete2hTs > now) {
                lembretes.add(LembreteInfo(lembrete2hTs, TIPO_2H_ANTES))
                Log.d("DateTimeUtils", "Lembrete Vacina 2h antes adicionado: ${dateFormat.format(Date(lembrete2hTs))}")
            } else {
                Log.d("DateTimeUtils", "Lembrete Vacina 2h antes já passou.")
            }

            // Calcular timestamp para 3 horas depois (usando tipo específico TIPO_CONFIRMACAO)
            val lembreteConfirmacaoTs = vacinaTimestamp + (3 * 60 * 60 * 1000L)
            if (lembreteConfirmacaoTs > now) {
                lembretes.add(LembreteInfo(lembreteConfirmacaoTs, TIPO_CONFIRMACAO))
                Log.d("DateTimeUtils", "Lembrete Vacina confirmação (3h depois) adicionado: ${dateFormat.format(Date(lembreteConfirmacaoTs))}")
            } else {
                Log.d("DateTimeUtils", "Lembrete Vacina confirmação (3h depois) já passou.")
            }

        } catch (e: Exception) {
            Log.e("DateTimeUtils", "Erro ao calcular lembretes para vacina $vacinaDateTimeString", e)
            return emptyList()
        }

        Log.d("DateTimeUtils", "Total de ${lembretes.size} lembretes calculados para a vacina")
        return lembretes.sortedBy { it.timestamp }
    }

    // *** NOVA FUNÇÃO PARA RECEITAS ***
    fun calcularLembretesReceita(
        validityDateString: String // Formato "dd/MM/yyyy" ou "ddMMyyyy"
    ): List<LembreteInfo> {
        val lembretes = mutableListOf<LembreteInfo>()
        Log.d("DateTimeUtils", "Calculando lembretes para receita com validade: $validityDateString")

        if (validityDateString.isBlank()) {
            Log.w("DateTimeUtils", "Data de validade da receita está em branco. Nenhum lembrete será calculado.")
            return emptyList()
        }

        var formattedValidityDate = validityDateString
        if (!validityDateString.contains("/") && validityDateString.length == 8 && validityDateString.all { it.isDigit() }) {
            formattedValidityDate = "${validityDateString.substring(0, 2)}/${validityDateString.substring(2, 4)}/${validityDateString.substring(4)}"
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        dateFormat.isLenient = false // Importante para validar a data

        try {
            val validityDate = dateFormat.parse(formattedValidityDate) ?: run {
                Log.e("DateTimeUtils", "Falha ao parsear data de validade da receita: $formattedValidityDate")
                return emptyList()
            }

            val validityCalendar = Calendar.getInstance().apply {
                time = validityDate
                // Definir horário para o final do dia de validade para incluir o dia todo
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val validityTimestampEnd = validityCalendar.timeInMillis
            Log.d("DateTimeUtils", "Data de validade parseada: ${dateFormat.format(validityCalendar.time)}, timestamp (fim do dia): $validityTimestampEnd")

            val now = Calendar.getInstance().timeInMillis
            val notificationHour = 23 // Notificações ao meio-dia

            // --- Calcular Timestamps ---

            // 1. Lembrete 3 dias antes
            val lembrete3dCalendar = Calendar.getInstance().apply {
                time = validityDate
                add(Calendar.DAY_OF_YEAR, -3)
                set(Calendar.HOUR_OF_DAY, notificationHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val lembrete3dTs = lembrete3dCalendar.timeInMillis
            if (lembrete3dTs > now) {
                lembretes.add(LembreteInfo(lembrete3dTs, TIPO_RECEITA_3D_ANTES))
                Log.d("DateTimeUtils", "Lembrete Receita 3d antes adicionado: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(lembrete3dTs))}")
            } else {
                Log.d("DateTimeUtils", "Lembrete Receita 3d antes já passou.")
            }

            // 2. Lembrete 1 dia antes
            val lembrete1dCalendar = Calendar.getInstance().apply {
                time = validityDate
                add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, notificationHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val lembrete1dTs = lembrete1dCalendar.timeInMillis
            if (lembrete1dTs > now) {
                lembretes.add(LembreteInfo(lembrete1dTs, TIPO_RECEITA_1D_ANTES))
                Log.d("DateTimeUtils", "Lembrete Receita 1d antes adicionado: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(lembrete1dTs))}")
            } else {
                Log.d("DateTimeUtils", "Lembrete Receita 1d antes já passou.")
            }

            // 3. Lembrete no dia do vencimento
            val lembreteVencimentoCalendar = Calendar.getInstance().apply {
                time = validityDate
                set(Calendar.HOUR_OF_DAY, notificationHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val lembreteVencimentoTs = lembreteVencimentoCalendar.timeInMillis
            if (lembreteVencimentoTs > now) {
                lembretes.add(LembreteInfo(lembreteVencimentoTs, TIPO_RECEITA_VENCIMENTO))
                Log.d("DateTimeUtils", "Lembrete Receita dia do vencimento adicionado: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(lembreteVencimentoTs))}")
            } else {
                Log.d("DateTimeUtils", "Lembrete Receita dia do vencimento já passou.")
            }

            // 4. Lembrete de confirmação 1 dia depois
            val lembreteConfirmacaoCalendar = Calendar.getInstance().apply {
                time = validityDate
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, notificationHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val lembreteConfirmacaoTs = lembreteConfirmacaoCalendar.timeInMillis
            if (lembreteConfirmacaoTs > now) {
                lembretes.add(LembreteInfo(lembreteConfirmacaoTs, TIPO_RECEITA_CONFIRMACAO))
                Log.d("DateTimeUtils", "Lembrete Receita confirmação (1d depois) adicionado: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(lembreteConfirmacaoTs))}")
            } else {
                Log.d("DateTimeUtils", "Lembrete Receita confirmação (1d depois) já passou.")
            }

        } catch (e: Exception) {
            Log.e("DateTimeUtils", "Erro ao calcular lembretes para receita com validade $formattedValidityDate", e)
            return emptyList()
        }

        Log.d("DateTimeUtils", "Total de ${lembretes.size} lembretes calculados para a receita")
        return lembretes.sortedBy { it.timestamp }
    }
}

