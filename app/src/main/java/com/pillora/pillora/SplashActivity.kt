package com.pillora.pillora

import android.content.Intent
import android.os.Bundle
// Removido Handler e Looper pois não são mais necessários com a API SplashScreen
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen // Importar a classe necessária

// A anotação @SuppressLint foi removida pois estamos usando a API corretamente
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Chamar installSplashScreen() ANTES de super.onCreate() e setContentView()
        // Isso conecta a Activity ao tema definido (incluindo o de values-v31)
        installSplashScreen() // Chamada direta sem atribuir à variável

        super.onCreate(savedInstanceState)

        // 2. (Opcional) Manter a splash screen visível por mais tempo se necessário
        // Para usar isso, volte a ter: val splashScreen = installSplashScreen()
        // E então use: splashScreen.setKeepOnScreenCondition { /* sua condição aqui */ true }

        // 3. Iniciar a MainActivity diretamente
        startActivity(Intent(this, MainActivity::class.java))

        // 4. Finalizar a SplashActivity
        finish()

        // 5. Remover setContentView e Handler.postDelayed
        // O layout activity_splash.xml não é mais necessário aqui
    }
}

