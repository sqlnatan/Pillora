package com.pillora.pillora.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(), // CORREÇÃO: Adicionar padding para a barra de navegação do sistema
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
Ao acessar ou utilizar este aplicativo, você declara que leu, compreendeu e concorda com os termos abaixo.

1. COLETA E USO DE DADOS

Coletamos dados pessoais fornecidos pelo próprio usuário, exclusivamente para o funcionamento do aplicativo, como nome, medicamentos, consultas, vacinas, datas, horários e informações relacionadas a dependentes cadastrados.

Esses dados são utilizados para possibilitar funcionalidades como lembretes, notificações, organização de informações e geração de relatórios.

Os dados são armazenados de forma segura utilizando serviços confiáveis de terceiros, como o Firebase.

2. PRIVACIDADE E SEGURANÇA

O Pillora não vende, não aluga e não compartilha dados pessoais sensíveis com terceiros para fins comerciais.

Utilizamos serviços de terceiros essenciais para o funcionamento e melhoria do aplicativo, incluindo:

Firebase (armazenamento e autenticação)

Google Analytics for Firebase (análise de uso e desempenho)

Google AdMob (exibição de anúncios)

Esses serviços podem coletar dados de uso de forma agregada ou anônima, conforme suas próprias políticas de privacidade.

3. DADOS DE TERCEIROS E DEPENDENTES

Ao cadastrar informações de terceiros, como dependentes ou familiares, o usuário declara possuir autorização para inserir esses dados e assume total responsabilidade sobre eles.

4. RESPONSABILIDADE DO USUÁRIO

É responsabilidade do usuário:

manter os dados cadastrados corretos e atualizados

conferir informações inseridas no aplicativo

gerenciar notificações e lembretes

O Pillora não se responsabiliza por informações incorretas inseridas pelo usuário nem por falhas decorrentes desses dados.

5. NATUREZA DO SERVIÇO

O Pillora é um aplicativo de organização e lembretes de saúde.

⚠️ O aplicativo não substitui consultas, diagnósticos ou acompanhamento médico profissional.
Sempre consulte um profissional de saúde qualificado para orientações médicas.

6. ANÚNCIOS

Usuários da versão gratuita do aplicativo podem visualizar anúncios exibidos por parceiros, como o Google AdMob.

Esses anúncios podem utilizar identificadores e dados de uso do dispositivo, conforme as configurações do usuário e as políticas do Google.

7. ASSINATURA PREMIUM

O Pillora oferece uma assinatura Premium que desbloqueia recursos adicionais.

A cobrança é realizada e gerenciada exclusivamente pela Google Play Store.

Valores, períodos e renovação automática são informados no momento da contratação.

O usuário pode cancelar a assinatura a qualquer momento pelas configurações da Play Store.

Após o cancelamento, os benefícios Premium permanecem ativos até o final do período já pago.

Não há reembolso fora das regras definidas pela Google Play.

8. TESTES GRATUITOS

Quando disponíveis, períodos de teste gratuito podem ser oferecidos conforme as regras da Google Play e podem ser alterados ou removidos a qualquer momento.

9. EXCLUSÃO DE DADOS E DIREITOS DO USUÁRIO (LGPD)

O usuário pode solicitar a exclusão de seus dados pessoais a qualquer momento.

Ao receber uma solicitação de exclusão:

todos os dados pessoais armazenados diretamente pelo Pillora serão removidos de nossos sistemas, incluindo informações salvas no Firebase.

Dados coletados por serviços de terceiros, como Google AdMob e Google Analytics, seguem as políticas de privacidade desses serviços e não são controlados diretamente pelo Pillora. A exclusão desses dados deve ser solicitada conforme as diretrizes dos próprios fornecedores.

O Pillora trata os dados pessoais em conformidade com a Lei Geral de Proteção de Dados (LGPD – Lei nº 13.709/2018).

10. ALTERAÇÕES E SUSPENSÃO DO SERVIÇO

O Pillora pode, a qualquer momento:

modificar funcionalidades

adicionar ou remover recursos

suspender ou encerrar serviços

Sempre buscando manter a melhor experiência possível ao usuário.

11. ATUALIZAÇÕES DOS TERMOS

Estes termos podem ser atualizados periodicamente.

Quando houver alterações relevantes, o usuário será informado e poderá ser solicitado a aceitar novamente os termos para continuar utilizando o aplicativo.

12. CONTATO E SUPORTE

Em caso de dúvidas, solicitações ou questões relacionadas à privacidade e aos termos, o usuário pode entrar em contato pelo e-mail:

📧 pillora.app@gmail.com

13. ACEITAÇÃO

Ao utilizar o aplicativo, o usuário declara concordar integralmente com estes Termos de Uso e Política de Privacidade.

14. RESTRIÇÃO DE IDADE
O Pillora não é destinado a menores de 16 anos. Ao utilizar o aplicativo, o usuário declara ter idade igual ou superior a 16 anos.

Agradecemos por confiar no Pillora para ajudar no cuidado com sua saúde. 💙

Última atualização: 01/01/2026
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
