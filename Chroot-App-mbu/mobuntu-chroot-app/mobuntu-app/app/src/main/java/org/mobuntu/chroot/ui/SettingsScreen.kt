package org.mobuntu.chroot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mobuntu.chroot.MainViewModel

private val RELEASES = listOf(
    "https://releases.mobuntu.org/base-resolute.tar.gz" to "Ubuntu 26.04 LTS Resolute (Recommended)",
    "https://releases.mobuntu.org/base-noble.tar.gz"    to "Ubuntu 24.04 LTS Noble (Switch / Legacy)",
)

private val UIS = listOf(
    "phosh"                  to "Phosh — touch-first mobile shell",
    "ubuntu-desktop-minimal" to "GNOME — Ubuntu Desktop Minimal",
    "plasma-mobile"          to "Plasma Mobile — KDE touch interface",
    "lomiri"                 to "Lomiri — Ubuntu Touch shell",
)

private val DISPLAYS = listOf(
    "termux-x11" to "Termux:X11 (recommended — low latency)",
    "vnc"        to "VNC (any VNC client)",
)

private val RESOLUTIONS = listOf("1280x720", "1920x1080", "2560x1440", "device")
private val DPIS        = listOf("120", "160", "200", "240", "280")

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // ── Rootfs ────────────────────────────────────────────────────────
        SettingsSection("Rootfs Version") {
            RELEASES.forEach { (url, label) ->
                RadioRow(
                    label    = label,
                    selected = settings.tarballUrl == url,
                    onClick  = { vm.updateSettings { copy(tarballUrl = url) } },
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("Custom URL", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            var customUrl by remember(settings.tarballUrl) {
                mutableStateOf(
                    if (RELEASES.none { it.first == settings.tarballUrl }) settings.tarballUrl else ""
                )
            }
            OutlinedTextField(
                value         = customUrl,
                onValueChange = { customUrl = it },
                placeholder   = { Text("https://example.com/base-resolute.tar.gz") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
            )
            if (customUrl.isNotBlank()) {
                TextButton(onClick = { vm.updateSettings { copy(tarballUrl = customUrl) } }) {
                    Text("Apply custom URL")
                }
            }
        }

        // ── UI ────────────────────────────────────────────────────────────
        SettingsSection("Desktop UI") {
            UIS.forEach { (id, label) ->
                RadioRow(
                    label    = label,
                    selected = settings.ui == id,
                    onClick  = { vm.updateSettings { copy(ui = id) } },
                )
            }
        }

        // ── Display ───────────────────────────────────────────────────────
        SettingsSection("Display Mode") {
            DISPLAYS.forEach { (id, label) ->
                RadioRow(
                    label    = label,
                    selected = settings.display == id,
                    onClick  = { vm.updateSettings { copy(display = id) } },
                )
            }
        }

        // ── Screen ────────────────────────────────────────────────────────
        SettingsSection("Screen Resolution") {
            ChipRow(
                items    = RESOLUTIONS,
                selected = settings.resolution,
                onSelect = { vm.updateSettings { copy(resolution = it) } },
            )
        }

        SettingsSection("DPI") {
            ChipRow(
                items    = DPIS,
                selected = settings.dpi,
                onSelect = { vm.updateSettings { copy(dpi = it) } },
            )
        }

        // ── Extra args ────────────────────────────────────────────────────
        SettingsSection("Extra Launch Arguments") {
            OutlinedTextField(
                value         = settings.extraArgs,
                onValueChange = { vm.updateSettings { copy(extraArgs = it) } },
                placeholder   = { Text("--legacy-drawing --force-bgra") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
            )
            Text(
                "Passed to termux-x11 at launch. See Termux:X11 docs for options.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

// ── Reusable components ───────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ChipRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { item ->
            FilterChip(
                selected = selected == item,
                onClick  = { onSelect(item) },
                label    = { Text(item) },
            )
        }
    }
}
