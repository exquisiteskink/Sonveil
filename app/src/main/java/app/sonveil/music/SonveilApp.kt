package app.sonveil.music

import android.app.Application
import app.sonveil.music.data.player.AutoEqCatalog
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SonveilApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        appScope.launch { AutoEqCatalog.load(this@SonveilApp) }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(container.client.http)
            // Authenticated artwork URLs can contain API keys or encoded passwords.
            .diskCachePolicy(coil.request.CachePolicy.DISABLED)
            .crossfade(380)
            .build()

    companion object {
        lateinit var instance: SonveilApp
            private set
    }
}
