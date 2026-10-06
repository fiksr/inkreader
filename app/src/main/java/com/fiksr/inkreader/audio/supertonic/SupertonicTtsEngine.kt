package com.fiksr.inkreader.audio.supertonic

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

private const val TAG = "SupertonicTTS"

data class SupertonicSentenceSpan(
    val index: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int
)

data class SupertonicChunk(
    val index: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int
)

data class SupertonicChunkAudio(
    val index: Int,
    val chunk: SupertonicChunk,
    val samples: FloatArray
)

fun splitSupertonicSentenceSpans(text: String): List<SupertonicSentenceSpan> {
    if (text.isBlank()) return emptyList()
    val list = mutableListOf<SupertonicSentenceSpan>()
    val pattern = Regex("[^.!?\\n\\r]+(?:[.!?]+|[\\n\\r]+|$)")
    val matches = pattern.findAll(text)
    var idx = 0
    for (match in matches) {
        val raw = match.value
        val trimmed = raw.trim()
        if (trimmed.isNotEmpty() && trimmed.any { it.isLetterOrDigit() }) {
            val startInRaw = raw.indexOf(trimmed)
            val startOffset = match.range.first + startInRaw
            val endOffset = startOffset + trimmed.length
            list.add(SupertonicSentenceSpan(idx, trimmed, startOffset, endOffset))
            idx++
        }
    }
    return list
}

fun splitSupertonicChunks(text: String, targetWordsPerChunk: Int = 30): List<SupertonicChunk> {
    if (text.isBlank()) return emptyList()
    val spans = splitSupertonicSentenceSpans(text)
    if (spans.isEmpty()) return emptyList()

    val chunks = mutableListOf<SupertonicChunk>()
    var currentGroup = mutableListOf<SupertonicSentenceSpan>()
    var currentWords = 0

    for (span in spans) {
        val words = span.text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        currentGroup.add(span)
        currentWords += words

        // Keep chunk size balanced for responsive UI highlighting & smooth pre-buffering
        if (currentWords >= targetWordsPerChunk || span.text.endsWith("\n")) {
            val first = currentGroup.first()
            val last = currentGroup.last()
            val chunkText = text.substring(first.startOffset, last.endOffset).trim()
            if (chunkText.isNotBlank()) {
                chunks.add(SupertonicChunk(chunks.size, chunkText, first.startOffset, last.endOffset))
            }
            currentGroup = mutableListOf()
            currentWords = 0
        }
    }

    if (currentGroup.isNotEmpty()) {
        val first = currentGroup.first()
        val last = currentGroup.last()
        val chunkText = text.substring(first.startOffset, last.endOffset).trim()
        if (chunkText.isNotBlank()) {
            chunks.add(SupertonicChunk(chunks.size, chunkText, first.startOffset, last.endOffset))
        }
    }

    return chunks
}

data class SupertonicTtsState(
    val isPlaying: Boolean = false,
    val isSynthesizing: Boolean = false,
    val isInitialized: Boolean = false,
    val currentSentenceIndex: Int = 0,
    val totalSentences: Int = 0,
    val currentSentenceText: String = "",
    val currentStartOffset: Int = -1,
    val currentEndOffset: Int = -1,
    val selectedVoice: SupertonicVoice = SupertonicVoiceCatalog.DEFAULT_VOICE,
    val quality: SupertonicQuality = SupertonicQuality.BALANCED,
    val speed: Float = 1.0f
)

class SupertonicTtsEngine(
    private val context: Context,
    val modelManager: SupertonicModelManager
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var offlineTts: OfflineTts? = null
    private var sampleRate: Int = 24000
    private var audioTrack: AudioTrack? = null
    private var audioChannel: Channel<SupertonicChunkAudio>? = null
    private var playbackJob: Job? = null
    private var producerJob: Job? = null
    private var preCacheJob: Job? = null

    private var ttsChunks: List<SupertonicChunk> = emptyList()
    private var nextPageText: String = ""
    private var onPageEndReachedCallback: (() -> Unit)? = null
    private var isAutoAdvancing = false

    // 40MB In-Memory Audio Sample Cache for instant 0ms repeated or pre-cached chunks
    private val audioCache = object : android.util.LruCache<String, FloatArray>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: FloatArray): Int {
            return value.size * 4 // 4 bytes per Float
        }
    }

    private var naturalSilenceSamples = FloatArray(24000 * 200 / 1000) // 200ms natural pause between chunks

    private val _state = MutableStateFlow(SupertonicTtsState())
    val state: StateFlow<SupertonicTtsState> = _state.asStateFlow()

    // init block removed: initialized lazily on-demand on background thread

    fun initIfModelReady(): Boolean {
        if (offlineTts != null) return true
        val modelDir = modelManager.getModelDirectory() ?: return false

        return try {
            val stConfig = OfflineTtsSupertonicModelConfig(
                durationPredictor = File(modelDir, "duration_predictor.int8.onnx").absolutePath,
                textEncoder = File(modelDir, "text_encoder.int8.onnx").absolutePath,
                vectorEstimator = File(modelDir, "vector_estimator.int8.onnx").absolutePath,
                vocoder = File(modelDir, "vocoder.int8.onnx").absolutePath,
                ttsJson = File(modelDir, "tts.json").absolutePath,
                unicodeIndexer = File(modelDir, "unicode_indexer.bin").absolutePath,
                voiceStyle = File(modelDir, "voice.bin").absolutePath
            )

            val threads = minOf(4, Runtime.getRuntime().availableProcessors())
            val ttsConfig = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    supertonic = stConfig,
                    numThreads = threads,
                    debug = false,
                    provider = "cpu"
                )
            )

            val tts = OfflineTts(assetManager = null, config = ttsConfig)
            this.offlineTts = tts
            this.sampleRate = tts.sampleRate()
            this.naturalSilenceSamples = FloatArray((sampleRate * 0.20f).toInt())

            _state.value = _state.value.copy(isInitialized = true)
            Log.d(TAG, "Supertonic 3 initialized (sampleRate=$sampleRate, threads=$threads)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Supertonic TTS", e)
            _state.value = _state.value.copy(isInitialized = false)
            false
        }
    }

    fun setContent(
        text: String,
        startIndex: Int = 0,
        nextPageText: String = "",
        onPageEndReached: (() -> Unit)? = null
    ) {
        this.onPageEndReachedCallback = onPageEndReached
        this.nextPageText = nextPageText
        val chunks = splitSupertonicChunks(text, targetWordsPerChunk = 30)
        this.ttsChunks = chunks

        val wasPlaying = _state.value.isPlaying || isAutoAdvancing
        isAutoAdvancing = false

        val safeIndex = startIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
        val currentChunk = chunks.getOrNull(safeIndex)

        _state.value = _state.value.copy(
            currentSentenceIndex = safeIndex,
            totalSentences = chunks.size,
            currentSentenceText = currentChunk?.text ?: "",
            currentStartOffset = currentChunk?.startOffset ?: -1,
            currentEndOffset = currentChunk?.endOffset ?: -1
        )

        if (wasPlaying && chunks.isNotEmpty() && isModelReady()) {
            play()
        }
    }

    fun isModelReady(): Boolean {
        return modelManager.isModelReady()
    }

    fun play() {
        if (!isModelReady() || ttsChunks.isEmpty()) return
        stopPlaybackOnly()

        val startIndex = _state.value.currentSentenceIndex.coerceIn(0, ttsChunks.size - 1)
        _state.value = _state.value.copy(isPlaying = true)

        val channel = Channel<SupertonicChunkAudio>(capacity = 6)
        this.audioChannel = channel

        val voiceId = _state.value.selectedVoice.id
        val speed = _state.value.speed
        val steps = _state.value.quality.steps

        Log.d(TAG, "Starting play pipeline from chunk $startIndex/${ttsChunks.size} (voice: $voiceId, speed: $speed, steps: $steps)")

        // 1. PRODUCER COROUTINE: Produces current page chunks into audio channel
        producerJob = scope.launch(Dispatchers.IO) {
            try {
                if (offlineTts == null) {
                    _state.value = _state.value.copy(isSynthesizing = true)
                    initIfModelReady()
                }
                if (offlineTts == null) {
                    _state.value = _state.value.copy(isPlaying = false, isSynthesizing = false)
                    return@launch
                }
                for (idx in startIndex until ttsChunks.size) {
                    if (!isActive || channel.isClosedForSend) break
                    val chunk = ttsChunks[idx]

                    _state.value = _state.value.copy(isSynthesizing = true)
                    val t0 = System.currentTimeMillis()
                    val samples = synthesizeDirect(chunk.text, voiceId, speed, steps)
                    val genTime = System.currentTimeMillis() - t0
                    _state.value = _state.value.copy(isSynthesizing = false)

                    if (samples != null && samples.isNotEmpty() && isActive && !channel.isClosedForSend) {
                        val audioDurationMs = (samples.size.toFloat() / sampleRate.toFloat() * 1000f).toLong()
                        val rtf = if (audioDurationMs > 0) genTime.toFloat() / audioDurationMs else 0f
                        Log.d(TAG, "Chunk $idx rendered in ${genTime}ms (${audioDurationMs}ms audio, RTF ${"%.2f".format(rtf)})")
                        channel.send(SupertonicChunkAudio(idx, chunk, samples))
                    }
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                Log.e(TAG, "Producer error", e)
            } finally {
                channel.close()
                _state.value = _state.value.copy(isSynthesizing = false)
            }

            // 2. BACKGROUND PRE-CACHE NEXT PAGE: Pre-renders Page N+1 into RAM while Page N is playing!
            val nextText = nextPageText
            if (isActive && nextText.isNotBlank()) {
                preCacheJob = scope.launch(Dispatchers.IO) {
                    try {
                        val nextChunks = splitSupertonicChunks(nextText, targetWordsPerChunk = 30)
                        Log.d(TAG, "Pre-caching next page (${nextChunks.size} chunks) in background...")
                        for (nChunk in nextChunks) {
                            if (!isActive) break
                            val cacheKey = makeCacheKey(nChunk.text, voiceId, speed, steps)
                            if (audioCache.get(cacheKey) == null) {
                                synthesizeDirect(nChunk.text, voiceId, speed, steps)
                            }
                        }
                        Log.d(TAG, "Next page fully pre-cached in RAM (0ms page transition ready)!")
                    } catch (_: Exception) {}
                }
            }
        }

        // 3. CONSUMER COROUTINE: Plays audio without interruptions
        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                initAudioTrack()
                val track = audioTrack ?: return@launch

                for (audio in channel) {
                    if (!isActive) break

                    _state.value = _state.value.copy(
                        currentSentenceIndex = audio.index,
                        currentSentenceText = audio.chunk.text,
                        currentStartOffset = audio.chunk.startOffset,
                        currentEndOffset = audio.chunk.endOffset
                    )

                    playSamples(track, audio.samples)

                    if (isActive && naturalSilenceSamples.isNotEmpty()) {
                        playSamples(track, naturalSilenceSamples)
                    }
                }

                // If channel finished normally and we reached the last chunk
                if (isActive && _state.value.currentSentenceIndex >= ttsChunks.size - 1) {
                    delay(250)
                    isAutoAdvancing = true
                    withContext(Dispatchers.Main) {
                        onPageEndReachedCallback?.invoke()
                    }
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                Log.e(TAG, "Playback error", e)
            } finally {
                if (!isAutoAdvancing) {
                    _state.value = _state.value.copy(isPlaying = false, isSynthesizing = false)
                }
                stopAudioTrack()
            }
        }
    }

    private fun makeCacheKey(text: String, voiceId: Int, speed: Float, steps: Int): String {
        return "supertonic_${voiceId}_${steps}_${(speed * 100).toInt()}_${text.hashCode()}_${text.length}"
    }

    private fun synthesizeDirect(text: String, voiceId: Int, speed: Float, steps: Int): FloatArray? {
        val cacheKey = makeCacheKey(text, voiceId, speed, steps)
        val cached = audioCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        val tts = offlineTts ?: run {
            if (initIfModelReady()) offlineTts else null
        } ?: return null

        return try {
            val genConfig = GenerationConfig(
                sid = voiceId,
                speed = speed,
                numSteps = steps
            )
            val generated = tts.generateWithConfig(text, genConfig)
            val samples = generated.samples
            if (samples != null && samples.isNotEmpty()) {
                audioCache.put(cacheKey, samples)
            }
            samples
        } catch (e: Exception) {
            Log.e(TAG, "Supertonic generate exception", e)
            null
        }
    }

    private suspend fun playSamples(track: AudioTrack, samples: FloatArray) {
        var offset = 0
        val total = samples.size
        val chunkSize = 2048

        while (offset < total && currentCoroutineContext().isActive) {
            val count = minOf(chunkSize, total - offset)
            val written = track.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
            if (written < 0) break
            offset += written
        }
    }

    private fun stopPlaybackOnly() {
        preCacheJob?.cancel()
        preCacheJob = null
        audioChannel?.cancel()
        audioChannel = null
        producerJob?.cancel()
        producerJob = null
        playbackJob?.cancel()
        playbackJob = null
        stopAudioTrack()
    }

    fun pause() {
        isAutoAdvancing = false
        stopPlaybackOnly()
        _state.value = _state.value.copy(isPlaying = false, isSynthesizing = false)
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun skipNext() {
        val nextIdx = _state.value.currentSentenceIndex + 1
        if (nextIdx < ttsChunks.size) {
            val wasPlaying = _state.value.isPlaying
            pause()
            val chunk = ttsChunks[nextIdx]
            _state.value = _state.value.copy(
                currentSentenceIndex = nextIdx,
                currentSentenceText = chunk.text,
                currentStartOffset = chunk.startOffset,
                currentEndOffset = chunk.endOffset
            )
            if (wasPlaying) play()
        } else {
            pause()
            onPageEndReachedCallback?.invoke()
        }
    }

    fun skipPrevious() {
        val prevIdx = (_state.value.currentSentenceIndex - 1).coerceAtLeast(0)
        val wasPlaying = _state.value.isPlaying
        pause()
        val chunk = ttsChunks.getOrNull(prevIdx)
        _state.value = _state.value.copy(
            currentSentenceIndex = prevIdx,
            currentSentenceText = chunk?.text ?: "",
            currentStartOffset = chunk?.startOffset ?: -1,
            currentEndOffset = chunk?.endOffset ?: -1
        )
        if (wasPlaying) play()
    }

    fun setVoice(voice: SupertonicVoice) {
        if (_state.value.selectedVoice == voice) return
        val wasPlaying = _state.value.isPlaying
        if (wasPlaying) pause()
        _state.value = _state.value.copy(selectedVoice = voice)
        if (wasPlaying) play()
    }

    fun setQuality(quality: SupertonicQuality) {
        if (_state.value.quality == quality) return
        val wasPlaying = _state.value.isPlaying
        if (wasPlaying) pause()
        _state.value = _state.value.copy(quality = quality)
        if (wasPlaying) play()
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        if (_state.value.speed == clamped) return
        val wasPlaying = _state.value.isPlaying
        if (wasPlaying) pause()
        _state.value = _state.value.copy(speed = clamped)
        if (wasPlaying) play()
    }

    fun speak(text: String) {
        if (text.isBlank() || !isModelReady()) return
        scope.launch(Dispatchers.IO) {
            val samples = synthesizeDirect(
                text,
                _state.value.selectedVoice.id,
                _state.value.speed,
                _state.value.quality.steps
            )
            if (samples != null && samples.isNotEmpty()) {
                val track = createOneShotAudioTrack(samples.size)
                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                track.play()
                val durationMs = (samples.size.toFloat() / sampleRate.toFloat() * 1000f).toLong()
                delay(durationMs)
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            }
        }
    }

    private fun initAudioTrack() {
        val minBufSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val bufSize = maxOf(minBufSize * 2, 4096 * 4)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build().apply {
                play()
            }
    }

    private fun stopAudioTrack() {
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) pause()
                flush()
                stop()
                release()
            }
        } catch (_: Exception) {}
        audioTrack = null
    }

    private fun createOneShotAudioTrack(sampleCount: Int): AudioTrack {
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(sampleCount * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
    }

    fun release() {
        pause()
        offlineTts?.release()
        offlineTts = null
        scope.cancel()
    }
}
