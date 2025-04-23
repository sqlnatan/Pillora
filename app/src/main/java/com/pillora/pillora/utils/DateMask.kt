package com.pillora.pillora.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Utilitário para formatação e validação de datas no formato DD/MM/AAAA
 */
object DateMask {
    const val MAX_LENGTH = 10 // DD/MM/AAAA

    /**
     * Aplica a máscara de data (DD/MM/AAAA) à string de entrada
     * @param text String contendo dígitos ou já parcialmente formatada
     * @return String formatada com a máscara de data
     */
    fun mask(text: String): String {
        // Remove caracteres não numéricos
        val digitsOnly = text.filter { it.isDigit() }

        // Limita a 8 dígitos (DD/MM/AAAA)
        val limitedDigits = digitsOnly.take(8)

        return buildString {
            limitedDigits.forEachIndexed { index, char ->
                append(char)
                // Adiciona barras após o segundo e quarto dígitos
                if (index == 1 && limitedDigits.length > 2) {
                    append('/')
                } else if (index == 3 && limitedDigits.length > 4) {
                    append('/')
                }
            }
        }
    }

    /**
     * Verifica se a data formatada é válida
     * @param date String no formato DD/MM/AAAA
     * @return Boolean indicando se a data é válida
     */
    fun isValid(date: String): Boolean {
        if (date.length != 10) return false

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        dateFormat.isLenient = false // Força validação estrita

        return try {
            dateFormat.parse(date)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Verifica se a data é futura
     * @param date String no formato DD/MM/AAAA
     * @return Boolean indicando se a data é futura
     */
    fun isFutureDate(date: String): Boolean {
        if (!isValid(date)) return false

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        try {
            val parsedDate = dateFormat.parse(date) ?: return false
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            return parsedDate.after(today)
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Formata uma data para exibição
     * @param date String no formato DD/MM/AAAA
     * @return String formatada para exibição
     */
    fun formatForDisplay(date: String): String {
        if (date.length != 10) return date

        return try {
            val parts = date.split("/")
            if (parts.size < 3) return date

            val day = parts[0].padStart(2, '0')
            val month = parts[1].padStart(2, '0')
            val year = parts[2].padStart(4, '0')

            "$day/$month/$year"
        } catch (e: Exception) {
            date
        }
    }

    /**
     * Converte uma string de data para um objeto Date
     * @param date String no formato DD/MM/AAAA
     * @return Date ou null se inválido
     */
    fun toDate(date: String): Date? {
        if (!isValid(date)) return null

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        return try {
            dateFormat.parse(date)
        } catch (e: Exception) {
            null
        }
    }
}
