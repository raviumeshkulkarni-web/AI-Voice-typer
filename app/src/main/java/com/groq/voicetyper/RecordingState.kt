package com.groq.voicetyper

/**
 * Represents the current state of voice recording and transcription.
 * Used by both the IME keyboard view and the floating bubble service.
 */
enum class RecordingState {
    IDLE,
    RECORDING,
    TRANSCRIBING,
    ERROR
}
