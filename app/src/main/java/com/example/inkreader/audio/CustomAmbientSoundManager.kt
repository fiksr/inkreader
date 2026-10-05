package com.example.inkreader.audio

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class CustomSoundItem(
    val id: String,
    val name: String,
    val filePath: String,
    val timestamp: Long = System.currentTimeMillis()
)

object CustomAmbientSoundManager {
    private const val PREFS_KEY = "custom_ambient_sounds_meta"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences("custom_ambient_prefs", Context.MODE_PRIVATE)
    }

    fun getSoundsDir(context: Context): File {
        val dir = File(context.filesDir, "custom_ambient_sounds")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun loadCustomSounds(context: Context): List<CustomSoundItem> {
        val prefs = getPrefs(context)
        val json = prefs.getString(PREFS_KEY, null) ?: return emptyList()
        val list = mutableListOf<CustomSoundItem>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val path = obj.getString("filePath")
                val file = File(path)
                if (file.exists()) {
                    list.add(
                        CustomSoundItem(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            filePath = path,
                            timestamp = obj.optLong("timestamp", 0L)
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveCustomSounds(context: Context, list: List<CustomSoundItem>) {
        val prefs = getPrefs(context)
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("filePath", item.filePath)
                put("timestamp", item.timestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString(PREFS_KEY, arr.toString()).apply()
    }

    fun importSound(context: Context, uri: Uri): CustomSoundItem? {
        try {
            val resolver = context.contentResolver
            var displayName = "ambient_sound_${System.currentTimeMillis()}"

            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) {
                        displayName = name
                    }
                }
            }

            // Extract friendly title without extension
            val dotIdx = displayName.lastIndexOf('.')
            val cleanTitle = if (dotIdx > 0) displayName.substring(0, dotIdx) else displayName
            val ext = if (dotIdx > 0) displayName.substring(dotIdx) else ".mp3"

            val soundsDir = getSoundsDir(context)
            val soundId = UUID.randomUUID().toString()
            val destFile = File(soundsDir, "sound_${soundId}${ext}")

            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (destFile.exists() && destFile.length() > 0) {
                val newItem = CustomSoundItem(
                    id = soundId,
                    name = cleanTitle,
                    filePath = destFile.absolutePath,
                    timestamp = System.currentTimeMillis()
                )
                val currentList = loadCustomSounds(context).toMutableList()
                currentList.add(newItem)
                saveCustomSounds(context, currentList)
                return newItem
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun deleteSound(context: Context, soundId: String) {
        val currentList = loadCustomSounds(context).toMutableList()
        val item = currentList.find { it.id == soundId }
        if (item != null) {
            try {
                File(item.filePath).delete()
            } catch (_: Exception) {}
            currentList.remove(item)
            saveCustomSounds(context, currentList)
        }
    }
}
