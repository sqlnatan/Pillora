package com.pillora.pillora.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import android.app.DatePickerDialog
import java.util.Calendar
import java.util.Locale
import android.widget.DatePicker
import androidx.compose.ui.platform.LocalContext
import com.pillora.pillora.utils.DateMask

@Composable
fun DateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorMessage: String? = null,
    onDateSelected: ((String) -> Unit)? = null // Novo callback para data selecionada
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDay: Int ->
            // Formata a data para DDMMYYYY (8 dígitos)
            val formattedDate = String.format(Locale.US, "%02d%02d%d", selectedDay, selectedMonth + 1, selectedYear)
            onDateSelected?.invoke(formattedDate)
        }, year, month, day
    )

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                // Filtra apenas dígitos
                val digitsOnly = input.filter { it.isDigit() }
                if (digitsOnly.length <= 8) {
                    onValueChange(digitsOnly)
                }
            },
            label = { Text(label) },
            trailingIcon = {
                if (onDateSelected != null) {
                    IconButton(onClick = { datePickerDialog.show() }) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Selecionar Data"
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = isError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = DateMask.maskVisualTransformation() // ✅ AGORA AQUI!
        )

        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}