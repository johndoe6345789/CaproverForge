package com.caproverforge.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caproverforge.data.CapRoverException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Failed(val message: String, val status: Int? = null) : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
}

fun Throwable.userMessage(): String = when (this) {
    is CapRoverException -> message ?: "Something went wrong."
    is IllegalArgumentException -> message ?: "Invalid input."
    else -> message ?: javaClass.simpleName
}

/**
 * Base for screens that show server data: a [state] that loads once and can be refreshed,
 * plus [action] for mutations that report success/failure via [messages] (shown as snackbars).
 */
abstract class LoadingViewModel<T> : ViewModel() {
    var state by mutableStateOf<Load<T>>(Load.Loading)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private var loadJob: Job? = null

    protected abstract suspend fun load(): T

    val data: T? get() = (state as? Load.Ready<T>)?.data

    fun refresh(userInitiated: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (state !is Load.Ready) state = Load.Loading
            refreshing = userInitiated
            try {
                state = Load.Ready(load())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (state is Load.Ready) _messages.tryEmit(e.userMessage())
                else state = Load.Failed(e.userMessage(), (e as? CapRoverException)?.status)
            } finally {
                refreshing = false
            }
        }
    }

    /** Silently reload (polling, returning to a screen) without replacing content on failure. */
    fun quietReload() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            runCatching { load() }.onSuccess { state = Load.Ready(it) }
        }
    }

    protected fun updateData(transform: (T) -> T) {
        val current = data ?: return
        state = Load.Ready(transform(current))
    }

    protected fun message(text: String) {
        _messages.tryEmit(text)
    }

    fun action(
        success: String? = null,
        reload: Boolean = true,
        onSuccess: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                block()
                success?.let { _messages.tryEmit(it) }
                onSuccess()
                if (reload) refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(e.userMessage())
            } finally {
                busy = false
            }
        }
    }
}
