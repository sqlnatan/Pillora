package com.pillora.pillora.data.dao // Ajuste o pacote

import androidx.room.*
import com.pillora.pillora.model.Lembrete
import kotlinx.coroutines.flow.Flow

@Dao
interface LembreteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLembrete(lembrete: Lembrete): Long

    @Update
    suspend fun updateLembrete(lembrete: Lembrete)

    @Delete
    suspend fun deleteLembrete(lembrete: Lembrete)

    @Query("SELECT * FROM lembretes WHERE id = :id")
    suspend fun getLembreteById(id: Long): Lembrete?

    @Query("SELECT * FROM lembretes WHERE ativo = 1 ORDER BY proximaOcorrenciaMillis ASC")
    fun getLembretesAtivos(): Flow<List<Lembrete>>

    @Query("SELECT * FROM lembretes WHERE ativo = 1")
    suspend fun getLembretesAtivosList(): List<Lembrete>
}
