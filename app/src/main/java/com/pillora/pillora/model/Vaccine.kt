package com.pillora.pillora.model

import com.google.firebase.firestore.DocumentId

/**
 * Modelo de dados para representar um lembrete de vacina,
 * seguindo o padrão de Consultation.kt.
 */
data class Vaccine(
    @DocumentId val id: String = "", // ID do Firestore
    val userId: String = "", // ID do usuário
    val name: String = "", // Nome da vacina/lembrete
    val reminderDate: String = "", // Data agendada (DD/MM/AAAA) - Obrigatório
    val reminderTime: String = "", // Horário agendado (HH:MM) - Obrigatório (simplificado, pode ser ajustado se necessário)
    val location: String = "", // Local - Opcional, mas String não nula como em Consultation
    val notes: String = "" // Observações - Opcional, mas String não nula como em Consultation
)

