package com.example.inkreader.audio.supertonic

data class SupertonicVoice(
    val id: Int,
    val code: String,
    val name: String,
    val gender: String,
    val emoji: String,
    val description: String
) {
    val displayName: String
        get() = "$emoji $name ($gender)"
}

enum class SupertonicQuality(val steps: Int, val label: String, val description: String) {
    STUDIO(10, "Studio (10 steps)", "Highest quality & maximum acoustic fidelity"),
    BALANCED(5, "Balanced (5 steps)", "Recommended - 2x faster than real-time, zero buffering"),
    FAST(3, "Ultra-Fast (3 steps)", "3.3x faster than real-time, minimal battery & CPU load");

    companion object {
        val DEFAULT = BALANCED
    }
}

object SupertonicVoiceCatalog {
    val VOICES: List<SupertonicVoice> = listOf(
        // Female Voices
        SupertonicVoice(
            id = 0,
            code = "F1",
            name = "Aria",
            gender = "Female",
            emoji = "🌸",
            description = "Natural, soothing and clear literary tone"
        ),
        SupertonicVoice(
            id = 1,
            code = "F2",
            name = "Luna",
            gender = "Female",
            emoji = "🌙",
            description = "Audiobook narrator, warm, immersive and expressive"
        ),
        SupertonicVoice(
            id = 2,
            code = "F3",
            name = "Moka",
            gender = "Female",
            emoji = "✨",
            description = "Gentle, pleasant and melodic voice"
        ),
        SupertonicVoice(
            id = 3,
            code = "F4",
            name = "Nora",
            gender = "Female",
            emoji = "🎙️",
            description = "Bright, articulate and professional reading style"
        ),
        SupertonicVoice(
            id = 4,
            code = "F5",
            name = "Serena",
            gender = "Female",
            emoji = "🍃",
            description = "Calm, tranquil and balanced storytelling tone"
        ),

        // Male Voices
        SupertonicVoice(
            id = 5,
            code = "M1",
            name = "Marcus",
            gender = "Male",
            emoji = "📖",
            description = "Authoritative, engaging and classic audiobook narrator"
        ),
        SupertonicVoice(
            id = 6,
            code = "M2",
            name = "Watson",
            gender = "Male",
            emoji = "☕",
            description = "Sophisticated, conversational and nuanced narrative voice"
        ),
        SupertonicVoice(
            id = 7,
            code = "M3",
            name = "Alphonse",
            gender = "Male",
            emoji = "🛡️",
            description = "Rich, resonant elder baritone with deep gravitas"
        ),
        SupertonicVoice(
            id = 8,
            code = "M4",
            name = "Keld",
            gender = "Male",
            emoji = "⚡",
            description = "Crisp, dynamic and modern reading style"
        ),
        SupertonicVoice(
            id = 9,
            code = "M5",
            name = "Rowan",
            gender = "Male",
            emoji = "🌲",
            description = "Deep, warm and reassuring narrative tone"
        )
    )

    val DEFAULT_VOICE: SupertonicVoice = VOICES[1] // Luna (F2)

    fun findById(id: Int): SupertonicVoice =
        VOICES.find { it.id == id } ?: DEFAULT_VOICE
}
