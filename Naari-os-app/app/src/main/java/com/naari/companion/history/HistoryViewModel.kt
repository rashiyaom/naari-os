package com.naari.companion.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naari.companion.actions.ActionDispatcher
import com.naari.companion.ledger.RequestLedgerEntry
import com.naari.companion.ledger.RequestLedgerRepository
import com.naari.companion.ledger.RequestLedgerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel managing the Activity History ledger screen state, retry confirmations, and feedback.
 * Ref: PRD Section 7 & Master Prompt Task 12.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val ledgerRepository: RequestLedgerRepository,
    private val actionDispatcher: ActionDispatcher
) : ViewModel() {

    private val _pendingRetryEntry = MutableStateFlow<RequestLedgerEntry?>(null)
    val pendingRetryEntry: StateFlow<RequestLedgerEntry?> = _pendingRetryEntry.asStateFlow()

    private val _isRetrying = MutableStateFlow(false)
    val isRetrying: StateFlow<Boolean> = _isRetrying.asStateFlow()

    private val _retryFeedback = MutableStateFlow<String?>(null)
    val retryFeedback: StateFlow<String?> = _retryFeedback.asStateFlow()

    val entries: StateFlow<List<RequestLedgerEntry>> = ledgerRepository.recentEntries
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    fun onRetryRequested(entry: RequestLedgerEntry) {
        // Only allow selecting retryable failure states
        if (entry.status == RequestLedgerStatus.Failed || entry.status == RequestLedgerStatus.UnknownOutcome) {
            _pendingRetryEntry.value = entry
            _retryFeedback.value = null
        }
    }

    fun dismissRetryDialog() {
        _pendingRetryEntry.value = null
    }

    fun confirmRetry() {
        val entry = _pendingRetryEntry.value ?: return
        val dispatcher = actionDispatcher ?: return

        _isRetrying.value = true
        _pendingRetryEntry.value = null

        viewModelScope.launch {
            val result = dispatcher.retryRequest(entry.id)
            _isRetrying.value = false
            if (result.isSuccess) {
                _retryFeedback.value = "Action retry dispatched successfully"
            } else {
                _retryFeedback.value = "Retry failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearFeedback() {
        _retryFeedback.value = null
    }
}
