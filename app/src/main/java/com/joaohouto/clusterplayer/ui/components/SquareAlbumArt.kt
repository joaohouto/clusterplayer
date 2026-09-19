package com.joaohouto.clusterplayer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.joaohouto.clusterplayer.R
import coil.request.ImageRequest
import coil.size.Scale
import com.joaohouto.clusterplayer.ui.theme.MetallicIntermediate
import com.joaohouto.clusterplayer.ui.theme.SurfaceCard
import com.joaohouto.clusterplayer.ui.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SquareAlbumArt(
    uriOrPath: String?,
    modifier: Modifier = Modifier,
    onDoubleTap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var artBytes by remember(uriOrPath) { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(uriOrPath) {
        if (!uriOrPath.isNullOrEmpty()) {
            artBytes = AudioArtExtractor.getEmbeddedArt(context, uriOrPath)
        } else {
            artBytes = null
        }
    }

    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(SurfaceCard)
            .then(
                if (onDoubleTap != null) {
                    Modifier.combinedClickable(
                        onClick = {},
                        onDoubleClick = onDoubleTap
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (artBytes != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artBytes)
                    .size(512, 512)
                    .scale(Scale.FILL)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(R.string.desc_album_art),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Metallic Fallback with subtle music note
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MetallicIntermediate),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = stringResource(R.string.desc_no_art),
                    tint = TextSecondary,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}
