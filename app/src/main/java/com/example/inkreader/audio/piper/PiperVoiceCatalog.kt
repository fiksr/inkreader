package com.example.inkreader.audio.piper

data class PiperVoice(
    val id: String,
    val name: String,
    val speaker: String,
    val language: String,
    val quality: String,
    val downloadUrl: String,
    val archiveFolder: String,
    val modelFileName: String,
    val sampleRate: Int = 22050,
    val sizeMb: Int = 18
)

object PiperVoiceCatalog {
    val LESSAC = PiperVoice(
        id = "en_US-lessac-medium",
        name = "Lessac",
        speaker = "Lessac (US Female - Clear & Balanced)",
        language = "en-US",
        quality = "Medium (22kHz)",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2",
        archiveFolder = "vits-piper-en_US-lessac-medium",
        modelFileName = "en_US-lessac-medium.onnx",
        sizeMb = 18
    )

    val AMY = PiperVoice(
        id = "en_US-amy-medium",
        name = "Amy",
        speaker = "Amy (US Female - Warm & Expressive)",
        language = "en-US",
        quality = "Medium (22kHz)",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-medium.tar.bz2",
        archiveFolder = "vits-piper-en_US-amy-medium",
        modelFileName = "en_US-amy-medium.onnx",
        sizeMb = 18
    )

    val RYAN = PiperVoice(
        id = "en_US-ryan-medium",
        name = "Ryan",
        speaker = "Ryan (US Male - Natural & Authoritative)",
        language = "en-US",
        quality = "Medium (22kHz)",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-ryan-medium.tar.bz2",
        archiveFolder = "vits-piper-en_US-ryan-medium",
        modelFileName = "en_US-ryan-medium.onnx",
        sizeMb = 18
    )

    val BRYCE = PiperVoice(
        id = "en_US-bryce-medium",
        name = "Bryce",
        speaker = "Bryce (US Male - Deep & Narrative)",
        language = "en-US",
        quality = "Medium (22kHz)",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-bryce-medium.tar.bz2",
        archiveFolder = "vits-piper-en_US-bryce-medium",
        modelFileName = "en_US-bryce-medium.onnx",
        sizeMb = 18
    )

    val ALAN = PiperVoice(
        id = "en_GB-alan-medium",
        name = "Alan",
        speaker = "Alan (British Male - Classic & Clear)",
        language = "en-GB",
        quality = "Medium (22kHz)",
        downloadUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_GB-alan-medium.tar.bz2",
        archiveFolder = "vits-piper-en_GB-alan-medium",
        modelFileName = "en_GB-alan-medium.onnx",
        sizeMb = 18
    )

    val ALL_VOICES = listOf(LESSAC, AMY, RYAN, BRYCE, ALAN)
    val DEFAULT_VOICE = LESSAC

    fun findById(id: String): PiperVoice {
        return ALL_VOICES.firstOrNull { it.id == id } ?: DEFAULT_VOICE
    }
}
