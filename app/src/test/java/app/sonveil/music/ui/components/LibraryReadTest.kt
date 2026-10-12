package app.sonveil.music.ui.components

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LibraryReadTest {
    @Test fun aSuccessfulEmptyLibraryIsNotAnError() = runTest {
        val read = readKeeping<List<String>>(null) { emptyList() }
        assertTrue(read.value!!.isEmpty()); assertNull(read.error)
    }
    @Test fun failedRefreshRetainsConfirmedContentWithoutLeakingExceptionDetails() = runTest {
        val read = readKeeping(listOf("album")) { throw java.io.IOException("https://server?secret=token") }
        assertEquals(listOf("album"), read.value)
        assertNotNull(read.error); assertFalse(read.error!!.contains("secret"))
    }
    @Test(expected = CancellationException::class) fun cancellationIsNotAnError() = runTest {
        readKeeping<String>(null) { throw CancellationException() }
    }
}
