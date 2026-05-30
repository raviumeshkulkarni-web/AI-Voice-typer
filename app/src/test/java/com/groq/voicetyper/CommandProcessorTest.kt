package com.groq.voicetyper

import org.junit.Assert.*
import org.junit.Test

class CommandProcessorTest {

    @Test
    fun testValidateCommandResult_validActionsPassThrough() {
        val deleteCommand = CommandResult(action = "DELETE_CHARS", deleteCount = 10)
        val validated = CommandProcessor.validateCommandResult(deleteCommand, 100)
        assertEquals("DELETE_CHARS", validated.action)
        assertEquals(10, validated.deleteCount)

        val insertCommand = CommandResult(action = "INSERT_TEXT", insertionText = "hello")
        val validatedInsert = CommandProcessor.validateCommandResult(insertCommand, 100)
        assertEquals("INSERT_TEXT", validatedInsert.action)
        assertEquals("hello", validatedInsert.insertionText)
    }

    @Test
    fun testValidateCommandResult_unknownActionFallsBackToInsertText() {
        val badCommand = CommandResult(action = "DELETE_DATABASE", insertionText = "fallback text")
        val validated = CommandProcessor.validateCommandResult(badCommand, 100)
        assertEquals("INSERT_TEXT", validated.action)
        assertEquals("fallback text", validated.insertionText)
    }

    @Test
    fun testValidateCommandResult_clampsDeleteCountToContextLength() {
        val deleteCommand = CommandResult(action = "DELETE_CHARS", deleteCount = 50)
        
        // Context length is 30, so delete count should be clamped to 30
        val validated = CommandProcessor.validateCommandResult(deleteCommand, 30)
        assertEquals(30, validated.deleteCount)
    }

    @Test
    fun testValidateCommandResult_clampsDeleteCountToAbsoluteMax() {
        val deleteCommand = CommandResult(action = "DELETE_CHARS", deleteCount = 1500)
        
        // Absolute max is 1000, so even if context is 2000, it should clamp to 1000
        val validated = CommandProcessor.validateCommandResult(deleteCommand, 2000)
        assertEquals(1000, validated.deleteCount)
    }

    @Test
    fun testValidateCommandResult_clampsNegativeDeleteCountToZero() {
        val deleteCommand = CommandResult(action = "DELETE_CHARS", deleteCount = -5)
        val validated = CommandProcessor.validateCommandResult(deleteCommand, 100)
        assertEquals(0, validated.deleteCount)
    }

    @Test
    fun testValidateCommandResult_truncatesExtremelyLongTexts() {
        val hugeText = "a".repeat(6000)
        val insertCommand = CommandResult(action = "INSERT_TEXT", insertionText = hugeText)
        val validatedInsert = CommandProcessor.validateCommandResult(insertCommand, 100)
        assertEquals(5000, validatedInsert.insertionText?.length)

        val replaceCommand = CommandResult(action = "REPLACE_TEXT", replacementText = hugeText)
        val validatedReplace = CommandProcessor.validateCommandResult(replaceCommand, 100)
        assertEquals(5000, validatedReplace.replacementText?.length)
    }

    @Test
    fun testValidateCommandResult_replacesNullTextWithIdleAction() {
        val insertCommand = CommandResult(action = "INSERT_TEXT", insertionText = null)
        val validatedInsert = CommandProcessor.validateCommandResult(insertCommand, 100)
        assertEquals("IDLE", validatedInsert.action)

        val replaceCommand = CommandResult(action = "REPLACE_TEXT", replacementText = null)
        val validatedReplace = CommandProcessor.validateCommandResult(replaceCommand, 100)
        assertEquals("IDLE", validatedReplace.action)
    }

    @Test
    fun testValidateCommandResult_normalizesCursorPosition() {
        val moveCommand = CommandResult(action = "MOVE_CURSOR", cursorPosition = "start")
        val validated = CommandProcessor.validateCommandResult(moveCommand, 100)
        assertEquals("START", validated.cursorPosition)

        val moveBadCommand = CommandResult(action = "MOVE_CURSOR", cursorPosition = "middle")
        val validatedBad = CommandProcessor.validateCommandResult(moveBadCommand, 100)
        assertEquals("END", validatedBad.cursorPosition)
    }
}
