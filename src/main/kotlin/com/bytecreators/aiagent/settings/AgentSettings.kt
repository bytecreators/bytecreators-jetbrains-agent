package com.bytecreators.aiagent.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "AIAgentSettings",
    storages = [Storage("aiAgent.xml")]
)
class AgentSettings : PersistentStateComponent<AgentSettings.State> {
    
    data class State(
        var selectedProvider: String = "OPENAI",
        var openaiModel: String = "gpt-4o-mini",
        var anthropicModel: String = "claude-sonnet-4-20250514",
        var customModel: String = "",
        var customEndpoint: String = "",
        var maxTokens: Int = 4096,
        var temperature: Double = 0.7,
        var autoApplyChanges: Boolean = false,
        var showToolCalls: Boolean = true,
        var streamResponses: Boolean = true,
        var debugLogging: Boolean = false
    )
    
    private var myState = State()
    
    override fun getState(): State = myState
    
    override fun loadState(state: State) {
        myState = state
    }
    
    var selectedProvider: String
        get() = myState.selectedProvider
        set(value) { myState.selectedProvider = value }
    
    var openaiModel: String
        get() = myState.openaiModel
        set(value) { myState.openaiModel = value }
    
    var anthropicModel: String
        get() = myState.anthropicModel
        set(value) { myState.anthropicModel = value }
    
    var customModel: String
        get() = myState.customModel
        set(value) { myState.customModel = value }
    
    var customEndpoint: String
        get() = myState.customEndpoint
        set(value) { myState.customEndpoint = value }
    
    var maxTokens: Int
        get() = myState.maxTokens
        set(value) { myState.maxTokens = value }
    
    var temperature: Double
        get() = myState.temperature
        set(value) { myState.temperature = value }
    
    var autoApplyChanges: Boolean
        get() = myState.autoApplyChanges
        set(value) { myState.autoApplyChanges = value }
    
    var showToolCalls: Boolean
        get() = myState.showToolCalls
        set(value) { myState.showToolCalls = value }
    
    var streamResponses: Boolean
        get() = myState.streamResponses
        set(value) { myState.streamResponses = value }
    
    var debugLogging: Boolean
        get() = myState.debugLogging
        set(value) { myState.debugLogging = value }
    
    companion object {
        fun getInstance(): AgentSettings {
            return ApplicationManager.getApplication().getService(AgentSettings::class.java)
        }
    }
}
