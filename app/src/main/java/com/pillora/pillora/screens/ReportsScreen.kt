package com.pillora.pillora.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.pillora.pillora.PilloraApplication
import com.pillora.pillora.viewmodel.ReportFile
import com.pillora.pillora.ads.NativeAdCard
import com.pillora.pillora.viewmodel.ReportsViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(navController: NavController) {
    val context = LocalContext.current
    val application = context.applicationContext as PilloraApplication

    val reportsViewModel: ReportsViewModel = viewModel(
        factory = ReportsViewModel.provideFactory(
            application = application,
            userPreferences = application.userPreferences,
            currentUserId = Firebase.auth.currentUser?.uid
        )
    )

    val reportFiles by reportsViewModel.reportFiles.collectAsState()
    val isPremium by reportsViewModel.isPremium.collectAsState(initial = false)

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text("Relatórios") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Todos os usuários (Free e Premium) veem o mesmo conteúdo
            // A diferença é que Free tem botão bloqueado
            UnifiedContent(
                reportsViewModel = reportsViewModel,
                reportFiles = reportFiles,
                context = context,
                isPremium = isPremium,
                navController = navController
            )
        }
    }
}

@Composable
fun UnifiedContent(
    reportsViewModel: ReportsViewModel,
    reportFiles: List<ReportFile>,
    context: Context,
    isPremium: Boolean,
    navController: NavController
) {
    var showPatientDialog by remember { mutableStateOf(false) }
    var patientNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingNames by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Button(
        onClick = {
            if (isPremium) {
                // Premium: Buscar nomes de pacientes antes de mostrar o diálogo
                isLoadingNames = true
                coroutineScope.launch {
                    patientNames = reportsViewModel.getAllPatientNames()
                    isLoadingNames = false
                    showPatientDialog = true
                }
            } else {
                // Free: Redirecionar para tela de assinatura
                navController.navigate("subscription")
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = isPremium && !isLoadingNames,
        colors = if (!isPremium) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ButtonDefaults.buttonColors()
        }
    ) {
        if (!isPremium) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Recurso Premium",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (isLoadingNames && isPremium) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            if (isLoadingNames && isPremium) "Carregando..."
            else if (!isPremium) "Gerar Novo Relatório (Premium)"
            else "Gerar Novo Relatório (PDF)"
        )
    }

    // Diálogo de seleção de paciente
    if (showPatientDialog) {
        PatientSelectionDialog(
            patientNames = patientNames,
            onDismiss = { showPatientDialog = false },
            onPatientSelected = { selectedPatient ->
                showPatientDialog = false
                reportsViewModel.generateReport("Relatorio_Pillora", selectedPatient)
                Toast.makeText(context, "Gerando relatório para $selectedPatient...", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Anúncio Nativo (apenas para usuários FREE)
    if (!isPremium) {
        NativeAdCard(
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    Text(
        "Relatórios Gerados:",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (reportFiles.isEmpty()) {
        Text("Nenhum relatório gerado ainda.", modifier = Modifier.padding(vertical = 24.dp))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(reportFiles) { report ->
                ReportFileItem(
                    report = report,
                    onDelete = { reportsViewModel.deleteReport(report) },
                    onShare = { shareReport(context, report) },
                    onOpen = { openReport(context, report) },
                    onDownload = { downloadReport(context, report) }
                )
            }
        }
    }
}

@Composable
fun ReportFileItem(
    report: ReportFile,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onDownload: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val formattedDate = remember(report.lastModified) { dateFormat.format(Date(report.lastModified)) }
    val formattedSize = remember(report.size) { "${"%.2f".format(report.size / 1024.0)} KB" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(report.name, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Gerado em: $formattedDate", style = MaterialTheme.typography.bodySmall)
                Text("Tamanho: $formattedSize", style = MaterialTheme.typography.bodySmall)
            }

            Row {
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = "Compartilhar")
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir")
                }
                IconButton(onClick = { showDownloadDialog = true }) {
                    Icon(Icons.Default.Download, contentDescription = "Download")
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Confirmar exclusão") },
            text = { Text("Deseja realmente excluir este relatório?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) { Text("Sim") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Não") }
            }
        )
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Confirmar Download") },
            text = { Text("Deseja salvar este relatório na pasta Downloads?") },
            confirmButton = {
                TextButton(onClick = {
                    onDownload()
                    showDownloadDialog = false
                }) { Text("Sim") }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) { Text("Não") }
            }
        )
    }
}

// ------------------------------------------------------------
// FUNÇÕES AUXILIARES
// ------------------------------------------------------------

fun openReport(context: Context, report: ReportFile) {
    val file = File(report.path)
    if (!file.exists()) {
        Toast.makeText(context, "Arquivo não encontrado.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider", // corrigido aqui
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "Não foi possível abrir o arquivo. Verifique se há um leitor de PDF instalado.",
            Toast.LENGTH_LONG
        ).show()
    }
}

fun shareReport(context: Context, report: ReportFile) {
    val file = File(report.path)
    if (!file.exists()) {
        Toast.makeText(context, "Arquivo não encontrado.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider", // corrigido aqui
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "application/pdf"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar Relatório"))
    } catch (e: Exception) {
        Toast.makeText(context, "Erro ao compartilhar: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

/** Função compatível com Android 6 (API 23) até 15 (API 35) */
fun downloadReport(context: Context, report: ReportFile) {
    val srcFile = File(report.path)
    if (!srcFile.exists()) {
        Toast.makeText(context, "Arquivo não encontrado.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        val fileName = report.name
        val mimeType = "application/pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { output ->
                    srcFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(context, "Relatório salvo em Downloads.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Falha ao acessar Downloads.", Toast.LENGTH_SHORT).show()
            }
        } else {
            val downloadsDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val destFile = File(downloadsDir, fileName)
            srcFile.copyTo(destFile, overwrite = true)
            Toast.makeText(context, "Relatório salvo em Downloads.", Toast.LENGTH_SHORT).show()
        }

    } catch (e: Exception) {
        Toast.makeText(context, "Erro ao salvar relatório: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun PatientSelectionDialog(
    patientNames: List<String>,
    onDismiss: () -> Unit,
    onPatientSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Selecione o Paciente",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            if (patientNames.isEmpty()) {
                Text(
                    "Nenhum paciente encontrado. Cadastre medicamentos, consultas ou vacinas primeiro.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(patientNames) { patientName ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPatientSelected(patientName)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = patientName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Selecionar",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
