package com.pillora.pillora.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("pillora_prefs", Context.MODE_PRIVATE)
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(48.dp),
                windowInsets = WindowInsets(0),
                title = { Text("Termos de Uso") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = """
                    Bem-vindo ao Pillora!

                    Ao utilizar este aplicativo, você concorda com os seguintes termos:

                    1. Coletamos dados pessoais apenas para o funcionamento do app, como nome, medicamentos, consultas, vacinas e datas.
                    2. Nenhum dado sensível é compartilhado com terceiros.
                    3. É responsabilidade do usuário manter os dados atualizados e corretos.
                    4. Este aplicativo oferece lembretes e notificações, mas não substitui o acompanhamento médico.
                    5. Usuários Premium têm acesso a recursos extras, como múltiplos perfis, histórico expandido e sincronização em nuvem.
                    6. O uso contínuo do app indica a aceitação destes termos e de futuras atualizações.

                    Agradecemos por confiar no Pillora para ajudar no cuidado com sua saúde 💙
                """.trimIndent(),
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    // Ativar indicador de carregamento
                    isLoading = true

                    // Usar Looper.getMainLooper() em vez do construtor depreciado
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        // Grava aceite dos termos usando KTX
                        prefs.edit {
                            putBoolean("accepted_terms", true)
                        }
                        // Navega para a tela de autenticação e remove esta do back-stack
                        navController.navigate("auth") {
                            popUpTo("terms") { inclusive = true }
                        }
                    }, 800) // Atraso de 800ms para mostrar o indicador
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = !isLoading // Desabilitar o botão durante o carregamento
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isLoading) {
                        // Mostrar indicador de carregamento
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Check, contentDescription = "Aceitar")
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Aceito os termos")
                }
            }
        }
    }
}
