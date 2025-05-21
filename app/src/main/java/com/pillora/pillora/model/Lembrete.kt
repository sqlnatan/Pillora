package com.pillora.pillora.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.pillora.pillora.data.local.ListIntConverter

@Entity(tableName = "lembretes")
@TypeConverters(ListIntConverter::class)
data class Lembrete(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicamentoId: String,
    val nomeMedicamento: String,
    val recipientName: String? = null, // Novo campo para o nome do destinatário
    val hora: Int,
    val minuto: Int,
    val dose: String, // Ex: "1 Cápsula", "5 ml"
    val observacao: String? = null,
    val diasDaSemana: List<Int>? = null,
    var proximaOcorrenciaMillis: Long,
    var ativo: Boolean = true
)

