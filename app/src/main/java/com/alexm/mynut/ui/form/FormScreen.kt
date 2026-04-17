package com.alexm.mynut.ui.form

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreen(
    viewModel: FormViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showPhotoChoice by remember { mutableStateOf(false) }

    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { uri ->
                val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                if (bytes != null) viewModel.scanPhoto(bytes)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(it)?.readBytes()
            if (bytes != null) viewModel.scanPhoto(bytes)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            photoUri = createTempImageUri(context)
            photoUri?.let { cameraLauncher.launch(it) }
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) "Modifier l'item" else "Nouvel item") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { showPhotoChoice = true }) {
                        if (uiState.scanInProgress) {
                            CircularProgressIndicator()
                        } else {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Scanner une étiquette")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.updateField("name", it) },
                label = { Text("Nom") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.portionLabel,
                onValueChange = { viewModel.updateField("portionLabel", it) },
                label = { Text("Portion (ex: 100g, 1 pot)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Valeurs nutritionnelles (par portion)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp)
            )

            NutrientField("Calories (kcal)", uiState.calories) { viewModel.updateField("calories", it) }
            NutrientField("Lipides (g)", uiState.fats) { viewModel.updateField("fats", it) }
            NutrientField("Acides gras saturés (g)", uiState.saturatedFats) { viewModel.updateField("saturatedFats", it) }
            NutrientField("Glucides (g)", uiState.carbs) { viewModel.updateField("carbs", it) }
            NutrientField("Sucres (g)", uiState.sugars) { viewModel.updateField("sugars", it) }
            NutrientField("Fibres (g)", uiState.fiber) { viewModel.updateField("fiber", it) }
            NutrientField("Protéines (g)", uiState.proteins) { viewModel.updateField("proteins", it) }
            NutrientField("Sodium (mg)", uiState.sodium) { viewModel.updateField("sodium", it) }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sauvegarder")
            }

            if (viewModel.isEditing) {
                OutlinedButton(
                    onClick = { viewModel.delete() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Supprimer")
                }
            }
        }
    }

    if (showPhotoChoice) {
        AlertDialog(
            onDismissRequest = { showPhotoChoice = false },
            title = { Text("Scanner une étiquette") },
            text = { Text("Choisissez la source de la photo :") },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoChoice = false
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) { Text("Caméra") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhotoChoice = false
                    galleryLauncher.launch("image/*")
                }) { Text("Galerie") }
            }
        )
    }
}

@Composable
private fun NutrientField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun createTempImageUri(context: android.content.Context): Uri {
    val file = File.createTempFile("label_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}