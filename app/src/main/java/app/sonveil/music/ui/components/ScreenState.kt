package app.sonveil.music.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.textSecondary

@Composable
fun LoadingStatus(label: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(20.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(label, color = LocalPalette.current.textSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        }
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again") }
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Text(message, color = LocalPalette.current.textSecondary, style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.fillMaxWidth().padding(20.dp))
}

@Composable
fun DetailLoadingScreen(title: String, onBack: () -> Unit, error: String?, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title, onBack)
        if (error == null) LoadingStatus("Loading $title") else ErrorState(error, onRetry)
    }
}
