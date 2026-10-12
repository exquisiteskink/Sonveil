package app.sonveil.music.ui.player

internal data class PlayerLayout(val artSize: Float, val twoPane: Boolean, val scrollMetadata: Boolean)

/** Reserve transport/queue controls first. Metadata can scroll instead of pushing controls away. */
internal fun playerLayout(width: Float, height: Float, fontScale: Float): PlayerLayout {
    val twoPane = width >= 600f && width > height
    val available = (height - 48f - 96f - 56f - 24f).coerceAtLeast(0f)
    val metadata = 180f * fontScale.coerceAtLeast(1f)
    val art = if (twoPane) minOf(width * .42f, height - 96f)
        else minOf(width * .9f, height * .42f, available - metadata)
    return PlayerLayout(art.coerceIn(64f, maxOf(64f, width)), twoPane, available < metadata + width * .9f)
}

internal fun seekPosition(fraction: Float, durationMs: Long): Long =
    if (durationMs <= 0 || !fraction.isFinite()) 0L
    else (fraction.coerceIn(0f, 1f) * durationMs).toLong().coerceIn(0, durationMs)
