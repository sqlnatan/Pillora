package com.pillora.pillora.model // Ou o pacote onde você está colocando seus modelos

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lembretes")
data class Lembrete(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicamentoId: String, // ALTERADO PARA STRING: ID do medicamento associado
    val nomeMedicamento: String, // Para fácil acesso na notificação (copiado de Medicine.name)
    val hora: Int, // Hora do lembrete (0-23)
    val minuto: Int, // Minuto do lembrete (0-59)
    val dose: String, // Descrição da dose para ESTE lembrete (ex: "1 comprimido")
    val observacao: String? = null, // Ex: "Tomar em jejum"
    val diasDaSemana: List<Int>? = null, // Opcional: para lembretes em dias específicos
    val proximaOcorrenciaMillis: Long, // Timestamp da próxima vez que o alarme deve tocar
    val ativo: Boolean = true
)
