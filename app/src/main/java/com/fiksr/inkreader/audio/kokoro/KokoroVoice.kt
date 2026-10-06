package com.fiksr.inkreader.audio.kokoro

data class KokoroVoice(
    val id: Int,
    val code: String,
    val name: String,
    val gender: String,
    val accent: String,
    val emoji: String,
    val description: String
) {
    val displayName: String
        get() = "$emoji $name ($accent)"
}

object KokoroVoiceCatalog {
    val VOICES: List<KokoroVoice> = listOf(
        // Top American Female Voices
        KokoroVoice(
            id = 3,
            code = "af_heart",
            name = "Heart",
            gender = "Female",
            accent = "US",
            emoji = "❤️",
            description = "Studio audiobook narrator, warm, immersive & soothing"
        ),
        KokoroVoice(
            id = 2,
            code = "af_bella",
            name = "Bella",
            gender = "Female",
            accent = "US",
            emoji = "🌟",
            description = "Expressive, bright and engaging reading style"
        ),
        KokoroVoice(
            id = 6,
            code = "af_nicole",
            name = "Nicole",
            gender = "Female",
            accent = "US",
            emoji = "📖",
            description = "Clear, articulate and balanced literary tone"
        ),
        KokoroVoice(
            id = 9,
            code = "af_sarah",
            name = "Sarah",
            gender = "Female",
            accent = "US",
            emoji = "🌿",
            description = "Soft, calm and melodic voice"
        ),
        KokoroVoice(
            id = 10,
            code = "af_sky",
            name = "Sky",
            gender = "Female",
            accent = "US",
            emoji = "✨",
            description = "Crisp, contemporary and modern"
        ),
        KokoroVoice(
            id = 0,
            code = "af_alloy",
            name = "Alloy",
            gender = "Female",
            accent = "US",
            emoji = "🎙️",
            description = "Neutral, focused narrative voice"
        ),

        // American Male Voices
        KokoroVoice(
            id = 11,
            code = "am_adam",
            name = "Adam",
            gender = "Male",
            accent = "US",
            emoji = "🎩",
            description = "Deep, rich, authoritative classic male narrator"
        ),
        KokoroVoice(
            id = 16,
            code = "am_michael",
            name = "Michael",
            gender = "Male",
            accent = "US",
            emoji = "📻",
            description = "Warm, natural conversational audiobook style"
        ),
        KokoroVoice(
            id = 13,
            code = "am_eric",
            name = "Eric",
            gender = "Male",
            accent = "US",
            emoji = "🌲",
            description = "Resonant, deep timbre"
        ),
        KokoroVoice(
            id = 12,
            code = "am_echo",
            name = "Echo",
            gender = "Male",
            accent = "US",
            emoji = "🌊",
            description = "Smooth and measured tempo"
        ),

        // British Accents
        KokoroVoice(
            id = 21,
            code = "bf_emma",
            name = "Emma",
            gender = "Female",
            accent = "GB",
            emoji = "🇬🇧",
            description = "Classic British literature narrator, elegant & poised"
        ),
        KokoroVoice(
            id = 22,
            code = "bf_isabella",
            name = "Isabella",
            gender = "Female",
            accent = "GB",
            emoji = "👑",
            description = "Refined British RP accent"
        ),
        KokoroVoice(
            id = 26,
            code = "bm_george",
            name = "George",
            gender = "Male",
            accent = "GB",
            emoji = "☕",
            description = "Distinguished British gentleman voice"
        ),
        KokoroVoice(
            id = 27,
            code = "bm_lewis",
            name = "Lewis",
            gender = "Male",
            accent = "GB",
            emoji = "🏰",
            description = "Dramatic, documentary-grade British male"
        ),

        // International Voices
        KokoroVoice(
            id = 28,
            code = "ef_dora",
            name = "Dora",
            gender = "Female",
            accent = "ES",
            emoji = "🇪🇸",
            description = "Spanish expressive female voice"
        ),
        KokoroVoice(
            id = 29,
            code = "em_alex",
            name = "Alex",
            gender = "Male",
            accent = "ES",
            emoji = "🇪🇸",
            description = "Spanish natural male voice"
        ),
        KokoroVoice(
            id = 30,
            code = "ff_siwis",
            name = "Siwis",
            gender = "Female",
            accent = "FR",
            emoji = "🇫🇷",
            description = "French lyrical female voice"
        ),
        KokoroVoice(
            id = 35,
            code = "if_sara",
            name = "Sara",
            gender = "Female",
            accent = "IT",
            emoji = "🇮🇹",
            description = "Italian melodic female voice"
        ),
        KokoroVoice(
            id = 36,
            code = "im_nicola",
            name = "Nicola",
            gender = "Male",
            accent = "IT",
            emoji = "🇮🇹",
            description = "Italian natural male voice"
        )
    )

    val DEFAULT_VOICE: KokoroVoice = VOICES.first { it.code == "af_heart" }

    fun findById(id: Int): KokoroVoice {
        return VOICES.firstOrNull { it.id == id } ?: DEFAULT_VOICE
    }

    fun findByCode(code: String): KokoroVoice {
        return VOICES.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DEFAULT_VOICE
    }
}
