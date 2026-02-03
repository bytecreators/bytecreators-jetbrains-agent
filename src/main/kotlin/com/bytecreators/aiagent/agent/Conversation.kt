package com.bytecreators.aiagent.agent

import com.bytecreators.aiagent.llm.Message
import com.bytecreators.aiagent.llm.Role

/**
 * Manages conversation history and context for the agent
 */
class Conversation {
    private val messages = mutableListOf<Message>()
    
    private val systemPrompt = """
You are an expert AI coding assistant integrated into a JetBrains IDE. You help developers with coding tasks by:

1. Understanding the project structure and codebase
2. Reading and writing files
3. Searching for code patterns
4. Running terminal commands (builds, tests, git, etc.)
5. Providing clear explanations for your actions

Guidelines:
- Always explore the codebase before making changes to understand context
- Use tools to gather information before responding
- When modifying code, explain what you're changing and why
- Be careful with destructive operations (deleting files, force pushes, etc.)
- If a task requires multiple steps, work through them methodically
- When encountering errors, analyze them and suggest fixes
- Keep responses concise but informative

You have access to the following tools:
- read_file: Read contents of a file
- write_file: Write or create a file
- list_files: List files in a directory
- search_code: Search for patterns in the codebase
- run_terminal: Execute shell commands

Always use the appropriate tool for the task at hand. Think step by step.
    """.trimIndent()
    
    init {
        messages.add(Message(Role.SYSTEM, systemPrompt))
    }
    
    fun addUserMessage(content: String) {
        messages.add(Message(Role.USER, content))
    }
    
    fun addAssistantMessage(content: String) {
        messages.add(Message(Role.ASSISTANT, content))
    }
    
    fun addAssistantToolCallMessage(toolCalls: List<com.bytecreators.aiagent.llm.ToolCall>) {
        messages.add(Message(Role.ASSISTANT, "", toolCalls = toolCalls))
    }
    
    fun addToolResultMessage(toolCallId: String, result: String) {
        // Truncate very long tool results to prevent token explosion
        // Each message adds to context; tool results can be huge (file contents, search results)
        val maxResultLength = 8000 // ~2000 tokens max per tool result
        val truncatedResult = if (result.length > maxResultLength) {
            result.take(maxResultLength) + "\n\n[Result truncated - showing first $maxResultLength characters of ${result.length} total]"
        } else {
            result
        }
        messages.add(Message(Role.TOOL, truncatedResult, toolCallId = toolCallId))
    }
    
    fun getMessages(): List<Message> = messages.toList()
    
    fun clear() {
        messages.clear()
        messages.add(Message(Role.SYSTEM, systemPrompt))
    }
    
    fun getLastUserMessage(): String? {
        return messages.lastOrNull { it.role == Role.USER }?.content
    }
    
    fun isEmpty(): Boolean = messages.size <= 1 // Only system message
}
