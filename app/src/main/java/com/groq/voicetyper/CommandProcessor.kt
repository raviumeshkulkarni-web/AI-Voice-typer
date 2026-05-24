package com.groq.voicetyper

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CommandProcessor {
    private const val TAG = "CommandProcessor"
    private const val CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"
    const val MODEL_LLAMA = "llama-3.3-70b-versatile"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun processCommand(
        apiKey: String,
        commandText: String,
        contextText: String,
        modelName: String = MODEL_LLAMA
    ): Result<CommandResult> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = """
                You are the AI Command Processor for Fluence, a voice-typing keyboard app.
                The user is editing a text box. You are given:
                - "text_context": The text currently present in the text box before the cursor.
                - "command": The user's spoken instruction.

                Analyze the command and context, then return a JSON object matching this schema:
                {
                  "action": "DELETE_CHARS" | "REPLACE_TEXT" | "INSERT_TEXT" | "SELECT_ALL" | "MOVE_CURSOR" | "SEND",
                  "delete_count": <number of characters to delete before the cursor, if DELETE_CHARS>,
                  "replacement_text": "<the text to replace current context with, if REPLACE_TEXT>",
                  "insertion_text": "<new text to insert at the cursor, if INSERT_TEXT>",
                  "cursor_position": "START" | "END" (if MOVE_CURSOR)
                }

                Rules:
                1. If the user wants to delete text (e.g., "delete last word", "remove the last two sentences"), calculate the exact number of characters to delete from the end of the provided "text_context" and set action="DELETE_CHARS" and "delete_count" to that number.
                2. If the user wants to rewrite/format/translate existing text (e.g., "make it professional", "translate this to French", "correct grammar"), apply the edit to "text_context" and set action="REPLACE_TEXT" with the rewritten result in "replacement_text".
                3. If the user wants to generate new content from scratch (e.g., "draft an email to Bob", "explain photosynthesis", "write a thank-you note"), generate the text and set action="INSERT_TEXT" with the generated content in "insertion_text".
                4. Respond ONLY with valid JSON. Do not include any explanations or conversational filler outside the JSON.
            """.trimIndent()

            val userContent = JSONObject().apply {
                put("text_context", contextText)
                put("command", commandText)
            }.toString()

            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            }

            val requestJson = JSONObject().apply {
                put("model", modelName)
                put("messages", messagesArray)
                put("response_format", JSONObject().apply {
                    put("type", "json_object")
                })
                put("temperature", 0.1) // Low temperature for deterministic actions
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(CHAT_URL)
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                if (!response.isSuccessful) {
                    val errorMsg = parseErrorMessage(bodyString) ?: "HTTP Error ${response.code}"
                    return@withContext Result.failure(Exception(errorMsg))
                }

                if (bodyString.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("Response body is empty"))
                }

                try {
                    val responseJson = JSONObject(bodyString)
                    val choices = responseJson.getJSONArray("choices")
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    val content = message.getString("content")

                    val contentJson = JSONObject(content)
                    val action = contentJson.getString("action")
                    val deleteCount = contentJson.optInt("delete_count", 0)
                    
                    // JSON optString returns "null" as string or empty if missing, standardizing to null:
                    val replacementText = contentJson.optString("replacement_text", "").takeIf { it.isNotEmpty() && it != "null" }
                    val insertionText = contentJson.optString("insertion_text", "").takeIf { it.isNotEmpty() && it != "null" }
                    val cursorPosition = contentJson.optString("cursor_position", "").takeIf { it.isNotEmpty() && it != "null" }

                    val result = CommandResult(
                        action = action,
                        deleteCount = deleteCount,
                        replacementText = replacementText,
                        insertionText = insertionText,
                        cursorPosition = cursorPosition
                    )
                    Result.success(result)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse chat response: $bodyString", e)
                    Result.failure(Exception("Failed to parse command response"))
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network request failed", e)
            Result.failure(Exception("Network error during AI command processing"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            Result.failure(Exception(e.localizedMessage ?: "Unexpected error during command processing"))
        }
    }

    private fun parseErrorMessage(responseBody: String?): String? {
        if (responseBody.isNullOrEmpty()) return null
        return try {
            val json = JSONObject(responseBody)
            if (json.has("error")) {
                val errorObj = json.getJSONObject("error")
                errorObj.optString("message", "").takeIf { it.isNotEmpty() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

data class CommandResult(
    val action: String,
    val deleteCount: Int = 0,
    val replacementText: String? = null,
    val insertionText: String? = null,
    val cursorPosition: String? = null
)
