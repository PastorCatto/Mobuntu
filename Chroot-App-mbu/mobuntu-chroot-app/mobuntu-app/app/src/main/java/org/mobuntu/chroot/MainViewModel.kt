package org.mobuntu.chroot

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mobuntu.chroot.engine.ChrootEngine
import org.mobuntu.chroot.engine.SessionManager
import org.mobuntu.chroot.engine.TermuxX11Launcher
import org.mobuntu.chroot.service.MobuntuForegroundService
import org.mobuntu.chroot.settings.SettingsRepository

data class UiState(
    val hasRoot: Boolean          = false,
    val hasTermuxX11: Boolean     = false,
    val isEngineInstalled: Boolean = false,
    val isDistroInstalled: Boolean = false,
    val installSize: String       = "",
    val isChecking: Boolean       = true,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val settingsRepo = SettingsRepository(app)
    val settings = settingsRepo.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        org.mobuntu.chroot.settings.AppSettings(),
    )

    val sessionStatus = SessionManager.status

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Log lines emitted to LogScreen
    private val _log = MutableSharedFlow<String>(replay = 200)
    val log = _log.asSharedFlow()

    init {
        checkEnvironment()
    }

    // ── Environment check ────────────────────────────────────────────────────

    fun checkEnvironment() {
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true) }

            val hasRoot        = com.topjohnwu.superuser.Shell.getShell().isRoot
            val hasTermuxX11   = TermuxX11Launcher.isInstalled(getApplication<Application>().packageManager)
            val engineOk       = ChrootEngine.isEngineInstalled()
            val distroOk       = ChrootEngine.isInstalled()
            val size           = if (distroOk) ChrootEngine.installSize() else ""

            _uiState.update {
                it.copy(
                    hasRoot           = hasRoot,
                    hasTermuxX11      = hasTermuxX11,
                    isEngineInstalled = engineOk,
                    isDistroInstalled = distroOk,
                    installSize       = size,
                    isChecking        = false,
                )
            }

            appendLog("sys", "Root: $hasRoot | Termux:X11: $hasTermuxX11 | Engine: $engineOk | Distro: $distroOk")
        }
    }

    // ── Session control ──────────────────────────────────────────────────────

    fun launchSession() {
        appendLog("sys", "Launching Mobuntu session…")
        MobuntuForegroundService.start(getApplication())
    }

    fun stopSession() {
        appendLog("sys", "Stopping Mobuntu session…")
        MobuntuForegroundService.stop(getApplication())
    }

    // ── chroot-distro commands ───────────────────────────────────────────────

    fun runCommand(cmd: String) {
        viewModelScope.launch {
            appendLog("cmd", "$ $cmd")
            val result = ChrootEngine.runCustom(cmd)
            result.output.forEach { appendLog("out", it) }
            if (!result.success) appendLog("err", "Command failed")
        }
    }

    fun runNamedCommand(name: String) {
        viewModelScope.launch {
            val result = when (name) {
                "install"   -> { appendLog("cmd", "$ chroot-distro install mobuntu"); ChrootEngine.install("") }
                "mount"     -> { appendLog("cmd", "$ chroot-distro mount mobuntu"); ChrootEngine.mount() }
                "unmount"   -> { appendLog("cmd", "$ chroot-distro unmount mobuntu"); ChrootEngine.unmount() }
                "backup"    -> { appendLog("cmd", "$ chroot-distro backup mobuntu"); ChrootEngine.backup() }
                "restore"   -> { appendLog("cmd", "$ chroot-distro restore mobuntu"); ChrootEngine.restore() }
                "reinstall" -> { appendLog("cmd", "$ chroot-distro reinstall --force mobuntu"); ChrootEngine.reinstall() }
                "remove"    -> { appendLog("cmd", "$ chroot-distro remove mobuntu"); ChrootEngine.remove() }
                else -> return@launch
            }
            result.output.forEach { appendLog("out", it) }
            if (result.success) {
                appendLog("sys", "✓ $name complete")
                checkEnvironment()
            } else {
                appendLog("err", "✗ $name failed")
            }
        }
    }

    // ── Settings ─────────────────────────────────────────────────────────────

    fun updateSettings(block: org.mobuntu.chroot.settings.AppSettings.() -> org.mobuntu.chroot.settings.AppSettings) {
        viewModelScope.launch { settingsRepo.update(block) }
    }

    // ── Log ──────────────────────────────────────────────────────────────────

    private fun appendLog(type: String, msg: String) {
        viewModelScope.launch { _log.emit("$type|$msg") }
    }
}
