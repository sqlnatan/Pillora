package com.pillora.pillora.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pillora.pillora.data.local.AppDatabase
import com.pillora.pillora.utils.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent?.action == "android.intent.action.BOOT_COMPLETED") {
            Log.d("BootReceiver", "Dispositivo reiniciado. Reagendando alarmes...")
            CoroutineScope(Dispatchers.IO).launch {
                val lembreteDao = AppDatabase.getDatabase(context).lembreteDao()
                val lembretesAtivos = lembreteDao.getLembretesAtivosList()
                for (lembrete in lembretesAtivos) {
                    AlarmScheduler.scheduleAlarm(context, lembrete)
                }
                Log.d("BootReceiver", "${lembretesAtivos.size} alarmes reagendados.")
            }
        }
    }
}
