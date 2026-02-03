package com.bytecreators.aiagent.agent

import com.bytecreators.aiagent.llm.*
import com.bytecreators.aiagent.tools.ToolRegistry
import com.bytecreators.aiagent.tools.ToolResult
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Main agent that orchestrates LLM and tool execution
 */
class Agent(
    private val project: Project,
    private val provider: LLMProvider
) {
    private val conversation = Conversation()
    private val json = Json { ignoreUnknownKeys = true }
    private val maxIterations = 10 // Prevent infinite loops
    
    /**
     * Send a message to the agent and get a streaming response
     */
    fun chat(userMessage: String): Flow<AgentEvent> = flow {
        conversation.addUserMessage(userMessage)
        emit(AgentEvent.Thinking)
        
        var iterations = 0
        var continueLoop = true
        
        while (continueLoop && iterations < maxIterations) {
            iterations++
            
            val response = provider.chat(
                messages = conversation.getMessages(),
                tools = ToolRegistry.getToolDefinitions(),
                maxTokens = 4096,
                temperature = 0.7
            )
            
            when (response) {
                is LLMResponse.Text -> {
                    conversation.addAssistantMessage(response.content)
                    emit(AgentEvent.TextResponse(response.content))
                    continueLoop = false
                }
                
                is LLMResponse.ToolUse -> {
                    conversation.addAssistantToolCallMessage(response.toolCalls)
                    
                    for (toolCall in response.toolCalls) {
                        emit(AgentEvent.ToolCallStart(toolCall.name, toolCall.arguments))
                        
                        val result = executeToolCall(toolCall)
                        
                        val resultText = when (result) {
                            is ToolResult.Success -> result.output
                            is ToolResult.Error -> "Error: ${result.message}"
                        }
                        
                        conversation.addToolResultMessage(toolCall.id, resultText)
                        emit(AgentEvent.ToolCallResult(toolCall.name, resultText, result is ToolResult.Success))
                    }
                    
                    // Continue the loop to get the next response
                    emit(AgentEvent.Thinking)
                }
                
                is LLMResponse.Error -> {
                    emit(AgentEvent.Error(response.message))
                    continueLoop = false
                }
            }
        }
        
        if (iterations >= maxIterations) {
            emit(AgentEvent.Error("Agent reached maximum iterations ($maxIterations). Stopping to prevent infinite loop."))
        }
        
        emit(AgentEvent.Done)
    }
    
    /**
     * Send a message and stream the response
     */
    fun streamChat(userMessage: String): Flow<AgentEvent> = flow {
        conversation.addUserMessage(userMessage)
        emit(AgentEvent.Thinking)
        
        var iterations = 0
        var continueLoop = true
        
        while (continueLoop && iterations < maxIterations) {
            iterations++
            
            val textBuilder = StringBuilder()
            val toolCalls = mutableListOf<ToolCall>()
            val toolCallArgumentBuilders = mutableMapOf<String, StringBuilder>()
            
            provider.streamChat(
                messages = conversation.getMessages(),
                tools = ToolRegistry.getToolDefinitions(),
                maxTokens = 4096,
                temperature = 0.7
            ).collect { chunk ->
                when (chunk) {
                    is StreamChunk.TextDelta -> {
                        textBuilder.append(chunk.delta)
                        emit(AgentEvent.TextDelta(chunk.delta))
                    }
                    is StreamChunk.ToolCallStart -> {
                        toolCallArgumentBuilders[chunk.id] = StringBuilder()
                        toolCalls.add(ToolCall(chunk.id, chunk.name, ""))
                        emit(AgentEvent.ToolCallStart(chunk.name, ""))
                    }
                    is StreamChunk.ToolCallDelta -> {
                        toolCallArgumentBuilders[chunk.id]?.append(chunk.argumentsDelta)
                    }
                    is StreamChunk.ToolCallEnd -> {
                        val args = toolCallArgumentBuilders[chunk.id]?.toString() ?: "{}"
                        val index = toolCalls.indexOfFirst { it.id == chunk.id }
                        if (index >= 0) {
                            toolCalls[index] = toolCalls[index].copy(arguments = args)
                        }
                    }
                    is StreamChunk.Done -> {
                        // Stream complete
                    }
                    is StreamChunk.Error -> {
                        emit(AgentEvent.Error(chunk.message))
                        continueLoop = false
                    }
                }
            }
            
            // Process results
            if (toolCalls.isNotEmpty()) {
                conversation.addAssistantToolCallMessage(toolCalls)
                
                for (toolCall in toolCalls) {
                    val result = executeToolCall(toolCall)
                    
                    val resultText = when (result) {
                        is ToolResult.Success -> result.output
                        is ToolResult.Error -> "Error: ${result.message}"
                    }
                    
                    conversation.addToolResultMessage(toolCall.id, resultText)
                    emit(AgentEvent.ToolCallResult(toolCall.name, resultText, result is ToolResult.Success))
                }
                
                emit(AgentEvent.Thinking)
            } else {
                val text = textBuilder.toString()
                if (text.isNotEmpty()) {
                    conversation.addAssistantMessage(text)
                }
                continueLoop = false
            }
        }
        
        emit(AgentEvent.Done)
    }
    
    private suspend fun executeToolCall(toolCall: ToolCall): ToolResult {
        val tool = ToolRegistry.getTool(toolCall.name)
            ?: return ToolResult.Error("Unknown tool: ${toolCall.name}")
        
        return try {
            val arguments = json.parseToJsonElement(toolCall.arguments).let {
                if (it is kotlinx.serialization.json.JsonObject) it
                else kotlinx.serialization.json.JsonObject(emptyMap())
            }
            tool.execute(project, arguments)
        } catch (e: Exception) {
            ToolResult.Error("Failed to execute tool: ${e.message}")
        }
    }
    
    fun clearConversation() {
        conversation.clear()
    }
    
    fun getConversation(): Conversation = conversation
}

sealed class AgentEvent {
    data object Thinking : AgentEvent()
    data class TextDelta(val delta: String) : AgentEvent()
    data class TextResponse(val content: String) : AgentEvent()
    data class ToolCallStart(val toolName: String, val arguments: String) : AgentEvent()
    data class ToolCallResult(val toolName: String, val result: String, val success: Boolean) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
    data object Done : AgentEvent()
}
