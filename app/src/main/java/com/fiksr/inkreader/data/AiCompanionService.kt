package com.fiksr.inkreader.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class AiProvider(val displayName: String, val defaultBaseUrl: String) {
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta"),
    GROQ("Groq (Ultra-Fast)", "https://api.groq.com/openai/v1"),
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    CUSTOM("Custom / OpenRouter", "https://openrouter.ai/api/v1")
}

data class AiSettings(
    val provider: AiProvider = AiProvider.GEMINI,
    val apiKey: String = "",
    val model: String = "gemini-3.8-flash",
    val customBaseUrl: String = "",
    val temperature: Float = 0.7f,
    val isEnabled: Boolean = false
)

data class AiChatMessage(
    val sender: String, // "user" or "ai"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class AiCompanionService(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("inkreader_ai_prefs", Context.MODE_PRIVATE)

    fun getSettings(): AiSettings {
        val providerName = prefs.getString("ai_provider", AiProvider.GEMINI.name) ?: AiProvider.GEMINI.name
        val provider = try { AiProvider.valueOf(providerName) } catch (_: Exception) { AiProvider.GEMINI }
        val apiKey = prefs.getString("ai_api_key", "") ?: ""
        val model = prefs.getString("ai_model", getDefaultModelFor(provider)) ?: getDefaultModelFor(provider)
        val customBaseUrl = prefs.getString("ai_custom_base_url", "") ?: ""
        val temperature = prefs.getFloat("ai_temp", 0.7f)
        val isEnabled = prefs.getBoolean("ai_enabled", apiKey.isNotBlank())

        return AiSettings(
            provider = provider,
            apiKey = apiKey,
            model = model,
            customBaseUrl = customBaseUrl,
            temperature = temperature,
            isEnabled = isEnabled
        )
    }

    fun saveSettings(settings: AiSettings) {
        prefs.edit()
            .putString("ai_provider", settings.provider.name)
            .putString("ai_api_key", settings.apiKey.trim())
            .putString("ai_model", settings.model.trim())
            .putString("ai_custom_base_url", settings.customBaseUrl.trim())
            .putFloat("ai_temp", settings.temperature)
            .putBoolean("ai_enabled", settings.apiKey.isNotBlank() && settings.isEnabled)
            .apply()
    }

    fun getDefaultModelFor(provider: AiProvider): String {
        return when (provider) {
            AiProvider.GEMINI -> "gemini-3.8-flash"
            AiProvider.GROQ -> "llama-3.3-70b-versatile"
            AiProvider.OPENAI -> "gpt-6-luna"
            AiProvider.CUSTOM -> "deepseek/deepseek-chat"
        }
    }

    fun getAvailableDefaultModels(provider: AiProvider): List<String> {
        return when (provider) {
            AiProvider.GEMINI -> listOf(
                "gemini-3.8-flash",
                "gemini-3.5-flash",
                "gemini-3.5-flash-lite",
                "gemini-3.1-pro",
                "gemini-2.5-flash",
                "gemini-2.0-flash"
            )
            AiProvider.GROQ -> listOf(
                "llama-3.3-70b-versatile",
                "llama-3.1-8b-instant",
                "meta-llama/llama-4-scout-17b-16e-instruct",
                "openai/gpt-oss-120b",
                "openai/gpt-oss-20b",
                "qwen/qwen3.8-27b"
            )
            AiProvider.OPENAI -> listOf(
                "gpt-6-luna",
                "gpt-6.1-sol",
                "gpt-6-astra",
                "gpt-4o-mini",
                "gpt-4o"
            )
            AiProvider.CUSTOM -> listOf(
                "deepseek/deepseek-chat",
                "deepseek/deepseek-r1",
                "anthropic/claude-3.5-sonnet",
                "meta-llama/llama-3.3-70b-instruct"
            )
        }
    }

    suspend fun generateCatchMeUp(
        bookTitle: String,
        author: String,
        currentChapterTitle: String,
        currentChapterIndex: Int = 1,
        totalChapters: Int = 1,
        bookProgressPercent: Int = 0,
        recentExcerpt: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val chapterInfo = if (totalChapters > 1) {
            "Chapter $currentChapterIndex of $totalChapters (\"$currentChapterTitle\", approximately $bookProgressPercent% through the book)"
        } else {
            "\"$currentChapterTitle\""
        }

        val prompt = """
You are InkReader AI Reading Companion. The reader is resuming reading "$bookTitle" by $author.
Current Reading Position: $chapterInfo.

Excerpt from the current page where the reader paused:
\"\"\"
$recentExcerpt
\"\"\"

Task:
Provide a concise, high-level "Catch Me Up" narrative recap (3 to 4 clear bullet points) that refreshes the reader's memory of the major plot developments, key events, and story arc leading up to this point in the book ($chapterInfo).

CRITICAL RULES:
- Use your deep literary knowledge of "$bookTitle" by $author to summarize what has happened in the story so far, from the beginning up to the current chapter ($chapterInfo).
- Do NOT just summarize the short excerpt snippet above; the excerpt is only provided to mark the exact spot where the reader is. Provide the broader narrative context of how the characters arrived at this situation.
- STRICTLY SPOILER-FREE: Absolutely DO NOT reveal, hint at, or anticipate any plot developments, twists, or resolutions that happen after $chapterInfo.
- Format with clean bullet points starting with '•'. Keep it punchy, engaging, and easy to read on an e-reader screen.
""".trimIndent()

        callAi(prompt, systemInstruction = "You are a knowledgeable literary reading companion providing spoiler-free narrative plot recaps for readers returning to their book.")
    }

    suspend fun explainCharacter(
        characterName: String,
        bookTitle: String,
        author: String,
        currentChapterTitle: String,
        surroundingContext: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val prompt = """
You are InkReader AI Reading Companion.
Book: "$bookTitle" by $author
Current Chapter/Section: "$currentChapterTitle"
Character/Entity query: "$characterName"

Surrounding context:
\"\"\"
$surroundingContext
\"\"\"

Task: Explain who "$characterName" is in "$bookTitle".
CRITICAL RULES:
- STRICTLY SPOILER-FREE: Only describe their role, relationships, and significance UP TO the current point in the story.
- If this is a fantasy/sci-fi faction, place, or concept, explain its meaning in the universe.
- Keep it concise (1 to 2 short paragraphs max).
""".trimIndent()

        callAi(prompt, systemInstruction = "You are a literary companion providing spoiler-free character and lore explanations.")
    }

    suspend fun askBookQuestion(
        question: String,
        bookTitle: String,
        author: String,
        currentChapterTitle: String,
        surroundingContext: String,
        conversationHistory: List<AiChatMessage> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        val historyFormatted = if (conversationHistory.isNotEmpty()) {
            "Recent Chat:\n" + conversationHistory.takeLast(4).joinToString("\n") { "${it.sender.uppercase()}: ${it.text}" } + "\n\n"
        } else ""

        val prompt = """
$historyFormatted
Book: "$bookTitle" by $author
Current Section: "$currentChapterTitle"
Context from page:
\"\"\"
$surroundingContext
\"\"\"

Reader's Question: "$question"

Instructions:
- Answer the reader's question directly, insightfully, and concisely.
- Be strictly spoiler-free regarding future events not yet reached in the book.
- If explaining literary themes, symbolism, or tricky dialogue, make it easy to understand.
""".trimIndent()

        callAi(prompt, systemInstruction = "You are an intelligent, friendly reading companion answering questions about the book.")
    }

    suspend fun explainPassage(
        passage: String,
        bookTitle: String,
        author: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val prompt = """
Book: "$bookTitle" by $author
Passage:
\"\"\"
$passage
\"\"\"

Task: Provide a brief, insightful explanation of this passage:
- Explain any difficult vocabulary, archaic phrasing, or metaphors.
- Give a 1-sentence summary of the core meaning.
- Keep it under 100 words.
""".trimIndent()

        callAi(prompt, systemInstruction = "You are a literary tutor explaining text passages clearly and concisely.")
    }

    suspend fun testConnection(settings: AiSettings): Result<String> = withContext(Dispatchers.IO) {
        val prompt = "Reply with 'InkReader AI Connected successfully!' and nothing else."
        callAiWithSettings(prompt, "System test", settings)
    }

    suspend fun fetchDynamicModels(settings: AiSettings): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val endpoint = when (settings.provider) {
                AiProvider.GROQ -> "https://api.groq.com/openai/v1/models"
                AiProvider.OPENAI -> "https://api.openai.com/v1/models"
                AiProvider.CUSTOM -> {
                    val base = settings.customBaseUrl.ifBlank { "https://openrouter.ai/api/v1" }.trimEnd('/')
                    "$base/models"
                }
                AiProvider.GEMINI -> {
                    return@withContext Result.success(getAvailableDefaultModels(AiProvider.GEMINI))
                }
            }

            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer ${settings.apiKey.trim()}")
                setRequestProperty("User-Agent", "InkReader/1.0")
                connectTimeout = 6000
                readTimeout = 6000
            }

            if (conn.responseCode in 200..299) {
                val responseText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val json = JSONObject(responseText)
                val data = json.optJSONArray("data") ?: JSONArray()
                val models = mutableListOf<String>()
                for (i in 0 until data.length()) {
                    val id = data.getJSONObject(i).optString("id")
                    if (id.isNotBlank()) models.add(id)
                }
                Result.success(models.sorted())
            } else {
                val err = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream, "UTF-8")).use { it.readText() }
                Result.failure(Exception("HTTP ${conn.responseCode}: $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun callAi(prompt: String, systemInstruction: String): Result<String> {
        val settings = getSettings()
        if (settings.apiKey.isBlank()) {
            return Result.failure(Exception("No API key configured. Please set your API key in Settings."))
        }
        return callAiWithSettings(prompt, systemInstruction, settings)
    }

    private suspend fun callAiWithSettings(
        prompt: String,
        systemInstruction: String,
        settings: AiSettings
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (settings.provider) {
                AiProvider.GEMINI -> callGemini(prompt, systemInstruction, settings)
                AiProvider.GROQ, AiProvider.OPENAI, AiProvider.CUSTOM -> callOpenAiCompatible(prompt, systemInstruction, settings)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun callGemini(
        prompt: String,
        systemInstruction: String,
        settings: AiSettings
    ): Result<String> {
        val model = settings.model.ifBlank { "gemini-3.8-flash" }
        val baseUrl = settings.customBaseUrl.ifBlank { "https://generativelanguage.googleapis.com/v1beta" }.trimEnd('/')
        val endpoint = "$baseUrl/models/$model:generateContent?key=${settings.apiKey.trim()}"

        val rootJson = JSONObject().apply {
            val contentsArr = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", "$systemInstruction\n\n$prompt") })
                    })
                })
            }
            put("contents", contentsArr)
        }

        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("User-Agent", "InkReader/1.0")
            doOutput = true
            connectTimeout = 12000
            readTimeout = 18000
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use {
            it.write(rootJson.toString())
            it.flush()
        }

        val code = conn.responseCode
        if (code in 200..299) {
            val responseText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            val resObj = JSONObject(responseText)
            val candidates = resObj.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val content = candidates.getJSONObject(0).optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text")
                    return Result.success(text.trim())
                }
            }
            return Result.success("No response generated.")
        } else {
            val err = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream, "UTF-8")).use { it.readText() }
            return Result.failure(Exception("Gemini API Error ($code): $err"))
        }
    }

    private fun callOpenAiCompatible(
        prompt: String,
        systemInstruction: String,
        settings: AiSettings
    ): Result<String> {
        val base = when (settings.provider) {
            AiProvider.GROQ -> "https://api.groq.com/openai/v1"
            AiProvider.OPENAI -> "https://api.openai.com/v1"
            AiProvider.CUSTOM -> settings.customBaseUrl.ifBlank { "https://openrouter.ai/api/v1" }.trimEnd('/')
            else -> "https://api.openai.com/v1"
        }
        val endpoint = "$base/chat/completions"

        val rootJson = JSONObject().apply {
            put("model", settings.model)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemInstruction)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            put("messages", messages)
            put("temperature", settings.temperature.toDouble())
        }

        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Authorization", "Bearer ${settings.apiKey.trim()}")
            setRequestProperty("User-Agent", "InkReader/1.0")
            doOutput = true
            connectTimeout = 12000
            readTimeout = 18000
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use {
            it.write(rootJson.toString())
            it.flush()
        }

        val code = conn.responseCode
        if (code in 200..299) {
            val responseText = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
            val resObj = JSONObject(responseText)
            val choices = resObj.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val message = choices.getJSONObject(0).optJSONObject("message")
                val content = message?.optString("content") ?: ""
                return Result.success(content.trim())
            }
            return Result.success("No response generated.")
        } else {
            val err = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream, "UTF-8")).use { it.readText() }
            return Result.failure(Exception("${settings.provider.displayName} API Error ($code): $err"))
        }
    }
}
