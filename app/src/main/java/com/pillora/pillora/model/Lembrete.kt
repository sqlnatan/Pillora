package com.pillora.pillora.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.pillora.pillora.data.local.ListIntConverter

@Entity(tableName = "lembretes")
@TypeConverters(ListIntConverter::class)
data class Lembrete(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicamentoId: String, // Para vacinas, este será o vaccineId
    val nomeMedicamento: String, // Para vacinas, será "Vacina: [Nome da Vacina]"
    val recipientName: String? = null, // Nome do paciente
    val hora: Int,
    val minuto: Int,
    val dose: String, // Para vacinas, armazenará o tipo de lembrete ("24 horas antes", "2 horas antes", "confirmação")
    val observacao: String? = null,
    val diasDaSemana: List<Int>? = null, // Não usado para vacinas
    var proximaOcorrenciaMillis: Long,
    var ativo: Boolean = true,
    val isVacina: Boolean = false, // Flag para identificar se é um lembrete de vacina
    val isConfirmacao: Boolean = false // Flag para identificar se é um lembrete de confirmação (3h depois)
)
