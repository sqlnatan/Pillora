package com.pillora.pillora

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity

@SuppressLint("CustomSplashScreen") // Suprime aviso sobre API de splash do Android 12
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Define o layout simples que criaremos a seguir
        setContentView(R.layout.activity_splash)

        // Aguarda um curto período e inicia a MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish() // Finaliza a SplashActivity para não voltar a ela
        }, 1000) // Tempo em milissegundos (1 segundo)
    }
}
