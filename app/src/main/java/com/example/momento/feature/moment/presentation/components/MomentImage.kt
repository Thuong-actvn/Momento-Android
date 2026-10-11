package com.example.momento.feature.moment.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

const val MOMENT_IMAGE_ASPECT_RATIO = 4f / 5f

/** Camera drafts and feed photos use the same frame and centered crop. */
@Composable
fun MomentImageFrame(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .aspectRatio(MOMENT_IMAGE_ASPECT_RATIO)
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        content = content,
    )
}

@Composable
fun MomentImage(
    image: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onSuccess: () -> Unit = {},
    onError: () -> Unit = {},
) {
    val context = LocalContext.current
    val request = remember(context, image) {
        ImageRequest.Builder(context).data(image).crossfade(false).build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier.fillMaxSize(),
        alignment = Alignment.Center,
        contentScale = ContentScale.Crop,
        onSuccess = { onSuccess() },
        onError = { onError() },
    )
}
