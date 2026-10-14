package com.example.momento.feature.moment.presentation.camera

internal data class CameraLensZoom(
    val intrinsicZoomRatio: Float,
    val minZoomRatio: Float,
    val maxZoomRatio: Float,
)

internal data class CameraZoomOption(
    val displayRatio: Float,
    val lensIndex: Int,
    val cameraZoomRatio: Float,
) {
    fun displayZoomRatio(cameraRatio: Float): Float = displayRatio * cameraRatio / cameraZoomRatio
}

/** Lenses must be ordered with the default camera first, and share the same lens facing. */
internal fun cameraZoomOptions(lenses: List<CameraLensZoom>): List<CameraZoomOption> {
    val default = lenses.firstOrNull() ?: return emptyList()
    val ultraWide = lenses.withIndex()
        .filter { (_, lens) ->
            lens.intrinsicZoomRatio > 0f && lens.intrinsicZoomRatio < 1f &&
                1f in lens.minZoomRatio..lens.maxZoomRatio
        }
        .minByOrNull { it.value.intrinsicZoomRatio }

    return listOf(0.5f, 1f, 2f).mapNotNull { ratio ->
        when {
            // Prefer a logical camera that can handle zoom-out without rebinding.
            ratio in default.minZoomRatio..default.maxZoomRatio -> CameraZoomOption(ratio, 0, ratio)
            // A separate ultra-wide camera still reports its own native zoom as 1x.
            // Map its full field of view to the existing 0.5x preset.
            ratio == 0.5f && ultraWide != null -> CameraZoomOption(ratio, ultraWide.index, 1f)
            else -> null
        }
    }
}
