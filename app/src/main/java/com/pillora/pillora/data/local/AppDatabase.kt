package com.pillora.pillora.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pillora.pillora.data.dao.LembreteDao
import com.pillora.pillora.model.Lembrete

@Database(entities = [Lembrete::class], version = 1, exportSchema = false) // Incremente a versão se já existir e estiver mudando o schema
@TypeConverters(ListIntConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun lembreteDao(): LembreteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pillora_database"
                )
                    // Adicione migrações aqui se necessário no futuro
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
