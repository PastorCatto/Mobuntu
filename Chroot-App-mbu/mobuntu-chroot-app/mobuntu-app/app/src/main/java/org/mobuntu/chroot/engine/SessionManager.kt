package org.mobuntu.chroot.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionState {
    STOPPED,
    MOUNTING,
    STARTING_DISPLAY,
    RUNNING,
    STOPPING,
    ERROR,
}

data class SessionStatus(
    val state: SessionState = SessionState.STOPPED,
    val installSize: String = "",
    val isInstalled: Boolean = false,
    val errorMessage: String? = null,
)

object SessionManager {
    private val _status = MutableStateFlow(SessionStatus())
    val status: StateFlow<SessionStatus> = _status.asStateFlow()

    fun update(block: SessionStatus.() -> SessionStatus) {
        _status.value = _status.value.block()
    }

    fun setState(state: SessionState, error: String? = null) {
        _status.value = _status.value.copy(state = state, errorMessage = error)
    }
}
