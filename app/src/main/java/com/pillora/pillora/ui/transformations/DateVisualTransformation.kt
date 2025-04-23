package com.pillora.pillora.ui.transformations

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Transformação visual para formatar entrada de texto como data no formato DD/MM/AAAA
 * com mapeamento de offset corrigido para evitar problemas de seleção de texto
 */
class DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // Obtém apenas os dígitos do texto de entrada
        val digitsOnly = text.text.filter { it.isDigit() }

        // Formata a data com barras
        val formattedText = buildString {
            digitsOnly.forEachIndexed { index, char ->
                append(char)
                // Adiciona barras após o segundo e quarto dígitos
                if (index == 1 && digitsOnly.length > 2) {
                    append('/')
                } else if (index == 3 && digitsOnly.length > 4) {
                    append('/')
                }
            }
        }

        /**
         * Classe de mapeamento de offset corrigida que lida corretamente com a posição do cursor
         * durante a digitação com barras inseridas automaticamente
         */
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                // Se não houver texto ou offset for 0, retorna 0
                if (offset == 0) return 0

                // Conta quantos dígitos existem até o offset
                val digitCount = text.text.take(offset).count { it.isDigit() }

                // Calcula o novo offset com base na quantidade de barras adicionadas
                return when {
                    digitCount <= 2 -> digitCount // Antes da primeira barra
                    digitCount <= 4 -> digitCount + 1 // Após a primeira barra (DD/)
                    digitCount <= 8 -> digitCount + 2 // Após a segunda barra (DD/MM/)
                    else -> formattedText.length // Limita ao tamanho máximo
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                // Se não houver texto ou offset for 0, retorna 0
                if (offset == 0) return 0

                // Limita o offset ao tamanho do texto formatado
                val safeOffset = offset.coerceAtMost(formattedText.length)

                // Conta quantas barras existem até o offset
                val slashCount = formattedText.take(safeOffset).count { it == '/' }

                // Retorna o offset original removendo as barras
                return safeOffset - slashCount
            }
        }

        return TransformedText(AnnotatedString(formattedText), offsetMapping)
    }
}
