package com.pirorin215.fastrecmob.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.pirorin215.fastrecmob.R
import androidx.compose.ui.unit.sp
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch // Import Switch
import androidx.compose.material3.SwitchDefaults // Import SwitchDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import com.pirorin215.fastrecmob.data.ThemeMode
import com.pirorin215.fastrecmob.data.TranscriptionProvider
import com.pirorin215.fastrecmob.viewModel.AppSettingsViewModel
import kotlin.math.roundToInt

import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(appSettingsViewModel: AppSettingsViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    // DataStoreから現在の設定値を取得
    val currentApiKey by appSettingsViewModel.apiKey.collectAsState()
    val currentGeminiApiKey by appSettingsViewModel.geminiApiKey.collectAsState()
    val currentGroqApiKey by appSettingsViewModel.groqApiKey.collectAsState()
    val currentTranscriptionProvider by appSettingsViewModel.transcriptionProvider.collectAsState()
    val currentTranscriptionCacheLimit by appSettingsViewModel.transcriptionCacheLimit.collectAsState() // Renamed
    val currentFontSize by appSettingsViewModel.transcriptionFontSize.collectAsState()
    val currentThemeMode by appSettingsViewModel.themeMode.collectAsState()
    val currentGoogleTaskTitleLength by appSettingsViewModel.googleTaskTitleLength.collectAsState()
    val currentAutoStartOnBoot by appSettingsViewModel.autoStartOnBoot.collectAsState() // New: Auto-start setting
    val currentChunkBurstSize by appSettingsViewModel.chunkBurstSize.collectAsState()
    val currentVoltageRetryCount by appSettingsViewModel.voltageRetryCount.collectAsState()
    val currentVoltageAcquisitionInterval by appSettingsViewModel.voltageAcquisitionInterval.collectAsState()
    val currentLowVoltageThreshold by appSettingsViewModel.lowVoltageThreshold.collectAsState()
    val currentLowVoltageNotifyEveryTime by appSettingsViewModel.lowVoltageNotifyEveryTime.collectAsState()
    val currentTranscriptionNotificationEnabled by appSettingsViewModel.transcriptionNotificationEnabled.collectAsState()

    // TextFieldの状態を管理
    var apiKeyText by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var geminiApiKeyText by remember(currentGeminiApiKey) { mutableStateOf(currentGeminiApiKey) }
    var groqApiKeyText by remember(currentGroqApiKey) { mutableStateOf(currentGroqApiKey) }
    var selectedTranscriptionProvider by remember(currentTranscriptionProvider) { mutableStateOf(currentTranscriptionProvider) }
    var transcriptionCacheLimitText by remember(currentTranscriptionCacheLimit) { mutableStateOf(currentTranscriptionCacheLimit.toString()) } // Renamed
    var fontSizeSliderValue by remember(currentFontSize) { mutableStateOf(currentFontSize.toFloat()) }
    var selectedThemeMode by remember(currentThemeMode) { mutableStateOf(currentThemeMode) }
    var googleTaskTitleLengthText by remember(currentGoogleTaskTitleLength) { mutableStateOf(currentGoogleTaskTitleLength.toString()) }
    var autoStartOnBootChecked by remember(currentAutoStartOnBoot) { mutableStateOf(currentAutoStartOnBoot) } // New: Auto-start checked state
    var chunkBurstSizeText by remember(currentChunkBurstSize) { mutableStateOf(currentChunkBurstSize.toString()) }
    var voltageRetryCountText by remember(currentVoltageRetryCount) { mutableStateOf(currentVoltageRetryCount.toString()) }
    var voltageAcquisitionIntervalText by remember(currentVoltageAcquisitionInterval) { mutableStateOf(currentVoltageAcquisitionInterval.toString()) }
    var lowVoltageThresholdText by remember(currentLowVoltageThreshold) { mutableStateOf(if (currentLowVoltageThreshold == 0f) "" else currentLowVoltageThreshold.toString()) }
    var lowVoltageNotifyEveryTimeChecked by remember(currentLowVoltageNotifyEveryTime) { mutableStateOf(currentLowVoltageNotifyEveryTime) }
    var transcriptionNotificationEnabledChecked by remember(currentTranscriptionNotificationEnabled) { mutableStateOf(currentTranscriptionNotificationEnabled) }

    val saveSettings = {
        appSettingsViewModel.saveApiKey(apiKeyText)
        appSettingsViewModel.saveGeminiApiKey(geminiApiKeyText)
        appSettingsViewModel.saveGroqApiKey(groqApiKeyText)
        appSettingsViewModel.saveTranscriptionProvider(selectedTranscriptionProvider)
        val transcriptionCacheLimit = transcriptionCacheLimitText.toIntOrNull() ?: 100
        appSettingsViewModel.saveTranscriptionCacheLimit(transcriptionCacheLimit)
        appSettingsViewModel.saveTranscriptionFontSize(fontSizeSliderValue.roundToInt())
        appSettingsViewModel.saveThemeMode(selectedThemeMode)
        val googleTaskTitleLength = googleTaskTitleLengthText.toIntOrNull() ?: 20
        appSettingsViewModel.saveGoogleTaskTitleLength(googleTaskTitleLength)
        appSettingsViewModel.saveAutoStartOnBoot(autoStartOnBootChecked)
        val chunkBurstSize = chunkBurstSizeText.toIntOrNull() ?: 8
        appSettingsViewModel.saveChunkBurstSize(chunkBurstSize)
        val voltageRetryCount = voltageRetryCountText.toIntOrNull() ?: 3
        appSettingsViewModel.saveVoltageRetryCount(voltageRetryCount)
        val voltageAcquisitionInterval = voltageAcquisitionIntervalText.toIntOrNull() ?: 100
        appSettingsViewModel.saveVoltageAcquisitionInterval(voltageAcquisitionInterval)
        val lowVoltageThreshold = lowVoltageThresholdText.toFloatOrNull() ?: 0f
        appSettingsViewModel.saveLowVoltageThreshold(lowVoltageThreshold)
        appSettingsViewModel.saveLowVoltageNotifyEveryTime(lowVoltageNotifyEveryTimeChecked)
        appSettingsViewModel.saveTranscriptionNotificationEnabled(transcriptionNotificationEnabledChecked)
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = saveSettings) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save_button))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = apiKeyText,
                onValueChange = { apiKeyText = it },
                label = { Text(stringResource(R.string.google_api_key_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = geminiApiKeyText,
                onValueChange = { geminiApiKeyText = it },
                label = { Text("Gemini API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = groqApiKeyText,
                onValueChange = { groqApiKeyText = it },
                label = { Text("Groq API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Transcription Provider Selection
            Text("文字起こしプロバイダー")
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                // Google
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = selectedTranscriptionProvider == TranscriptionProvider.GOOGLE,
                        onClick = { selectedTranscriptionProvider = TranscriptionProvider.GOOGLE },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = "Google Cloud Speech-to-Text",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                // Groq
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = selectedTranscriptionProvider == TranscriptionProvider.GROQ,
                        onClick = { selectedTranscriptionProvider = TranscriptionProvider.GROQ },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = "Groq (Whisper)",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = transcriptionCacheLimitText, // Renamed
                onValueChange = { transcriptionCacheLimitText = it },
                label = { Text(stringResource(R.string.transcription_retention_count)) }, // Updated text
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = googleTaskTitleLengthText,
                onValueChange = { googleTaskTitleLengthText = it },
                label = { Text(stringResource(R.string.max_title_characters)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = chunkBurstSizeText,
                onValueChange = { chunkBurstSizeText = it },
                label = { Text(stringResource(R.string.ble_burst_size_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = voltageRetryCountText,
                onValueChange = { voltageRetryCountText = it },
                label = { Text(stringResource(R.string.voltage_retry_count_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = voltageAcquisitionIntervalText,
                onValueChange = { voltageAcquisitionIntervalText = it },
                label = { Text(stringResource(R.string.voltage_acquisition_interval_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = lowVoltageThresholdText,
                onValueChange = { lowVoltageThresholdText = it },
                label = { Text(stringResource(R.string.low_voltage_threshold_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.low_voltage_notify_every_time), fontSize = 16.sp)
                Switch(
                    checked = lowVoltageNotifyEveryTimeChecked,
                    onCheckedChange = { lowVoltageNotifyEveryTimeChecked = it },
                    colors = SwitchDefaults.colors()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            // Transcription notification setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.transcription_notification_enabled), fontSize = 16.sp)
                Switch(
                    checked = transcriptionNotificationEnabledChecked,
                    onCheckedChange = { transcriptionNotificationEnabledChecked = it },
                    colors = SwitchDefaults.colors()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // New: Auto-start on boot setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.auto_start_on_boot), fontSize = 16.sp)
                Switch(
                    checked = autoStartOnBootChecked,
                    onCheckedChange = { autoStartOnBootChecked = it },
                    colors = SwitchDefaults.colors()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Font size slider
            Text(stringResource(R.string.transcription_font_size, fontSizeSliderValue.roundToInt()))
            Slider(
                value = fontSizeSliderValue,
                onValueChange = { fontSizeSliderValue = it },
                valueRange = 10f..24f,
                steps = 13, // (24 - 10) / 1 = 14 steps, so 13 intermediate points
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Theme mode selection
            Text(stringResource(R.string.theme_mode), fontSize = 16.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThemeMode.values().forEach { themeMode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (themeMode == selectedThemeMode),
                            onClick = { selectedThemeMode = themeMode },
                            colors = RadioButtonDefaults.colors()
                        )
                        Text(themeMode.name, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

