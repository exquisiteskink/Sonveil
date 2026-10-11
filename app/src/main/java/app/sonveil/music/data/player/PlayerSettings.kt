package app.sonveil.music.data.player

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import kotlin.math.roundToInt

enum class ReplayGainMode { Off, Track, Album }

class PlayerSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var replayGainMode: ReplayGainMode
        get() = ReplayGainMode.entries.getOrElse(prefs.getInt(RG_MODE, 0)) { ReplayGainMode.Off }
        set(value) { prefs.edit().putInt(RG_MODE, value.ordinal).apply() }

    var peakLimiter: Boolean
        get() = prefs.getBoolean(RG_LIMIT, true)
        set(value) { prefs.edit().putBoolean(RG_LIMIT, value).apply() }

    var gapless: Boolean
        get() = prefs.getBoolean(GAPLESS, true)
        set(value) {
            prefs.edit().putBoolean(GAPLESS, value).apply()
            if (!value && crossfade) crossfade = false
        }

    var crossfade: Boolean
        get() = prefs.getBoolean(CROSSFADE, false)
        set(value) {
            if (value && !gapless) gapless = true
            prefs.edit().putBoolean(CROSSFADE, value && gapless).apply()
        }

    var crossfadeMs: Int
        get() = prefs.getInt(CROSSFADE_MS, 5000).coerceIn(1000, 12000)
        set(value) { prefs.edit().putInt(CROSSFADE_MS, value.coerceIn(1000, 12000)).apply() }

    var pauseOnDisconnect: Boolean
        get() = prefs.getBoolean(PAUSE_DISC, true)
        set(value) { prefs.edit().putBoolean(PAUSE_DISC, value).apply() }

    /**
     * Sleep timer selection written by Settings / cleared by PlaybackService.
     * `0` = off, `-1` = end of current track, otherwise minutes (`15`/`30`/`45`/`60`).
     */
    var sleepTimerMinutes: Int
        get() = prefs.getInt(SLEEP_MINUTES, 0)
        set(value) { prefs.edit().putInt(SLEEP_MINUTES, value).apply() }

    /** `SystemClock.elapsedRealtime()` deadline for minute-based timers; `0` if inactive or end-of-track. */
    var sleepDeadlineElapsed: Long
        get() = prefs.getLong(SLEEP_DEADLINE, 0L)
        set(value) { prefs.edit().putLong(SLEEP_DEADLINE, value).apply() }

    /**
     * Wall-clock (`System.currentTimeMillis()`) deadline for minute-based timers.
     * Survives reboot; [sleepDeadlineElapsed] alone does not (`elapsedRealtime` resets).
     */
    var sleepDeadlineWallMs: Long
        get() = prefs.getLong(SLEEP_DEADLINE_WALL, 0L)
        set(value) { prefs.edit().putLong(SLEEP_DEADLINE_WALL, value).apply() }

    val currentBootCount: Int
        get() = Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, -1)

    var sleepDeadlineBootCount: Int
        get() = prefs.getInt(SLEEP_DEADLINE_BOOT_COUNT, -1)
        set(value) { prefs.edit().putInt(SLEEP_DEADLINE_BOOT_COUNT, value).apply() }

    fun clearSleepTimer() {
        prefs.edit()
            .putInt(SLEEP_MINUTES, 0)
            .putLong(SLEEP_DEADLINE, 0L)
            .putLong(SLEEP_DEADLINE_WALL, 0L)
            .putInt(SLEEP_DEADLINE_BOOT_COUNT, -1)
            .apply()
    }

    fun armSleepMinutes(minutes: Int) {
        require(minutes in SLEEP_MINUTE_OPTIONS)
        val elapsedDeadline = android.os.SystemClock.elapsedRealtime() + minutes * 60_000L
        val wallDeadline = System.currentTimeMillis() + minutes * 60_000L
        prefs.edit()
            .putInt(SLEEP_MINUTES, minutes)
            .putLong(SLEEP_DEADLINE, elapsedDeadline)
            .putLong(SLEEP_DEADLINE_WALL, wallDeadline)
            .putInt(SLEEP_DEADLINE_BOOT_COUNT, currentBootCount)
            .apply()
    }

    fun armSleepEndOfTrack() {
        prefs.edit()
            .putInt(SLEEP_MINUTES, SLEEP_END_OF_TRACK)
            .putLong(SLEEP_DEADLINE, 0L)
            .putLong(SLEEP_DEADLINE_WALL, 0L)
            .putInt(SLEEP_DEADLINE_BOOT_COUNT, -1)
            .apply()
    }

    /** When true, OpenSubsonic original/`download` offline fetches require unmetered Wi‑Fi. */
    var wifiOnlyHiResDownloads: Boolean
        get() = prefs.getBoolean(WIFI_ONLY_HIRES_DL, true)
        set(value) { prefs.edit().putBoolean(WIFI_ONLY_HIRES_DL, value).apply() }

    var eqEnabled: Boolean
        get() = prefs.getBoolean(EQ_ON, false)
        set(value) { prefs.edit().putBoolean(EQ_ON, value).apply() }

    var eqPreset: String
        get() {
            val id = prefs.getString(EQ_PRESET, EqPresets.FLAT.id) ?: EqPresets.FLAT.id
            return id.ifBlank { EqPresets.FLAT.id }
        }
        set(value) { prefs.edit().putString(EQ_PRESET, value).apply() }

    var eqMode: EqMode
        get() = if (prefs.getString(EQ_MODE, EqMode.Graphic.name) == EqMode.Parametric.name) {
            EqMode.Parametric
        } else {
            EqMode.Graphic
        }
        set(value) { prefs.edit().putString(EQ_MODE, value.name).apply() }

    var eqPreampGraphic: Float
        get() = prefs.getFloat(EQ_PREAMP_GRAPHIC, 0f)
        set(value) { prefs.edit().putFloat(EQ_PREAMP_GRAPHIC, value.coerceIn(-24f, 12f)).apply() }

    var eqPreampParametric: Float
        get() = prefs.getFloat(EQ_PREAMP_PARAMETRIC, 0f)
        set(value) { prefs.edit().putFloat(EQ_PREAMP_PARAMETRIC, value.coerceIn(-24f, 12f)).apply() }

    var eqFilters: List<EqFilter>
        get() = EqFilterCodec.decode(prefs.getString(EQ_FILTERS, null))
        set(value) { prefs.edit().putString(EQ_FILTERS, EqFilterCodec.encode(value)).apply() }

    var eqHeadphoneName: String
        get() = prefs.getString(EQ_HEADPHONE, "").orEmpty()
        set(value) { prefs.edit().putString(EQ_HEADPHONE, value).apply() }

    /** True while a device profile is being copied into the live equalizer. */
    @Volatile
    var applyingProfile: Boolean = false
        private set

    /** Remember the equalizer from the first run so a new output does not inherit another headphone. */
    fun ensureBaseline() {
        if (!prefs.getString(EQ_BASELINE, null).isNullOrBlank()) return
        val baseline = snapshotFor(OutputDevice("baseline", "Default", OutputKind.Speaker, 100))
        prefs.edit().putString(EQ_BASELINE, EqProfileCodec.encode(mapOf(baseline.id to baseline))).apply()
    }

    fun baselineProfile(): EqDeviceProfile =
        EqProfileCodec.decode(prefs.getString(EQ_BASELINE, null)).values.firstOrNull()
            ?: snapshotFor(OutputDevice("baseline", "Default", OutputKind.Speaker, 100))

    fun deviceProfiles(): List<EqDeviceProfile> =
        EqProfileCodec.decode(prefs.getString(EQ_PROFILES, null)).values.sortedBy { it.name.lowercase() }

    fun profileFor(deviceId: String): EqDeviceProfile? =
        EqProfileCodec.decode(prefs.getString(EQ_PROFILES, null))[deviceId]

    fun saveProfileFor(device: OutputDevice) {
        if (device.id.isBlank() || applyingProfile) return
        val profiles = EqProfileCodec.decode(prefs.getString(EQ_PROFILES, null)).toMutableMap()
        profiles[device.id] = snapshotFor(device)
        prefs.edit().putString(EQ_PROFILES, EqProfileCodec.encode(profiles)).apply()
    }

    fun deleteProfile(deviceId: String) {
        val profiles = EqProfileCodec.decode(prefs.getString(EQ_PROFILES, null)).toMutableMap()
        if (profiles.remove(deviceId) == null) return
        prefs.edit().putString(EQ_PROFILES, EqProfileCodec.encode(profiles)).apply()
    }

    /** Replace the live equalizer with a profile. Listeners see the finished curve. */
    fun writeEqSnapshot(profile: EqDeviceProfile) {
        applyingProfile = true
        try {
            prefs.edit()
                .putBoolean(EQ_ON, profile.enabled)
                .putString(EQ_MODE, profile.mode)
                .putString(EQ_PRESET, profile.preset.ifBlank { EqPresets.FLAT.id })
                .putString(EQ_GAINS, profile.gains.ifBlank { flatGains() })
                .putFloat(EQ_PREAMP_GRAPHIC, profile.preampGraphic)
                .putFloat(EQ_PREAMP_PARAMETRIC, profile.preampParametric)
                .putString(EQ_FILTERS, profile.filters)
                .putString(EQ_HEADPHONE, profile.headphone)
                .apply()
        } finally {
            applyingProfile = false
        }
    }

    private fun snapshotFor(device: OutputDevice) = EqDeviceProfile(
        id = device.id,
        name = device.name,
        kind = device.kind.name,
        enabled = eqEnabled,
        mode = eqMode.name,
        preset = eqPreset,
        gains = prefs.getString(EQ_GAINS, null) ?: flatGains(),
        preampGraphic = eqPreampGraphic,
        preampParametric = eqPreampParametric,
        filters = prefs.getString(EQ_FILTERS, "").orEmpty(),
        headphone = eqHeadphoneName,
    )

    private fun flatGains(): String = FloatArray(10).joinToString(",") { "0.0" }

    var eqGains: FloatArray
        get() {
            val raw = prefs.getString(EQ_GAINS, null) ?: return EqPresets.FLAT.gains.copyOf()
            val parts = raw.split(',')
            if (parts.size != 10) return EqPresets.FLAT.gains.copyOf()
            return FloatArray(10) { i -> parts[i].toFloatOrNull() ?: 0f }
        }
        set(value) {
            // Locale.US: a comma-decimal locale would format 1.5 as "1,5", colliding with the
            // ',' separator so the stored string no longer splits into 10 parts on read.
            prefs.edit().putString(EQ_GAINS, value.joinToString(",") { "%.1f".format(java.util.Locale.US, it) }).apply()
        }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        // Keep the legacy preference file so upgrades retain playback and appearance settings.
        const val PREFS = "auralis_prefs"
        const val RG_MODE = "rg_mode"
        const val RG_LIMIT = "rg_limit"
        const val GAPLESS = "gapless"
        const val CROSSFADE = "crossfade"
        const val CROSSFADE_MS = "crossfade_ms"
        const val PAUSE_DISC = "pause_disconnect"
        const val SLEEP_MINUTES = "sleep_minutes"
        const val SLEEP_DEADLINE = "sleep_deadline"
        const val SLEEP_DEADLINE_WALL = "sleep_deadline_wall"
        const val SLEEP_DEADLINE_BOOT_COUNT = "sleep_deadline_boot_count"
        const val SLEEP_END_OF_TRACK = -1
        val SLEEP_MINUTE_OPTIONS = setOf(15, 30, 45, 60)

        const val WIFI_ONLY_HIRES_DL = "wifi_only_hires_dl"
        const val EQ_ON = "eq_on"
        const val EQ_PRESET = "eq_preset"
        const val EQ_GAINS = "eq_gains"
        const val EQ_MODE = "eq_mode"
        const val EQ_PREAMP_GRAPHIC = "eq_preamp_graphic"
        const val EQ_PREAMP_PARAMETRIC = "eq_preamp_parametric"
        const val EQ_FILTERS = "eq_filters"
        const val EQ_HEADPHONE = "eq_headphone"
        const val EQ_PROFILES = "eq_profiles"
        const val EQ_BASELINE = "eq_baseline"
        val LIVE_EQ_KEYS = setOf(
            EQ_ON, EQ_PRESET, EQ_GAINS, EQ_MODE,
            EQ_PREAMP_GRAPHIC, EQ_PREAMP_PARAMETRIC, EQ_FILTERS, EQ_HEADPHONE,
        )
        // Keep legacy metadata keys shared by playback components for compatibility.
        const val EXTRA_RG_TRACK = "auralis.rg.track"
        const val EXTRA_RG_ALBUM = "auralis.rg.album"
        const val EXTRA_RG_TRACK_PEAK = "auralis.rg.trackPeak"
        const val EXTRA_RG_ALBUM_PEAK = "auralis.rg.albumPeak"
        const val EXTRA_RG_FALLBACK = "auralis.rg.fallback"

        fun crossfadeLabel(ms: Int): String {
            val s = ms / 1000f
            return if (s == s.roundToInt().toFloat()) "${s.roundToInt()} s" else "${"%.1f".format(s)} s"
        }
    }
}

internal object SleepTimerDeadline {
    fun remainingDelayMs(
        savedBootCount: Int,
        currentBootCount: Int,
        elapsedDeadlineMs: Long,
        wallDeadlineMs: Long,
        elapsedNowMs: Long,
        wallNowMs: Long,
        fallbackDurationMs: Long,
    ): Long = when {
        savedBootCount >= 0 && savedBootCount == currentBootCount && elapsedDeadlineMs > 0L ->
            elapsedDeadlineMs - elapsedNowMs
        wallDeadlineMs > 0L -> wallDeadlineMs - wallNowMs
        elapsedDeadlineMs > 0L -> elapsedDeadlineMs - elapsedNowMs
        else -> fallbackDurationMs
    }
}
