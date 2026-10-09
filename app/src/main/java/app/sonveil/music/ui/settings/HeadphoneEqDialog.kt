package app.sonveil.music.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonveil.music.data.player.AutoEqCatalog
import app.sonveil.music.data.player.AutoEqEntry
import app.sonveil.music.data.player.EqMode
import app.sonveil.music.ui.theme.LocalPalette
import android.content.Context

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadphoneEqDialog(
    context: Context,
    mode: EqMode,
    onDismiss: () -> Unit,
    onPick: (AutoEqEntry) -> Unit,
) {
    val p = LocalPalette.current
    var catalog by remember { mutableStateOf<List<AutoEqEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        catalog = AutoEqCatalog.load(context)
    }
    val results = remember(catalog, query) {
        AutoEqCatalog.search(catalog.orEmpty(), query)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.background,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Headphones", color = p.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text(
                if (mode == EqMode.Parametric) {
                    "Applies the AutoEQ parametric preset and its preamp."
                } else {
                    "Applies the AutoEQ 10-band preset and its preamp."
                },
                color = p.onBackground.copy(alpha = 0.65f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search, for example HD 600") },
            )
            Spacer(Modifier.height(8.dp))
            when {
                catalog == null -> CircularProgressIndicator()
                catalog!!.isEmpty() -> Text(
                    "The headphone catalog did not load. Check Sonveil/AutoEq in logcat.",
                    color = p.onBackground,
                    fontSize = 13.sp,
                )
                query.trim().length < 2 -> Text(
                    "Type at least two letters. ${catalog!!.size} measurements.",
                    color = p.onBackground.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
                results.isEmpty() -> Text(
                    "No headphones match that search.",
                    color = p.onBackground,
                    fontSize = 13.sp,
                )
                else -> LazyColumn(Modifier.height(360.dp)) {
                    items(results, key = { it.id }) { entry ->
                        val kind = when {
                            mode == EqMode.Parametric && entry.hasParametric -> "Parametric"
                            mode == EqMode.Graphic && entry.hasGraphic -> "10-band"
                            entry.hasParametric -> "Parametric only"
                            else -> "10-band only"
                        }
                        val pre = if (mode == EqMode.Parametric || !entry.hasGraphic) {
                            entry.parametricPreamp
                        } else {
                            entry.graphicPreamp
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(entry) }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                entry.name,
                                color = p.onBackground,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOf(entry.source, entry.rig, kind, "preamp ${"%.1f".format(pre)} dB")
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                color = p.onBackground.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 4.dp)) {
                Text("Close")
            }
            Text(
                "Curves from AutoEq by Jaakko Pasanen, MIT.",
                color = p.onBackground.copy(alpha = 0.4f),
                fontSize = 11.sp,
            )
        }
    }
}
