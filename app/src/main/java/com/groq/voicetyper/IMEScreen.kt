package com.groq.voicetyper

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun IMEScreen(
    audioRecorder: AudioRecorder,
    apiKey: String?,
    onBackspace: () -> Unit,
    onBackspaceSelect: (Int) -> Unit = {},
    onBackspaceDeleteSelected: () -> Unit = {},
    onBackspaceCancelSelect: () -> Unit = {},
    onSpace: () -> Unit,
    onEnter: () -> Unit,
    recordingState: RecordingState,
    errorMessage: String?,
    onCancelRecording: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onDismiss: () -> Unit = {},
    onSwitchKeyboard: () -> Unit = {}
) {
    val context = LocalContext.current
    val amplitude by audioRecorder.amplitude.collectAsState()

    // Recording duration timer
    var recordTimeSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(recordingState) {
        if (recordingState == RecordingState.RECORDING) {
            recordTimeSeconds = 0
            while (isActive) {
                delay(1000)
                recordTimeSeconds++
            }
        }
    }

    val minutes = recordTimeSeconds / 60
    val seconds = recordTimeSeconds % 60
    val timeText = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    val isEnabled = !apiKey.isNullOrBlank() && recordingState != RecordingState.TRANSCRIBING
    val currentRecordingState by rememberUpdatedState(recordingState)
    val currentOnStartRecording by rememberUpdatedState(onStartRecording)
    val currentOnStopRecording by rememberUpdatedState(onStopRecording)
    val currentOnCancelRecording by rememberUpdatedState(onCancelRecording)

    // Infinite transitions for smooth animations
    val infiniteTransition = rememberInfiniteTransition(label = "aura")

    // Dynamic phase values for rotation and morphing of the wavy rings
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 2f * Math.PI.toFloat(),
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )

    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase3"
    )

    // Breathe scaling for Aura circles/rings
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheScale"
    )

    val speakingPulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speakingPulseScale"
    )

    val currentGlowScale = when (recordingState) {
        RecordingState.RECORDING -> speakingPulseScale
        RecordingState.IDLE -> breatheScale
        else -> 1.0f
    }

    // Radar ping animation (when listening)
    val pingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pingScale"
    )

    val pingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pingAlpha"
    )

    // Transcribing spinner rotation
    val transcribingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "transcribingRotation"
    )

    // Color definitions matching Google Stitch design system
    val bgThemeColor = Color(0xFF0D0C11)
    val outlineBorderColor = Color(0x1AD18CFF) // rgba(209, 140, 255, 0.1)

    val primaryGlowColor = when (recordingState) {
        RecordingState.RECORDING -> Color(0xFFA855F7) // Purple
        RecordingState.TRANSCRIBING -> Color(0xFF8B5CF6)
        RecordingState.ERROR -> Color(0xFFFF5252) // Red
        RecordingState.IDLE -> Color(0xFF00F2FE) // Cyan
    }

    val micBtnGradient = when (recordingState) {
        RecordingState.RECORDING -> Brush.linearGradient(
            colors = listOf(Color(0xFFA855F7), Color(0xFFEC4899))
        )
        RecordingState.ERROR -> Brush.linearGradient(
            colors = listOf(Color(0xFFFF5252), Color(0xFFFF8A80))
        )
        RecordingState.IDLE -> if (isEnabled) {
            Brush.linearGradient(
                colors = listOf(Color(0xFF00F2FE), Color(0xFF4FACFE))
            )
        } else {
            Brush.linearGradient(
                colors = listOf(Color(0xFF2A2438), Color(0xFF1E1C24))
            )
        }
        RecordingState.TRANSCRIBING -> Brush.linearGradient(
            colors = listOf(Color(0xFF2A2438), Color(0xFF1E1C24))
        )
    }

    val statusTextColor = when (recordingState) {
        RecordingState.RECORDING -> Color(0xFFD18CFF)
        RecordingState.TRANSCRIBING -> Color(0xFFEC4899)
        RecordingState.ERROR -> Color(0xFFFF5252)
        RecordingState.IDLE -> if (apiKey.isNullOrBlank()) Color(0xFFFF5252) else Color(0xFF4FACFE)
    }

    val statusText = when (recordingState) {
        RecordingState.IDLE -> if (apiKey.isNullOrBlank()) "API KEY REQUIRED IN APP" else "READY TO LISTEN"
        RecordingState.RECORDING -> "LISTENING ($timeText)"
        RecordingState.TRANSCRIBING -> "TRANSCRIBING..."
        RecordingState.ERROR -> errorMessage ?: "AN ERROR OCCURRED"
    }

    val localOnBackspace by rememberUpdatedState(onBackspace)
    val localOnBackspaceSelect by rememberUpdatedState(onBackspaceSelect)
    val localOnBackspaceDeleteSelected by rememberUpdatedState(onBackspaceDeleteSelected)
    val localOnBackspaceCancelSelect by rememberUpdatedState(onBackspaceCancelSelect)

    // Keyboard container layout
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgThemeColor)
            .drawWithContent {
                drawContent()
                // Premium glass top-border
                drawLine(
                    color = outlineBorderColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(top = 16.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Reactive Aura Visualizer
        Box(
            modifier = Modifier
                .size(180.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background Aura Canvas: Draws Radial Glow and Wavy Rings
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(currentGlowScale)
            ) {
                val width = size.width
                val height = size.height
                val centerX = width / 2f
                val centerY = height / 2f

                // A. Radial Glow
                val glowRadius = 80.dp.toPx()
                val actualAlpha = when (recordingState) {
                    RecordingState.RECORDING -> 0.6f
                    RecordingState.TRANSCRIBING -> 0.3f
                    RecordingState.ERROR -> 0.5f
                    RecordingState.IDLE -> 0.2f
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryGlowColor.copy(alpha = actualAlpha), Color.Transparent),
                        center = Offset(centerX, centerY),
                        radius = glowRadius
                    ),
                    radius = glowRadius
                )

                // B. Ping ring animation when recording
                if (recordingState == RecordingState.RECORDING) {
                    drawCircle(
                        color = Color.White.copy(alpha = pingAlpha),
                        radius = 40.dp.toPx() * pingScale,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // C. Wavy Aura Rings
                // Base amplitudes: scale wave height based on audio amplitude when recording
                val ampFactor = if (recordingState == RecordingState.RECORDING) amplitude else 0f
                
                val waveAmp1 = if (recordingState == RecordingState.RECORDING) (2.dp.toPx() + ampFactor * 10.dp.toPx()) else 0.8.dp.toPx()
                val waveAmp2 = if (recordingState == RecordingState.RECORDING) (3.dp.toPx() + ampFactor * 14.dp.toPx()) else 1.2.dp.toPx()
                val waveAmp3 = if (recordingState == RecordingState.RECORDING) (4.dp.toPx() + ampFactor * 18.dp.toPx()) else 1.6.dp.toPx()

                // Ring 1 (Inner, Wavy)
                val ringColor1 = when (recordingState) {
                    RecordingState.RECORDING -> Color(0x99D18CFF)
                    RecordingState.ERROR -> Color(0x99FF8A80)
                    else -> Color(0x3300F2FE)
                }
                val path1 = createWavyPath(
                    centerX, centerY, 
                    baseRadius = 46.dp.toPx(), 
                    waveAmplitude = waveAmp1, 
                    frequency = 3, 
                    phase = phase1
                )
                drawPath(path = path1, color = ringColor1, style = Stroke(width = 2.dp.toPx()))

                // Ring 2 (Middle, Wavy)
                val ringColor2 = when (recordingState) {
                    RecordingState.RECORDING -> Color(0x66EC4899)
                    RecordingState.ERROR -> Color(0x66FF5252)
                    else -> Color(0x1A4FACFE)
                }
                val path2 = createWavyPath(
                    centerX, centerY, 
                    baseRadius = 56.dp.toPx(), 
                    waveAmplitude = waveAmp2, 
                    frequency = 4, 
                    phase = phase2
                )
                drawPath(path = path2, color = ringColor2, style = Stroke(width = 1.dp.toPx()))

                // Ring 3 (Outer, Wavy)
                val ringColor3 = when (recordingState) {
                    RecordingState.RECORDING -> Color(0x33D18CFF)
                    RecordingState.ERROR -> Color(0x33FFCDD2)
                    else -> Color(0x0D00F2FE)
                }
                val path3 = createWavyPath(
                    centerX, centerY, 
                    baseRadius = 66.dp.toPx(), 
                    waveAmplitude = waveAmp3, 
                    frequency = 5, 
                    phase = phase3
                )
                drawPath(path = path3, color = ringColor3, style = Stroke(width = 1.dp.toPx()))

                // D. Spinning Transcribing Arc
                if (recordingState == RecordingState.TRANSCRIBING) {
                    drawArc(
                        color = Color(0xFFD18CFF),
                        startAngle = transcribingRotation,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(centerX - 46.dp.toPx(), centerY - 46.dp.toPx()),
                        size = Size(92.dp.toPx(), 92.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            // Central Mic Button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(micBtnGradient)
                    .pointerInput(isEnabled) {
                        if (!isEnabled) return@pointerInput
                        detectTapGestures(
                            onPress = {
                                if (currentRecordingState == RecordingState.RECORDING) {
                                    currentOnStopRecording()
                                    return@detectTapGestures
                                }

                                currentOnStartRecording()
                                val startTime = System.currentTimeMillis()
                                val released = tryAwaitRelease()
                                if (released) {
                                    val duration = System.currentTimeMillis() - startTime
                                    if (duration > 500) {
                                        currentOnStopRecording()
                                    }
                                } else {
                                    currentOnCancelRecording()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Microphone SVG Icon inside button
                Canvas(modifier = Modifier.size(32.dp)) {
                    val w = size.width
                    val h = size.height
                    
                    // Mic main cylinder
                    val micPath = Path().apply {
                        moveTo(w * 0.35f, h * 0.2f)
                        lineTo(w * 0.65f, h * 0.2f)
                        arcTo(
                            rect = Rect(w * 0.35f, h * 0.1f, w * 0.65f, h * 0.4f),
                            startAngleDegrees = 180f,
                            sweepAngleDegrees = 180f,
                            forceMoveTo = false
                        )
                        lineTo(w * 0.65f, h * 0.55f)
                        arcTo(
                            rect = Rect(w * 0.35f, h * 0.45f, w * 0.65f, h * 0.65f),
                            startAngleDegrees = 0f,
                            sweepAngleDegrees = 180f,
                            forceMoveTo = false
                        )
                        close()
                    }
                    drawPath(
                        path = micPath,
                        color = if (recordingState == RecordingState.TRANSCRIBING) Color(0xFF6E6E7A) else Color.White
                    )
                    
                    // Mic U-shaped stand
                    val standPath = Path().apply {
                        moveTo(w * 0.25f, h * 0.45f)
                        arcTo(
                            rect = Rect(w * 0.25f, h * 0.35f, w * 0.75f, h * 0.75f),
                            startAngleDegrees = 180f,
                            sweepAngleDegrees = -180f,
                            forceMoveTo = true
                        )
                        moveTo(w * 0.5f, h * 0.75f)
                        lineTo(w * 0.5f, h * 0.85f)
                        moveTo(w * 0.35f, h * 0.85f)
                        lineTo(w * 0.65f, h * 0.85f)
                    }
                    drawPath(
                        path = standPath,
                        color = if (recordingState == RecordingState.TRANSCRIBING) Color(0xFF6E6E7A) else Color.White,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }

        // 2. Centered Status & Timer Text
        Text(
            text = statusText,
            color = statusTextColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Extra Controls Row (Keyboard selector & Backspace with Gestures)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Keyboard Switcher (Left)
            IconButton(
                onClick = {
                    val imeManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imeManager.showInputMethodPicker()
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF1C1B1F), CircleShape)
                    .border(1.dp, Color(0x0DFFFFFF), CircleShape)
            ) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    val path = Path().apply {
                        moveTo(2f, 5f)
                        lineTo(22f, 5f)
                        lineTo(22f, 17f)
                        lineTo(2f, 17f)
                        close()
                        // Space bar
                        moveTo(7f, 14f)
                        lineTo(17f, 14f)
                        // Keys
                        moveTo(5f, 8f); lineTo(7f, 8f)
                        moveTo(9f, 8f); lineTo(11f, 8f)
                        moveTo(13f, 8f); lineTo(15f, 8f)
                        moveTo(17f, 8f); lineTo(19f, 8f)
                        moveTo(6f, 11f); lineTo(8f, 11f)
                        moveTo(10f, 11f); lineTo(12f, 11f)
                        moveTo(14f, 11f); lineTo(16f, 11f)
                        moveTo(18f, 11f); lineTo(20f, 11f)
                    }
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.8f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // Backspace (Right) with Auto-repeat and Gboard Swipe-to-Delete
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF1C1B1F), CircleShape)
                    .border(1.dp, Color(0x0DFFFFFF), CircleShape)
                    .pointerInput(Unit) {
                        coroutineScope {
                            var autoRepeatJob: Job? = null
                            var startX = 0f
                            var isDragging = false
                            var currentWordsSelected = 0

                            awaitPointerEventScope {
                                while (true) {
                                    val downEvent = awaitFirstDown()
                                    startX = downEvent.position.x
                                    isDragging = false
                                    currentWordsSelected = 0
                                    swipeActive = false
                                    swipeWords = 0

                                    // Start auto-repeat timer for holding (400ms delay, then deletes every 60ms)
                                    autoRepeatJob = launch {
                                        delay(400)
                                        while (isActive) {
                                            localOnBackspace()
                                            delay(60)
                                        }
                                    }

                                    // Track drag movement
                                    var dragEvent: PointerInputChange? = null
                                    do {
                                        val event = awaitPointerEvent()
                                        dragEvent = event.changes.firstOrNull()
                                        if (dragEvent != null && dragEvent.pressed) {
                                            val currentX = dragEvent.position.x
                                            val deltaX = currentX - startX
                                            
                                            // If user dragged left past a threshold (e.g. 24dp)
                                            if (deltaX < -24.dp.toPx()) {
                                                autoRepeatJob?.cancel() // Cancel holding repeat
                                                isDragging = true
                                                
                                                // 1 word selected per 32dp drag
                                                val words = ((-deltaX - 24.dp.toPx()) / 32.dp.toPx()).toInt() + 1
                                                if (words != currentWordsSelected) {
                                                    currentWordsSelected = words
                                                    localOnBackspaceSelect(words)
                                                }
                                            } else if (isDragging && deltaX >= -12.dp.toPx()) {
                                                // Dragged back right to cancel selection
                                                currentWordsSelected = 0
                                                localOnBackspaceCancelSelect()
                                            }
                                            dragEvent.consume()
                                        }
                                    } while (dragEvent != null && dragEvent.pressed)

                                    // Released / Touch up!
                                    autoRepeatJob?.cancel()
                                    if (isDragging) {
                                        if (currentWordsSelected > 0) {
                                            localOnBackspaceDeleteSelected()
                                        } else {
                                            localOnBackspaceCancelSelect()
                                        }
                                    } else {
                                        // Regular tap
                                        localOnBackspace()
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(w * 0.35f, h * 0.2f)
                        lineTo(w * 0.9f, h * 0.2f)
                        lineTo(w * 0.9f, h * 0.8f)
                        lineTo(w * 0.35f, h * 0.8f)
                        lineTo(w * 0.1f, h * 0.5f)
                        close()
                        // X mark
                        moveTo(w * 0.5f, h * 0.38f)
                        lineTo(w * 0.75f, h * 0.62f)
                        moveTo(w * 0.75f, h * 0.38f)
                        lineTo(w * 0.5f, h * 0.62f)
                    }
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.8f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 4. Primary Utility Keys (Space & Enter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Space button (60% width)
            Button(
                onClick = onSpace,
                modifier = Modifier
                    .weight(0.6f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C1B1F),
                    contentColor = Color.White.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0x0DFFFFFF))
            ) {
                Text("Space", fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
            }

            // Enter button (40% width)
            Button(
                onClick = onEnter,
                modifier = Modifier
                    .weight(0.4f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C1B1F),
                    contentColor = Color.White.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0x0DFFFFFF))
            ) {
                Text("Enter", fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
            }
        }

        // 5. Bottom Keyboard Handle Bar
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
        
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x0DFFFFFF))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Dismiss arrow
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp)
            ) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(w * 0.25f, h * 0.35f)
                        lineTo(w * 0.5f, h * 0.6f)
                        lineTo(w * 0.75f, h * 0.35f)
                    }
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.6f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // Center drag handle
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 4.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
            )

            // Globe / Switch IME button
            IconButton(
                onClick = onSwitchKeyboard,
                modifier = Modifier.size(36.dp)
            ) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    val w = size.width
                    val h = size.height
                    val r = w * 0.4f
                    val cx = w / 2f
                    val cy = h / 2f
                    
                    drawCircle(
                        color = Color.White.copy(alpha = 0.6f),
                        radius = r,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    
                    drawLine(
                        color = Color.White.copy(alpha = 0.6f),
                        start = Offset(cx - r, cy),
                        end = Offset(cx + r, cy),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    
                    drawLine(
                        color = Color.White.copy(alpha = 0.6f),
                        start = Offset(cx, cy - r),
                        end = Offset(cx, cy + r),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    
                    drawOval(
                        color = Color.White.copy(alpha = 0.6f),
                        topLeft = Offset(cx - r * 0.5f, cy - r),
                        size = Size(r, r * 2f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }
    }
}

// Generates a path with sine-wave deviations for morphing organic aura visualizer shapes
private fun createWavyPath(
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    waveAmplitude: Float,
    frequency: Int,
    phase: Float
): Path {
    val path = Path()
    val steps = 80
    for (i in 0..steps) {
        val theta = (i.toFloat() / steps.toFloat()) * 2f * Math.PI.toFloat()
        // Compute perturbed radius using sine wave
        val r = baseRadius + waveAmplitude * kotlin.math.sin(frequency * theta + phase)
        val x = centerX + r * kotlin.math.cos(theta)
        val y = centerY + r * kotlin.math.sin(theta)
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    return path
}
