package com.bytecreators.aiagent.ui

import com.bytecreators.aiagent.agent.Agent
import com.bytecreators.aiagent.agent.AgentEvent
import com.bytecreators.aiagent.llm.LLMProviderFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AgentToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = AgentToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
    
    override fun shouldBeAvailable(project: Project): Boolean = true
}

class AgentToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var agent: Agent? = null
    private var currentJob: Job? = null
    private val chatPanel: ChatPanel
    
    init {
        chatPanel = ChatPanel(
            onSendMessage = { message -> sendMessage(message) },
            onStopGeneration = { stopGeneration() },
            onClearChat = { clearChat() }
        )
        
        setContent(chatPanel.component)
        
        // Initialize agent if provider is configured
        initializeAgent()
    }
    
    private fun initializeAgent(showErrorIfNotConfigured: Boolean = true) {
        val provider = LLMProviderFactory.createProvider()
        if (provider != null) {
            agent = Agent(project, provider)
            chatPanel.setConfigured(true)
        } else {
            chatPanel.setConfigured(false)
            if (showErrorIfNotConfigured) {
                chatPanel.showMessage(
                    "AI Agent not configured. Please go to Settings > Tools > ByteCreators AI Coding Agent to configure your API keys.",
                    isError = true
                )
            }
        }
    }
    
    private fun sendMessage(message: String) {
        // Try to initialize agent if not already done (in case settings were updated)
        if (agent == null) {
            initializeAgent(showErrorIfNotConfigured = false)
        }
        
        val currentAgent = agent
        if (currentAgent == null) {
            chatPanel.showMessage("Agent not configured. Please configure your API keys in Settings, then try again.", isError = true)
            return
        }
        
        // Cancel any existing generation
        currentJob?.cancel()
        
        chatPanel.addUserMessage(message)
        chatPanel.setLoading(true)
        
        currentJob = scope.launch {
            try {
                currentAgent.chat(message).collect { event ->
                    handleAgentEvent(event)
                }
            } catch (e: CancellationException) {
                chatPanel.showMessage("Generation stopped by user.", isError = false)
                throw e // Re-throw to properly cancel the coroutine
            } catch (e: Exception) {
                chatPanel.showMessage("Error: ${e.message}", isError = true)
            } finally {
                chatPanel.setLoading(false)
                currentJob = null
            }
        }
    }
    
    private fun handleAgentEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.Thinking -> {
                chatPanel.showThinking()
            }
            is AgentEvent.TextDelta -> {
                chatPanel.appendToCurrentMessage(event.delta)
            }
            is AgentEvent.TextResponse -> {
                chatPanel.addAssistantMessage(event.content)
            }
            is AgentEvent.ToolCallStart -> {
                chatPanel.showToolCall(event.toolName, event.arguments)
            }
            is AgentEvent.ToolCallResult -> {
                chatPanel.showToolResult(event.toolName, event.result, event.success)
            }
            is AgentEvent.Error -> {
                chatPanel.showMessage(event.message, isError = true)
            }
            is AgentEvent.Done -> {
                chatPanel.hideThinking()
            }
        }
    }
    
    private fun stopGeneration() {
        currentJob?.let { job ->
            job.cancel()
            chatPanel.hideThinking()
            chatPanel.setLoading(false)
        }
    }
    
    private fun clearChat() {
        agent?.clearConversation()
        chatPanel.clearMessages()
    }
    
    fun dispose() {
        scope.cancel()
    }
}
