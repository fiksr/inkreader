package com.example.inkreader.ui.sync

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inkreader.data.ReaderRepository
import com.example.inkreader.theme.BookFontFamily
import com.example.inkreader.theme.LocalEInkColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsDialog(
    repository: ReaderRepository,
    onDismiss: () -> Unit
) {
    val colors = LocalEInkColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val syncClient = repository.syncClient
    val syncSettings by syncClient.settings.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var serverUrl by remember(syncSettings.serverUrl) { mutableStateOf(syncSettings.serverUrl) }
    var username by remember(syncSettings.username) { mutableStateOf(syncSettings.username) }
    var userKey by remember(syncSettings.userKey) { mutableStateOf(syncSettings.userKey) }
    var deviceName by remember(syncSettings.deviceName) { mutableStateOf(syncSettings.deviceName) }
    var autoSyncOpen by remember(syncSettings.autoSyncOnOpen) { mutableStateOf(syncSettings.autoSyncOnOpen) }
    var autoSyncClose by remember(syncSettings.autoSyncOnClose) { mutableStateOf(syncSettings.autoSyncOnClose) }
    var showPassword by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = colors.text,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Cloud Sync & BookOrbit",
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
                text = "Sync reading progress across devices via BookOrbit & KOReader sync protocol.",
                fontFamily = BookFontFamily,
                fontSize = 12.sp,
                color = colors.muted
            )

            HorizontalDivider(thickness = 1.dp, color = colors.border)

            // Quick Presets
            Text(
                text = "SERVER PRESETS",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.muted
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { serverUrl = "https://buks.lol" },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, colors.border),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("BookOrbit (buks.lol)", fontSize = 11.sp, color = colors.text)
                }
                OutlinedButton(
                    onClick = { serverUrl = "https://sync.koreader.rocks" },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, colors.border),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("KOReader Cloud", fontSize = 11.sp, color = colors.text)
                }
            }

            Surface(
                color = colors.card,
                border = BorderStroke(1.dp, colors.border),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ℹ️ BookOrbit Credentials: Go to your BookOrbit web portal > Settings > KOReader and copy the dedicated KOReader username & password (key).",
                    fontFamily = BookFontFamily,
                    fontSize = 11.sp,
                    color = colors.text,
                    modifier = Modifier.padding(10.dp)
                )
            }

            // Server URL input
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Server URL (e.g. https://buks.lol/api/v1/koreader)", fontSize = 11.sp, color = colors.muted) },
                singleLine = false,
                maxLines = 3,
                minLines = 1,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.text,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text
                )
            )

            // Username input
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("BookOrbit / KOReader Username", fontSize = 11.sp, color = colors.muted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.text,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text
                )
            )

            // User Key / Password input
            OutlinedTextField(
                value = userKey,
                onValueChange = { userKey = it },
                label = { Text("Password / User Key / API Token", fontSize = 11.sp, color = colors.muted) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password",
                            tint = colors.muted
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.text,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text
                )
            )

            // Device Name
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text("Device Name (e.g. InkReader Tablet)", fontSize = 11.sp, color = colors.muted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.text,
                    unfocusedBorderColor = colors.border,
                    focusedTextColor = colors.text,
                    unfocusedTextColor = colors.text
                )
            )

            // Auto-sync toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-sync on book open", fontFamily = BookFontFamily, fontSize = 13.sp, color = colors.text)
                Switch(
                    checked = autoSyncOpen,
                    onCheckedChange = { autoSyncOpen = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.background,
                        checkedTrackColor = colors.text,
                        uncheckedThumbColor = colors.muted,
                        uncheckedTrackColor = colors.card
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-sync on book close", fontFamily = BookFontFamily, fontSize = 13.sp, color = colors.text)
                Switch(
                    checked = autoSyncClose,
                    onCheckedChange = { autoSyncClose = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.background,
                        checkedTrackColor = colors.text,
                        uncheckedThumbColor = colors.muted,
                        uncheckedTrackColor = colors.card
                    )
                )
            }

            // Test Result Banner
            if (testResult != null) {
                Surface(
                    color = colors.card,
                    border = BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = testResult ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = colors.text,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Test Connection Button & Register Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        isTesting = true
                        testResult = "Testing connection..."
                        coroutineScope.launch {
                            syncClient.saveSettings(serverUrl, username, userKey, deviceName, autoSyncOpen, autoSyncClose)
                            val result = syncClient.testConnection()
                            isTesting = false
                            result.fold(
                                onSuccess = { msg ->
                                    testResult = "✓ $msg"
                                    isError = false
                                },
                                onFailure = { err ->
                                    testResult = "✗ ${err.message}"
                                    isError = true
                                }
                            )
                        }
                    },
                    enabled = !isTesting && serverUrl.isNotBlank() && username.isNotBlank(),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isTesting) "Testing..." else "Test Link", fontSize = 12.sp, color = colors.text)
                }

                OutlinedButton(
                    onClick = {
                        isTesting = true
                        testResult = "Registering account..."
                        coroutineScope.launch {
                            val result = syncClient.registerAccount(serverUrl, username, userKey)
                            isTesting = false
                            result.fold(
                                onSuccess = { msg ->
                                    testResult = "✓ $msg"
                                    isError = false
                                },
                                onFailure = { err ->
                                    testResult = "✗ ${err.message}"
                                    isError = true
                                }
                            )
                        }
                    },
                    enabled = !isTesting && serverUrl.isNotBlank() && username.isNotBlank() && userKey.isNotBlank(),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Register", fontSize = 12.sp, color = colors.text)
                }
            }

            // Save button
            Button(
                onClick = {
                    syncClient.saveSettings(serverUrl, username, userKey, deviceName, autoSyncOpen, autoSyncClose)
                    Toast.makeText(context, "Sync settings saved", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.text, contentColor = colors.background),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text("Save Settings", fontWeight = FontWeight.Bold)
            }

            if (syncSettings.lastSyncTimestamp > 0) {
                val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(syncSettings.lastSyncTimestamp))
                Text(
                    text = "Last synced: $dateStr",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = colors.muted,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
