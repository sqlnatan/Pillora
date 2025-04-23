package com.pillora.pillora.model

data class Medicine(
    val name: String = "",
    val dose: String = "",
    val doseUnit: String? = null, // "Cápsula" ou "ml"
    val frequencyType: String = "", // "vezes_dia" ou "a_cada_x_horas"
    val timesPerDay: Int? = null, // só se for "vezes_dia"
    val horarios: List<String>? = null, // só se for "vezes_dia"
    val intervalHours: Int? = null, // só se for "a_cada_x_horas"
    val startTime: String? = null, // só se for "a_cada_x_horas"
    val startDate: String = "",
    val duration: Int = 0, // -1 significa medicamento contínuo (sem tempo definido)
    val notes: String = ""
)
