package com.pillora.pillora.data.dao

import androidx.room.*
import com.pillora.pillora.model.Lembrete
import kotlinx.coroutines.flow.Flow

@Dao
interface LembreteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLembrete(lembrete: Lembrete): Long // Retorna o ID do lembrete inserido

    @Update
    suspend fun updateLembrete(lembrete: Lembrete)

    @Delete
    suspend fun deleteLembrete(lembrete: Lembrete)

    // *** NOVO: Método para deletar uma lista de lembretes ***
    @Delete
    suspend fun deleteLembretes(lembretes: List<Lembrete>) // Recebe uma lista

    @Query("DELETE FROM lembretes WHERE medicamentoId = :medicamentoId")
    suspend fun deleteLembretesByMedicamentoId(medicamentoId: String)

    @Query("DELETE FROM lembretes WHERE medicamentoId = :medicamentoId AND dose = :tipo")
    suspend fun deleteLembretesByMedicamentoIdAndType(medicamentoId: String, tipo: String)

    @Query("SELECT * FROM lembretes WHERE id = :id")
    suspend fun getLembreteById(id: Long): Lembrete?

    @Query("SELECT * FROM lembretes WHERE ativo = 1 ORDER BY proximaOcorrenciaMillis ASC")
    fun getLembretesAtivosFlow(): Flow<List<Lembrete>> // Para observar mudanças na UI

    @Query("SELECT * FROM lembretes WHERE ativo = 1")
    suspend fun getLembretesAtivosList(): List<Lembrete> // Para uso em background, como no BootReceiver

    @Query("SELECT * FROM lembretes WHERE medicamentoId = :medicamentoId")
    suspend fun getLembretesByMedicamentoId(medicamentoId: String): List<Lembrete>
}

