package com.github.radlance.shield.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrScanRegionTest {
    private val viewport = QrImageRect(left = 0, top = 0, right = 1_000, bottom = 2_000)
    private val region = QrScanRegion.forViewport(viewport.width, viewport.height)

    @Test
    fun acceptsQrFullyInsideFinder() {
        val barcode = QrImageRect(left = 200, top = 700, right = 800, bottom = 1_300)

        assertTrue(region.contains(barcode, viewport))
    }

    @Test
    fun rejectsQrOutsideOrCrossingFinder() {
        val outside = QrImageRect(left = 200, top = 100, right = 800, bottom = 700)
        val crossing = QrImageRect(left = 100, top = 700, right = 800, bottom = 1_300)

        assertFalse(region.contains(outside, viewport))
        assertFalse(region.contains(crossing, viewport))
    }

    @Test
    fun rotatesCameraCropIntoDisplayCoordinates() {
        val crop = QrImageRect(left = 200, top = 100, right = 1_700, bottom = 900)

        assertEquals(
            QrImageRect(left = 180, top = 200, right = 980, bottom = 1_700),
            orientedCropRect(
                imageWidth = 1_920,
                imageHeight = 1_080,
                rotationDegrees = 90,
                cropRect = crop
            )
        )
    }
}
