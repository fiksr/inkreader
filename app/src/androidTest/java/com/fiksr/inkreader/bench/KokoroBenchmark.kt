package com.fiksr.inkreader.bench

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import org.junit.Test
import java.io.File

/**
 * On-device Kokoro synthesis benchmark (debug/dev only, not shipped in the app).
 *
 * Measures real-time factor (RTF = compute time / audio duration) for different
 * thread counts and model files, so we can pick the fastest config for a device.
 *
 * Run (without reinstalling/wiping the app):
 *   adb shell am instrument -w -e class com.fiksr.inkreader.bench.KokoroBenchmark \
 *     -e modelDir kokoro_model -e modelFile model.int8.onnx -e threads 4,2,3,6,8,4 \
 *     com.fiksr.inkreader.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Results are logged under the "KokoroBench" logcat tag.
 */
class KokoroBenchmark {

    private val tag = "KokoroBench"

    private val passage =
        "The rain had not stopped for three days, and the old house groaned under its weight. " +
        "Margaret lit another candle, listening to the wind as it searched the windows for a way in, " +
        "and wondered whether anyone would come before morning."

    @Test
    fun benchmark() {
        val instr = InstrumentationRegistry.getInstrumentation()
        val ctx = instr.targetContext
        val args = InstrumentationRegistry.getArguments()

        val modelDirName = args.getString("modelDir") ?: "kokoro_model"
        val modelFileName = args.getString("modelFile") ?: "model.int8.onnx"
        val threadList = (args.getString("threads") ?: "4,2,3,6,8,4")
            .split(",").mapNotNull { it.trim().toIntOrNull() }
        val runs = args.getString("runs")?.toIntOrNull() ?: 3
        val cooldownSec = args.getString("cooldownSec")?.toLongOrNull() ?: 20L
        val sid = args.getString("sid")?.toIntOrNull() ?: 3
        val speed = args.getString("speed")?.toFloatOrNull() ?: 1.0f

        val dir = resolveDir(ctx, modelDirName)
            ?: error("Model dir '$modelDirName' not found in filesDir or externalFilesDir")

        log("=== Kokoro benchmark: dir=${dir.absolutePath} model=$modelFileName threads=$threadList runs=$runs sid=$sid speed=$speed ===")
        log("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${socName()}), cores=${Runtime.getRuntime().availableProcessors()}")

        val summary = mutableListOf<String>()
        threadList.forEachIndexed { i, threads ->
            if (i > 0 && cooldownSec > 0) {
                log("Cooling down ${cooldownSec}s... ${thermal(ctx)}")
                Thread.sleep(cooldownSec * 1000)
            }
            summary += runConfig(ctx, dir, modelFileName, threads, runs, sid, speed)
        }

        log("=== SUMMARY ($modelDirName/$modelFileName) ===")
        summary.forEach { log(it) }
    }

    private fun runConfig(
        ctx: Context, dir: File, modelFile: String, threads: Int, runs: Int, sid: Int, speed: Float
    ): String {
        val dictDir = File(dir, "dict")
        val lexicon = File(dir, "lexicon-us-en.txt")
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = File(dir, modelFile).absolutePath,
                    voices = File(dir, "voices.bin").absolutePath,
                    tokens = File(dir, "tokens.txt").absolutePath,
                    dataDir = File(dir, "espeak-ng-data").absolutePath,
                    lexicon = if (lexicon.exists()) lexicon.absolutePath else "",
                    dictDir = if (dictDir.exists()) dictDir.absolutePath else ""
                ),
                numThreads = threads,
                debug = false,
                provider = "cpu"
            )
        )

        val tLoad = System.currentTimeMillis()
        val tts = OfflineTts(assetManager = null, config = config)
        val loadMs = System.currentTimeMillis() - tLoad

        try {
            val tWarm = System.currentTimeMillis()
            tts.generate("Warming up the model.", sid, speed)
            val warmMs = System.currentTimeMillis() - tWarm

            val rtfs = mutableListOf<Float>()
            var lastAudioMs = 0L
            var lastGenMs = 0L
            repeat(runs) { r ->
                val t0 = System.currentTimeMillis()
                val audio = tts.generate(passage, sid, speed)
                val genMs = System.currentTimeMillis() - t0
                val audioMs = (audio.samples.size * 1000L) / audio.sampleRate
                val rtf = genMs.toFloat() / audioMs
                rtfs += rtf
                lastAudioMs = audioMs
                lastGenMs = genMs
                log("threads=$threads run ${r + 1}/$runs: gen=${genMs}ms audio=${audioMs}ms RTF=${"%.2f".format(rtf)} | ${thermal(ctx)}")
            }
            val median = rtfs.sorted()[rtfs.size / 2]
            val line = "threads=$threads load=${loadMs}ms warmup=${warmMs}ms medianRTF=${"%.2f".format(median)} " +
                "all=${rtfs.joinToString { "%.2f".format(it) }} (passage ${passage.length} chars, ${lastAudioMs}ms audio, last gen ${lastGenMs}ms)"
            log("RESULT $line")
            return line
        } finally {
            tts.release()
        }
    }

    private fun resolveDir(ctx: Context, name: String): File? {
        val candidates = listOfNotNull(File(ctx.filesDir, name), ctx.getExternalFilesDir(null)?.resolve(name))
        return candidates.firstOrNull { File(it, "tokens.txt").exists() }
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
