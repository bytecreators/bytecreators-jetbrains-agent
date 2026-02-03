package com.bytecreators.aiagent.llm

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Base interface for all LLM providers
 */
interface LLMProvider {
    val name: String
    val supportsToolCalling: Boolean
    
    suspend fun chat(
        messages: List<Message>,
        tools: List<ToolDefinition> = emptyList(),
        maxTokens: Int = 4096,
        temperature: Double = 0.7
    ): LLMResponse
    
    suspend fun streamChat(
        messages: List<Message>,
        tools: List<ToolDefinition> = emptyList(),
        maxTokens: Int = 4096,
        temperature: Double = 0.7
    ): Flow<StreamChunk>
}

@Serializable
data class Message(
    val role: Role,
    val content: String,
    val toolCallId: String? = null,
    val toolCalls: List<ToolCall>? = null
)

@Serializable
enum class Role {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterDefinition>
)

@Serializable
data class ParameterDefinition(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val enumValues: List<String>? = null
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String // JSON string
)

sealed class LLMResponse {
    data class Text(val content: String) : LLMResponse()
    data class ToolUse(val toolCalls: List<ToolCall>) : LLMResponse()
    data class Error(val message: String, val code: String? = null) : LLMResponse()
}

sealed class StreamChunk {
    data class TextDelta(val delta: String) : StreamChunk()
    data class ToolCallStart(val id: String, val name: String) : StreamChunk()
    data class ToolCallDelta(val id: String, val argumentsDelta: String) : StreamChunk()
    data class ToolCallEnd(val id: String) : StreamChunk()
    data object Done : StreamChunk()
    data class Error(val message: String) : StreamChunk()
}
