package com.rk.terminal.ui.screens.downloader

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rk.libcommons.*
import com.rk.resources.strings
import com.rk.terminal.ui.activities.terminal.MainActivity
import com.rk.terminal.ui.screens.terminal.Rootfs
import com.rk.terminal.ui.screens.terminal.TerminalScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.UnknownHostException

@Composable
fun Downloader(
    modifier: Modifier = Modifier,
    mainActivity: MainActivity,
    navController: NavHostController
) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    val installingStr = stringResource(strings.installing)
    val networkErrorStr = stringResource(strings.network_error)
    val setupFailedStr = stringResource(strings.setup_failed)
    var progressText by remember { mutableStateOf(installingStr) }
    var isSetupComplete by remember { mutableStateOf(false) }
    var needsDownload by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {

        try {
            val abi = Build.SUPPORTED_ABIS.firstOrNull {
                it in abiMap
            } ?: throw RuntimeException("Unsupported CPU")

            val filesToDownload = listOf(
                "ubuntu.tar.gz" to abiMap[abi]!!.ubuntu
            ).map { (name, url) -> DownloadFile(url, Rootfs.reTerminal.child(name)) }

            needsDownload = filesToDownload.any { !it.outputFile.exists() }

            setupEnvironment(
                filesToDownload,
                onProgress = { completed, total, currentProgress ->
                    if (needsDownload) {
                        progress = ((completed + currentProgress) / total).coerceIn(0f, 1f)
                        progressText = "Downloading.. ${(progress * 100).toInt()}%"
                    }
                },
                onComplete = {
                    isSetupComplete = true
                },
                onError = { error ->
                    toast(if (error is UnknownHostException) networkErrorStr else setupFailedStr.format(error.message))
                }
            )
        } catch (e: Exception) {
            toast(if (e is UnknownHostException) networkErrorStr else setupFailedStr.format(e.message))
        }
    }

    var customRootfsExists by remember { mutableStateOf(Rootfs.reTerminal.child("custom-rootfs.tar.gz").exists()) }
    var showInfo by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val dest = Rootfs.reTerminal.child("custom-rootfs.tar.gz")
            context.contentResolver.openInputStream(it)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            customRootfsExists = true
            toast("Custom rootfs selected. Restart session to use it.")
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!isSetupComplete) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                if (needsDownload) {
                    Text(progressText, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(0.8f))
                    Spacer(modifier = Modifier.height(32.dp))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Custom Rootfs", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Use any ARM64 Linux distro (Fedora, Arch, etc).\nSelect a .tar.gz rootfs or place as custom-rootfs.tar.gz",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { filePicker.launch("application/gzip") }) {
                        Text("Select File")
                    }
                    if (customRootfsExists) {
                        OutlinedButton(onClick = {
                            Rootfs.reTerminal.child("custom-rootfs.tar.gz").delete()
                            customRootfsExists = false
                            toast("Custom rootfs removed.")
                        }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                            Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove")
                        }
                    }
                    IconButton(onClick = { showInfo = !showInfo }) {
                        Icon(Icons.Outlined.Info, "Info", Modifier.size(18.dp))
                    }
                }
                if (customRootfsExists) Text("Custom rootfs active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            TerminalScreen(mainActivityActivity = mainActivity, navController = navController)
        }
    }
}

private data class DownloadFile(val url: String, val outputFile: File)

private suspend fun setupEnvironment(
    filesToDownload: List<DownloadFile>,
    onProgress: (Int, Int, Float) -> Unit,
    onComplete: () -> Unit,
    onError: (Exception) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            var completedFiles = 0
            val totalFiles = filesToDownload.size

            filesToDownload.forEach { file ->
                val outputFile = file.outputFile.apply { parentFile?.mkdirs() }
                if (!outputFile.exists()) {
                    downloadFile(file.url, outputFile) { downloaded, total ->
                        runOnUiThread { onProgress(completedFiles, totalFiles, downloaded.toFloat() / total) }
                    }
                }
                completedFiles++
                runOnUiThread { onProgress(completedFiles, totalFiles, 1f) }
                outputFile.setExecutable(true, false)
            }
            runOnUiThread { onComplete() }
        } catch (e: Exception) {
            localDir().deleteRecursively()
            withContext(Dispatchers.Main) { onError(e) }
        }
    }
}

private suspend fun downloadFile(url: String, outputFile: File, onProgress: (Long, Long) -> Unit) {
    withContext(Dispatchers.IO) {
        OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Failed to download file: ${response.code}")

            val body = response.body ?: throw Exception("Empty response body")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            outputFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        withContext(Dispatchers.Main) { onProgress(downloadedBytes, totalBytes) }
                    }
                }
            }
        }
    }
}

private val abiMap = mapOf(
    "arm64-v8a" to AbiUrls(
        ubuntu = "https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/ubuntu-base-24.04.4-base-arm64.tar.gz"
    )
)

private data class AbiUrls(val ubuntu: String)
