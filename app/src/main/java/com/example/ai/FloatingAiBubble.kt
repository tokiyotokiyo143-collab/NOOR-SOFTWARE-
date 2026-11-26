package com.example.ai

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.NavSection
import com.example.ui.AiChatMessage
import com.example.ui.MainViewModel
import com.example.ui.theme.LocalNoorColors
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FloatingAiBubbleOverlay(
    vm: MainViewModel,
    tts: TextToSpeech?,
    onRequestScreenShare: () -> Unit,
    onStopScreenShare: () -> Unit
) {
    val palette = LocalNoorColors.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val aiSettings by vm.aiSettings.collectAsState()
    val messages by vm.aiMessages.collectAsState()
    val actions by vm.aiActions.collectAsState()
    val pendingConfirm by vm.pendingAiConfirmation.collectAsState()
    val isScreenSharing by ScreenCaptureService.isSharing.collectAsState()
    val latestFrame by ScreenCaptureService.latestFrame.collectAsState()

    if (!aiSettings.enabled) return

    var panelOpen by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var typedCommand by remember { mutableStateOf("") }
    var showMicPermissionExplainer by remember { mutableStateOf(false) }

    val audioRecorder = remember { AudioRecorderHelper() }
    val engine = remember(vm) { GeminiAssistantEngine(context, vm) }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            vm.appendAiMessage(AiChatMessage(isUser = false, text = "Microphone permission needed for push-to-talk. You can type commands below."))
            panelOpen = true
        }
    }

    fun speakIfAllowed(reply: String) {
        if (aiSettings.speakRepliesAloud && tts != null && reply.isNotBlank()) {
            val locale = if (aiSettings.replyLanguage == "ta") Locale("ta", "IN") else Locale("en", "IN")
            tts.language = locale
            tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "noor_ai_reply")
        }
    }

    fun sendTextCommand(cmd: String) {
        if (cmd.isBlank() || isThinking) return
        vm.appendAiMessage(AiChatMessage(isUser = true, text = cmd))
        isThinking = true
        scope.launch {
            val frame = if (isScreenSharing && aiSettings.allowScreenShare) latestFrame else null
            val reply = engine.handleTurn(textPrompt = cmd, screenBitmap = frame)
            vm.appendAiMessage(reply)
            speakIfAllowed(reply.text)
            isThinking = false
        }
    }

    fun startPushToTalk(): Boolean {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            showMicPermissionExplainer = true
            return false
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        isListening = audioRecorder.start()
        return isListening
    }

    fun stopPushToTalkAndSend() {
        if (!isListening) return
        isListening = false
        val wav = audioRecorder.stopAndGetWav() ?: return
        vm.appendAiMessage(AiChatMessage(isUser = true, text = "Voice command..."))
        isThinking = true
        scope.launch {
            val frame = if (isScreenSharing && aiSettings.allowScreenShare) latestFrame else null
            val reply = engine.handleTurn(wavAudioBytes = wav, screenBitmap = frame)
            vm.appendAiMessage(reply)
            speakIfAllowed(reply.text)
            isThinking = false
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val bubblePx = with(density) { 56.dp.toPx() }
        val edgePad = with(density) { 12.dp.toPx() }

        var offsetX by remember(aiSettings.bubbleOffsetXFraction, maxW) {
            mutableFloatStateOf((aiSettings.bubbleOffsetXFraction * (maxW - bubblePx - edgePad)).coerceIn(edgePad, (maxW - bubblePx - edgePad).coerceAtLeast(edgePad)))
        }
        var offsetY by remember(aiSettings.bubbleOffsetYFraction, maxH) {
            mutableFloatStateOf((aiSettings.bubbleOffsetYFraction * (maxH - bubblePx - edgePad)).coerceIn(edgePad * 3, (maxH - bubblePx - edgePad * 3).coerceAtLeast(edgePad * 3)))
        }

        // Always visible Stop Sharing pill when MediaProjection is active
        if (isScreenSharing) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.statusRed)
                    .clickable { onStopScreenShare() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Outlined.ScreenShare, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text("AI viewing screen · Stop sharing", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        // Expandable Chat / Command Panel
        AnimatedVisibility(
            visible = panelOpen,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 82.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .heightIn(max = 420.dp)
                    .shadow(16.dp, RoundedCornerShape(20.dp), ambientColor = palette.accent, spotColor = palette.accent)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.card)
                    .border(1.dp, palette.accent.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Mic, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                        Text("Noor AI (${if (aiSettings.replyLanguage == "ta") "தமிழ்" else "EN"})", color = palette.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (actions.isNotEmpty() && actions.first().undoBlock != null) {
                            IconButton(onClick = { vm.undoLastAiAction() }, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo AI action", tint = palette.statusAmber, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (aiSettings.allowScreenShare) {
                            TextButton(
                                onClick = { if (isScreenSharing) onStopScreenShare() else onRequestScreenShare() },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(
                                    if (isScreenSharing) "Stop sharing" else "Share screen",
                                    color = if (isScreenSharing) palette.statusRed else palette.accent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                        IconButton(
                            onClick = { vm.updateAiSettings { it.copy(speakRepliesAloud = !it.speakRepliesAloud) } },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                if (aiSettings.speakRepliesAloud) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                                contentDescription = "Toggle voice reply",
                                tint = palette.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                panelOpen = false
                                vm.navigateTo(NavSection.SETTINGS)
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Outlined.Settings, contentDescription = "AI Settings", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { panelOpen = false }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Close AI Panel", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Messages List
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).heightIn(min = 100.dp, max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Text(
                                "Hold the pink mic bubble to speak in Tamil, Tanglish, or English — or type below (e.g. \"oru sale open pannu\", \"stock-la Y20 glass iruka?\").",
                                color = palette.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        items(messages, key = { it.id }) { msg ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (msg.isUser) palette.accent.copy(alpha = 0.22f) else palette.inputBg)
                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    Text(msg.text, color = palette.textPrimary, fontSize = 12.5.sp)
                                }
                                if (msg.choices.isNotEmpty()) {
                                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        msg.choices.forEach { ch ->
                                            AssistChip(
                                                onClick = { sendTextCommand(ch.promptToSend) },
                                                label = { Text(ch.label, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (isThinking) {
                        item { ThinkingDotsRow() }
                    }
                }

                // Confirmation prompt if pending
                pendingConfirm?.let { conf ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.statusAmber.copy(alpha = 0.16f))
                            .border(1.dp, palette.statusAmber, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(conf.title, color = palette.statusAmber, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Text(conf.description, color = palette.textPrimary, fontSize = 11.5.sp)
                        }
                        Row {
                            TextButton(onClick = { vm.setPendingAiConfirmation(null) }) { Text("No") }
                            TextButton(onClick = {
                                val action = conf.onConfirm
                                vm.setPendingAiConfirmation(null)
                                scope.launch {
                                    val doneMsg = action()
                                    vm.appendAiMessage(AiChatMessage(isUser = false, text = doneMsg))
                                    speakIfAllowed(doneMsg)
                                }
                            }) { Text("Confirm", color = palette.accent, fontWeight = FontWeight.Bold) }
                        }
                    }
                }

                // Input Row
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = typedCommand,
                        onValueChange = { typedCommand = it },
                        placeholder = { Text("Type command (Tamil / English)...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    IconButton(
                        onClick = {
                            if (isListening) stopPushToTalkAndSend() else startPushToTalk()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isListening) palette.statusRed else palette.inputBg)
                    ) {
                        Icon(Icons.Outlined.Mic, contentDescription = "Mic", tint = if (isListening) Color.White else palette.accent)
                    }
                    IconButton(
                        onClick = {
                            val cmd = typedCommand.trim()
                            if (cmd.isNotEmpty()) {
                                typedCommand = ""
                                sendTextCommand(cmd)
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(palette.accent)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send command", tint = Color.White)
                    }
                }
            }
        }

        // Draggable Floating AI Bubble
        val pulseAnim = rememberInfiniteTransition(label = "pulse")
        val pulseScale by pulseAnim.animateFloat(
            initialValue = 1f,
            targetValue = if (isListening) 1.18f else 1f,
            animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
            label = "pulse_scale"
        )
        val scale = if (isDragging) 1.12f else pulseScale

        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isListening) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.card)
                        .border(1.dp, palette.accent, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("Listening...", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            } else if (isThinking && !panelOpen) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.card)
                        .border(1.dp, palette.accent, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    ThinkingDotsRow()
                }
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .scale(scale)
                    .shadow(14.dp, CircleShape, ambientColor = palette.accent, spotColor = palette.accent)
                    .clip(CircleShape)
                    .background(palette.primaryGradient)
                    .border(2.dp, if (isListening) Color.White else palette.accent.copy(alpha = 0.7f), CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = {
                                isDragging = false
                                val snapRight = offsetX > (maxW - bubblePx) / 2f
                                offsetX = if (snapRight) (maxW - bubblePx - edgePad) else edgePad
                                val xf = if (maxW > bubblePx) offsetX / (maxW - bubblePx) else 0.92f
                                val yf = if (maxH > bubblePx) offsetY / (maxH - bubblePx) else 0.72f
                                vm.updateAiSettings { it.copy(bubbleOffsetXFraction = xf, bubbleOffsetYFraction = yf) }
                            },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount.x).coerceIn(edgePad, (maxW - bubblePx - edgePad).coerceAtLeast(edgePad))
                                offsetY = (offsetY + dragAmount.y).coerceIn(edgePad * 2, (maxH - bubblePx - edgePad * 2).coerceAtLeast(edgePad * 2))
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { panelOpen = !panelOpen },
                            onPress = {
                                val job = scope.launch {
                                    kotlinx.coroutines.delay(220L)
                                    startPushToTalk()
                                }
                                tryAwaitRelease()
                                job.cancel()
                                if (isListening) {
                                    stopPushToTalkAndSend()
                                }
                            }
                        )
                    }
                    .testTag("floating_ai_bubble"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = "Noor AI Assistant Bubble",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }

    if (showMicPermissionExplainer) {
        AlertDialog(
            onDismissRequest = { showMicPermissionExplainer = false },
            containerColor = palette.card,
            title = { Text("Microphone Access", color = palette.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Noor AI Assistant needs microphone permission only when you press and hold the pink bubble to speak commands in Tamil or English.",
                    color = palette.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMicPermissionExplainer = false
                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Allow Microphone", color = palette.accent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showMicPermissionExplainer = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ThinkingDotsRow() {
    val palette = LocalNoorColors.current
    val inf = rememberInfiniteTransition(label = "dots")
    val phase by inf.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing)),
        label = "dot_phase"
    )
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
        repeat(3) { i ->
            val alpha = if (phase.toInt() == i) 1f else 0.35f
            Box(Modifier.size(7.dp).clip(CircleShape).background(palette.accent.copy(alpha = alpha)))
        }
    }
}
