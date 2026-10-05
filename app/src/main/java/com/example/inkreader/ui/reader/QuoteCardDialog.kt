package com.example.inkreader.ui.reader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.inkreader.theme.LocalEInkColors
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

enum class QuoteCardTheme(
    val title: String,
    val bg: Color,
    val text: Color,
    val accent: Color,
    val border: Color
) {
    EDITORIAL_CREAM("Paper Cream", Color(0xFFFBF8F1), Color(0xFF1E2124), Color(0xFF8C7355), Color(0xFFE5DDD0)),
    MIDNIGHT_OBSIDIAN("Midnight", Color(0xFF111315), Color(0xFFF3F4F6), Color(0xFFE5A93C), Color(0xFF2C2F33)),
    EINK_CLASSIC("E-Ink Mono", Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFF444444), Color(0xFF000000)),
    FOREST_SAGE("Forest Sage", Color(0xFF1A2621), Color(0xFFEAE6DF), Color(0xFF7CA982), Color(0xFF2A3D35))
}

@Composable
fun QuoteCardDialog(
    quote: String,
    bookTitle: String,
    bookAuthor: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalEInkColors.current

    var selectedTheme by remember { mutableStateOf(QuoteCardTheme.EDITORIAL_CREAM) }
    var showWatermark by remember { mutableStateOf(true) }
    var cardFontSize by remember { mutableFloatStateOf(18f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(16.dp)),
            color = colors.surface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, colors.divider)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aesthetic Quote Card",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Live Preview Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(12.dp))
                        .background(selectedTheme.bg, RoundedCornerShape(12.dp))
                        .border(1.5.dp, selectedTheme.border, RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatQuote,
                            contentDescription = null,
                            tint = selectedTheme.accent,
                            modifier = Modifier.size(36.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "“$quote”",
                            color = selectedTheme.text,
                            fontSize = cardFontSize.sp,
                            lineHeight = (cardFontSize * 1.45f).sp,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Divider
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(2.dp)
                                .background(selectedTheme.accent)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = bookTitle.ifBlank { "Untitled Book" },
                            color = selectedTheme.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            textAlign = TextAlign.Center
                        )

                        if (bookAuthor.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "— $bookAuthor",
                                color = selectedTheme.accent,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Serif,
                                textAlign = TextAlign.Center
                            )
                        }

                        if (showWatermark) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Read with InkReader",
                                color = selectedTheme.accent.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Theme Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuoteCardTheme.values().forEach { th ->
                        val isSel = th == selectedTheme
                        Surface(
                            onClick = { selectedTheme = th },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSel) colors.textPrimary else colors.background,
                            border = BorderStroke(1.dp, if (isSel) colors.textPrimary else colors.divider),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(th.bg, CircleShape)
                                        .border(1.dp, th.accent, CircleShape)
                                )
                                Text(
                                    text = th.title,
                                    fontSize = 12.sp,
                                    color = if (isSel) colors.background else colors.textPrimary,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Controls & Share Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            shareQuoteCardAsImage(
                                context = context,
                                quote = quote,
                                bookTitle = bookTitle,
                                bookAuthor = bookAuthor,
                                theme = selectedTheme,
                                showWatermark = showWatermark,
                                fontSizeSp = cardFontSize
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.textPrimary,
                            contentColor = colors.background
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Quote Card", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private fun shareQuoteCardAsImage(
    context: Context,
    quote: String,
    bookTitle: String,
    bookAuthor: String,
    theme: QuoteCardTheme,
    showWatermark: Boolean,
    fontSizeSp: Float
) {
    try {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        val bgPaint = android.graphics.Paint().apply {
            color = theme.bg.toArgb()
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Outer decorative border
        val borderPaint = android.graphics.Paint().apply {
            color = theme.border.toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRoundRect(40f, 40f, (width - 40).toFloat(), (height - 40).toFloat(), 30f, 30f, borderPaint)

        // Inner frame
        val innerBorder = android.graphics.Paint().apply {
            color = theme.accent.toArgb()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f
            alpha = 140
        }
        canvas.drawRoundRect(56f, 56f, (width - 56).toFloat(), (height - 56).toFloat(), 20f, 20f, innerBorder)

        // Quote Text Paint
        val quotePaint = android.text.TextPaint().apply {
            color = theme.text.toArgb()
            textSize = fontSizeSp * 3.2f
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC)
        }

        val textWidth = width - 220
        val quoteFormatted = "“$quote”"
        val staticLayout = android.text.StaticLayout.Builder.obtain(
            quoteFormatted, 0, quoteFormatted.length, quotePaint, textWidth
        ).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.4f)
            .build()

        val textHeight = staticLayout.height
        val startY = (height - textHeight) / 2f - 90f

        canvas.save()
        canvas.translate(110f, startY.coerceAtLeast(180f))
        staticLayout.draw(canvas)
        canvas.restore()

        // Divider accent
        val divY = startY + textHeight + 70f
        val divPaint = android.graphics.Paint().apply {
            color = theme.accent.toArgb()
            strokeWidth = 4f
        }
        canvas.drawLine((width / 2f) - 60f, divY, (width / 2f) + 60f, divY, divPaint)

        // Book Title & Author
        val titlePaint = android.text.TextPaint().apply {
            color = theme.text.toArgb()
            textSize = 38f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        }
        canvas.drawText(bookTitle.ifBlank { "InkReader" }, width / 2f, divY + 60f, titlePaint)

        if (bookAuthor.isNotBlank()) {
            val authorPaint = android.text.TextPaint().apply {
                color = theme.accent.toArgb()
                textSize = 32f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
            }
            canvas.drawText("— $bookAuthor —", width / 2f, divY + 110f, authorPaint)
        }

        // Watermark
        if (showWatermark) {
            val wmPaint = android.text.TextPaint().apply {
                color = theme.accent.toArgb()
                textSize = 24f
                alpha = 160
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText("Read with InkReader", width / 2f, height - 90f, wmPaint)
        }

        // Save to cache and share
        val cachePath = File(context.cacheDir, "quote_cards").apply { mkdirs() }
        val imageFile = File(cachePath, "quote_${System.currentTimeMillis()}.png")
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            clipData = android.content.ClipData.newRawUri("Quote Card", contentUri)
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, "“$quote” — $bookTitle by $bookAuthor #InkReader")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Quote Card").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Error generating card: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
