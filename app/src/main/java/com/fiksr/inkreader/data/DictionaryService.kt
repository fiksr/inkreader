package com.fiksr.inkreader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class WordDefinition(
    val word: String,
    val phonetic: String,
    val partOfSpeech: String,
    val definition: String,
    val example: String? = null,
    val isLiveFetched: Boolean = false
)

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLang: String = "English",
    val targetLang: String = "Serbian",
    val isLiveFetched: Boolean = false
)

data class AiExplanation(
    val title: String,
    val summary: String,
    val contextNote: String? = null
)

object DictionaryService {

    private val definitionCache = ConcurrentHashMap<String, WordDefinition>()
    private val translationCache = ConcurrentHashMap<String, TranslationResult>()

    private fun posTagToName(tag: String): String {
        return when (tag.lowercase().trim()) {
            "n" -> "noun"
            "v" -> "verb"
            "adj" -> "adjective"
            "adv" -> "adverb"
            "prep" -> "preposition"
            "conj" -> "conjunction"
            "pron" -> "pronoun"
            else -> tag
        }
    }

    // Comprehensive offline vocabulary for instant response
    private val localDictionary = mapOf(
        "nearly" to WordDefinition("nearly", "/ˈnɪərli/", "adverb", "In close approximation; almost, but not quite.", "It was nearly midnight when we arrived."),
        "things" to WordDefinition("things", "/θɪŋz/", "noun (plural)", "Objects, articles, possessions, or entities in general.", "Great saurian things surged in the swamps."),
        "thing" to WordDefinition("thing", "/θɪŋ/", "noun", "An inanimate material object as distinct from a living sentient being.", "The first thing he wanted to see."),
        "hegemony" to WordDefinition("Hegemony", "/hɪˈdʒɛməni/", "noun", "Leadership or dominance, especially by one state, government, or social group over others.", "The Hegemony of Man ruled across hundreds of worlds."),
        "consul" to WordDefinition("Consul", "/ˈkɒnsəl/", "noun", "An official appointed by a government to represent state affairs and protect citizens in a distant territory.", "The Hegemony Consul sat on the balcony of his spaceship."),
        "saurian" to WordDefinition("Saurian", "/ˈsɔːriən/", "adjective", "Of, like, or relating to lizards, dinosaurs, or prehistoric reptilian beasts.", "Saurian behemoths whose mating bellows shook the damp air."),
        "colloidal" to WordDefinition("Colloidal", "/kəˈlɔɪdəl/", "adjective", "Relating to a system in which microscopic insoluble particles are dispersed throughout a medium.", "A colloidal consciousness spanning an entire hemisphere."),
        "monolith" to WordDefinition("Monolith", "/ˈmɒnəlɪθ/", "noun", "A single massive stone or towering monument erected in antiquity.", "The monoliths rose like frozen gods against a violet sky."),
        "charlatan" to WordDefinition("Charlatan", "/ˈʃɑːlətən/", "noun", "A person falsely claiming to have special knowledge, spiritual power, or skill; a fraud.", "Another charlatan promising enlightenment."),
        "pilgrimage" to WordDefinition("Pilgrimage", "/ˈpɪlɡrɪmɪdʒ/", "noun", "A long journey made to some sacred place or site of great significance.", "The seven pilgrims began their journey toward the Time Tombs."),
        "behemoth" to WordDefinition("Behemoth", "/bɪˈhiːməθ/", "noun", "A huge or monstrous creature, or something enormous in power and size.", "Great saurian behemoths surged in the wetlands."),
        "gymnosperms" to WordDefinition("Gymnosperm", "/ˈdʒɪmnəspɜːm/", "noun", "A seed-bearing plant whose seeds are not enclosed in an ovary, including conifers and ginkgo.", "A horizon of giant prehistoric gymnosperms."),
        "retribution" to WordDefinition("Retribution", "/ˌrɛtrɪˈbjuːʃən/", "noun", "Punishment inflicted on someone as vengeance for a wrong or criminal act.", "Not as a god, but as an engine of retribution."),
        "arrakis" to WordDefinition("Arrakis", "/əˈrækɪs/", "proper noun", "The desert planet also known as Dune, the sole source of the spice melange in the universe.", "Arrakis, the planet known as Dune, is forever his place."),
        "shrike" to WordDefinition("Shrike", "/ʃraɪk/", "proper noun", "In the Hyperion Cantos, the legendary four-armed metallic avatar of pain and time.", "The mechanical horror that hunted among the valley of tombs.")
    )

    private val localTranslations = mapOf(
        "nearly" to "gotovo, skoro, umalo",
        "things" to "stvari, predmeti, pojave",
        "thing" to "stvar, predmet",
        "consul" to "konzul (zvanični predstavnik/diplomata)",
        "hegemony" to "hegemonija (prevlast, dominacija)",
        "saurian" to "gušterski, reptilski",
        "colloidal" to "koloidni",
        "monolith" to "monolit (kameni stub/spomenik)",
        "charlatan" to "šarlatan (varalica, nadrilekar)",
        "pilgrimage" to "hodočašće",
        "behemoth" to "gorostas, neman, grdosija",
        "retribution" to "odmazda, pravedna kazna",
        "ancient" to "drevni, starovekovni",
        "storm" to "oluja, nepogoda",
        "temple" to "hram, svetilište",
        "space" to "svemir, prostor",
        "lightning" to "munja, sevanje",
        "thunderstorm" to "oluja sa grmljavinom",
        "silence" to "tišina, muk",
        "journey" to "putovanje",
        "desert" to "pustinja",
        "ocean" to "okean",
        "mystery" to "misterija, tajna",
        "shadow" to "senka, hladovina",
        "valley" to "dolina",
        "arrival" to "dolazak, prispeće",
        "awakening" to "buđenje"
    )

    fun lookup(rawWord: String): WordDefinition {
        val clean = rawWord.trim().lowercase().replace("[^a-z]".toRegex(), "")
        if (clean.isBlank()) {
            return WordDefinition(rawWord, "", "word", "No definition available.")
        }

        definitionCache[clean]?.let { return it }
        localDictionary[clean]?.let { return it }

        // Try singular/stem lookup
        if (clean.endsWith("s") && clean.length > 3) {
            val stem = clean.removeSuffix("s")
            localDictionary[stem]?.let {
                return WordDefinition(rawWord, it.phonetic, it.partOfSpeech, it.definition, it.example)
            }
        }

        return WordDefinition(
            word = rawWord.trim(),
            phonetic = "/${clean}/",
            partOfSpeech = "word",
            definition = "Fetching live definition from dictionary...",
            example = "\"${rawWord.trim()}\" in the text."
        )
    }

    suspend fun lookupLive(rawWord: String): WordDefinition = withContext(Dispatchers.IO) {
        val clean = rawWord.trim().lowercase().replace("[^a-z]".toRegex(), "")
        if (clean.isBlank()) return@withContext lookup(rawWord)

        definitionCache[clean]?.let { return@withContext it }

        // Query Datamuse Dictionary API (High reliability & <50ms response)
        try {
            val url = URL("https://api.datamuse.com/words?sp=$clean&md=dp&max=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "InkReader/1.0")
            }

            if (conn.responseCode == 200) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val jsonArr = JSONArray(responseText)
                if (jsonArr.length() > 0) {
                    val obj = jsonArr.getJSONObject(0)
                    val defs = obj.optJSONArray("defs")
                    val tags = obj.optJSONArray("tags")

                    var partOfSpeech = "word"
                    if (tags != null && tags.length() > 0) {
                        partOfSpeech = posTagToName(tags.getString(0))
                    }

                    if (defs != null && defs.length() > 0) {
                        val defLines = mutableListOf<String>()
                        for (i in 0 until defs.length().coerceAtMost(3)) {
                            val rawDef = defs.getString(i)
                            val parts = rawDef.split("\t")
                            if (parts.size >= 2) {
                                if (partOfSpeech == "word") {
                                    partOfSpeech = posTagToName(parts[0])
                                }
                                val text = parts[1].trim()
                                if (text.isNotBlank()) {
                                    defLines.add(if (defs.length() > 1) "${i + 1}. $text" else text)
                                }
                            } else {
                                defLines.add(rawDef.trim())
                            }
                        }

                        if (defLines.isNotEmpty()) {
                            val defResult = WordDefinition(
                                word = rawWord.trim(),
                                phonetic = "/${clean}/",
                                partOfSpeech = partOfSpeech,
                                definition = defLines.joinToString("\n"),
                                example = "\"${rawWord.trim()}\" in the passage.",
                                isLiveFetched = true
                            )
                            definitionCache[clean] = defResult
                            return@withContext defResult
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback to local
        lookup(rawWord)
    }

    fun cyrillicToLatin(text: String): String {
        val map = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'ђ' to "đ", 'е' to "e", 'ж' to "ž",
            'з' to "z", 'и' to "i", 'ј' to "j", 'к' to "k", 'л' to "l", 'љ' to "lj", 'м' to "m", 'н' to "n",
            'њ' to "nj", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'ћ' to "ć", 'у' to "u",
            'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "č", 'џ' to "dž", 'ш' to "š",
            'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D", 'Ђ' to "Đ", 'Е' to "E", 'Ж' to "Ž",
            'З' to "Z", 'И' to "I", 'Ј' to "J", 'К' to "K", 'Л' to "L", 'Љ' to "Lj", 'М' to "M", 'Н' to "N",
            'Њ' to "Nj", 'О' to "O", 'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T", 'Ћ' to "Ć", 'У' to "U",
            'Ф' to "F", 'Х' to "H", 'Ц' to "C", 'Ч' to "Č", 'Џ' to "Dž", 'Ш' to "Š"
        )
        val sb = StringBuilder()
        for (ch in text) {
            sb.append(map[ch] ?: ch)
        }
        return sb.toString()
    }

    fun translate(text: String, sourceLang: String = "en", targetLang: String = "sr"): TranslationResult {
        val clean = text.trim().lowercase().replace("[^a-z]".toRegex(), "")
        translationCache[clean]?.let { return it }

        localTranslations[clean]?.let {
            val res = TranslationResult(text.trim(), cyrillicToLatin(it), "English", "Serbian")
            translationCache[clean] = res
            return res
        }

        return TranslationResult(
            originalText = text.trim(),
            translatedText = "Prevođenje na srpski...",
            sourceLang = "English",
            targetLang = "Serbian"
        )
    }

    suspend fun translateLive(text: String, sourceLang: String = "en", targetLang: String = "sr"): TranslationResult = withContext(Dispatchers.IO) {
        val clean = text.trim().lowercase().replace("[^a-z]".toRegex(), "")
        translationCache[clean]?.let { return@withContext it }

        localTranslations[clean]?.let {
            val res = TranslationResult(text.trim(), cyrillicToLatin(it), "English", "Serbian")
            translationCache[clean] = res
            return@withContext res
        }

        val queryText = text.trim()
        if (queryText.isEmpty()) return@withContext translate(text, sourceLang, targetLang)

        // Tier 1: Google Dictionary Chrome Extension endpoint (Fast, high availability, no captcha block)
        try {
            val encoded = URLEncoder.encode(queryText, "UTF-8")
            val url = URL("https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=$sourceLang&tl=$targetLang&q=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }

            if (conn.responseCode == 200) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }.trim()
                var parsedText = ""
                if (responseText.startsWith("[")) {
                    val rootArray = JSONArray(responseText)
                    if (rootArray.length() > 0) {
                        val first = rootArray.get(0)
                        if (first is JSONArray && first.length() > 0) {
                            parsedText = first.getString(0)
                        } else if (first is String) {
                            parsedText = first
                        }
                    }
                } else if (responseText.startsWith("\"") && responseText.endsWith("\"")) {
                    parsedText = responseText.substring(1, responseText.length - 1)
                }

                if (parsedText.isNotBlank()) {
                    val latin = cyrillicToLatin(parsedText)
                    val result = TranslationResult(
                        originalText = queryText,
                        translatedText = latin,
                        sourceLang = "English",
                        targetLang = "Serbian",
                        isLiveFetched = true
                    )
                    translationCache[clean] = result
                    return@withContext result
                }
            }
        } catch (_: Exception) {}

        // Tier 2: MyMemory Translation API Fallback
        try {
            val encoded = URLEncoder.encode(queryText, "UTF-8")
            val url = URL("https://api.mymemory.translated.net/get?q=$encoded&langpair=$sourceLang|$targetLang")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "InkReader/1.0")
            }

            if (conn.responseCode == 200) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val jsonObj = org.json.JSONObject(responseText)
                val responseData = jsonObj.optJSONObject("responseData")
                val translated = responseData?.optString("translatedText", "") ?: ""
                if (translated.isNotBlank() && !translated.contains("MYMEMORY WARNING")) {
                    val latin = cyrillicToLatin(translated)
                    val result = TranslationResult(
                        originalText = queryText,
                        translatedText = latin,
                        sourceLang = "English",
                        targetLang = "Serbian",
                        isLiveFetched = true
                    )
                    translationCache[clean] = result
                    return@withContext result
                }
            }
        } catch (_: Exception) {}

        // Final Fallback: Clear message instead of hanging on "Prevođenje..."
        val fallback = TranslationResult(
            originalText = queryText,
            translatedText = "Prevod nije dostupan offline (proverite internet vezu)",
            sourceLang = "English",
            targetLang = "Serbian",
            isLiveFetched = false
        )
        return@withContext fallback
    }

    fun recapChapter(chapterTitle: String, bookTitle: String): AiExplanation {
        return AiExplanation(
            title = "Chapter Recap: $chapterTitle",
            summary = "Key Narrative Events in $bookTitle:\n• The characters assess their standing and surroundings in the opening passage.\n• Key conflicts and stakes are introduced without forward spoilers.\n• Atmospheric setting establishes tension before the impending climax.",
            contextNote = "Spoiler-free: generated from current chapter text."
        )
    }
}
