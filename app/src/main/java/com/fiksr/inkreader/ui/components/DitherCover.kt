package com.fiksr.inkreader.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fiksr.inkreader.theme.BookFontFamily
import com.fiksr.inkreader.theme.LocalEInkColors
import java.io.File

@Composable
fun DitherCover(
    title: String,
    author: String = "",
    coverImagePath: String? = null,
    coverUrl: String? = null,
    username: String = "",
    password: String = "",
    xAuthUser: String = "",
    xAuthKey: String = "",
    pattern: Int = 0,
    modifier: Modifier = Modifier,
    isDitherEnabled: Boolean = true
) {
    val colors = LocalEInkColors.current

    val effectiveUrl = if (!coverImagePath.isNullOrBlank()) coverImagePath else coverUrl
    val loadedBitmap = rememberCoverBitmap(
        url = effectiveUrl,
        username = username,
        password = password,
        xAuthUser = xAuthUser,
        xAuthKey = xAuthKey
    )

    val imageBitmap = remember(loadedBitmap) {
        loadedBitmap?.asImageBitmap()
    }

    Box(
        modifier = modifier
            .border(1.5.dp, colors.border, RoundedCornerShape(2.dp))
            .background(colors.card)
            .clip(RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Left spine overlay line
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .align(Alignment.CenterStart)
                    .background(colors.border.copy(alpha = 0.35f))
            )
        } else {
            // High-Quality Classic Stylized Book Cover
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Double border frame
                drawRect(
                    color = colors.border,
                    topLeft = Offset(4f, 4f),
                    size = Size(w - 8f, h - 8f),
                    style = Stroke(width = 1.5f)
                )
                drawRect(
                    color = colors.border,
                    topLeft = Offset(7f, 7f),
                    size = Size(w - 14f, h - 14f),
                    style = Stroke(width = 0.8f)
                )

                // Corner decorative corner brackets
                val cornerSize = 8f
                // Top-left
                drawLine(colors.border, Offset(10f, 10f), Offset(10f + cornerSize, 10f), 1.2f)
                drawLine(colors.border, Offset(10f, 10f), Offset(10f, 10f + cornerSize), 1.2f)
                // Top-right
                drawLine(colors.border, Offset(w - 10f, 10f), Offset(w - 10f - cornerSize, 10f), 1.2f)
                drawLine(colors.border, Offset(w - 10f, 10f), Offset(w - 10f, 10f + cornerSize), 1.2f)
                // Bottom-left
                drawLine(colors.border, Offset(10f, h - 10f), Offset(10f + cornerSize, h - 10f), 1.2f)
                drawLine(colors.border, Offset(10f, h - 10f), Offset(10f, h - 10f - cornerSize), 1.2f)
                // Bottom-right
                drawLine(colors.border, Offset(w - 10f, h - 10f), Offset(w - 10f - cornerSize, h - 10f), 1.2f)
                drawLine(colors.border, Offset(w - 10f, h - 10f), Offset(w - 10f, h - 10f - cornerSize), 1.2f)

                // Center artistic geometric emblem
                val midY = h * 0.52f
                val midX = w * 0.5f

                when (pattern % 4) {
                    0 -> {
                        // Hyperion Spire / Diamond Motif
                        drawLine(colors.border, Offset(midX, midY - 14f), Offset(midX + 12f, midY), 1.2f)
                        drawLine(colors.border, Offset(midX + 12f, midY), Offset(midX, midY + 14f), 1.2f)
                        drawLine(colors.border, Offset(midX, midY + 14f), Offset(midX - 12f, midY), 1.2f)
                        drawLine(colors.border, Offset(midX - 12f, midY), Offset(midX, midY - 14f), 1.2f)
                        drawLine(colors.border, Offset(midX, midY - 20f), Offset(midX, midY + 20f), 1f)
                    }
                    1 -> {
                        // Dune Arrakis Crescent & Ring
                        drawCircle(colors.border, radius = 10f, center = Offset(midX, midY), style = Stroke(1.2f))
                        drawLine(colors.border, Offset(midX - 16f, midY + 5f), Offset(midX + 16f, midY - 5f), 1f)
                    }
                    2 -> {
                        // Solaris Ocean Wave curves
                        drawLine(colors.border, Offset(midX - 14f, midY - 4f), Offset(midX - 4f, midY + 4f), 1.2f)
                        drawLine(colors.border, Offset(midX - 4f, midY + 4f), Offset(midX + 4f, midY - 4f), 1.2f)
                        drawLine(colors.border, Offset(midX + 4f, midY - 4f), Offset(midX + 14f, midY + 4f), 1.2f)
                        drawLine(colors.border, Offset(midX - 14f, midY + 3f), Offset(midX - 4f, midY + 11f), 1.2f)
                        drawLine(colors.border, Offset(midX - 4f, midY + 11f), Offset(midX + 4f, midY + 3f), 1.2f)
                        drawLine(colors.border, Offset(midX + 4f, midY + 3f), Offset(midX + 14f, midY + 11f), 1.2f)
                    }
                    3 -> {
                        // Ishmael Tree & Horizon
                        drawLine(colors.border, Offset(midX, midY - 12f), Offset(midX, midY + 12f), 1.5f)
                        drawLine(colors.border, Offset(midX - 10f, midY - 2f), Offset(midX, midY - 8f), 1.2f)
                        drawLine(colors.border, Offset(midX + 10f, midY - 2f), Offset(midX, midY - 8f), 1.2f)
                        drawLine(colors.border, Offset(midX - 12f, midY + 4f), Offset(midX, midY - 2f), 1.2f)
                        drawLine(colors.border, Offset(midX + 12f, midY + 4f), Offset(midX, midY - 2f), 1.2f)
                        drawLine(colors.border, Offset(midX - 16f, midY + 12f), Offset(midX + 16f, midY + 12f), 1.2f)
                    }
                }
            }

            // Typography overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Book Title
                Text(
                    text = title,
                    fontFamily = BookFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )

                // Author Name
                if (author.isNotBlank()) {
                    Text(
                        text = author,
                        fontFamily = FontFamily.Monospace,
                        fontStyle = FontStyle.Italic,
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}
