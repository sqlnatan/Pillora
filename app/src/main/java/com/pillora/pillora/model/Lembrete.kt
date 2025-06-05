package com.pillora.pillora.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.pillora.pillora.data.local.ListIntConverter

@Entity(tableName = "lembretes")
@TypeConverters(ListIntConverter::class)
data class Lembrete(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Para Medicamentos: ID do medicamento
    // Para Consultas: ID da consulta
    // Para Vacinas: ID da vacina
    // Para Receitas: ID da receita
    val medicamentoId: String,
    // Para Medicamentos: Nome do medicamento
    // Para Consultas: "Consulta: [Especialidade]"
    // Para Vacinas: "Vacina: [Nome da Vacina]"
    // Para Receitas: "Receita: [Nome Médico]" ou similar
    val nomeMedicamento: String,
    val recipientName: String? = null, // Nome do paciente (usado em Consulta, Vacina, Receita)
    val hora: Int, // Hora do lembrete (para receitas, será 12)
    val minuto: Int, // Minuto do lembrete (para receitas, será 0)
    // Para Medicamentos: Dose (ex: "1 comprimido")
    // Para Consultas/Vacinas/Receitas: Tipo do lembrete (ex: "24 horas antes", "Receita: 3 dias antes")
    val dose: String,
    val observacao: String? = null, // Usado em Consulta, Vacina, Receita para detalhes adicionais
    val diasDaSemana: List<Int>? = null, // Usado apenas para medicamentos recorrentes
    var proximaOcorrenciaMillis: Long,
    var ativo: Boolean = true,
    // Flags para identificar o tipo de lembrete
    val isConsulta: Boolean = false, // Adicionado para clareza, embora possa ser inferido pelo tipo na 'dose'
    val isVacina: Boolean = false,
    val isReceita: Boolean = false, // *** NOVO FLAG PARA RECEITAS ***
    val isConfirmacao: Boolean = false // Flag para identificar se é um lembrete de confirmação (3h depois consulta/vacina, 1d depois receita)
)

