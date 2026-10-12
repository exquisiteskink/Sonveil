package app.sonveil.music.ui.components

import app.sonveil.music.data.remote.SubsonicException
import kotlinx.coroutines.CancellationException

/** Only successful empty reads are empty states; failed refreshes retain confirmed content. */
internal data class LibraryRead<T>(val value: T? = null, val error: String? = null)

internal suspend fun <T> readKeeping(previous: T?, read: suspend () -> T): LibraryRead<T> = try {
    LibraryRead(value = read())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    LibraryRead(value = previous, error = libraryError(e))
}

/** Never render raw transport URLs, credentials or exception internals in library errors. */
internal fun libraryError(error: Exception): String = when (error) {
    is SubsonicException -> when (error.code) {
        40, 41, 42, 43, 44 -> "The server rejected this sign-in. Check your account in Settings."
        else -> "The server could not return this music. Try again."
    }
    is javax.net.ssl.SSLException -> "The server's secure connection could not be verified. Check its certificate."
    is java.io.IOException -> "Could not reach your music server. Check the connection and try again."
    else -> "Could not load this music. Try again."
}
