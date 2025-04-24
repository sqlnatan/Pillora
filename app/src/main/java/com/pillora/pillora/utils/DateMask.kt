package com.pillora.pillora.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

object DateMask {
    private const val DATE_MASK = "##/##/####"

    fun mask(text: String): String {
        val maskedText = StringBuilder()
        var textIndex = 0
        for (maskChar in DATE_MASK) {
            if (maskChar == '#') {
                if (textIndex < text.length) {
                    maskedText.append(text[textIndex])
                    textIndex++
                } else {
                    break // Sai do loop se não houver mais caracteres na entrada
                }
            } else {
                maskedText.append(maskChar)
            }
        }
        return maskedText.toString()
    }

    fun maskVisualTransformation() = object : VisualTransformation {
        override fun filter(text: AnnotatedString): TransformedText {
            val maskedText = mask(text.text)

            val offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    if (offset <= 0) return 0
                    if(offset <= 2) return offset + if (offset == 2) 1 else 0
                    if(offset <= 4) return offset + 1
                    if(offset <= 8) return offset + 2

                    return maskedText.length
                }

                override fun transformedToOriginal(offset: Int): Int {
                    if (offset <= 0) return 0
                    if(offset <= 2) return offset
                    if(offset <= 3) return offset - 1
                    if(offset <= 5) return offset - 1
                    if(offset <= 10) return offset -2

                    return maskedText.length
                }
            }

            return TransformedText(AnnotatedString(maskedText), offsetMapping)
        }
    }
}