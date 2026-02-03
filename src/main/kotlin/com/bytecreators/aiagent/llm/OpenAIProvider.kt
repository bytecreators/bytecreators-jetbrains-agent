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

class OpenAIProvider(
    private val apiKey: String,
    private val model: String = "gpt-4o-mini",
    private val baseUrl: String = "https://api.openai.com/v1"
) : LLMProvider {
    
    override val name: String = "OpenAI"
    override val supportsToolCalling: Boolean = true
    
    private val logger = Logger.getInstance(OpenAIProvider::class.java)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
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
            logger.info("[AI Agent API Request] POST $baseUrl/chat/completions")
            logger.info("[AI Agent API Request Body] $requestBody")
        }
        
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
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
            logger.info("[AI Agent API Stream Request] POST $baseUrl/chat/completions")
            logger.info("[AI Agent API Stream Request Body] $requestBody")
        }
        
        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
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
        return buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("stream", stream)
            
            putJsonArray("messages") {
                for (msg in messages) {
                    addJsonObject {
                        put("role", msg.role.name.lowercase())
                        put("content", msg.content)
                        msg.toolCallId?.let { put("tool_call_id", it) }
                        msg.toolCalls?.let { calls ->
                            putJsonArray("tool_calls") {
                                for (call in calls) {
                                    addJsonObject {
                                        put("id", call.id)
                                        put("type", "function")
                                        putJsonObject("function") {
                                            put("name", call.name)
                                            put("arguments", call.arguments)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    for (tool in tools) {
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                putJsonObject("parameters") {
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
            }
        }.toString()
    }
    
    private fun parseResponse(responseBody: String): LLMResponse {
        return try {
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val choices = jsonResponse["choices"]?.jsonArray ?: return LLMResponse.Error("No choices in response")
            val firstChoice = choices.firstOrNull()?.jsonObject ?: return LLMResponse.Error("Empty choices")
            val message = firstChoice["message"]?.jsonObject ?: return LLMResponse.Error("No message in choice")
            
            val toolCalls = message["tool_calls"]?.jsonArray
            if (toolCalls != null && toolCalls.isNotEmpty()) {
                val calls = toolCalls.map { callJson ->
                    val callObj = callJson.jsonObject
                    val function = callObj["function"]?.jsonObject!!
                    ToolCall(
                        id = callObj["id"]?.jsonPrimitive?.content ?: "",
                        name = function["name"]?.jsonPrimitive?.content ?: "",
                        arguments = function["arguments"]?.jsonPrimitive?.content ?: "{}"
                    )
                }
                LLMResponse.ToolUse(calls)
            } else {
                val content = message["content"]?.jsonPrimitive?.content ?: ""
                LLMResponse.Text(content)
            }
        } catch (e: Exception) {
            LLMResponse.Error("Failed to parse response: ${e.message}")
        }
    }
    
    private fun parseStreamResponse(reader: BufferedReader): Flow<StreamChunk> = flow {
        val toolCallBuilders = mutableMapOf<String, StringBuilder>()
        
        reader.useLines { lines ->
            for (line in lines) {
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        emit(StreamChunk.Done)
                        break
                    }
                    
                    try {
                        val jsonData = json.parseToJsonElement(data).jsonObject
                        val choices = jsonData["choices"]?.jsonArray ?: continue
                        val delta = choices.firstOrNull()?.jsonObject?.get("delta")?.jsonObject ?: continue
                        
                        // Check for tool calls
                        val toolCalls = delta["tool_calls"]?.jsonArray
                        if (toolCalls != null) {
                            for (toolCall in toolCalls) {
                                val callObj = toolCall.jsonObject
                                val index = callObj["index"]?.jsonPrimitive?.int ?: 0
                                val id = callObj["id"]?.jsonPrimitive?.content
                                val function = callObj["function"]?.jsonObject
                                
                                if (id != null) {
                                    val name = function?.get("name")?.jsonPrimitive?.content ?: ""
                                    emit(StreamChunk.ToolCallStart(id, name))
                                    toolCallBuilders[id] = StringBuilder()
                                }
                                
                                val argDelta = function?.get("arguments")?.jsonPrimitive?.content
                                if (argDelta != null) {
                                    // Find the ID for this index
                                    val currentId = toolCallBuilders.keys.elementAtOrNull(index)
                                    if (currentId != null) {
                                        toolCallBuilders[currentId]?.append(argDelta)
                                        emit(StreamChunk.ToolCallDelta(currentId, argDelta))
                                    }
                                }
                            }
                        }
                        
                        // Check for content
                        val content = delta["content"]?.jsonPrimitive?.content
                        if (content != null) {
                            emit(StreamChunk.TextDelta(content))
                        }
                    } catch (e: Exception) {
                        // Skip malformed chunks
                    }
                }
            }
        }
        
        // Emit tool call ends
        for (id in toolCallBuilders.keys) {
            emit(StreamChunk.ToolCallEnd(id))
        }
    }
}
