package io.github.alessiop04.videocamerasegreta

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class HelpersTest {
    @Test
    fun versions() {
        assertTrue(Versions.isNewer("v0.2.0", "0.1.0"))
        assertTrue(Versions.isNewer("0.10.0", "0.9.3"))
        assertTrue(Versions.isNewer("1.0.0", "0.9.9"))
        assertFalse(Versions.isNewer("v0.1.0", "0.1.0"))
        assertFalse(Versions.isNewer("0.1.0", "0.2.0"))
    }

    @Test
    fun rotation() {
        assertEquals(Surface.ROTATION_0, surfaceRotationFor(0))
        assertEquals(Surface.ROTATION_0, surfaceRotationFor(350))
        assertEquals(Surface.ROTATION_270, surfaceRotationFor(90))
        assertEquals(Surface.ROTATION_180, surfaceRotationFor(180))
        assertEquals(Surface.ROTATION_90, surfaceRotationFor(270))
        assertNull(surfaceRotationFor(-1)) // telefono in piano
    }

    @Test
    fun fileName() {
        assertEquals("VID_20261121_090507", videoFileName(LocalDateTime.of(2026, 11, 21, 9, 5, 7)))
    }
}
