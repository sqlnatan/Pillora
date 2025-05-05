package com.pillora.pillora.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Utilitário para validação de datas no formato DD/MM/AAAA
 */
object DateValidator {
    // Usando lazy para evitar problemas com Locale.getDefault()
    private val dateFormat by lazy {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
            isLenient = false // Força validação estrita de datas
        }
    }

    /**
     * Valida se uma string de data está no formato correto e representa uma data válida
     * @param dateStr String contendo apenas dígitos (8 caracteres para DD/MM/AAAA)
     * @return Par contendo (isValid, errorMessage)
     */
    fun validateDate(dateStr: String): Pair<Boolean, String?> {
        // Verifica se tem 8 dígitos
        // **NOTA:** Esta validação original espera 8 dígitos SEM barras,
        // mas o ViewModel parece usar DD/MM/AAAA. Isso pode ser a causa raiz.
        // No entanto, como você indicou que funciona para outros formulários,
        // vamos manter a lógica original por enquanto e focar no ViewModel.
        if (dateStr.length != 8) {
            // Se o ViewModel *realmente* passa DD/MM/AAAA, esta condição SEMPRE falhará.
            // Se ele passa 8 dígitos, então está correto.
            // return Pair(false, "Data incompleta. Use o formato DD/MM/AAAA") // Mensagem original pode ser confusa
            return Pair(false, "Formato de data inválido (esperado 8 dígitos).")
        }

        try {
            // Formata a string para DD/MM/AAAA
            val formattedDate = formatDateString(dateStr) // Isso adiciona as barras

            // Tenta fazer o parse da data
            val date = dateFormat.parse(formattedDate) ?:
            return Pair(false, "Data inválida. Verifique dia, mês e ano.")

            // Se chegou aqui, a data é válida
            // Verifica se a data é futura
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            val dateCalendar = Calendar.getInstance().apply {
                time = date
            }

            // **IMPORTANTE:** Esta lógica retorna uma MENSAGEM mesmo se a data for VÁLIDA (apenas futura)
            if (dateCalendar.time.after(today)) {
                return Pair(true, "Atenção: A data selecionada está no futuro")
            }

            return Pair(true, null) // Retorna null apenas se for hoje ou passado
        } catch (exception: Exception) {
            return Pair(false, "Data inválida. Verifique dia, mês e ano.")
        }
    }

    /**
     * Formata uma string de dígitos para o formato de data DD/MM/AAAA
     * @param dateStr String contendo apenas dígitos (8 caracteres)
     * @return String formatada como DD/MM/AAAA ou string vazia se inválida
     */
    fun formatDateString(dateStr: String): String {
        if (dateStr.length != 8) return ""

        return "${dateStr.substring(0, 2)}/${dateStr.substring(2, 4)}/${dateStr.substring(4, 8)}"
    }

}

