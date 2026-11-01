package com.notiz.app.photos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.notiz.app.LocalAppContainer
import com.notiz.app.data.AttachmentRepository
import com.notiz.app.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

private val Thumb = 88.dp
private val ThumbShape = RoundedCornerShape(10.dp)

@OptIn(ExperimentalResourceApi::class)
private suspend fun decode(bytes: ByteArray): ImageBitmap? = withContext(Dispatchers.Default) {
    try {
        bytes.decodeToImageBitmap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }
}

/**
 * Photos chosen for a new notice or complaint: thumbnails with a remove button, plus an
 * "Add photo" tile until [AttachmentRepository.MAX_PHOTOS] is reached.
 */
@Composable
fun PhotoPickerRow(
    photos: List<ByteArray>,
    enabled: Boolean,
    onAdd: (ByteArray) -> Unit,
    onRemove: (Int) -> Unit,
    onPickFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pick = rememberPhotoPicker(onPicked = onAdd, onError = onPickFailed)
    Column(modifier) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            photos.forEachIndexed { index, bytes ->
                Box {
                    LocalPhoto(bytes, Modifier.size(Thumb).clip(ThumbShape))
                    if (enabled) {
                        IconButton(
                            onClick = { onRemove(index) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(28.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.photos_remove), tint = Color.White)
                        }
                    }
                }
            }
            if (photos.size < AttachmentRepository.MAX_PHOTOS) {
                Box(
                    Modifier.size(Thumb).clip(ThumbShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, ThumbShape)
                        .clickable(enabled = enabled, onClick = pick),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(Res.string.photos_add), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalPhoto(bytes: ByteArray, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, bytes) { value = decode(bytes) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** Photos saved on a notice or complaint. Tap one to see it full screen. */
@Composable
fun RemotePhotoStrip(paths: List<String>, modifier: Modifier = Modifier, size: Dp = Thumb) {
    if (paths.isEmpty()) return
    var open by remember { mutableStateOf<String?>(null) }
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        paths.forEach { path ->
            RemotePhoto(
                path,
                ContentScale.Crop,
                Modifier.size(size).clip(ThumbShape).clickable { open = path },
            )
        }
    }
    open?.let { path ->
        Dialog(onDismissRequest = { open = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { open = null }) {
                RemotePhoto(path, ContentScale.Fit, Modifier.fillMaxSize())
                IconButton(onClick = { open = null }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.action_back), tint = Color.White)
                }
            }
        }
    }
}

private sealed interface PhotoLoad {
    data object Loading : PhotoLoad
    data object Failed : PhotoLoad
    data class Ready(val bitmap: ImageBitmap) : PhotoLoad
}

@Composable
private fun RemotePhoto(path: String, contentScale: ContentScale, modifier: Modifier) {
    val attachments = LocalAppContainer.current.attachments
    var attempt by remember(path) { mutableStateOf(0) }
    var load by remember(path) { mutableStateOf<PhotoLoad>(PhotoLoad.Loading) }
    LaunchedEffect(path, attempt) {
        load = PhotoLoad.Loading
        load = try {
            decode(attachments.download(path))?.let { PhotoLoad.Ready(it) } ?: PhotoLoad.Failed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            PhotoLoad.Failed
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        when (val l = load) {
            PhotoLoad.Loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            PhotoLoad.Failed -> Text(
                stringResource(Res.string.photos_retry),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().clickable { attempt++ }.padding(8.dp),
            )
            is PhotoLoad.Ready -> Image(l.bitmap, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
    }
}
