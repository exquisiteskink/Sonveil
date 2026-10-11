package app.sonveil.music.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonveil.music.BuildConfig
import app.sonveil.music.data.player.AutoEqEntry
import app.sonveil.music.data.player.EqFilter
import app.sonveil.music.data.player.EqMode
import app.sonveil.music.data.player.ExternalEqRisk
import app.sonveil.music.data.player.PlayerSettings
import app.sonveil.music.data.player.ReplayGainMode
import app.sonveil.music.ui.components.GlassSurface
import app.sonveil.music.ui.theme.LocalContainer
import app.sonveil.music.ui.theme.LocalPalette
import app.sonveil.music.ui.theme.ThemeMode
import app.sonveil.music.ui.theme.sonveilSliderColors

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    transcode: Int,
    onTranscode: (Int) -> Unit,
    onLoggedOut: () -> Unit,
) {
    val p = LocalPalette.current
    val container = LocalContainer.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val playerPrefs = remember { PlayerSettings(context) }
    val downloads = container.downloads
    val dlState by downloads.state.collectAsState()
    val creds = container.credentials.load()
    val server = creds?.serverUrl.orEmpty()
    val user = if (creds?.authMode?.name == "ApiKey") "API key" else creds?.username.orEmpty()

    var rgMode by remember { mutableStateOf(playerPrefs.replayGainMode) }
    var peak by remember { mutableStateOf(playerPrefs.peakLimiter) }
    var gapless by remember { mutableStateOf(playerPrefs.gapless) }
    var crossfade by remember { mutableStateOf(playerPrefs.crossfade) }
    var fadeMs by remember { mutableStateOf(playerPrefs.crossfadeMs.toFloat()) }
    val externalEqDualCfBlocked = remember { ExternalEqRisk.isDualPlayerCrossfadeUnsafe(context) }
    var pauseDisc by remember { mutableStateOf(playerPrefs.pauseOnDisconnect) }
    var sleepMins by remember { mutableStateOf(playerPrefs.sleepTimerMinutes) }
    // PlaybackService clears the timer when it fires, and after a pause then resume.
    DisposableEffect(playerPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == PlayerSettings.SLEEP_MINUTES || key == PlayerSettings.SLEEP_DEADLINE) {
                sleepMins = playerPrefs.sleepTimerMinutes
            }
        }
        playerPrefs.register(listener)
        onDispose { playerPrefs.unregister(listener) }
    }
    var eqOn by remember { mutableStateOf(playerPrefs.eqEnabled) }
    var eqPreset by remember { mutableStateOf(playerPrefs.eqPreset) }
    var eqGains by remember { mutableStateOf(playerPrefs.eqGains.copyOf()) }
    var eqMode by remember { mutableStateOf(playerPrefs.eqMode) }
    var eqPreampGraphic by remember { mutableStateOf(playerPrefs.eqPreampGraphic) }
    var eqPreampParametric by remember { mutableStateOf(playerPrefs.eqPreampParametric) }
    var eqFilters by remember { mutableStateOf(playerPrefs.eqFilters) }
    var eqHeadphone by remember { mutableStateOf(playerPrefs.eqHeadphoneName) }
    var showHeadphoneSearch by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf(playerPrefs.deviceProfiles()) }
    val output by container.outputs.output.collectAsState()
    DisposableEffect(playerPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key != PlayerSettings.EQ_PROFILES && key !in PlayerSettings.LIVE_EQ_KEYS) return@OnSharedPreferenceChangeListener
            eqOn = playerPrefs.eqEnabled
            eqPreset = playerPrefs.eqPreset
            eqGains = playerPrefs.eqGains.copyOf()
            eqMode = playerPrefs.eqMode
            eqPreampGraphic = playerPrefs.eqPreampGraphic
            eqPreampParametric = playerPrefs.eqPreampParametric
            eqFilters = playerPrefs.eqFilters
            eqHeadphone = playerPrefs.eqHeadphoneName
            profiles = playerPrefs.deviceProfiles()
        }
        playerPrefs.register(listener)
        onDispose { playerPrefs.unregister(listener) }
    }
    var wifiOnlyHiRes by remember { mutableStateOf(playerPrefs.wifiOnlyHiResDownloads) }
    LaunchedEffect(Unit) { downloads.refreshBytes() }

    fun applyHeadphone(entry: AutoEqEntry) {
        val nextMode = when {
            eqMode == EqMode.Graphic && !entry.hasGraphic && entry.hasParametric -> EqMode.Parametric
            eqMode == EqMode.Parametric && !entry.hasParametric && entry.hasGraphic -> EqMode.Graphic
            else -> eqMode
        }
        eqOn = true
        eqMode = nextMode
        eqHeadphone = entry.name
        eqPreset = "autoeq"
        playerPrefs.eqEnabled = true
        playerPrefs.eqMode = nextMode
        playerPrefs.eqHeadphoneName = entry.name
        playerPrefs.eqPreset = "autoeq"
        if (entry.hasGraphic) {
            val gains = FloatArray(10) { entry.bands[it].coerceIn(-12f, 12f) }
            eqGains = gains
            eqPreampGraphic = entry.graphicPreamp
            playerPrefs.eqGains = gains
            playerPrefs.eqPreampGraphic = entry.graphicPreamp
        }
        if (entry.hasParametric) {
            val filters: List<EqFilter> = entry.toFilters()
            eqFilters = filters
            eqPreampParametric = entry.parametricPreamp
            playerPrefs.eqFilters = filters
            playerPrefs.eqPreampParametric = entry.parametricPreamp
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .padding(bottom = 64.dp),
    ) {
        Text(
            "Settings",
            color = p.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        SettingsGroup("Account") {
            Text(server, color = p.onBackground, fontWeight = FontWeight.Medium)
            if (user.isNotBlank()) {
                Text(user, color = p.onBackground.copy(alpha = 0.6f), fontSize = 13.sp)
            }
            Text(
                "Login uses salted token auth (or an API key). The password is encrypted in Android Keystore.",
                color = p.onBackground.copy(alpha = 0.45f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = {
                container.signOut()
                onLoggedOut()
            }) {
                Text("Sign out")
            }
        }

        SettingsGroup("Appearance") {
            ThemeMode.entries.forEach { mode ->
                RadioRow(
                    selected = themeMode == mode,
                    label = when (mode) {
                        ThemeMode.System -> "Match system"
                        ThemeMode.Dark -> "Dark"
                        ThemeMode.Light -> "Light"
                    },
                    onClick = { onThemeMode(mode) },
                )
            }
            Hint("While music plays, backgrounds, the seek bar, and buttons and switches follow colors from the album art.")
        }

        SettingsGroup("Playback") {
            Text("Streaming quality", color = p.onBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            listOf(0 to "Original (audiophile)", 320 to "320 kbps", 192 to "192 kbps", 128 to "128 kbps").forEach { (rate, label) ->
                RadioRow(selected = transcode == rate, label = label, onClick = { onTranscode(rate) })
            }
            Hint("Original streams the file as stored on the server.")

            Spacer(Modifier.height(12.dp))
            Text("ReplayGain / R128", color = p.onBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            ReplayGainMode.entries.forEach { mode ->
                RadioRow(
                    selected = rgMode == mode,
                    label = when (mode) {
                        ReplayGainMode.Off -> "Off"
                        ReplayGainMode.Track -> "Track gain"
                        ReplayGainMode.Album -> "Album gain"
                    },
                    onClick = {
                        rgMode = mode
                        playerPrefs.replayGainMode = mode
                    },
                )
            }
            ToggleRow(
                title = "Peak limiter",
                subtitle = "Lower gain so tagged peaks do not clip",
                checked = peak,
                onChecked = { peak = it; playerPrefs.peakLimiter = it },
            )

            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = "True gapless",
                subtitle = if (externalEqDualCfBlocked) {
                    "Join tracks without a pause when the external EQ is ready"
                } else {
                    "Join tracks without a pause using decoder delay/padding"
                },
                checked = gapless,
                onChecked = {
                    gapless = it
                    playerPrefs.gapless = it
                    if (!it) {
                        crossfade = false
                    }
                },
            )
            ToggleRow(
                title = "Crossfade",
                subtitle = when {
                    !gapless -> "Turn on true gapless to enable crossfade"
                    externalEqDualCfBlocked ->
                        "External EQ detected — overlapping tracks are disabled"
                    else -> "Overlap the end of one track with the start of the next"
                },
                checked = crossfade && !externalEqDualCfBlocked,
                enabled = gapless && !externalEqDualCfBlocked,
                onChecked = {
                    if (!gapless) return@ToggleRow
                    crossfade = it
                    playerPrefs.crossfade = it
                },
            )
            if (crossfade && gapless && !externalEqDualCfBlocked) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Crossfade length ${PlayerSettings.crossfadeLabel(fadeMs.toInt())}",
                    color = p.onBackground.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
                Slider(
                    value = fadeMs,
                    onValueChange = { fadeMs = it },
                    onValueChangeFinished = { playerPrefs.crossfadeMs = fadeMs.toInt() },
                    valueRange = 1000f..12000f,
                    steps = 10,
                    colors = sonveilSliderColors(),
                )
            }
            ToggleRow(
                title = "Pause on disconnect",
                subtitle = "Pause when headphones or Bluetooth audio disconnect",
                checked = pauseDisc,
                onChecked = { pauseDisc = it; playerPrefs.pauseOnDisconnect = it },
            )

            Spacer(Modifier.height(12.dp))
            Text("Sleep timer", color = p.onBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            listOf(
                0 to "Off",
                15 to "15 minutes",
                30 to "30 minutes",
                45 to "45 minutes",
                60 to "60 minutes",
                PlayerSettings.SLEEP_END_OF_TRACK to "End of track",
            ).forEach { (value, label) ->
                RadioRow(
                    selected = sleepMins == value,
                    label = label,
                    onClick = {
                        sleepMins = value
                        when (value) {
                            0 -> playerPrefs.clearSleepTimer()
                            PlayerSettings.SLEEP_END_OF_TRACK -> playerPrefs.armSleepEndOfTrack()
                            else -> playerPrefs.armSleepMinutes(value)
                        }
                    },
                )
            }
            Hint("Pauses playback after the chosen time. Seek, a new queue, or resume after pause clears it.")
        }


        SettingsGroup("Offline downloads") {
            ToggleRow(
                title = "Wi‑Fi only for HiRes downloads",
                subtitle = "Original OpenSubsonic download files require unmetered Wi‑Fi when enabled",
                checked = wifiOnlyHiRes,
                onChecked = {
                    wifiOnlyHiRes = it
                    playerPrefs.wifiOnlyHiResDownloads = it
                },
            )
            Hint("Uses the documented download endpoint (original file). Streaming quality is separate.")
            Spacer(Modifier.height(8.dp))
            val usedMb = dlState.bytesUsed / (1024.0 * 1024.0)
            Text(
                if (dlState.bytesUsed > 0) "Stored offline  %.1f MB".format(usedMb) else "No offline files yet",
                color = p.onBackground.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
            if (dlState.phase.name != "Idle" && !dlState.message.isNullOrBlank()) {
                Text(dlState.message ?: "", color = p.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            TextButton(onClick = { downloads.refreshBytes() }) {
                Text("Refresh size")
            }
            TextButton(onClick = { downloads.clearDownloads() }) {
                Text("Clear offline downloads")
            }
        }

        SettingsGroup("Equalizer") {
            Text(
                "Output · ${output.name}",
                color = p.onBackground,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
            Text(
                "The curve is saved for this output and loads again when it connects.",
                color = p.onBackground.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            if (profiles.isNotEmpty()) {
                Text("Profiles", color = p.onBackground, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                profiles.forEach { profile ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, color = p.onBackground, fontSize = 14.sp)
                            Text(
                                profile.curveLabel,
                                color = p.onBackground.copy(alpha = 0.55f),
                                fontSize = 12.sp,
                            )
                        }
                        TextButton(onClick = {
                            playerPrefs.deleteProfile(profile.id)
                            profiles = playerPrefs.deviceProfiles()
                        }) {
                            Text("Remove")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            ToggleRow(
                title = "Equalizer",
                subtitle = "10-band or parametric, saved for the connected output",
                checked = eqOn,
                onChecked = { eqOn = it; playerPrefs.eqEnabled = it },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EqModeButton(
                    label = "10-band",
                    selected = eqMode == EqMode.Graphic,
                    modifier = Modifier.weight(1f),
                ) {
                    eqMode = EqMode.Graphic
                    playerPrefs.eqMode = EqMode.Graphic
                    if (!eqOn) {
                        eqOn = true
                        playerPrefs.eqEnabled = true
                    }
                }
                EqModeButton(
                    label = "Parametric",
                    selected = eqMode == EqMode.Parametric,
                    modifier = Modifier.weight(1f),
                ) {
                    eqMode = EqMode.Parametric
                    playerPrefs.eqMode = EqMode.Parametric
                    if (!eqOn) {
                        eqOn = true
                        playerPrefs.eqEnabled = true
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            AutoEqSearchField(onClick = { showHeadphoneSearch = true })
            if (eqHeadphone.isNotBlank()) {
                Text(
                    "$eqHeadphone · saved for ${output.name}",
                    color = p.onBackground.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (eqOn && eqMode == EqMode.Graphic) {
                Spacer(Modifier.height(12.dp))
                GraphicEqBank(
                    preamp = eqPreampGraphic,
                    gains = eqGains,
                    onPreamp = { value ->
                        eqPreampGraphic = value
                        playerPrefs.eqPreampGraphic = value
                    },
                    onBand = { index, gain ->
                        val next = eqGains.copyOf()
                        next[index] = gain
                        eqGains = next
                        eqPreset = "custom"
                        playerPrefs.eqPreset = "custom"
                        playerPrefs.eqGains = next
                    },
                )
                TextButton(onClick = {
                    val flat = FloatArray(10)
                    eqGains = flat
                    eqPreampGraphic = 0f
                    eqPreset = "flat"
                    eqHeadphone = ""
                    playerPrefs.eqGains = flat
                    playerPrefs.eqPreampGraphic = 0f
                    playerPrefs.eqPreset = "flat"
                    playerPrefs.eqHeadphoneName = ""
                }) { Text("Flat") }
            }
            if (eqOn && eqMode == EqMode.Parametric) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Preamp ${"%+.1f".format(eqPreampParametric)} dB",
                    color = p.onBackground,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
                Slider(
                    value = eqPreampParametric.coerceIn(-24f, 12f),
                    onValueChange = { value ->
                        eqPreampParametric = value
                        playerPrefs.eqPreampParametric = value
                    },
                    valueRange = -24f..12f,
                    colors = sonveilSliderColors(),
                )
                if (eqFilters.isEmpty()) {
                    Hint("Search AutoEQ above, or add a band. Each band has frequency, gain, and Q.")
                }
                eqFilters.forEachIndexed { index, filter ->
                    ParametricBandEditor(
                        filter = filter,
                        index = index,
                        onChange = { updated ->
                            val next = eqFilters.toMutableList()
                            next[index] = updated
                            eqFilters = next
                            eqPreset = "custom"
                            playerPrefs.eqFilters = next
                            playerPrefs.eqPreset = "custom"
                        },
                        onRemove = {
                            val next = eqFilters.toMutableList().also { it.removeAt(index) }
                            eqFilters = next
                            playerPrefs.eqFilters = next
                        },
                    )
                }
                if (eqFilters.size < 10) {
                    TextButton(onClick = {
                        val next = eqFilters + EqFilter("PK", 1000f, 1f, 0f)
                        eqFilters = next
                        eqPreset = "custom"
                        playerPrefs.eqFilters = next
                        playerPrefs.eqPreset = "custom"
                        if (!eqOn) {
                            eqOn = true
                            playerPrefs.eqEnabled = true
                        }
                    }) { Text("Add band") }
                }
            }
        }
        if (showHeadphoneSearch) {
            HeadphoneEqDialog(
                context = context,
                mode = eqMode,
                onDismiss = { showHeadphoneSearch = false },
                onPick = { entry ->
                    applyHeadphone(entry)
                    showHeadphoneSearch = false
                },
            )
        }

        SettingsGroup("Support Sonveil") {
            Hint("Donations are optional and help support development.")
            TextButton(onClick = { uriHandler.openUri("https://ko-fi.com/exquisiteskink") }) {
                Text("Donate with Ko-fi")
            }
            TextButton(onClick = { uriHandler.openUri("https://liberapay.com/exquisiteskink/") }) {
                Text("Donate with Liberapay")
            }
        }

        SettingsGroup("About") {
            Text("Sonveil", color = p.onBackground, fontWeight = FontWeight.SemiBold)
            Text(BuildConfig.VERSION_NAME, color = p.onBackground.copy(alpha = 0.5f), fontSize = 13.sp)
            Text(
                "A listening app for Navidrome, Subsonic, and OpenSubsonic.",
                color = p.onBackground.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Text(
        title.uppercase(),
        color = p.onBackground.copy(alpha = 0.5f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
    )
    GlassSurface(Modifier.fillMaxWidth()) { content() }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun Hint(text: String) {
    val p = LocalPalette.current
    Text(text, color = p.onBackground.copy(alpha = 0.45f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun RadioRow(selected: Boolean, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Text(label, color = p.onBackground)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = p.onBackground.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 15.sp)
            Text(subtitle, color = p.onBackground.copy(alpha = 0.45f), fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            enabled = enabled,
            // Stock M3 roles from the art scheme: checked track = primary, thumb = onPrimary.
            colors = SwitchDefaults.colors(),
        )
    }
}

@Composable
private fun EqModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    Text(
        label,
        color = if (selected) p.onPrimary else p.onBackground,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(
                if (selected) p.primary else p.onBackground.copy(alpha = 0.12f),
                androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun AutoEqSearchField(onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(p.onBackground.copy(alpha = 0.08f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Search AutoEQ", color = p.onBackground, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("Headphones", color = p.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
    }
}

class AppearancePrefs(context: Context) {
    // Share the legacy preference file so upgrades retain appearance settings.
    private val prefs = context.getSharedPreferences("auralis_prefs", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.entries.getOrElse(prefs.getInt("theme", 0)) { ThemeMode.System }
        set(value) { prefs.edit().putInt("theme", value.ordinal).apply() }

    var transcode: Int
        get() = prefs.getInt("transcode", 0)
        set(value) { prefs.edit().putInt("transcode", value).apply() }

    var artistGrid: Boolean
        get() = prefs.getBoolean("artist_grid", true)
        set(value) { prefs.edit().putBoolean("artist_grid", value).apply() }
}
