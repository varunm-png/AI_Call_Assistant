package in.aicallassistant.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import in.aicallassistant.app.data.CallItem

@Composable
fun HomeScreen(
    calls: List<CallItem>,
    loading: Boolean,
    loadError: String?,
    onStart: () -> Unit,
    onScreenCalls: () -> Unit,
    onOpen: (CallItem) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("AI Call Assistant") })
        }
    ) { p ->
        Column(
            Modifier.padding(p).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Your AI phone secretary", style = MaterialTheme.typography.headlineSmall)
            Text("Screen calls, talk with AI, and keep searchable summaries.")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onScreenCalls) { Text("Enable Call Screening") }
                OutlinedButton(onClick = onStart) { Text("Test AI Call") }
            }

            Text("Call History", style = MaterialTheme.typography.titleLarge)

            when {
                loading -> CircularProgressIndicator()
                loadError != null -> Text("Could not load call history: $loadError. Is the backend running and is API_BASE_URL correct?")
                calls.isEmpty() -> Text("No saved calls yet.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(calls) { c ->
                        Card(onClick = { onOpen(c) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(c.number, style = MaterialTheme.typography.titleMedium)
                                Text("${c.intent} · ${c.status}")
                                Text(c.summary)
                            }
                        }
                    }
                }
            }
        }
    }
}
