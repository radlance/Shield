package com.github.radlance.shield.qr

internal data class QrScanRegion(
    val widthFraction: Float,
    val heightFraction: Float
) {
    fun contains(
        barcode: QrImageRect,
        viewport: QrImageRect
    ): Boolean {
        if (!barcode.isValid || !viewport.isValid) {
            return false
        }
        val regionWidth = viewport.width * widthFraction
        val regionHeight = viewport.height * heightFraction
        val regionLeft = viewport.left + (viewport.width - regionWidth) / 2f
        val regionTop = viewport.top + (viewport.height - regionHeight) / 2f
        val regionRight = regionLeft + regionWidth
        val regionBottom = regionTop + regionHeight
        return barcode.left >= regionLeft &&
            barcode.top >= regionTop &&
            barcode.right <= regionRight &&
            barcode.bottom <= regionBottom
    }

    companion object {
        fun forViewport(width: Int, height: Int): QrScanRegion {
            if (width <= 0 || height <= 0) {
                return QrScanRegion(FINDER_SIZE_FRACTION, FINDER_SIZE_FRACTION)
            }
            val finderSize = minOf(width, height) * FINDER_SIZE_FRACTION
            return QrScanRegion(
                widthFraction = finderSize / width,
                heightFraction = finderSize / height
            )
        }

        const val FINDER_SIZE_FRACTION = 0.72f
    }
}

internal data class QrImageRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isValid: Boolean get() = width > 0 && height > 0
}

internal fun orientedCropRect(
    imageWidth: Int,
    imageHeight: Int,
    rotationDegrees: Int,
    cropRect: QrImageRect
): QrImageRect = when (rotationDegrees) {
    90 -> QrImageRect(
        left = imageHeight - cropRect.bottom,
        top = cropRect.left,
        right = imageHeight - cropRect.top,
        bottom = cropRect.right
    )
    180 -> QrImageRect(
        left = imageWidth - cropRect.right,
        top = imageHeight - cropRect.bottom,
        right = imageWidth - cropRect.left,
        bottom = imageHeight - cropRect.top
    )
    270 -> QrImageRect(
        left = cropRect.top,
        top = imageWidth - cropRect.right,
        right = cropRect.bottom,
        bottom = imageWidth - cropRect.left
    )
    else -> cropRect
}
