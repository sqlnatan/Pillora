package com.pillora.pillora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pillora.pillora.ui.theme.PilloraTheme
import com.pillora.pillora.navigation.AppNavigation // <-- importa aqui
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.analytics.FirebaseAnalytics

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val analytics = Firebase.analytics
        analytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, null)
        enableEdgeToEdge()
        setContent {
            PilloraTheme {
                AppNavigation() // <-- substitui o conteúdo antigo por isso
            }
        }
    }
}
