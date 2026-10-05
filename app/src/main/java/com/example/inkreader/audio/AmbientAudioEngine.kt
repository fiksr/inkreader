package com.example.inkreader.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.inkreader.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class AmbientSoundType(val id: String, val displayName: String, val iconLabel: String, val rawResId: Int?) {
    OFF("off", "Off", "🔇", null),
    BROWN_NOISE("brown", "Deep Brown Noise", "🎧", R.raw.ambient_brown_noise),
    RAIN("rain", "Gentle Rain", "🌧️", R.raw.ambient_rain),
    FIREPLACE("fireplace", "Cozy Fireplace", "🪵", R.raw.ambient_fireplace),
    FOREST_WIND("forest", "Forest Breeze", "🍃", R.raw.ambient_forest_wind),
    OCEAN_WAVES("ocean", "Ocean Waves", "🌊", R.raw.ambient_ocean_waves)
}

class AmbientAudioEngine(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var volume = 0.5f

    private val _currentSound = MutableStateFlow(AmbientSoundType.OFF)
    val currentSound: StateFlow<AmbientSoundType> = _currentSound.asStateFlow()

    private val _activeCustomSoundId = MutableStateFlow<String?>(null)
    val activeCustomSoundId: StateFlow<String?> = _activeCustomSoundId.asStateFlow()

    private val _activeSoundLabel = MutableStateFlow("Off")
    val activeSoundLabel: StateFlow<String> = _activeSoundLabel.asStateFlow()

    private val _activeSoundIcon = MutableStateFlow("🔇")
    val activeSoundIcon: StateFlow<String> = _activeSoundIcon.asStateFlow()

    private val _currentVolume = MutableStateFlow(0.5f)
    val currentVolume: StateFlow<Float> = _currentVolume.asStateFlow()

    private val _sleepTimerMinutesLeft = MutableStateFlow<Int?>(null)
    val sleepTimerMinutesLeft: StateFlow<Int?> = _sleepTimerMinutesLeft.asStateFlow()

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun setSound(type: AmbientSoundType) {
        if (type == _currentSound.value && _activeCustomSoundId.value == null && mediaPlayer?.isPlaying == true) return

        stopAudio()
        _currentSound.value = type
        _activeCustomSoundId.value = null
        _activeSoundLabel.value = type.displayName
        _activeSoundIcon.value = type.iconLabel

        if (type == AmbientSoundType.OFF || type.rawResId == null) {
            return
        }

        try {
            mediaPlayer = MediaPlayer.create(context, type.rawResId)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                setVolume(volume, volume)
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playCustomSound(item: CustomSoundItem) {
        if (_activeCustomSoundId.value == item.id && mediaPlayer?.isPlaying == true) return

        stopAudio()
        _currentSound.value = AmbientSoundType.OFF
        _activeCustomSoundId.value = item.id
        _activeSoundLabel.value = item.name
        _activeSoundIcon.value = "🎵"

        try {
            val file = File(item.filePath)
            if (!file.exists()) return

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(file.absolutePath)
                isLooping = true
                prepare()
                setVolume(volume, volume)
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        _currentVolume.value = volume
        try {
            mediaPlayer?.setVolume(volume, volume)
        } catch (_: Exception) {}
    }

    fun setSleepTimer(minutes: Int?) {
        timerJob?.cancel()
        _sleepTimerMinutesLeft.value = minutes

        if (minutes == null || minutes <= 0) return

        timerJob = scope.launch {
            var remaining = minutes
            while (remaining > 0 && isActive) {
                _sleepTimerMinutesLeft.value = remaining
                delay(60_000L)
                remaining--
            }
            _sleepTimerMinutesLeft.value = null
            setSound(AmbientSoundType.OFF)
        }
    }

    fun cancelSleepTimer() {
        setSleepTimer(null)
    }

    fun isAudioActive(): Boolean {
        return _currentSound.value != AmbientSoundType.OFF || _activeCustomSoundId.value != null
    }

    private fun stopAudio() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun release() {
        timerJob?.cancel()
        stopAudio()
        scope.cancel()
    }
}
