package com.pirorin215.fastrecmob.ui.screen

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pirorin215.fastrecmob.viewModel.MainViewModel
import com.pirorin215.fastrecmob.viewModel.AppSettingsViewModel

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.pirorin215.fastrecmob.R
// ...
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleTasksSyncSettingsScreen(
    viewModel: MainViewModel,
    appSettingsViewModel: AppSettingsViewModel,
    onBack: () -> Unit,
    onSignInIntent: suspend (Intent) -> Unit // New callback
) {
    BackHandler(onBack = onBack)
    val googleAccount by viewModel.account.collectAsState()
    val isLoadingGoogleTasks by viewModel.isLoadingGoogleTasks.collectAsState()
    // val googleSignInClient by viewModel.googleSignInClient.collectAsState(initial = null) // No longer directly used here
    val currentGoogleTodoListName by appSettingsViewModel.googleTodoListName.collectAsState()
    var googleTodoListNameText by remember(currentGoogleTodoListName) { mutableStateOf(currentGoogleTodoListName) }

    val scope = rememberCoroutineScope() // Add coroutine scope

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.google_tasks_sync_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Google Sign-In/Out Button
            if (googleAccount == null) {
                Button(
                    onClick = {
                        scope.launch { // Use coroutine scope
                            viewModel.getGoogleSignInIntent()?.let { intent ->
                                onSignInIntent(intent)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.sign_in_to_google_tasks))
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.currently_logged_in_account),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${googleAccount?.displayName}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.signOut() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.sign_out_content_description))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.sign_out_button))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.syncTranscriptionResultsWithGoogleTasks() },
                                enabled = !isLoadingGoogleTasks,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = stringResource(R.string.sync_with_google_tasks_content_description))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.sync_button))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            val currentSyncInterval by appSettingsViewModel.googleTasksSyncIntervalMinutes.collectAsState()
            var sliderPosition by remember(currentSyncInterval) { mutableStateOf(currentSyncInterval.toFloat()) }

            Text(stringResource(R.string.google_tasks_sync_interval_minutes, sliderPosition.toInt()))
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                valueRange = 1f..60f,
                steps = 58, // 60 - 1 - 1 = 58 steps for integer values
                onValueChangeFinished = {
                    appSettingsViewModel.saveGoogleTasksSyncIntervalMinutes(sliderPosition.toInt())
                }
            )
            Spacer(modifier = Modifier.height(16.dp))


            OutlinedTextField(
                value = googleTodoListNameText,
                onValueChange = { googleTodoListNameText = it },
                label = { Text(stringResource(R.string.google_todo_list_name_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    appSettingsViewModel.saveGoogleTodoListName(googleTodoListNameText)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save_google_todo_list_name_button))
            }

            if (isLoadingGoogleTasks) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.syncing_with_google_tasks), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
