package com.rmbg.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rmbg.app.presentation.MainViewModel
import com.rmbg.app.ui.components.EngineSelector
import com.rmbg.app.ui.components.ImagePreviewBox
import com.rmbg.app.ui.components.ServerSettingsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { imageUri ->
            try {
                context.contentResolver.openInputStream(imageUri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        viewModel.onImageSelected(bitmap)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RMBG - AI Remover") },
                actions = {
                    IconButton(onClick = { viewModel.setSettingsDialogVisible(true) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EngineSelector(
                selectedEngine = state.selectedEngine,
                onEngineSelected = { viewModel.onEngineChanged(it) }
            )

            Text(
                text = "Original Image",
                style = MaterialTheme.typography.titleSmall
            )
            ImagePreviewBox(
                bitmap = state.selectedBitmap,
                placeholderText = "No image selected (Tap 'Select Image')"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    enabled = !state.isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Select Image")
                }

                Button(
                    onClick = { viewModel.onRemoveBackground() },
                    enabled = state.selectedBitmap != null && !state.isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Remove BG")
                    }
                }
            }

            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Text(
                text = "Result Preview",
                style = MaterialTheme.typography.titleSmall
            )
            ImagePreviewBox(
                bitmap = state.resultBitmap,
                placeholderText = "Processed result will appear here"
            )

            Button(
                onClick = {
                    viewModel.onSaveResult { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = state.resultBitmap != null && !state.isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save to Gallery")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (state.showSettingsDialog) {
            ServerSettingsDialog(
                initialUrl = state.serverUrl,
                onDismiss = { viewModel.setSettingsDialogVisible(false) },
                onSave = { viewModel.onServerUrlChanged(it) }
            )
        }
    }
}
