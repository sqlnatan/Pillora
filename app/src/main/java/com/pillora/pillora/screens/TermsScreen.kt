package com.pillora.pillora.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pillora.pillora.repository.AuthRepository
import com.pillora.pillora.repository.TermsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(
    navController: NavController,
    viewOnly: Boolean = false // Modo de visualização apenas (acessado pelas configurações)
) {
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Termos de Uso e Privacidade") },
                navigationIcon = {
                    if (viewOnly) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Bem-vindo ao Pillora!",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = """
                        Ao utilizar este aplicativo, você concorda com os seguintes termos:

                        1. COLETA DE DADOS
                        Coletamos dados pessoais apenas para o funcionamento do app, como nome, medicamentos, consultas, vacinas e datas. Todos os dados são armazenados de forma segura no Firebase.

                        2. PRIVACIDADE
                        Nenhum dado sensível é compartilhado com terceiros. Seus dados são protegidos e utilizados exclusivamente para as funcionalidades do aplicativo.

                        3. RESPONSABILIDADE DO USUÁRIO
                        É responsabilidade do usuário manter os dados atualizados e corretos. O Pillora não se responsabiliza por informações incorretas inseridas pelo usuário.

                        4. NATUREZA DO SERVIÇO
                        Este aplicativo oferece lembretes e notificações, mas não substitui o acompanhamento médico profissional. Sempre consulte um médico para orientações sobre sua saúde.

                        5. RECURSOS PREMIUM
                        Usuários Premium têm acesso a recursos extras, como relatórios em PDF, vacinas, receitas médicas e sincronização em nuvem.

                        6. ATUALIZAÇÕES DOS TERMOS
                        O uso contínuo do app indica a aceitação destes termos. Quando houver atualizações importantes nos termos, você será notificado e precisará aceitar novamente.

                        7. VERSÃO DOS TERMOS
                        Versão atual: ${TermsRepository.CURRENT_TERMS_VERSION}

                        Agradecemos por confiar no Pillora para ajudar no cuidado com sua saúde 💙
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (!viewOnly) {
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        isLoading = true
                        scope.launch {
                            val userId = AuthRepository.getCurrentUser()?.uid
                            if (userId != null) {
                                val success = TermsRepository.acceptTerms(userId)
                                if (success) {
                                    // Navega para home após aceitar
                                    navController.navigate("home") {
                                        popUpTo("terms") { inclusive = true }
                                    }
                                } else {
                                    isLoading = false
                                    // TODO: Mostrar mensagem de erro
                                }
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enabled = !isLoading
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isLoading) {
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
}
