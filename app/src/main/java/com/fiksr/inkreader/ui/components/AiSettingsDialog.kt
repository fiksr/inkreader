package com.fiksr.inkreader.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fiksr.inkreader.data.AiCompanionService
import com.fiksr.inkreader.data.AiProvider
import com.fiksr.inkreader.data.AiSettings
import com.fiksr.inkreader.theme.BookFontFamily
import com.fiksr.inkreader.theme.LocalEInkColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsDialog(
    aiService: AiCompanionService,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalEInkColors.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialSettings = remember { aiService.getSettings() }
    var selectedProvider by remember { mutableStateOf(initialSettings.provider) }
    var apiKey by remember { mutableStateOf(initialSettings.apiKey) }
    var selectedModel by remember { mutableStateOf(initialSettings.model) }
    var customBaseUrl by remember { mutableStateOf(initialSettings.customBaseUrl) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTestSuccess by remember { mutableStateOf(false) }

    var availableModels by remember { mutableStateOf(aiService.getAvailableDefaultModels(selectedProvider)) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    // When provider changes, update model choices and default model
    LaunchedEffect(selectedProvider) {
        val models = aiService.getAvailableDefaultModels(selectedProvider)
        availableModels = models
        if (selectedModel !in models) {
            selectedModel = aiService.getDefaultModelFor(selectedProvider)
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = colors.text,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "AI Reading Companion",
                        fontFamily = BookFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.text
                    )
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

            Text(
                text = "Configure AI models for live word lookups, translations, and deep text explanations.",
                fontFamily = BookFontFamily,
                fontSize = 12.sp,
                color = colors.muted
            )

            HorizontalDivider(thickness = 1.dp, color = colors.border)

            // Provider Selector
            Text(
                text = "AI PROVIDER",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.muted
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AiProvider.values().forEach { provider ->
                    val isSel = provider == selectedProvider
                    Surface(
                        onClick = { selectedProvider = provider },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSel) colors.text else colors.card,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = when (provider) {
                                    AiProvider.GEMINI -> "Gemini"
                                    AiProvider.GROQ -> "Groq"
                                    AiProvider.OPENAI -> "OpenAI"
                                    AiProvider.CUSTOM -> "Custom"
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) colors.background else colors.text
                            )
                        }
                    }
                }
            }

            // Free Key helper banner
            val freeKeyUrl = when (selectedProvider) {
                AiProvider.GEMINI -> "https://aistudio.google.com/app/apikey"
                AiProvider.GROQ -> "https://console.groq.com/keys"
                AiProvider.OPENAI -> "https://platform.openai.com/api-keys"
                AiProvider.CUSTOM -> "https://openrouter.ai/keys"
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = colors.card,
                border = BorderStroke(1.dp, colors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedProvider == AiProvider.GEMINI || selectedProvider == AiProvider.GROQ)
                                "Get a 100% Free ${selectedProvider.displayName} Key"
                            else "Get ${selectedProvider.displayName} API Key",
                            fontFamily = BookFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.text
                        )
                        Text(
                            text = "No credit card needed for free tier",
                            fontFamily = BookFontFamily,
                            fontSize = 11.sp,
                            color = colors.muted
                        )
                    }
                    TextButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(freeKeyUrl))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.text)
                    ) {
                        Text("Get Key ↗", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // API Key Field
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key", fontSize = 11.sp, color = colors.muted) },
                placeholder = { Text("Paste your API key here...") },
                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                        Icon(
                            imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle visibility",
                            tint = colors.muted
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.text,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text
                )
            )

            // Model Selector
            ExposedDropdownMenuBox(
                expanded = modelDropdownExpanded,
                onExpandedChange = { modelDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedModel,
                    onValueChange = { selectedModel = it },
                    label = { Text("Model ID", fontSize = 11.sp, color = colors.muted) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.text,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text
                    )
                )
                ExposedDropdownMenu(
                    expanded = modelDropdownExpanded,
                    onDismissRequest = { modelDropdownExpanded = false }
                ) {
                    availableModels.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m, fontSize = 13.sp) },
                            onClick = {
                                selectedModel = m
                                modelDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            if (selectedProvider == AiProvider.CUSTOM) {
                OutlinedTextField(
                    value = customBaseUrl,
                    onValueChange = { customBaseUrl = it },
                    label = { Text("Custom Base URL", fontSize = 11.sp, color = colors.muted) },
                    placeholder = { Text("https://openrouter.ai/api/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.text,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.text,
                        unfocusedTextColor = colors.text
                    )
                )
            }

            // Test Connection & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        isTesting = true
                        testResult = null
                        scope.launch {
                            val testSettings = AiSettings(
                                provider = selectedProvider,
                                apiKey = apiKey,
                                model = selectedModel,
                                customBaseUrl = customBaseUrl
                            )
                            val res = aiService.testConnection(testSettings)
                            isTesting = false
                            if (res.isSuccess) {
                                isTestSuccess = true
                                testResult = "Connection successful!"
                            } else {
                                isTestSuccess = false
                                testResult = res.exceptionOrNull()?.message ?: "Failed to connect"
                            }
                        }
                    },
                    enabled = !isTesting && apiKey.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.text)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing...", fontSize = 13.sp, color = colors.text)
                    } else {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.text)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Connection", fontSize = 13.sp, color = colors.text)
                    }
                }

                if (selectedProvider != AiProvider.GEMINI && apiKey.isNotBlank()) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val testSettings = AiSettings(
                                    provider = selectedProvider,
                                    apiKey = apiKey,
                                    model = selectedModel,
                                    customBaseUrl = customBaseUrl
                                )
                                val res = aiService.fetchDynamicModels(testSettings)
                                if (res.isSuccess) {
                                    availableModels = res.getOrDefault(emptyList())
                                    Toast.makeText(context, "Loaded ${availableModels.size} models from API", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Could not fetch models: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh models", tint = colors.text)
                    }
                }
            }

            testResult?.let { msg ->
                Surface(
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        color = if (isTestSuccess) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Save Settings button
            Button(
                onClick = {
                    val newSettings = AiSettings(
                        provider = selectedProvider,
                        apiKey = apiKey.trim(),
                        model = selectedModel.trim(),
                        customBaseUrl = customBaseUrl.trim(),
                        isEnabled = apiKey.isNotBlank()
                    )
                    aiService.saveSettings(newSettings)
                    Toast.makeText(context, "AI Settings Saved", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.text,
                    contentColor = colors.background
                )
            ) {
                Text("Save Settings", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
