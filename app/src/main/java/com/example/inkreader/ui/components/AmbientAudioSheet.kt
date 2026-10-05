package com.example.inkreader.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inkreader.audio.AmbientAudioEngine
import com.example.inkreader.audio.AmbientSoundType
import com.example.inkreader.audio.CustomAmbientSoundManager
import com.example.inkreader.audio.TtsEngine
import com.example.inkreader.audio.TtsEngineType
import com.example.inkreader.audio.kokoro.KokoroDownloadState
import com.example.inkreader.audio.kokoro.KokoroVoiceCatalog
import com.example.inkreader.audio.piper.PiperDownloadState
import com.example.inkreader.audio.piper.PiperVoiceCatalog
import com.example.inkreader.audio.supertonic.SupertonicDownloadState
import com.example.inkreader.audio.supertonic.SupertonicQuality
import com.example.inkreader.audio.supertonic.SupertonicVoiceCatalog
import com.example.inkreader.theme.BookFontFamily
import com.example.inkreader.theme.LocalEInkColors
import kotlinx.coroutines.launch

private enum class AudioSheetTab {
    READ_ALOUD, AMBIENT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientAudioSheet(
    audioEngine: AmbientAudioEngine,
    ttsEngine: TtsEngine? = null,
    currentText: String = "",
    onPageEndReached: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val colors = LocalEInkColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var activeTab by remember { mutableStateOf(if (ttsEngine != null) AudioSheetTab.READ_ALOUD else AudioSheetTab.AMBIENT) }
    var showPiperVoiceDropdown by remember { mutableStateOf(false) }
    var showKokoroVoiceDropdown by remember { mutableStateOf(false) }
    var showSupertonicVoiceDropdown by remember { mutableStateOf(false) }

    val currentSound by audioEngine.currentSound.collectAsState()
    val activeCustomSoundId by audioEngine.activeCustomSoundId.collectAsState()
    val currentVolume by audioEngine.currentVolume.collectAsState()
    val sleepTimerLeft by audioEngine.sleepTimerMinutesLeft.collectAsState()

    val ttsState by (ttsEngine?.state ?: remember { mutableStateOf(null) }).let {
        if (ttsEngine != null) ttsEngine.state.collectAsState() else remember { mutableStateOf(null) }
    }

    val piperDownloadState by (ttsEngine?.piperModelManager?.downloadState ?: remember { mutableStateOf(PiperDownloadState.Idle) }).let {
        if (ttsEngine != null) ttsEngine.piperModelManager.downloadState.collectAsState() else remember { mutableStateOf(PiperDownloadState.Idle) }
    }

    val kokoroDownloadState by (ttsEngine?.kokoroModelManager?.downloadState ?: remember { mutableStateOf(KokoroDownloadState.Idle) }).let {
        if (ttsEngine != null) ttsEngine.kokoroModelManager.downloadState.collectAsState() else remember { mutableStateOf(KokoroDownloadState.Idle) }
    }

    val supertonicDownloadState by (ttsEngine?.supertonicModelManager?.downloadState ?: remember { mutableStateOf(SupertonicDownloadState.Idle) }).let {
        if (ttsEngine != null) ttsEngine.supertonicModelManager.downloadState.collectAsState() else remember { mutableStateOf(SupertonicDownloadState.Idle) }
    }

    var customSounds by remember { mutableStateOf(CustomAmbientSoundManager.loadCustomSounds(context)) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val imported = CustomAmbientSoundManager.importSound(context, uri)
            if (imported != null) {
                customSounds = CustomAmbientSoundManager.loadCustomSounds(context)
                audioEngine.playCustomSound(imported)
                Toast.makeText(context, "Added '${imported.name}'", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to import audio file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        contentColor = colors.text,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(colors.border, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // TOP HEADER: Title + Status + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Audio & Narrator",
                        fontFamily = BookFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.text
                    )

                    if (activeTab == AudioSheetTab.READ_ALOUD && ttsState != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (ttsState!!.isPlaying) colors.text else colors.card,
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Text(
                                text = if (ttsState!!.isPlaying) "🔊 Playing" else "⏸ Paused",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (ttsState!!.isPlaying) colors.background else colors.muted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = colors.text,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // TOP TABS: [ 🎙️ Read Aloud | 🌿 Ambient Sounds ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    .background(colors.card, RoundedCornerShape(8.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isReadAloud = activeTab == AudioSheetTab.READ_ALOUD
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isReadAloud) colors.text else colors.card,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeTab = AudioSheetTab.READ_ALOUD }
                ) {
                    Text(
                        text = "🎙️ Read Aloud",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = if (isReadAloud) FontWeight.Bold else FontWeight.Medium,
                        color = if (isReadAloud) colors.background else colors.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                val isAmbient = activeTab == AudioSheetTab.AMBIENT
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isAmbient) colors.text else colors.card,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { activeTab = AudioSheetTab.AMBIENT }
                ) {
                    Text(
                        text = "🌿 Ambient Sounds",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = if (isAmbient) FontWeight.Bold else FontWeight.Medium,
                        color = if (isAmbient) colors.background else colors.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            // TAB 1: READ ALOUD (TTS)
            if (activeTab == AudioSheetTab.READ_ALOUD) {
                if (ttsEngine != null && ttsState != null) {
                    // TTS ENGINE SWITCHER CHIPS (Horizontal Scrollable)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "TTS ENGINE",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.muted
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Supertonic 3 (Flow Matching)
                            EngineChip(
                                icon = "⚡",
                                title = "Supertonic 3",
                                badge = "2x Fast",
                                isSelected = ttsState!!.engineType == TtsEngineType.SUPERTONIC,
                                colors = colors,
                                onClick = { ttsEngine.setEngineType(TtsEngineType.SUPERTONIC) }
                            )

                            // Kokoro (82M)
                            EngineChip(
                                icon = "🎙️",
                                title = "Kokoro",
                                badge = "82M",
                                isSelected = ttsState!!.engineType == TtsEngineType.KOKORO,
                                colors = colors,
                                onClick = { ttsEngine.setEngineType(TtsEngineType.KOKORO) }
                            )

                            // Piper (Fast)
                            EngineChip(
                                icon = "🚀",
                                title = "Piper",
                                badge = "Fast",
                                isSelected = ttsState!!.engineType == TtsEngineType.PIPER,
                                colors = colors,
                                onClick = { ttsEngine.setEngineType(TtsEngineType.PIPER) }
                            )

                            // System
                            EngineChip(
                                icon = "📱",
                                title = "System",
                                badge = "Default",
                                isSelected = ttsState!!.engineType == TtsEngineType.SYSTEM,
                                colors = colors,
                                onClick = { ttsEngine.setEngineType(TtsEngineType.SYSTEM) }
                            )
                        }
                    }

                    // 1. PIPER SECTION
                    if (ttsState!!.engineType == TtsEngineType.PIPER) {
                        val currentVoice = ttsState!!.piperVoice
                        val isVoiceReady = ttsEngine.piperModelManager.isVoiceReady(currentVoice)

                        if (!isVoiceReady) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.card,
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "🚀 Ultra-Fast Neural Voice: ${currentVoice.name}",
                                        fontFamily = BookFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = colors.text
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Instant, gapless playback with 0ms buffering. ~${currentVoice.sizeMb}MB download, 100% offline.",
                                        fontFamily = BookFontFamily,
                                        fontSize = 11.sp,
                                        color = colors.muted,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    when (val state = piperDownloadState) {
                                        is PiperDownloadState.Downloading -> {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                LinearProgressIndicator(
                                                    progress = { state.progress },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(6.dp),
                                                    color = colors.text,
                                                    trackColor = colors.background
                                                )
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    text = "Downloading: ${(state.progress * 100).toInt()}% (${state.downloadedBytes / (1024 * 1024)}MB / ${state.totalBytes / (1024 * 1024)}MB)",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = colors.muted
                                                )
                                            }
                                        }
                                        is PiperDownloadState.Extracting -> {
                                            Text(
                                                text = "📦 Installing neural voice...",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.text
                                            )
                                        }
                                        else -> {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        ttsEngine.piperModelManager.downloadAndInstall(currentVoice) { success ->
                                                             if (success) {
                                                                ttsEngine.piperEngine.initIfModelReady(currentVoice)
                                                            }
                                                        }
                                                    }
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = colors.text, contentColor = colors.background),
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Download Voice (~${currentVoice.sizeMb}MB)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Piper Voice Selector Card
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.card,
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPiperVoiceDropdown = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "NEURAL VOICE",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.muted
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${currentVoice.name} ${if (isVoiceReady) "✓" else "↓"}",
                                        fontFamily = BookFontFamily,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.text
                                    )
                                    Text(
                                        text = currentVoice.speaker,
                                        fontFamily = BookFontFamily,
                                        fontSize = 11.sp,
                                        color = colors.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Box {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Voice",
                                        tint = colors.text,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    DropdownMenu(
                                        expanded = showPiperVoiceDropdown,
                                        onDismissRequest = { showPiperVoiceDropdown = false },
                                        modifier = Modifier
                                            .background(colors.card)
                                            .heightIn(max = 300.dp)
                                    ) {
                                        PiperVoiceCatalog.ALL_VOICES.forEach { voice ->
                                            val ready = ttsEngine.piperModelManager.isVoiceReady(voice)
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(
                                                            text = "${voice.name} ${if (ready) "✓ Ready" else "↓ (~${voice.sizeMb}MB)"}",
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 12.sp,
                                                            fontWeight = if (ttsState!!.piperVoice.id == voice.id) FontWeight.Bold else FontWeight.Normal,
                                                            color = colors.text
                                                        )
                                                        Text(
                                                            text = voice.speaker,
                                                            fontSize = 10.sp,
                                                            color = colors.muted
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    ttsEngine.setPiperVoice(voice)
                                                    showPiperVoiceDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. KOKORO SECTION
                    if (ttsState!!.engineType == TtsEngineType.KOKORO) {
                        if (!ttsEngine.kokoroModelManager.isModelReady()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.card,
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "🎙️ Kokoro Neural Narrator (82M)",
                                        fontFamily = BookFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = colors.text
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "54 expressive voices. Higher compute requirement on mobile CPU.",
                                        fontFamily = BookFontFamily,
                                        fontSize = 11.sp,
                                        color = colors.muted,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    when (val state = kokoroDownloadState) {
                                        is KokoroDownloadState.Downloading -> {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                LinearProgressIndicator(
                                                    progress = { state.progress },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(6.dp),
                                                    color = colors.text,
                                                    trackColor = colors.background
                                                )
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    text = "Downloading: ${(state.progress * 100).toInt()}% (${state.downloadedBytes / (1024 * 1024)}MB / ${state.totalBytes / (1024 * 1024)}MB)",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = colors.muted
                                                )
                                            }
                                        }
                                        is KokoroDownloadState.Extracting -> {
                                            Text(
                                                text = "📦 Extracting Kokoro model...",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.text
                                            )
                                        }
                                        else -> {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        ttsEngine.kokoroModelManager.downloadAndInstall()
                                                    }
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = colors.text, contentColor = colors.background),
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Download Voice Pack (~126MB)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Kokoro Voice Selector Card
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.card,
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showKokoroVoiceDropdown = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "KOKORO VOICE (82M)",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.muted
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = ttsState!!.kokoroVoice.displayName,
                                            fontFamily = BookFontFamily,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.text
                                        )
                                        Text(
                                            text = ttsState!!.kokoroVoice.description,
                                            fontFamily = BookFontFamily,
                                            fontSize = 11.sp,
                                            color = colors.muted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Box {
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Voice",
                                            tint = colors.text,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        DropdownMenu(
                                            expanded = showKokoroVoiceDropdown,
                                            onDismissRequest = { showKokoroVoiceDropdown = false },
                                            modifier = Modifier
                                                .background(colors.card)
                                                .heightIn(max = 300.dp)
                                        ) {
                                            KokoroVoiceCatalog.VOICES.forEach { voice ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(
                                                                text = voice.displayName,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 12.sp,
                                                                fontWeight = if (ttsState!!.kokoroVoice.id == voice.id) FontWeight.Bold else FontWeight.Normal,
                                                                color = colors.text
                                                            )
                                                            Text(
                                                                text = voice.description,
                                                                fontSize = 10.sp,
                                                                color = colors.muted
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        ttsEngine.setKokoroVoice(voice)
                                                        showKokoroVoiceDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. SUPERTONIC 3 SECTION
                    if (ttsState!!.engineType == TtsEngineType.SUPERTONIC) {
                        if (!ttsEngine.supertonicModelManager.isModelReady()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.card,
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "⚡ Supertonic 3 Flow-Matching (99M)",
                                        fontFamily = BookFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = colors.text
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Zero-buffering Flow Matching engine. 2x-3.3x faster than real-time on Pixel 8 Pro.",
                                        fontFamily = BookFontFamily,
                                        fontSize = 11.sp,
                                        color = colors.muted,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    when (val state = supertonicDownloadState) {
                                        is SupertonicDownloadState.Downloading -> {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                LinearProgressIndicator(
                                                    progress = { state.progress },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(6.dp),
                                                    color = colors.text,
                                                    trackColor = colors.background
                                                )
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    text = "Downloading: ${(state.progress * 100).toInt()}% (${state.downloadedBytes / (1024 * 1024)}MB / ${state.totalBytes / (1024 * 1024)}MB)",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = colors.muted
                                                )
                                            }
                                        }
                                        is SupertonicDownloadState.Extracting -> {
                                            Text(
                                                text = "📦 Installing Supertonic 3...",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.text
                                            )
                                        }
                                        else -> {
                                            Button(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        ttsEngine.supertonicModelManager.downloadAndInstall()
                                                    }
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = colors.text, contentColor = colors.background),
                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                            ) {
                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Download Supertonic 3 (~140MB)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val currentVoice = ttsState!!.supertonicVoice

                            // Supertonic Voice Card
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.card,
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSupertonicVoiceDropdown = true }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "NARRATOR VOICE",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.muted
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(text = currentVoice.emoji, fontSize = 15.sp)
                                            Text(
                                                text = "${currentVoice.name} (${currentVoice.gender})",
                                                fontFamily = BookFontFamily,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.text
                                            )
                                        }
                                        Text(
                                            text = currentVoice.description,
                                            fontFamily = BookFontFamily,
                                            fontSize = 11.sp,
                                            color = colors.muted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Box {
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "Select Voice",
                                            tint = colors.text,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        DropdownMenu(
                                            expanded = showSupertonicVoiceDropdown,
                                            onDismissRequest = { showSupertonicVoiceDropdown = false },
                                            modifier = Modifier
                                                .background(colors.card)
                                                .heightIn(max = 300.dp)
                                        ) {
                                            SupertonicVoiceCatalog.VOICES.forEach { voice ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(
                                                                text = "${voice.emoji} ${voice.name} (${voice.gender})",
                                                                fontFamily = FontFamily.Monospace,
                                                                fontSize = 12.sp,
                                                                fontWeight = if (ttsState!!.supertonicVoice.id == voice.id) FontWeight.Bold else FontWeight.Normal,
                                                                color = colors.text
                                                            )
                                                            Text(
                                                                text = voice.description,
                                                                fontSize = 10.sp,
                                                                color = colors.muted
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        ttsEngine.setSupertonicVoice(voice)
                                                        showSupertonicVoiceDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Synthesis Quality & Steps Setting Card
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.card,
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "SYNTHESIS QUALITY & SPEED",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.muted
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = ttsState!!.supertonicQuality.description,
                                            fontFamily = BookFontFamily,
                                            fontSize = 12.sp,
                                            color = colors.text
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SupertonicQuality.values().forEach { quality ->
                                            val isSelected = ttsState!!.supertonicQuality == quality
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isSelected) colors.text else colors.background,
                                                border = BorderStroke(1.dp, if (isSelected) colors.text else colors.border),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable { ttsEngine.setSupertonicQuality(quality) }
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = when (quality) {
                                                            SupertonicQuality.STUDIO -> "Studio"
                                                            SupertonicQuality.BALANCED -> "Balanced"
                                                            SupertonicQuality.FAST -> "Fast"
                                                        },
                                                        fontFamily = BookFontFamily,
                                                        fontSize = 13.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (isSelected) colors.background else colors.text
                                                    )
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(
                                                        text = "${quality.steps} steps",
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 10.sp,
                                                        color = if (isSelected) colors.background.copy(alpha = 0.85f) else colors.muted
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. SYSTEM TTS SECTION
                    if (ttsState!!.engineType == TtsEngineType.SYSTEM) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.card,
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("📱", fontSize = 20.sp)
                                Column {
                                    Text(
                                        text = "Android System TTS",
                                        fontFamily = BookFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = colors.text
                                    )
                                    Text(
                                        text = "Standard Android speech synthesizer. Zero download required.",
                                        fontFamily = BookFontFamily,
                                        fontSize = 11.sp,
                                        color = colors.muted
                                    )
                                }
                            }
                        }
                    }

                    // SPOKEN SENTENCE QUOTE CARD
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.card,
                        border = BorderStroke(0.8.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (ttsState!!.totalSentences > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sentence ${ttsState!!.currentSentenceIndex + 1} of ${ttsState!!.totalSentences}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = colors.muted
                                    )
                                    if (ttsState!!.engineType == TtsEngineType.SUPERTONIC) {
                                        Text(
                                            text = "⚡ Zero-Buffer Active",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.text
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (ttsState!!.currentSentenceText.isNotBlank())
                                    "“${ttsState!!.currentSentenceText}”"
                                else
                                    "Tap 'Read Aloud' to start continuous audiobook narration.",
                                fontFamily = BookFontFamily,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = if (ttsState!!.currentSentenceText.isNotBlank()) colors.text else colors.muted,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // TRANSPORT CONTROLS: Prev | Play/Pause | Next
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { ttsEngine.skipPrevious() },
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous Sentence",
                                tint = colors.text,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(Modifier.width(18.dp))

                        Button(
                            onClick = {
                                if (currentText.isNotBlank() && ttsState!!.totalSentences == 0) {
                                    ttsEngine.setContent(currentText, startIndex = 0, onPageEndReached = onPageEndReached)
                                }
                                ttsEngine.togglePlayPause()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.text,
                                contentColor = colors.background
                            ),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Icon(
                                if (ttsState!!.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (ttsState!!.isPlaying) "Pause" else "Read Aloud",
                                fontFamily = BookFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(Modifier.width(18.dp))

                        IconButton(
                            onClick = { ttsEngine.skipNext() },
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next Sentence",
                                tint = colors.text,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // SPEED SELECTOR ROW
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "READING SPEED",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.muted
                        )
                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0.75f to "0.75x", 1.0f to "1.0x", 1.25f to "1.25x", 1.5f to "1.5x", 2.0f to "2.0x").forEach { (speed, label) ->
                                val isSelected = kotlin.math.abs(ttsState!!.speed - speed) < 0.05f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(1.dp, if (isSelected) colors.text else colors.border, RoundedCornerShape(6.dp))
                                        .background(if (isSelected) colors.text else colors.card)
                                        .clickable { ttsEngine.setSpeed(speed) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) colors.background else colors.text
                                    )
                                }
                            }
                        }
                    }

                    // STORAGE INFO / MODEL MANAGEMENT FOOTER
                    if (ttsState!!.engineType == TtsEngineType.SUPERTONIC && ttsEngine.supertonicModelManager.isModelReady()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Supertonic 3 Model: ${ttsEngine.supertonicModelManager.getModelSizeFormatted()} • Offline",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = colors.muted
                            )

                            TextButton(
                                onClick = {
                                    ttsEngine.pause()
                                    ttsEngine.supertonicModelManager.deleteModel()
                                    Toast.makeText(context, "Supertonic 3 model deleted to free space", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Delete Model", fontSize = 10.sp, color = colors.muted)
                            }
                        }
                    } else if (ttsState!!.engineType == TtsEngineType.PIPER && ttsEngine.piperModelManager.isVoiceReady(ttsState!!.piperVoice)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Voice Size: ${ttsEngine.piperModelManager.getVoiceSizeFormatted(ttsState!!.piperVoice)} • Offline",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = colors.muted
                            )

                            TextButton(
                                onClick = {
                                    ttsEngine.pause()
                                    ttsEngine.piperModelManager.deleteVoice(ttsState!!.piperVoice)
                                    Toast.makeText(context, "Voice deleted to free space", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Delete Voice", fontSize = 10.sp, color = colors.muted)
                            }
                        }
                    } else if (ttsState!!.engineType == TtsEngineType.KOKORO && ttsEngine.kokoroModelManager.isModelReady()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Voice Pack: ${ttsEngine.kokoroModelManager.getModelSizeFormatted()} • Offline",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = colors.muted
                            )

                            TextButton(
                                onClick = {
                                    ttsEngine.pause()
                                    ttsEngine.kokoroModelManager.deleteModel()
                                    Toast.makeText(context, "Kokoro voice pack deleted to free space", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Delete Pack", fontSize = 10.sp, color = colors.muted)
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.card,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Open any book in your library to start Read Aloud narration.",
                            fontFamily = BookFontFamily,
                            fontSize = 13.sp,
                            color = colors.muted,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // TAB 2: AMBIENT SOUNDS & SLEEP TIMER
            if (activeTab == AudioSheetTab.AMBIENT) {
                // Preset Soundscapes Grid
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "AMBIENT SOUNDSCAPES",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.muted
                    )
                    Spacer(Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val rows = AmbientSoundType.entries.chunked(2)
                        rows.forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { type ->
                                    val isSelected = currentSound == type && activeCustomSoundId == null
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) colors.text else colors.card,
                                        border = BorderStroke(1.dp, if (isSelected) colors.text else colors.border),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { audioEngine.setSound(type) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 11.dp, horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(text = type.iconLabel, fontSize = 16.sp)
                                            Text(
                                                text = type.displayName,
                                                fontFamily = BookFontFamily,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.sp,
                                                color = if (isSelected) colors.background else colors.text,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Master Volume Slider & Sleep Timer
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "VOLUME & SLEEP TIMER",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.muted
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Slider(
                                value = currentVolume,
                                onValueChange = { audioEngine.setVolume(it) },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.text,
                                    activeTrackColor = colors.text,
                                    inactiveTrackColor = colors.border
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = colors.text, modifier = Modifier.size(18.dp))
                        }

                        // Sleep Timer Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (sleepTimerLeft != null) "Timer: ${sleepTimerLeft}m left" else "Sleep Timer:",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sleepTimerLeft != null) colors.text else colors.muted
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(15, 30, 45, 60).forEach { mins ->
                                    val isSet = sleepTimerLeft == mins
                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, if (isSet) colors.text else colors.border, RoundedCornerShape(4.dp))
                                            .background(if (isSet) colors.text else colors.card)
                                            .clickable { audioEngine.setSleepTimer(mins) }
                                            .padding(horizontal = 7.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${mins}m",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSet) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSet) colors.background else colors.text
                                        )
                                    }
                                }

                                if (sleepTimerLeft != null) {
                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                                            .background(colors.card)
                                            .clickable { audioEngine.cancelSleepTimer() }
                                            .padding(horizontal = 7.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Off",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = colors.muted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Custom Audio Imports Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CUSTOM SOUNDS & AUDIO",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.muted
                        )
                        TextButton(
                            onClick = { audioPickerLauncher.launch("audio/*") },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.text)
                            Spacer(Modifier.width(4.dp))
                            Text("Import Audio", fontSize = 11.sp, color = colors.text, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (customSounds.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.card,
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No custom audio files added yet. Tap '+ Import Audio' to add your own ambient files or music.",
                                fontFamily = BookFontFamily,
                                fontSize = 11.sp,
                                color = colors.muted,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            customSounds.forEach { sound ->
                                val isPlaying = activeCustomSoundId == sound.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, if (isPlaying) colors.text else colors.border, RoundedCornerShape(8.dp))
                                        .background(if (isPlaying) colors.text else colors.card)
                                        .clickable { audioEngine.playCustomSound(sound) }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🎵", fontSize = 14.sp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = sound.name,
                                            fontFamily = BookFontFamily,
                                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            color = if (isPlaying) colors.background else colors.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (isPlaying) {
                                                audioEngine.setSound(AmbientSoundType.OFF)
                                            }
                                            CustomAmbientSoundManager.deleteSound(context, sound.id)
                                            customSounds = CustomAmbientSoundManager.loadCustomSounds(context)
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = if (isPlaying) colors.background else colors.muted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EngineChip(
    icon: String,
    title: String,
    badge: String,
    isSelected: Boolean,
    colors: com.example.inkreader.theme.EInkColors,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) colors.text else colors.card,
        border = BorderStroke(1.dp, if (isSelected) colors.text else colors.border),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = icon, fontSize = 12.sp)
            Text(
                text = title,
                fontSize = 12.sp,
                fontFamily = BookFontFamily,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) colors.background else colors.text
            )
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isSelected) colors.background.copy(alpha = 0.2f) else colors.border.copy(alpha = 0.4f)
            ) {
                Text(
                    text = badge,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) colors.background else colors.muted,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}
