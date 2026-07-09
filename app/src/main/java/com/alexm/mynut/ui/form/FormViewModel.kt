package com.alexm.mynut.ui.form

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.alexm.mynut.MyNutApplication
import com.alexm.mynut.data.NutItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FormUiState(
    val name: String = "",
    val portionLabel: String = "100g",
    val calories: String = "",
    val fats: String = "",
    val saturatedFats: String = "",
    val carbs: String = "",
    val sugars: String = "",
    val fiber: String = "",
    val proteins: String = "",
    val sodium: String = "",
    val scanInProgress: Boolean = false,
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
    val modelDownloadProgress: Float? = null,
    val showModelDownloadConfirm: Boolean = false
)

class FormViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val app = application as MyNutApplication
    private val nutItemDao = app.database.nutItemDao()
    private val labelScanApi = app.labelScanApi
    private val ocrTextLlmScanEngine = app.ocrTextLlmScanEngine
    private val localModelManager = app.localModelManager
    private var pendingLocalScanBytes: ByteArray? = null

    private val itemId: Long? = savedStateHandle.get<Long>("itemId")?.takeIf { it != 0L }
    val isEditing: Boolean = itemId != null

    private val _uiState = MutableStateFlow(FormUiState())
    val uiState: StateFlow<FormUiState> = _uiState.asStateFlow()

    init {
        if (itemId != null) {
            viewModelScope.launch {
                val item = nutItemDao.getItemById(itemId).first() ?: return@launch
                _uiState.value = FormUiState(
                    name = item.name,
                    portionLabel = item.portionLabel,
                    calories = item.calories.toString(),
                    fats = item.fats.toString(),
                    saturatedFats = item.saturatedFats.toString(),
                    carbs = item.carbs.toString(),
                    sugars = item.sugars.toString(),
                    fiber = item.fiber.toString(),
                    proteins = item.proteins.toString(),
                    sodium = item.sodium.toString()
                )
            }
        }
    }

    fun updateField(field: String, value: String) {
        _uiState.value = when (field) {
            "name" -> _uiState.value.copy(name = value)
            "portionLabel" -> _uiState.value.copy(portionLabel = value)
            "calories" -> _uiState.value.copy(calories = value)
            "fats" -> _uiState.value.copy(fats = value)
            "saturatedFats" -> _uiState.value.copy(saturatedFats = value)
            "carbs" -> _uiState.value.copy(carbs = value)
            "sugars" -> _uiState.value.copy(sugars = value)
            "fiber" -> _uiState.value.copy(fiber = value)
            "proteins" -> _uiState.value.copy(proteins = value)
            "sodium" -> _uiState.value.copy(sodium = value)
            else -> _uiState.value
        }
    }

    fun scanPhoto(imageBytes: ByteArray) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(scanInProgress = true, errorMessage = null)

            labelScanApi.scanLabel(imageBytes).fold(
                onSuccess = { values ->
                    _uiState.value = _uiState.value.copy(
                        calories = values.calories?.toString() ?: _uiState.value.calories,
                        fats = values.fats?.toString() ?: _uiState.value.fats,
                        saturatedFats = values.saturatedFats?.toString() ?: _uiState.value.saturatedFats,
                        carbs = values.carbs?.toString() ?: _uiState.value.carbs,
                        sugars = values.sugars?.toString() ?: _uiState.value.sugars,
                        fiber = values.fiber?.toString() ?: _uiState.value.fiber,
                        proteins = values.proteins?.toString() ?: _uiState.value.proteins,
                        sodium = values.sodium?.toString() ?: _uiState.value.sodium,
                        scanInProgress = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        scanInProgress = false,
                        errorMessage = "Scan failed: ${error.message}"
                    )
                }
            )
        }
    }

    fun scanPhotoLocally(imageBytes: ByteArray) {
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) { localModelManager.isModelReady() }
            if (ready) {
                runLocalScan(imageBytes)
            } else {
                pendingLocalScanBytes = imageBytes
                _uiState.value = _uiState.value.copy(showModelDownloadConfirm = true)
            }
        }
    }

    fun confirmModelDownload() {
        _uiState.value = _uiState.value.copy(showModelDownloadConfirm = false)
        val bytes = pendingLocalScanBytes ?: return

        viewModelScope.launch {
            val progressJob = launch {
                localModelManager.state.collect { state ->
                    if (state is com.alexm.mynut.data.ModelDownloadState.Downloading) {
                        _uiState.value = _uiState.value.copy(modelDownloadProgress = state.progress)
                    }
                }
            }

            localModelManager.ensureModelReady().fold(
                onSuccess = {
                    progressJob.cancel()
                    _uiState.value = _uiState.value.copy(modelDownloadProgress = null)
                    runLocalScan(bytes)
                },
                onFailure = { error ->
                    progressJob.cancel()
                    _uiState.value = _uiState.value.copy(
                        modelDownloadProgress = null,
                        errorMessage = "Téléchargement du modèle échoué : ${error.message}"
                    )
                }
            )
        }
    }

    fun cancelModelDownload() {
        pendingLocalScanBytes = null
        _uiState.value = _uiState.value.copy(showModelDownloadConfirm = false)
    }

    private fun runLocalScan(imageBytes: ByteArray) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(scanInProgress = true, errorMessage = null)

            ocrTextLlmScanEngine.scanLabel(imageBytes).fold(
                onSuccess = { values ->
                    _uiState.value = _uiState.value.copy(
                        calories = values.calories?.toString() ?: _uiState.value.calories,
                        fats = values.fats?.toString() ?: _uiState.value.fats,
                        saturatedFats = values.saturatedFats?.toString() ?: _uiState.value.saturatedFats,
                        carbs = values.carbs?.toString() ?: _uiState.value.carbs,
                        sugars = values.sugars?.toString() ?: _uiState.value.sugars,
                        fiber = values.fiber?.toString() ?: _uiState.value.fiber,
                        proteins = values.proteins?.toString() ?: _uiState.value.proteins,
                        sodium = values.sodium?.toString() ?: _uiState.value.sodium,
                        scanInProgress = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        scanInProgress = false,
                        errorMessage = "Scan local échoué : ${error.message}"
                    )
                }
            )
        }
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.name.isBlank()) {
                _uiState.value = state.copy(errorMessage = "Name is required")
                return@launch
            }

            val item = NutItem(
                id = itemId ?: 0,
                name = state.name.trim(),
                portionLabel = state.portionLabel.trim(),
                calories = state.calories.toDoubleOrNull() ?: 0.0,
                fats = state.fats.toDoubleOrNull() ?: 0.0,
                saturatedFats = state.saturatedFats.toDoubleOrNull() ?: 0.0,
                carbs = state.carbs.toDoubleOrNull() ?: 0.0,
                sugars = state.sugars.toDoubleOrNull() ?: 0.0,
                fiber = state.fiber.toDoubleOrNull() ?: 0.0,
                proteins = state.proteins.toDoubleOrNull() ?: 0.0,
                sodium = state.sodium.toDoubleOrNull() ?: 0.0
            )

            if (isEditing) {
                nutItemDao.update(item)
            } else {
                nutItemDao.insert(item)
            }

            _uiState.value = state.copy(isSaved = true)
        }
    }

    fun delete() {
        if (itemId == null) return
        viewModelScope.launch {
            val item = nutItemDao.getItemById(itemId).first() ?: return@launch
            nutItemDao.delete(item)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}