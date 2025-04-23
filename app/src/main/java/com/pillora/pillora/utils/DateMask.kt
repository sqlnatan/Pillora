package com.pillora.pillora.utils


object DateMask {
    const val MAX_LENGTH = 10

    private fun unmask(text: String): String {
        return text.replace(Regex("\\D"), "")
    }

    fun mask(text: String): String {
        val unmasked = unmask(text)
        val builder = StringBuilder()

        for (i in unmasked.indices) {
            if (i >= 8) break // Limit to 8 digits (DDMMYYYY)

            if (i == 2 || i == 4) {
                builder.append('/')
            }
            builder.append(unmasked[i])
        }

        return builder.toString()
    }

    fun isValid(text: String): Boolean {
        val regex = Regex("^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[012])/\\d{4}$")
        if (!regex.matches(text)) return false

        // Additional date validation
        val parts = text.split("/")
        val day = parts[0].toInt()
        val month = parts[1].toInt()
        val year = parts[2].toInt()

        // Check for valid month days
        val maxDays = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        return day <= maxDays
    }

    private fun isLeapYear(year: Int): Boolean {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }
}