package com.pirorin215.fastrecmob.viewModel

import com.pirorin215.fastrecmob.data.TranscriptionResult
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow

interface TranscriptionManagement {
    val transcriptionState: StateFlow<String>
    val transcriptionResult: StateFlow<String?>
        val audioFileCount: StateFlow<Int>
        val transcriptionCompletedFlow: SharedFlow<String>
    
        fun updateLocalAudioFileCount()
        fun processPendingTranscriptions()
        suspend fun cleanupTranscriptionResultsAndAudioFiles()
        fun retranscribe(result: TranscriptionResult)
        suspend fun addPendingTranscription(fileName: String)
    fun addManualTranscription(text: String)
    fun clearTranscriptionResults()
    fun removeTranscriptionResult(result: TranscriptionResult)
    fun updateTranscriptionResult(originalResult: TranscriptionResult, newTranscription: String, newNote: String?)
    fun removeTranscriptionResults(fileNames: Set<String>, clearSelectionCallback: () -> Unit)

    fun resetTranscriptionState()
    fun onCleared()
}
