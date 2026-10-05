package com.example.inkreader.audio.piper

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.inkreader.audio.kokoro.SentenceSpan
import com.example.inkreader.audio.kokoro.splitIntoSentenceSpans
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

private const val TAG = "PiperTTS"

data class PiperChunkAudio(
    val index: Int,
    val span: SentenceSpan,
    val samples: FloatArray,
    val sampleRate: Int
)

data class PiperTtsState(
    val isPlaying: Boolean = false,
    val isSynthesizing: Boolean = false,
    val isInitialized: Boolean = false,
    val currentSentenceIndex: Int = 0,
    val totalSentences: Int = 0,
    val currentSentenceText: String = "",
    val currentStartOffset: Int = -1,
    val currentEndOffset: Int = -1,
    val selectedVoice: PiperVoice = PiperVoiceCatalog.DEFAULT_VOICE,
    val speed: Float = 1.0f
)

class PiperTtsEngine(
    private val context: Context,
    val modelManager: PiperModelManager
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var offlineTts: OfflineTts? = null
    private var activeVoice: PiperVoice? = null
    private var audioTrack: AudioTrack? = null
    private var currentTrackSampleRate: Int = 22050
    private var audioChannel: Channel<PiperChunkAudio>? = null
    private var playbackJob: Job? = null
    private var producerJob: Job? = null

    private var sentenceSpans: List<SentenceSpan> = emptyList()
    private var onPageEndReachedCallback: (() -> Unit)? = null
    private var isAutoAdvancing = false

    private val _state = MutableStateFlow(PiperTtsState())
    val state: StateFlow<PiperTtsState> = _state.asStateFlow()

    // init block removed: initialized lazily on-demand on background thread

    fun initIfModelReady(voiceToLoad: PiperVoice? = null): Boolean {
        val targetVoice = voiceToLoad ?: _state.value.selectedVoice
        if (offlineTts != null && activeVoice?.id == targetVoice.id) return true

        if (!modelManager.isVoiceReady(targetVoice)) {
            Log.w(TAG, "Piper voice '${targetVoice.name}' is not downloaded yet.")
            _state.value = _state.value.copy(isInitialized = false)
            return false
        }

        return try {
            val dir = modelManager.getVoiceDir(targetVoice)
            val modelFile = File(dir, targetVoice.modelFileName)
            val tokensFile = File(dir, "tokens.txt")
            val espeakDir = File(dir, "espeak-ng-data")

            val vitsConfig = OfflineTtsVitsModelConfig(
                model = modelFile.absolutePath,
                tokens = tokensFile.absolutePath,
                dataDir = espeakDir.absolutePath,
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f / _state.value.speed.coerceIn(0.5f, 2.0f)
            )

            val threads = minOf(2, Runtime.getRuntime().availableProcessors())
            val ttsConfig = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = vitsConfig,
                    numThreads = threads,
                    debug = false,
                    provider = "cpu"
                )
            )

            offlineTts?.release()
            offlineTts = OfflineTts(assetManager = null, config = ttsConfig)
            activeVoice = targetVoice
            _state.value = _state.value.copy(
                isInitialized = true,
                selectedVoice = targetVoice
            )
            Log.d(TAG, "Piper OfflineTts initialized successfully for voice '${targetVoice.name}' (${offlineTts?.sampleRate()}Hz).")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Piper TTS", e)
            _state.value = _state.value.copy(isInitialized = false)
            false
        }
    }

    fun setContent(text: String, startIndex: Int = 0, onPageEndReached: (() -> Unit)? = null) {
        this.onPageEndReachedCallback = onPageEndReached
        val spans = splitIntoSentenceSpans(text)
        this.sentenceSpans = spans

        val wasPlaying = _state.value.isPlaying || isAutoAdvancing
        isAutoAdvancing = false

        val safeIndex = startIndex.coerceIn(0, (spans.size - 1).coerceAtLeast(0))
        val currentSpan = spans.getOrNull(safeIndex)

        _state.value = _state.value.copy(
            currentSentenceIndex = safeIndex,
            totalSentences = spans.size,
            currentSentenceText = currentSpan?.text ?: "",
            currentStartOffset = currentSpan?.startOffset ?: -1,
            currentEndOffset = currentSpan?.endOffset ?: -1
        )

        if (wasPlaying && spans.isNotEmpty() && isModelReady()) {
            play()
        }
    }

    fun isModelReady(): Boolean {
        return modelManager.isVoiceReady(_state.value.selectedVoice)
    }

    fun play() {
        if (!isModelReady() || sentenceSpans.isEmpty()) return
        stopPlaybackOnly()

        val startIndex = _state.value.currentSentenceIndex.coerceIn(0, sentenceSpans.size - 1)
        _state.value = _state.value.copy(isPlaying = true)

        val channel = Channel<PiperChunkAudio>(capacity = 4)
        this.audioChannel = channel

        val speed = _state.value.speed

        Log.d(TAG, "Starting Piper play pipeline from sentence $startIndex/${sentenceSpans.size} (speed: $speed)")

        // 1. DEDICATED PRODUCER COROUTINE (Piper generates in ~50ms per sentence -> RTF 0.02)
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
                for (idx in startIndex until sentenceSpans.size) {
                    if (!isActive || channel.isClosedForSend) break
                    val span = sentenceSpans[idx]

                    _state.value = _state.value.copy(isSynthesizing = true)
                    val t0 = System.currentTimeMillis()
                    val audio = synthesizeDirect(span.text, speed)
                    val genTime = System.currentTimeMillis() - t0
                    _state.value = _state.value.copy(isSynthesizing = false)

                    if (audio != null && audio.samples.isNotEmpty() && isActive && !channel.isClosedForSend) {
                        val durationMs = (audio.samples.size.toFloat() / audio.sampleRate * 1000f).toLong()
                        val rtf = if (durationMs > 0) genTime.toFloat() / durationMs else 0f
                        Log.d(TAG, "Piper generated sentence $idx (${span.text.length} chars) in ${genTime}ms. Audio: ${durationMs}ms (RTF: ${String.format("%.3f", rtf)})")
                        channel.send(PiperChunkAudio(idx, span, audio.samples, audio.sampleRate))
                    } else if (audio == null) {
                        Log.e(TAG, "Piper synthesis failed for sentence $idx: '${span.text}'")
                    }
                }
            } catch (e: CancellationException) {
                // Normal cancel
            } catch (e: Exception) {
                Log.e(TAG, "Piper producer error", e)
            } finally {
                channel.close()
                _state.value = _state.value.copy(isSynthesizing = false)
            }
        }

        // 2. PLAYBACK CONSUMER COROUTINE
        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                val sampleRate = offlineTts?.sampleRate() ?: 22050
                initAudioTrack(sampleRate)
                val track = audioTrack ?: return@launch
                val silenceSamples = FloatArray(sampleRate * 180 / 1000) // 180ms natural pause

                for (audio in channel) {
                    if (!isActive) break

                    _state.value = _state.value.copy(
                        currentSentenceIndex = audio.index,
                        currentSentenceText = audio.span.text,
                        currentStartOffset = audio.span.startOffset,
                        currentEndOffset = audio.span.endOffset
                    )

                    playSentenceSamples(track, audio.samples)

                    if (isActive) {
                        playSentenceSamples(track, silenceSamples)
                    }
                }

                // If playback finished normally and we completed the last sentence of the page
                if (isActive && _state.value.currentSentenceIndex >= sentenceSpans.size - 1) {
                    delay(200)
                    isAutoAdvancing = true
                    withContext(Dispatchers.Main) {
                        onPageEndReachedCallback?.invoke()
                    }
                }
            } catch (e: CancellationException) {
                // Paused
            } catch (e: Exception) {
                Log.e(TAG, "Piper playback error", e)
            } finally {
                if (!isAutoAdvancing) {
                    _state.value = _state.value.copy(isPlaying = false, isSynthesizing = false)
                }
                stopAudioTrack()
            }
        }
    }

    private class PiperAudioData(val samples: FloatArray, val sampleRate: Int)

    private fun synthesizeDirect(text: String, speed: Float): PiperAudioData? {
        val tts = offlineTts ?: run {
            if (initIfModelReady()) offlineTts else null
        } ?: return null

        return try {
            val generated = tts.generate(
                text = text,
                sid = 0,
                speed = speed
            )
            PiperAudioData(generated.samples, generated.sampleRate)
        } catch (e: Exception) {
            Log.e(TAG, "Piper TTS generate error", e)
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
            if (written < 0) break
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
        if (nextIdx < sentenceSpans.size) {
            val wasPlaying = _state.value.isPlaying
            pause()
            val span = sentenceSpans[nextIdx]
            _state.value = _state.value.copy(
                currentSentenceIndex = nextIdx,
                currentSentenceText = span.text,
                currentStartOffset = span.startOffset,
                currentEndOffset = span.endOffset
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
        val span = sentenceSpans.getOrNull(prevIdx)
        _state.value = _state.value.copy(
            currentSentenceIndex = prevIdx,
            currentSentenceText = span?.text ?: "",
            currentStartOffset = span?.startOffset ?: -1,
            currentEndOffset = span?.endOffset ?: -1
        )
        if (wasPlaying) play()
    }

    fun speak(text: String) {
        if (text.isBlank() || !isModelReady()) return
        scope.launch(Dispatchers.IO) {
            val audio = synthesizeDirect(text, _state.value.speed)
            if (audio != null && audio.samples.isNotEmpty()) {
                val track = createOneShotAudioTrack(audio.samples.size, audio.sampleRate)
                track.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
                track.play()
                val durationMs = (audio.samples.size.toFloat() / audio.sampleRate * 1000f).toLong()
                delay(durationMs)
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            }
        }
    }

    private fun createOneShotAudioTrack(sampleCount: Int, sampleRate: Int): AudioTrack {
        val minBufSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val bufSize = maxOf(minBufSize, sampleCount * 4)

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
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun setVoice(voice: PiperVoice) {
        if (_state.value.selectedVoice.id == voice.id) return
        val wasPlaying = _state.value.isPlaying
        if (wasPlaying) pause()
        _state.value = _state.value.copy(selectedVoice = voice)
        initIfModelReady(voice)
        if (wasPlaying) play()
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        if (_state.value.speed == clamped) return
        val wasPlaying = _state.value.isPlaying
        if (wasPlaying) pause()
        _state.value = _state.value.copy(speed = clamped)
        // Re-init with new speed length scale
        initIfModelReady(_state.value.selectedVoice)
        if (wasPlaying) play()
    }

    private fun initAudioTrack(sampleRate: Int) {
        if (audioTrack != null && currentTrackSampleRate == sampleRate) {
            try { audioTrack?.play() } catch (_: Exception) {}
            return
        }
        stopAudioTrack()
        currentTrackSampleRate = sampleRate
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
                    stop()
                }
                release()
            }
        } catch (_: Exception) {}
        audioTrack = null
    }

    fun release() {
        pause()
        offlineTts?.release()
        offlineTts = null
    }
}
