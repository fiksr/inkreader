package com.fiksr.inkreader.audio.kokoro

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

private const val TAG = "KokoroTTS"

data class SentenceSpan(
    val index: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int
)

data class TtsChunk(
    val index: Int,
    val text: String,
    val startOffset: Int,
    val endOffset: Int
)

data class ChunkAudio(
    val index: Int,
    val chunk: TtsChunk,
    val samples: FloatArray
)

fun splitIntoSentenceSpans(text: String): List<SentenceSpan> {
    if (text.isBlank()) return emptyList()
    val list = mutableListOf<SentenceSpan>()
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
            list.add(SentenceSpan(idx, trimmed, startOffset, endOffset))
            idx++
        }
    }
    return list
}

fun splitIntoTtsChunks(text: String, targetWordsPerChunk: Int = 45): List<TtsChunk> {
    if (text.isBlank()) return emptyList()
    val spans = splitIntoSentenceSpans(text)
    if (spans.isEmpty()) return emptyList()

    val chunks = mutableListOf<TtsChunk>()
    var currentGroup = mutableListOf<SentenceSpan>()
    var currentWords = 0

    for (span in spans) {
        val words = span.text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        currentGroup.add(span)
        currentWords += words

        if (currentWords >= targetWordsPerChunk || span.text.endsWith("\n")) {
            val first = currentGroup.first()
            val last = currentGroup.last()
            val chunkText = text.substring(first.startOffset, last.endOffset).trim()
            if (chunkText.isNotBlank()) {
                chunks.add(TtsChunk(chunks.size, chunkText, first.startOffset, last.endOffset))
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
            chunks.add(TtsChunk(chunks.size, chunkText, first.startOffset, last.endOffset))
        }
    }

    return chunks
}

data class KokoroTtsState(
    val isPlaying: Boolean = false,
    val isSynthesizing: Boolean = false,
    val isInitialized: Boolean = false,
    val currentSentenceIndex: Int = 0,
    val totalSentences: Int = 0,
    val currentSentenceText: String = "",
    val currentStartOffset: Int = -1,
    val currentEndOffset: Int = -1,
    val selectedVoice: KokoroVoice = KokoroVoiceCatalog.DEFAULT_VOICE,
    val speed: Float = 1.0f
)

class KokoroTtsEngine(
    private val context: Context,
    val modelManager: KokoroModelManager
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var offlineTts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private var audioChannel: Channel<ChunkAudio>? = null
    private var playbackJob: Job? = null
    private var producerJob: Job? = null

    private var ttsChunks: List<TtsChunk> = emptyList()
    private var nextPageText: String = ""
    private var onPageEndReachedCallback: (() -> Unit)? = null
    private var isAutoAdvancing = false

    // 40MB In-Memory Audio Sample Cache for instant gapless page transitions
    private val audioCache = object : android.util.LruCache<String, FloatArray>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: FloatArray): Int {
            return value.size * 4 // 4 bytes per Float
        }
    }

    private val naturalSilenceSamples = FloatArray(24000 * 250 / 1000) // 250ms silence

    private val _state = MutableStateFlow(KokoroTtsState())
    val state: StateFlow<KokoroTtsState> = _state.asStateFlow()

    // init block removed: initialized lazily on-demand on background thread

    fun initIfModelReady(): Boolean {
        if (offlineTts != null) return true
        val modelDir = modelManager.getModelDirectory() ?: return false

        return try {
            val dictFile = File(modelDir, "dict")
            val modelFile = File(modelDir, "model.onnx").let {
                if (it.exists() && it.length() > 50_000_000) it else File(modelDir, "model.int8.onnx")
            }
            Log.d(TAG, "Loading Kokoro model file: ${modelFile.name}")
            val kokoroConfig = OfflineTtsKokoroModelConfig(
                model = modelFile.absolutePath,
                voices = File(modelDir, "voices.bin").absolutePath,
                tokens = File(modelDir, "tokens.txt").absolutePath,
                dataDir = File(modelDir, "espeak-ng-data").absolutePath,
                lexicon = File(modelDir, "lexicon-us-en.txt").let { if (it.exists()) it.absolutePath else "" },
                dictDir = if (dictFile.exists()) dictFile.absolutePath else ""
            )

            val threads = minOf(4, Runtime.getRuntime().availableProcessors())
            val ttsConfig = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    kokoro = kokoroConfig,
                    numThreads = threads,
                    debug = false,
                    provider = "cpu"
                )
            )

            offlineTts = OfflineTts(assetManager = null, config = ttsConfig)
            _state.value = _state.value.copy(isInitialized = true)
            Log.d(TAG, "Kokoro OfflineTts initialized successfully with $threads threads.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Kokoro TTS", e)
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
        val chunks = splitIntoTtsChunks(text, targetWordsPerChunk = 45)
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

        val channel = Channel<ChunkAudio>(capacity = 4)
        this.audioChannel = channel

        val voiceId = _state.value.selectedVoice.id
        val speed = _state.value.speed

        Log.d(TAG, "Starting play pipeline from chunk $startIndex/${ttsChunks.size} (voice: $voiceId, speed: $speed)")

        // 1. DEDICATED PRODUCER COROUTINE (Batched paragraph chunks + background next-page pre-cache)
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
                // A. Produce chunks for current page
                for (idx in startIndex until ttsChunks.size) {
                    if (!isActive || channel.isClosedForSend) break
                    val chunk = ttsChunks[idx]

                    _state.value = _state.value.copy(isSynthesizing = true)
                    val t0 = System.currentTimeMillis()
                    val samples = synthesizeDirect(chunk.text, voiceId, speed)
                    val genTime = System.currentTimeMillis() - t0
                    _state.value = _state.value.copy(isSynthesizing = false)

                    if (samples != null && samples.isNotEmpty() && isActive && !channel.isClosedForSend) {
                        val audioDurationMs = (samples.size.toFloat() / 24000f * 1000f).toLong()
                        val rtf = genTime.toFloat() / audioDurationMs
                        Log.d(TAG, "Produced chunk $idx (${chunk.text.length} chars) in ${genTime}ms. Audio: ${audioDurationMs}ms (RTF: ${String.format("%.2f", rtf)})")
                        channel.send(ChunkAudio(idx, chunk, samples))
                    } else if (samples == null) {
                        Log.e(TAG, "Synthesis failed for chunk $idx: '${chunk.text}'")
                    }
                }

                // B. PRE-CACHE NEXT PAGE IN ADVANCE (While current page is playing!)
                val nextText = nextPageText
                if (isActive && nextText.isNotBlank()) {
                    val nextChunks = splitIntoTtsChunks(nextText, targetWordsPerChunk = 45)
                    Log.d(TAG, "Starting background pre-caching for next page (${nextChunks.size} chunks)...")
                    for (nIdx in nextChunks.indices) {
                        if (!isActive) break
                        val nChunk = nextChunks[nIdx]
                        val cacheKey = makeCacheKey(nChunk.text, voiceId, speed)
                        if (audioCache.get(cacheKey) == null) {
                            val t0 = System.currentTimeMillis()
                            synthesizeDirect(nChunk.text, voiceId, speed)
                            Log.d(TAG, "Pre-cached next page chunk $nIdx in ${System.currentTimeMillis() - t0}ms")
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Stopped/cancelled normally
            } catch (e: Exception) {
                Log.e(TAG, "Producer error", e)
            } finally {
                channel.close()
                _state.value = _state.value.copy(isSynthesizing = false)
            }
        }

        // 2. PLAYBACK CONSUMER COROUTINE
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

                    playSentenceSamples(track, audio.samples)

                    if (isActive) {
                        playSentenceSamples(track, naturalSilenceSamples)
                    }
                }

                // If channel finished normally and we reached the last chunk
                if (isActive && _state.value.currentSentenceIndex >= ttsChunks.size - 1) {
                    delay(300)
                    isAutoAdvancing = true
                    withContext(Dispatchers.Main) {
                        onPageEndReachedCallback?.invoke()
                    }
                }
            } catch (e: CancellationException) {
                // Playback paused
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

    private fun makeCacheKey(text: String, voiceId: Int, speed: Float): String {
        return "kokoro_${voiceId}_${(speed * 100).toInt()}_${text.hashCode()}_${text.length}"
    }

    private fun synthesizeDirect(text: String, voiceId: Int, speed: Float): FloatArray? {
        val cacheKey = makeCacheKey(text, voiceId, speed)
        val cached = audioCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        val tts = offlineTts ?: run {
            if (initIfModelReady()) offlineTts else null
        } ?: return null

        return try {
            val generated = tts.generate(
                text = text,
                sid = voiceId,
                speed = speed
            )
            val samples = generated.samples
            if (samples != null && samples.isNotEmpty()) {
                audioCache.put(cacheKey, samples)
            }
            samples
        } catch (e: Exception) {
            Log.e(TAG, "TTS generate exception", e)
            null
        }
    }

    private suspend fun playSentenceSamples(track: AudioTrack, samples: FloatArray) {
        var offset = 0
        val total = samples.size
        val chunkSize = 2048

        while (offset < total && currentCoroutineContext().isActive) {
            val count = minOf(chunkSize, total - offset)
            val written = track.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
            if (written < 0) {
                break
            }
            offset += written
        }
    }

    private fun stopPlaybackOnly() {
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
        if (_state.value.isPlaying) {
            pause()
        } else {
            play()
        }
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

    fun setVoice(voice: KokoroVoice) {
        if (_state.value.selectedVoice == voice) return
        val wasPlaying = _state.value.isPlaying
        if (wasPlaying) pause()
        _state.value = _state.value.copy(selectedVoice = voice)
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
            val samples = synthesizeDirect(text, _state.value.selectedVoice.id, _state.value.speed)
            if (samples != null && samples.isNotEmpty()) {
                val track = createOneShotAudioTrack(samples.size)
                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                track.play()
                val durationMs = (samples.size.toFloat() / 24000f * 1000f).toLong()
                delay(durationMs)
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            }
        }
    }

    private fun initAudioTrack() {
        val sampleRate = 24000
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
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    pause()
                }
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
                    .setSampleRate(24000)
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
