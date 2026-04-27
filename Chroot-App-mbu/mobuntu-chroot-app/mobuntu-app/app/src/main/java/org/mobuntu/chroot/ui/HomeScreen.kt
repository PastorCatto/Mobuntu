package org.mobuntu.chroot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mobuntu.chroot.MainViewModel
import org.mobuntu.chroot.engine.SessionState

@Composable
fun HomeScreen(vm: MainViewModel) {
    val uiState    by vm.uiState.collectAsStateWithLifecycle()
    val session    by vm.sessionStatus.collectAsStateWithLifecycle()
    val settings   by vm.settings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // ── Status card ───────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Mobuntu", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    SessionBadge(session.state)
                }
                Spacer(Modifier.height(8.dp))
                Text("Release: ${settings.tarballUrl.substringAfterLast('/').removeSuffix(".tar.gz")}",
                    style = MaterialTheme.typography.bodySmall)
                Text("UI: ${settings.ui}  ·  Display: ${settings.display}",
                    style = MaterialTheme.typography.bodySmall)
                if (uiState.installSize.isNotEmpty()) {
                    Text("Installed: ${uiState.installSize}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ── Environment warnings ──────────────────────────────────────────
        if (uiState.isChecking) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            if (!uiState.hasRoot) {
                WarningCard("Root access not granted. Grant root permission to this app via your root manager.")
            }
            if (!uiState.hasTermuxX11) {
                WarningCard(
                    "Termux:X11 is not installed. It is required for display output.\n" +
                    "Download the nightly APK from github.com/termux/termux-x11/releases"
                )
            }
            if (!uiState.isEngineInstalled) {
                WarningCard("chroot-distro engine not found. First-run setup required.")
            }
            if (!uiState.isDistroInstalled) {
                WarningCard("Mobuntu rootfs not installed. Tap Install below.")
            }
        }

        // ── Session error ─────────────────────────────────────────────────
        session.errorMessage?.let { err ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Error: $err",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // ── Launch / Stop button ──────────────────────────────────────────
        val isRunning = session.state == SessionState.RUNNING
        val isBusy    = session.state in listOf(
            SessionState.MOUNTING, SessionState.STARTING_DISPLAY, SessionState.STOPPING
        )
        val canLaunch = uiState.hasRoot && uiState.hasTermuxX11 &&
                        uiState.isDistroInstalled && !isBusy

        Button(
            onClick = { if (isRunning) vm.stopSession() else vm.launchSession() },
            enabled = canLaunch || isRunning,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = if (isRunning)
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            else
                ButtonDefaults.buttonColors(),
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(busyLabel(session.state))
            } else {
                Text(if (isRunning) "STOP SESSION" else "LAUNCH MOBUNTU")
            }
        }

        // ── Refresh check button ──────────────────────────────────────────
        OutlinedButton(
            onClick = vm::checkEnvironment,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Refresh Status")
        }
    }
}

@Composable
private fun SessionBadge(state: SessionState) {
    val (label, color) = when (state) {
        SessionState.RUNNING          -> "RUNNING"  to MaterialTheme.colorScheme.primary
        SessionState.MOUNTING,
        SessionState.STARTING_DISPLAY -> "STARTING" to MaterialTheme.colorScheme.secondary
        SessionState.STOPPING         -> "STOPPING" to MaterialTheme.colorScheme.tertiary
        SessionState.ERROR            -> "ERROR"    to MaterialTheme.colorScheme.error
        SessionState.STOPPED          -> "STOPPED"  to MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color    = color,
            style    = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun WarningCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "⚠  $message",
            modifier = Modifier.padding(12.dp),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun busyLabel(state: SessionState) = when (state) {
    SessionState.MOUNTING          -> "Mounting…"
    SessionState.STARTING_DISPLAY  -> "Starting display…"
    SessionState.STOPPING          -> "Stopping…"
    else                           -> "Please wait…"
}
