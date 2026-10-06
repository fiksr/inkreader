package com.fiksr.inkreader.theme

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class FontItem(
    val id: String,
    val name: String,
    val fontFamily: FontFamily,
    val isCustom: Boolean = false,
    val filePath: String? = null
)

object CustomFontManager {

    private val customFontsList = mutableListOf<FontItem>()

    fun getAvailableFonts(context: Context): List<FontItem> {
        val list = mutableListOf<FontItem>()

        // 1. Built-in Popular Typography Presets
        list.add(FontItem("serif", "Literata Serif (Default)", FontFamily.Serif))
        list.add(FontItem("sans", "Modern Sans", FontFamily.SansSerif))
        list.add(FontItem("mono", "Typewriter Mono", FontFamily.Monospace))
        list.add(FontItem("cursive", "Humanist / Script", FontFamily.Cursive))

        // 2. Load User Installed Custom Fonts (.ttf / .otf)
        val fontsDir = File(context.filesDir, "custom_fonts")
        if (fontsDir.exists() && fontsDir.isDirectory) {
            val fontFiles = fontsDir.listFiles { f ->
                f.extension.equals("ttf", ignoreCase = true) || f.extension.equals("otf", ignoreCase = true)
            } ?: emptyArray()

            for (file in fontFiles) {
                try {
                    val typeface = Typeface.createFromFile(file)
                    val cleanName = file.nameWithoutExtension.replace("_", " ").replace("-", " ")
                    list.add(
                        FontItem(
                            id = "custom_${file.name}",
                            name = cleanName,
                            fontFamily = FontFamily(typeface),
                            isCustom = true,
                            filePath = file.absolutePath
                        )
                    )
                } catch (_: Exception) {}
            }
        }

        return list
    }

    fun installFontFromUri(context: Context, uri: Uri): FontItem? {
        try {
            val fontsDir = File(context.filesDir, "custom_fonts")
            if (!fontsDir.exists()) fontsDir.mkdirs()

            // Resolve file name
            var fileName = "font_${System.currentTimeMillis()}.ttf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val rawName = cursor.getString(nameIndex)
                    if (!rawName.isNullOrBlank()) fileName = rawName
                }
            }

            val targetFile = File(fontsDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            val typeface = Typeface.createFromFile(targetFile)
            val cleanName = targetFile.nameWithoutExtension.replace("_", " ").replace("-", " ")
            return FontItem(
                id = "custom_${targetFile.name}",
                name = cleanName,
                fontFamily = FontFamily(typeface),
                isCustom = true,
                filePath = targetFile.absolutePath
            )
        } catch (_: Exception) {
            return null
        }
    }

    fun deleteCustomFont(context: Context, fontId: String) {
        val fontsDir = File(context.filesDir, "custom_fonts")
        if (fontsDir.exists() && fontsDir.isDirectory) {
            val fileName = fontId.removePrefix("custom_")
            val target = File(fontsDir, fileName)
            if (target.exists()) {
                target.delete()
            }
        }
    }

    fun resolveFontFamily(context: Context, fontId: String): FontFamily {
        if (fontId.startsWith("custom_")) {
            val fileName = fontId.removePrefix("custom_")
            val target = File(File(context.filesDir, "custom_fonts"), fileName)
            if (target.exists()) {
                try {
                    val tf = Typeface.createFromFile(target)
                    return FontFamily(tf)
                } catch (_: Exception) {}
            }
        }

        return when (fontId.lowercase()) {
            "sans", "sans_serif" -> FontFamily.SansSerif
            "mono", "monospace" -> FontFamily.Monospace
            "cursive" -> FontFamily.Cursive
            else -> FontFamily.Serif
        }
    }
}
