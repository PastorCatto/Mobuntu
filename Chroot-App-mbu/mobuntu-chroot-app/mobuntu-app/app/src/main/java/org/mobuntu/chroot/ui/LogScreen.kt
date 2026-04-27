package org.mobuntu.chroot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun LogScreen(vm: MainViewModel) {
    val lines = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // Collect log lines from SharedFlow
    LaunchedEffect(Unit) {
        vm.log.collect { line ->
            lines.add(line)
            scope.launch {
                listState.animateScrollToItem(lines.size - 1)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Session Log",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        val text = lines.joinToString("\n") { it.substringAfter("|") }
                        clipboard.setText(AnnotatedString(text))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy log")
                    }
                    TextButton(onClick = { lines.clear() }) {
                        Text("Clear")
                    }
                }
            }

            // Terminal-style log
            LazyColumn(
                state    = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF050508))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(lines) { line ->
                    val type    = line.substringBefore("|")
                    val message = line.substringAfter("|")
                    val color   = when (type) {
                        "cmd" -> Color(0xFFa855f7)  // purple
                        "sys" -> Color(0xFF4ade80)  // green
                        "err" -> Color(0xFFf87171)  // red
                        else  -> Color(0xFF777790)  // grey
                    }
                    Text(
                        text       = if (type == "cmd") message else "  $message",
                        color      = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                // Blinking cursor item
                item {
                    Text(
                        "█",
                        color      = Color(0xFFe8426a),
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 12.sp,
                    )
                }
            }
        }
    }
}
