package com.pillora.pillora.model

data class Medicine(
    val id: String? = null,
    val userId: String? = null, // Novo campo para armazenar o ID do usuário
    val name: String = "",
    val recipientName: String = "",
    val dose: String = "",
    val doseUnit: String? = null, // "Cápsula" ou "ml"
    val frequencyType: String = "", // "vezes_dia" ou "a_cada_x_horas"
    val timesPerDay: Int? = null, // só se for "vezes_dia"
    val horarios: List<String>? = null, // só se for "vezes_dia"
    val intervalHours: Int? = null, // só se for "a_cada_x_horas"
    val startTime: String? = null, // só se for "a_cada_x_horas"
    val startDate: String = "",
    val duration: Int = 0, // -1 significa medicamento contínuo (sem tempo definido)
    val notes: String = "",

    // Novos campos para rastreamento de estoque
    val trackStock: Boolean = false, // Flag para indicar se o usuário deseja ser alertado quando o medicamento estiver acabando
    val stockQuantity: Double = 0.0, // Quantidade atual em estoque
    val stockUnit: String = "Unidades", // Unidade de medida do estoque (Unidades, ml, etc.)
    val alarmsEnabled: Boolean = true // Novo campo para controlar se os alarmes estão ativos
)

