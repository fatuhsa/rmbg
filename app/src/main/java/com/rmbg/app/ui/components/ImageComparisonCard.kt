package com.rmbg.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BeforeAfterComparisonCard(
    originalBitmap: Bitmap?,
    resultBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        if (originalBitmap == null) {
            // Empty placeholder state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No image selected\n(Tap 'Select Image' to begin)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else if (resultBitmap == null) {
            // Only original image selected (not processed yet)
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = originalBitmap.asImageBitmap(),
                    contentDescription = "Original Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                LabelBadge(
                    text = "ORIGINAL",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )
            }
        } else {
            // Both original & processed result available -> Interactive Split Comparison Slider!
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            splitFraction = (offset.x / size.width).coerceIn(0.02f, 0.98f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            splitFraction = (change.position.x / size.width).coerceIn(0.02f, 0.98f)
                        }
                    }
            ) {
                val boxWidthPx = constraints.maxWidth.toFloat()
                val splitXPx = boxWidthPx * splitFraction

                // 1. Checkerboard background to show transparency clearly
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridSize = 24f
                    val cols = (size.width / gridSize).toInt() + 1
                    val rows = (size.height / gridSize).toInt() + 1
                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            val color = if ((r + c) % 2 == 0) Color(0xFFEEEEEE) else Color(0xFFDDDDDD)
                            drawRect(
                                color = color,
                                topLeft = Offset(c * gridSize, r * gridSize),
                                size = Size(gridSize, gridSize)
                            )
                        }
                    }
                }

                // 2. Result Image (Processed - Background Removed)
                Image(
                    bitmap = resultBitmap.asImageBitmap(),
                    contentDescription = "Background Removed Result",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // 3. Original Image (Clipped to the left of the split line)
                Image(
                    bitmap = originalBitmap.asImageBitmap(),
                    contentDescription = "Original Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            clipRect(right = splitXPx) {
                                this@drawWithContent.drawContent()
                            }
                        },
                    contentScale = ContentScale.Fit
                )

                // 4. Badges
                LabelBadge(
                    text = "ORIGINAL",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )

                LabelBadge(
                    text = "REMOVED",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )

                // 5. Vertical Split Divider Line
                Box(
                    modifier = Modifier
                        .offset { IntOffset(splitXPx.toInt() - 2.dp.roundToPx(), 0) }
                        .width(4.dp)
                        .fillMaxSize()
                        .background(Color.White)
                )

                // 6. Split Slider Draggable Thumb Handle
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(splitXPx.toInt() - 18.dp.roundToPx(), 0) }
                        .size(36.dp),
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "◀ ▶",
                            fontSize = 10.sp,
                            color = Color(0xFF333333)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = 0.65f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

