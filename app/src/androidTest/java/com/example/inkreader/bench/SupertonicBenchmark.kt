package com.example.inkreader.bench

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import org.junit.Test
import java.io.File

/**
 * On-device Supertonic 3 synthesis benchmark.
 */
class SupertonicBenchmark {

    private val tag = "SupertonicBench"

    private val passage =
        "The rain had not stopped for three days, and the old house groaned under its weight. " +
        "Margaret lit another candle, listening to the wind as it searched the windows for a way in, " +
        "and wondered whether anyone would come before morning."

    @Test
    fun benchmark() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val ctx = instr.targetContext
        val args = InstrumentationRegistry.getArguments()

        val modelDirName = args.getString("modelDir") ?: "supertonic3_model"
        val threadList = (args.getString("threads") ?: "4,3")
            .split(",").mapNotNull { it.trim().toIntOrNull() }
        val stepsList = (args.getString("steps") ?: "10,5,3")
            .split(",").mapNotNull { it.trim().toIntOrNull() }
        val runs = args.getString("runs")?.toIntOrNull() ?: 2
        val sid = args.getString("sid")?.toIntOrNull() ?: 0
        val speed = args.getString("speed")?.toFloatOrNull() ?: 1.0f

        val dir = resolveDir(ctx, modelDirName)
            ?: error("Model dir '$modelDirName' not found in filesDir or externalFilesDir")

        log("=== Supertonic 3 benchmark: dir=${dir.absolutePath} threads=$threadList steps=$stepsList runs=$runs sid=$sid speed=$speed ===")
        log("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${socName()}), cores=${Runtime.getRuntime().availableProcessors()}")

        val summary = mutableListOf<String>()
        for (threads in threadList) {
            for (steps in stepsList) {
                summary += runConfig(ctx, dir, threads, steps, runs, sid, speed)
                Thread.sleep(5000)
            }
        }

        log("=== SUMMARY (Supertonic 3) ===")
        summary.forEach { log(it) }
    }

    private fun runConfig(
        ctx: Context, dir: File, threads: Int, steps: Int, runs: Int, sid: Int, speed: Float
    ): String {
        val stConfig = OfflineTtsSupertonicModelConfig(
            durationPredictor = File(dir, "duration_predictor.int8.onnx").absolutePath,
            textEncoder = File(dir, "text_encoder.int8.onnx").absolutePath,
            vectorEstimator = File(dir, "vector_estimator.int8.onnx").absolutePath,
            vocoder = File(dir, "vocoder.int8.onnx").absolutePath,
            ttsJson = File(dir, "tts.json").absolutePath,
            unicodeIndexer = File(dir, "unicode_indexer.bin").absolutePath,
            voiceStyle = File(dir, "voice.bin").absolutePath
        )

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                supertonic = stConfig,
                numThreads = threads,
                debug = false,
                provider = "cpu"
            )
        )

        val tLoad = System.currentTimeMillis()
        val tts = OfflineTts(assetManager = null, config = config)
        val loadMs = System.currentTimeMillis() - tLoad

        try {
            val genConfig = GenerationConfig(
                sid = sid,
                speed = speed,
                numSteps = steps
            )

            val tWarm = System.currentTimeMillis()
            tts.generateWithConfig("Warming up the model.", genConfig)
            val warmMs = System.currentTimeMillis() - tWarm

            val rtfs = mutableListOf<Float>()
            var lastAudioMs = 0L
            var lastGenMs = 0L
            repeat(runs) { r ->
                val t0 = System.currentTimeMillis()
                val audio = tts.generateWithConfig(passage, genConfig)
                val genMs = System.currentTimeMillis() - t0
                val audioMs = (audio.samples.size * 1000L) / audio.sampleRate
                val rtf = genMs.toFloat() / audioMs
                rtfs += rtf
                lastAudioMs = audioMs
                lastGenMs = genMs
                log("threads=$threads steps=$steps run ${r + 1}/$runs: gen=${genMs}ms audio=${audioMs}ms RTF=${"%.2f".format(rtf)} | ${thermal(ctx)}")
            }
            val median = rtfs.sorted()[rtfs.size / 2]
            val line = "threads=$threads steps=$steps load=${loadMs}ms warmup=${warmMs}ms medianRTF=${"%.2f".format(median)} " +
                "all=${rtfs.joinToString { "%.2f".format(it) }} (passage ${passage.length} chars, ${lastAudioMs}ms audio, last gen ${lastGenMs}ms)"
            log("RESULT $line")
            return line
        } catch (e: Exception) {
            log("ERROR threads=$threads steps=$steps: ${e.message}")
            return "threads=$threads steps=$steps ERROR: ${e.message}"
        } finally {
            tts.release()
        }
    }

    private fun resolveDir(ctx: Context, name: String): File? {
        val candidates = listOfNotNull(
            File(ctx.filesDir, name),
            ctx.getExternalFilesDir(null)?.resolve(name),
            ctx.getExternalFilesDir(null)?.resolve(name)?.resolve("sherpa-onnx-supertonic-3-tts-int8-2026-05-11")
        )
        return candidates.firstOrNull { File(it, "tts.json").exists() }
    }

    private fun thermal(ctx: Context): String {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val status = if (Build.VERSION.SDK_INT >= 29) pm.currentThermalStatus else -1
        val headroom = if (Build.VERSION.SDK_INT >= 30) pm.getThermalHeadroom(0) else Float.NaN
        return "thermalStatus=$status headroom=${"%.2f".format(headroom)}"
    }

    private fun socName(): String =
        if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else "unknown"

    private fun log(msg: String) {
        Log.i(tag, msg)
    }
}
