package com.fiksr.inkreader.audio

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.fiksr.inkreader.audio.kokoro.KokoroModelManager
import com.fiksr.inkreader.audio.kokoro.KokoroTtsEngine
import com.fiksr.inkreader.audio.kokoro.KokoroVoice
import com.fiksr.inkreader.audio.kokoro.KokoroVoiceCatalog
import com.fiksr.inkreader.audio.kokoro.SentenceSpan
import com.fiksr.inkreader.audio.kokoro.splitIntoSentenceSpans
import com.fiksr.inkreader.audio.piper.PiperModelManager
import com.fiksr.inkreader.audio.piper.PiperTtsEngine
import com.fiksr.inkreader.audio.piper.PiperVoice
import com.fiksr.inkreader.audio.piper.PiperVoiceCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.fiksr.inkreader.audio.supertonic.SupertonicModelManager
import com.fiksr.inkreader.audio.supertonic.SupertonicQuality
import com.fiksr.inkreader.audio.supertonic.SupertonicTtsEngine
import com.fiksr.inkreader.audio.supertonic.SupertonicVoice
import com.fiksr.inkreader.audio.supertonic.SupertonicVoiceCatalog
import java.util.Locale
import java.util.UUID

private const val TAG = "TtsEngine"

enum class TtsEngineType {
    PIPER, SYSTEM, KOKORO, SUPERTONIC
}

data class UnifiedTtsState(
    val isPlaying: Boolean = false,
    val isSynthesizing: Boolean = false,
    val isInitialized: Boolean = false,
    val engineType: TtsEngineType = TtsEngineType.PIPER,
    val currentSentenceIndex: Int = 0,
    val totalSentences: Int = 0,
    val currentSentenceText: String = "",
    val currentStartOffset: Int = -1,
    val currentEndOffset: Int = -1,
    val speed: Float = 1.0f,
    val piperVoice: PiperVoice = PiperVoiceCatalog.DEFAULT_VOICE,
    val kokoroVoice: KokoroVoice = KokoroVoiceCatalog.DEFAULT_VOICE,
    val supertonicVoice: SupertonicVoice = SupertonicVoiceCatalog.DEFAULT_VOICE,
    val supertonicQuality: SupertonicQuality = SupertonicQuality.BALANCED,
    val availableSystemVoices: List<String> = emptyList(),
    val selectedSystemVoice: String = ""
)

class TtsEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val prefs: SharedPreferences = context.getSharedPreferences("inkreader_tts_prefs", Context.MODE_PRIVATE)

    val piperModelManager = PiperModelManager(context)
    val piperEngine = PiperTtsEngine(context, piperModelManager)

    val kokoroModelManager = KokoroModelManager(context)
    val kokoroEngine = KokoroTtsEngine(context, kokoroModelManager)

    val supertonicModelManager = SupertonicModelManager(context)
    val supertonicEngine = SupertonicTtsEngine(context, supertonicModelManager)

    private var systemTts: TextToSpeech? = null
    private var systemSpans: List<SentenceSpan> = emptyList()
    private var onPageEndReachedCallback: (() -> Unit)? = null
    private var isSystemAutoAdvancing = false

    private val _engineType = MutableStateFlow(loadEngineType())
    val engineType: StateFlow<TtsEngineType> = _engineType.asStateFlow()

    private val _state = MutableStateFlow(
        UnifiedTtsState(
            engineType = _engineType.value,
            piperVoice = PiperVoiceCatalog.findById(prefs.getString("piper_voice_id", PiperVoiceCatalog.DEFAULT_VOICE.id) ?: PiperVoiceCatalog.DEFAULT_VOICE.id),
            kokoroVoice = KokoroVoiceCatalog.findByCode(prefs.getString("kokoro_voice_code", "af_heart") ?: "af_heart"),
            supertonicVoice = SupertonicVoiceCatalog.findById(prefs.getInt("supertonic_voice_id", SupertonicVoiceCatalog.DEFAULT_VOICE.id)),
            supertonicQuality = try {
                SupertonicQuality.valueOf(prefs.getString("supertonic_quality", SupertonicQuality.BALANCED.name) ?: SupertonicQuality.BALANCED.name)
            } catch (_: Exception) { SupertonicQuality.BALANCED }
        )
    )
    val state: StateFlow<UnifiedTtsState> = _state.asStateFlow()

    init {
        systemTts = TextToSpeech(context.applicationContext, this)

        // Sync Piper state
        scope.launch {
            piperEngine.state.collect { pState ->
                if (_engineType.value == TtsEngineType.PIPER) {
                    _state.value = _state.value.copy(
                        isPlaying = pState.isPlaying,
                        isSynthesizing = pState.isSynthesizing,
                        isInitialized = pState.isInitialized,
                        currentSentenceIndex = pState.currentSentenceIndex,
                        totalSentences = pState.totalSentences,
                        currentSentenceText = pState.currentSentenceText,
                        currentStartOffset = pState.currentStartOffset,
                        currentEndOffset = pState.currentEndOffset,
                        speed = pState.speed,
                        piperVoice = pState.selectedVoice
                    )
                }
            }
        }

        // Sync Kokoro state
        scope.launch {
            kokoroEngine.state.collect { kState ->
                if (_engineType.value == TtsEngineType.KOKORO) {
                    _state.value = _state.value.copy(
                        isPlaying = kState.isPlaying,
                        isSynthesizing = kState.isSynthesizing,
                        isInitialized = kState.isInitialized,
                        currentSentenceIndex = kState.currentSentenceIndex,
                        totalSentences = kState.totalSentences,
                        currentSentenceText = kState.currentSentenceText,
                        currentStartOffset = kState.currentStartOffset,
                        currentEndOffset = kState.currentEndOffset,
                        speed = kState.speed,
                        kokoroVoice = kState.selectedVoice
                    )
                }
            }
        }

        // Sync Supertonic state
        scope.launch {
            supertonicEngine.state.collect { sState ->
                if (_engineType.value == TtsEngineType.SUPERTONIC) {
                    _state.value = _state.value.copy(
                        isPlaying = sState.isPlaying,
                        isSynthesizing = sState.isSynthesizing,
                        isInitialized = sState.isInitialized,
                        currentSentenceIndex = sState.currentSentenceIndex,
                        totalSentences = sState.totalSentences,
                        currentSentenceText = sState.currentSentenceText,
                        currentStartOffset = sState.currentStartOffset,
                        currentEndOffset = sState.currentEndOffset,
                        speed = sState.speed,
                        supertonicVoice = sState.selectedVoice,
                        supertonicQuality = sState.quality
                    )
                }
            }
        }
    }

    private fun loadEngineType(): TtsEngineType {
        val saved = prefs.getString("tts_engine_type", null)
        return if (saved != null) {
            try {
                TtsEngineType.valueOf(saved)
            } catch (_: Exception) {
                TtsEngineType.PIPER
            }
        } else {
            // Default to PIPER if a voice is ready, else PIPER
            TtsEngineType.PIPER
        }
    }

    fun setEngineType(type: TtsEngineType) {
        if (_engineType.value == type) return
        pause()
        _engineType.value = type
        prefs.edit().putString("tts_engine_type", type.name).apply()
        _state.value = _state.value.copy(engineType = type)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            systemTts?.let { engine ->
                val result = engine.setLanguage(Locale.getDefault())
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    val voices = try {
                        engine.voices?.filter { !it.isNetworkConnectionRequired }?.map { it.name } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (_engineType.value == TtsEngineType.SYSTEM) {
                        _state.value = _state.value.copy(
                            isInitialized = true,
                            availableSystemVoices = voices,
                            selectedSystemVoice = engine.voice?.name ?: ""
                        )
                    }
                }
                setupSystemUtteranceListener()
            }
        }
    }

    private fun setupSystemUtteranceListener() {
        systemTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (_engineType.value == TtsEngineType.SYSTEM) {
                    _state.value = _state.value.copy(isPlaying = true)
                }
            }

            override fun onDone(utteranceId: String?) {
                if (_engineType.value != TtsEngineType.SYSTEM) return
                val nextIdx = _state.value.currentSentenceIndex + 1
                if (nextIdx < systemSpans.size) {
                    val span = systemSpans[nextIdx]
                    _state.value = _state.value.copy(
                        currentSentenceIndex = nextIdx,
                        currentSentenceText = span.text,
                        currentStartOffset = span.startOffset,
                        currentEndOffset = span.endOffset
                    )
                    speakSystemSentence(nextIdx)
                } else {
                    // Reached visual page end
                    isSystemAutoAdvancing = true
                    scope.launch(Dispatchers.Main) {
                        onPageEndReachedCallback?.invoke()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (_engineType.value == TtsEngineType.SYSTEM) {
                    _state.value = _state.value.copy(isPlaying = false)
                }
            }
        })
    }

    fun setContent(
        text: String,
        startIndex: Int = 0,
        nextPageText: String = "",
        onPageEndReached: (() -> Unit)? = null
    ) {
        this.onPageEndReachedCallback = onPageEndReached
        val spans = splitIntoSentenceSpans(text)
        this.systemSpans = spans

        val wasPlaying = _state.value.isPlaying || isSystemAutoAdvancing
        isSystemAutoAdvancing = false

        piperEngine.setContent(text, startIndex, onPageEndReached)
        kokoroEngine.setContent(text, startIndex, nextPageText, onPageEndReached)
        supertonicEngine.setContent(text, startIndex, nextPageText, onPageEndReached)

        val safeIndex = startIndex.coerceIn(0, (spans.size - 1).coerceAtLeast(0))
        val currentSpan = spans.getOrNull(safeIndex)

        if (_engineType.value == TtsEngineType.SYSTEM) {
            _state.value = _state.value.copy(
                currentSentenceIndex = safeIndex,
                totalSentences = spans.size,
                currentSentenceText = currentSpan?.text ?: "",
                currentStartOffset = currentSpan?.startOffset ?: -1,
                currentEndOffset = currentSpan?.endOffset ?: -1
            )
            if (wasPlaying && spans.isNotEmpty()) {
                play()
            }
        }
    }

    fun play() {
        when (_engineType.value) {
            TtsEngineType.SUPERTONIC -> {
                systemTts?.stop()
                piperEngine.pause()
                kokoroEngine.pause()
                supertonicEngine.play()
            }
            TtsEngineType.PIPER -> {
                systemTts?.stop()
                kokoroEngine.pause()
                supertonicEngine.pause()
                piperEngine.play()
            }
            TtsEngineType.KOKORO -> {
                systemTts?.stop()
                piperEngine.pause()
                supertonicEngine.pause()
                kokoroEngine.play()
            }
            TtsEngineType.SYSTEM -> {
                piperEngine.pause()
                kokoroEngine.pause()
                supertonicEngine.pause()
                if (systemSpans.isEmpty()) return
                speakSystemSentence(_state.value.currentSentenceIndex)
            }
        }
    }

    fun pause() {
        when (_engineType.value) {
            TtsEngineType.SUPERTONIC -> supertonicEngine.pause()
            TtsEngineType.PIPER -> piperEngine.pause()
            TtsEngineType.KOKORO -> kokoroEngine.pause()
            TtsEngineType.SYSTEM -> {
                systemTts?.stop()
                isSystemAutoAdvancing = false
                _state.value = _state.value.copy(isPlaying = false)
            }
        }
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun skipNext() {
        when (_engineType.value) {
            TtsEngineType.SUPERTONIC -> supertonicEngine.skipNext()
            TtsEngineType.PIPER -> piperEngine.skipNext()
            TtsEngineType.KOKORO -> kokoroEngine.skipNext()
            TtsEngineType.SYSTEM -> {
                val nextIdx = _state.value.currentSentenceIndex + 1
                if (nextIdx < systemSpans.size) {
                    val span = systemSpans[nextIdx]
                    _state.value = _state.value.copy(
                        currentSentenceIndex = nextIdx,
                        currentSentenceText = span.text,
                        currentStartOffset = span.startOffset,
                        currentEndOffset = span.endOffset
                    )
                    if (_state.value.isPlaying) {
                        speakSystemSentence(nextIdx)
                    }
                } else {
                    onPageEndReachedCallback?.invoke()
                }
            }
        }
    }

    fun skipPrevious() {
        when (_engineType.value) {
            TtsEngineType.SUPERTONIC -> supertonicEngine.skipPrevious()
            TtsEngineType.PIPER -> piperEngine.skipPrevious()
            TtsEngineType.KOKORO -> kokoroEngine.skipPrevious()
            TtsEngineType.SYSTEM -> {
                val prevIdx = (_state.value.currentSentenceIndex - 1).coerceAtLeast(0)
                val span = systemSpans.getOrNull(prevIdx)
                _state.value = _state.value.copy(
                    currentSentenceIndex = prevIdx,
                    currentSentenceText = span?.text ?: "",
                    currentStartOffset = span?.startOffset ?: -1,
                    currentEndOffset = span?.endOffset ?: -1
                )
                if (_state.value.isPlaying) {
                    speakSystemSentence(prevIdx)
                }
            }
        }
    }

    fun setPiperVoice(voice: PiperVoice) {
        prefs.edit().putString("piper_voice_id", voice.id).apply()
        _state.value = _state.value.copy(piperVoice = voice)
        piperEngine.setVoice(voice)
    }

    fun setKokoroVoice(voice: KokoroVoice) {
        prefs.edit().putString("kokoro_voice_code", voice.code).apply()
        _state.value = _state.value.copy(kokoroVoice = voice)
        kokoroEngine.setVoice(voice)
    }

    fun setSupertonicVoice(voice: SupertonicVoice) {
        prefs.edit().putInt("supertonic_voice_id", voice.id).apply()
        _state.value = _state.value.copy(supertonicVoice = voice)
        supertonicEngine.setVoice(voice)
    }

    fun setSupertonicQuality(quality: SupertonicQuality) {
        prefs.edit().putString("supertonic_quality", quality.name).apply()
        _state.value = _state.value.copy(supertonicQuality = quality)
        supertonicEngine.setQuality(quality)
    }

    fun setSpeed(speed: Float) {
        _state.value = _state.value.copy(speed = speed)
        piperEngine.setSpeed(speed)
        kokoroEngine.setSpeed(speed)
        supertonicEngine.setSpeed(speed)
        systemTts?.setSpeechRate(speed)
    }

    private fun speakSystemSentence(index: Int) {
        val span = systemSpans.getOrNull(index) ?: return
        systemTts?.let { engine ->
            val utteranceId = UUID.randomUUID().toString()
            _state.value = _state.value.copy(
                isPlaying = true,
                currentSentenceIndex = index,
                currentSentenceText = span.text,
                currentStartOffset = span.startOffset,
                currentEndOffset = span.endOffset
            )
            engine.speak(span.text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    fun isModelReady(): Boolean {
        return when (_engineType.value) {
            TtsEngineType.SUPERTONIC -> supertonicEngine.isModelReady()
            TtsEngineType.PIPER -> piperEngine.isModelReady()
            TtsEngineType.KOKORO -> kokoroEngine.isModelReady()
            TtsEngineType.SYSTEM -> true
        }
    }

    fun speak(text: String) {
        when (_engineType.value) {
            TtsEngineType.SUPERTONIC -> supertonicEngine.speak(text)
            TtsEngineType.PIPER -> piperEngine.speak(text)
            TtsEngineType.KOKORO -> kokoroEngine.speak(text)
            TtsEngineType.SYSTEM -> {
                systemTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
            }
        }
    }

    fun release() {
        supertonicEngine.release()
        kokoroEngine.release()
        piperEngine.release()
        systemTts?.shutdown()
        systemTts = null
    }
}
