package app.sonveil.music.data.player

import android.content.Context
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CacheBitmapLoader

/** The session/notification loader must resolve the local URIs supplied to car clients. */
@UnstableApi
internal fun mediaArtworkBitmapLoader(context: Context): BitmapLoader =
    CacheBitmapLoader(DataSourceBitmapLoader(context))
