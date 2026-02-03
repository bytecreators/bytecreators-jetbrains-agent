package com.bytecreators.aiagent.llm

import com.bytecreators.aiagent.settings.AgentSettings
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

class AnthropicProvider(
    private val apiKey: String,
    private val model: String = "claude-sonnet-4-20250514"
) : LLMProvider {
    
    override val name: String = "Anthropic"
    override val supportsToolCalling: Boolean = true
    
    private val logger = Logger.getInstance(AnthropicProvider::class.java)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    companion object {
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        private const val API_VERSION = "2023-06-01"
    }
    
    override suspend fun chat(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        maxTokens: Int,
        temperature: Double
    ): LLMResponse {
        val requestBody = buildRequestBody(messages, tools, maxTokens, temperature, stream = false)
        val debugEnabled = AgentSettings.getInstance().debugLogging
        
        if (debugEnabled) {
            logger.info("[AI Agent API Request] POST $API_URL")
            logger.info("[AI Agent API Request Body] $requestBody")
        }
        
        val request = Request.Builder()
            .url(API_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", API_VERSION)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    // Always log API errors
                    logger.warn("[AI Agent API Error] ${response.code} - $errorBody")
                    return LLMResponse.Error("API error: ${response.code} - $errorBody")
                }
                
                val responseBody = response.body?.string() ?: return LLMResponse.Error("Empty response")
                if (debugEnabled) {
                    logger.info("[AI Agent API Response] $responseBody")
                }
                parseResponse(responseBody)
            }
        } catch (e: Exception) {
            // Always log exceptions
            logger.error("[AI Agent API Exception] ${e.message}", e)
            LLMResponse.Error("Request failed: ${e.message}")
        }
    }
    
    override suspend fun streamChat(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        maxTokens: Int,
        temperature: Double
    ): Flow<StreamChunk> = flow {
        val requestBody = buildRequestBody(messages, tools, maxTokens, temperature, stream = true)
        val debugEnabled = AgentSettings.getInstance().debugLogging
        
        if (debugEnabled) {
            logger.info("[AI Agent API Stream Request] POST $API_URL")
            logger.info("[AI Agent API Stream Request Body] $requestBody")
        }
        
        val request = Request.Builder()
            .url(API_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", API_VERSION)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
        
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    // Always log stream errors
                    logger.warn("[AI Agent API Stream Error] ${response.code} - $errorBody")
                    emit(StreamChunk.Error("API error: ${response.code} - $errorBody"))
                    return@use
                }
                
                val reader = response.body?.byteStream()?.bufferedReader()
                    ?: run {
                        emit(StreamChunk.Error("Empty response stream"))
                        return@use
                    }
                
                if (debugEnabled) {
                    logger.info("[AI Agent API Stream Started] Connection established")
                }
                
                parseStreamResponse(reader).collect { chunk ->
                    emit(chunk)
                }
                
                if (debugEnabled) {
                    logger.info("[AI Agent API Stream Completed]")
                }
            }
        } catch (e: Exception) {
            // Always log stream exceptions
            logger.error("[AI Agent API Stream Exception] ${e.message}", e)
            emit(StreamChunk.Error("Stream failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
    
    private fun buildRequestBody(
        messages: List<Message>,
        tools: List<ToolDefinition>,
        maxTokens: Int,
        temperature: Double,
        stream: Boolean
    ): String {
        // Extract system message
        val systemMessage = messages.find { it.role == Role.SYSTEM }?.content
        val nonSystemMessages = messages.filter { it.role != Role.SYSTEM }
        
        return buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("stream", stream)
            
            systemMessage?.let { put("system", it) }
            
            putJsonArray("messages") {
                for (msg in nonSystemMessages) {
                    addJsonObject {
                        put("role", when (msg.role) {
                            Role.USER -> "user"
                            Role.ASSISTANT -> "assistant"
                            Role.TOOL -> "user" // Tool results go as user messages
                            else -> "user"
                        })
                        
                        if (msg.role == Role.TOOL && msg.toolCallId != null) {
                            // Tool result format for Anthropic
                            putJsonArray("content") {
                                addJsonObject {
                                    put("type", "tool_result")
                                    put("tool_use_id", msg.toolCallId)
                                    put("content", msg.content)
                                }
                            }
                        } else if (msg.toolCalls != null && msg.toolCalls.isNotEmpty()) {
                            // Assistant message with tool use
                            putJsonArray("content") {
                                for (call in msg.toolCalls) {
                                    addJsonObject {
                                        put("type", "tool_use")
                                        put("id", call.id)
                                        put("name", call.name)
                                        put("input", json.parseToJsonElement(call.arguments))
                                    }
                                }
                            }
                        } else {
                            put("content", msg.content)
                        }
                    }
                }
            }
            
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    for (tool in tools) {
                        addJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            putJsonObject("input_schema") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    for ((paramName, paramDef) in tool.parameters) {
                                        putJsonObject(paramName) {
                                            put("type", paramDef.type)
                                            put("description", paramDef.description)
                                            paramDef.enumValues?.let { values ->
                                                putJsonArray("enum") {
                                                    values.forEach { add(it) }
                                                }
                                            }
                                        }
                                    }
                                }
                                putJsonArray("required") {
                                    for ((paramName, paramDef) in tool.parameters) {
                                        if (paramDef.required) add(paramName)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.toString()
    }
    
    private fun parseResponse(responseBody: String): LLMResponse {
        return try {
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val content = jsonResponse["content"]?.jsonArray ?: return LLMResponse.Error("No content in response")
            
            val toolCalls = mutableListOf<ToolCall>()
            val textParts = mutableListOf<String>()
            
            for (block in content) {
                val blockObj = block.jsonObject
                when (blockObj["type"]?.jsonPrimitive?.content) {
                    "text" -> {
                        textParts.add(blockObj["text"]?.jsonPrimitive?.content ?: "")
                    }
                    "tool_use" -> {
                        toolCalls.add(ToolCall(
                            id = blockObj["id"]?.jsonPrimitive?.content ?: "",
                            name = blockObj["name"]?.jsonPrimitive?.content ?: "",
                            arguments = blockObj["input"]?.toString() ?: "{}"
                        ))
                    }
                }
            }
            
            if (toolCalls.isNotEmpty()) {
                LLMResponse.ToolUse(toolCalls)
            } else {
                LLMResponse.Text(textParts.joinToString(""))
            }
        } catch (e: Exception) {
            LLMResponse.Error("Failed to parse response: ${e.message}")
        }
    }
    
    private fun parseStreamResponse(reader: BufferedReader): Flow<StreamChunk> = flow {
        reader.useLines { lines ->
            for (line in lines) {
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data.isEmpty()) continue
                    
                    try {
                        val jsonData = json.parseToJsonElement(data).jsonObject
                        val eventType = jsonData["type"]?.jsonPrimitive?.content
                        
                        when (eventType) {
                            "content_block_start" -> {
                                val contentBlock = jsonData["content_block"]?.jsonObject
                                if (contentBlock?.get("type")?.jsonPrimitive?.content == "tool_use") {
                                    val id = contentBlock["id"]?.jsonPrimitive?.content ?: ""
                                    val name = contentBlock["name"]?.jsonPrimitive?.content ?: ""
                                    emit(StreamChunk.ToolCallStart(id, name))
                                }
                            }
                            "content_block_delta" -> {
                                val delta = jsonData["delta"]?.jsonObject
                                when (delta?.get("type")?.jsonPrimitive?.content) {
                                    "text_delta" -> {
                                        val text = delta["text"]?.jsonPrimitive?.content ?: ""
                                        emit(StreamChunk.TextDelta(text))
                                    }
                                    "input_json_delta" -> {
                                        val partialJson = delta["partial_json"]?.jsonPrimitive?.content ?: ""
                                        // We need to track current tool call ID
                                        // For simplicity, emit as text delta for now
                                        emit(StreamChunk.TextDelta("")) // placeholder
                                    }
                                }
                            }
                            "content_block_stop" -> {
                                val index = jsonData["index"]?.jsonPrimitive?.int
                                // Tool call ended
                            }
                            "message_stop" -> {
                                emit(StreamChunk.Done)
                            }
                        }
                    } catch (e: Exception) {
                        // Skip malformed chunks
                    }
                }
            }
        }
    }
}
