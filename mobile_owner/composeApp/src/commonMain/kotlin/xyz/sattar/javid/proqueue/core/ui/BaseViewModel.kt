package xyz.sattar.javid.proqueue.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class BaseViewModel<STATE, PARTIAL_STATE, EVENT, INTENT>(
    initialState: STATE
) : ViewModel() {

    private val intentChannel = Channel<INTENT>(Channel.UNLIMITED)

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<STATE> = _uiState.asStateFlow()

    private val eventChannel = Channel<EVENT>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            intentChannel.receiveAsFlow()
                .flatMapMerge { intent ->
                    handleIntent(intent)
                        .catch { error ->
                            emit(createErrorState(error.message ?: "خطای نامشخص"))
                        }
                }
                .scan(uiState.value) { currentState, partialState ->
                    reduceState(currentState, partialState)
                }.collect { newState ->
                    _uiState.update { newState }
                }
        }
    }

    fun sendIntent(intent: INTENT) {
        viewModelScope.launch {
            intentChannel.send(intent)
        }
    }

    protected fun sendEvent(event: EVENT) : Flow<PARTIAL_STATE> {
        viewModelScope.launch {
            eventChannel.send(event)
        }
        return emptyFlow<PARTIAL_STATE>()
    }

    protected fun doAsyncTask(task: suspend () -> Unit): Flow<PARTIAL_STATE> {
        viewModelScope.launch(Dispatchers.IO) {
            task.invoke()
        }
        return emptyFlow<PARTIAL_STATE>()
    }

    protected abstract fun handleIntent(intent: INTENT): Flow<PARTIAL_STATE>
    protected abstract fun reduceState(currentState: STATE, partialState: PARTIAL_STATE): STATE
    protected abstract fun createErrorState(message: String): PARTIAL_STATE
}