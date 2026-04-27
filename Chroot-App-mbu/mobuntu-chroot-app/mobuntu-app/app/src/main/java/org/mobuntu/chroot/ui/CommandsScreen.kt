package org.mobuntu.chroot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private data class CmdEntry(
    val name:    String,
    val label:   String,
    val cmd:     String,
    val danger:  Boolean = false,
)

private val COMMANDS = listOf(
    CmdEntry("install",   "Install",   "chroot-distro install mobuntu"),
    CmdEntry("mount",     "Mount",     "chroot-distro mount mobuntu"),
    CmdEntry("unmount",   "Unmount",   "chroot-distro unmount mobuntu"),
    CmdEntry("login",     "Login",     "chroot-distro login mobuntu"),
    CmdEntry("backup",    "Backup",    "chroot-distro backup mobuntu"),
    CmdEntry("restore",   "Restore",   "chroot-distro restore mobuntu"),
    CmdEntry("reinstall", "Reinstall", "chroot-distro reinstall --force mobuntu", danger = true),
    CmdEntry("remove",    "Remove",    "chroot-distro remove mobuntu",            danger = true),
)

@Composable
fun CommandsScreen(vm: MainViewModel) {
    var customCmd by remember { mutableStateOf("") }
    var confirmCmd: CmdEntry? by remember { mutableStateOf(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // ── Command grid ──────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("chroot-distro Commands",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))

                // 2-column grid via chunked rows
                COMMANDS.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { entry ->
                            val colors = if (entry.danger)
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                )
                            else
                                ButtonDefaults.outlinedButtonColors()

                            OutlinedButton(
                                onClick  = { if (entry.danger) confirmCmd = entry else vm.runNamedCommand(entry.name) },
                                modifier = Modifier.weight(1f),
                                colors   = colors,
                            ) {
                                Column {
                                    Text(entry.label, style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        entry.cmd,
                                        style      = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color      = MaterialTheme.colorScheme.outline,
                                        maxLines   = 2,
                                    )
                                }
                            }
                        }
                        // Fill empty slot in last row if odd number of commands
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // ── Custom command ─────────────────────────────────────────────────
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Custom Command",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = customCmd,
                        onValueChange = { customCmd = it },
                        placeholder   = { Text("chroot-distro command mobuntu \"apt update\"") },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true,
                        textStyle     = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    )
                    Button(
                        onClick  = { if (customCmd.isNotBlank()) { vm.runCommand(customCmd); customCmd = "" } },
                        enabled  = customCmd.isNotBlank(),
                        modifier = Modifier.height(56.dp),
                    ) {
                        Text("Run")
                    }
                }
                Text(
                    "Runs any shell command as root. Output appears in the Log tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }

    // ── Confirm dialog for destructive commands ───────────────────────────
    confirmCmd?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmCmd = null },
            title   = { Text("Confirm: ${entry.label}") },
            text    = {
                Column {
                    Text("This will run:")
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            entry.cmd,
                            modifier  = Modifier.padding(10.dp),
                            fontFamily = FontFamily.Monospace,
                            style     = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (entry.name == "remove" || entry.name == "reinstall") {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "⚠ This is a destructive action and cannot be undone.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.runNamedCommand(entry.name); confirmCmd = null },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCmd = null }) { Text("Cancel") }
            },
        )
    }
}
