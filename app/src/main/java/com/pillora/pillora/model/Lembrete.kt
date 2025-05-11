package com.pillora.pillora.model // Ou o pacote onde você está colocando seus modelos

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters // IMPORT ADICIONADO
import com.pillora.pillora.data.local.ListIntConverter // IMPORT ADICIONADO

@Entity(tableName = "lembretes")
@TypeConverters(ListIntConverter::class) // ANOTAÇÃO ADICIONADA
data class Lembrete(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicamentoId: String,
    val nomeMedicamento: String,
    val hora: Int,
    val minuto: Int,
    val dose: String,
    val observacao: String? = null,
    val diasDaSemana: List<Int>? = null,
    var proximaOcorrenciaMillis: Long, // SUGESTÃO: var
    var ativo: Boolean = true          // SUGESTÃO: var
)
