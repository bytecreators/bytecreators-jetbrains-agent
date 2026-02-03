package com.bytecreators.aiagent.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent
import javax.swing.JPanel

class AgentSettingsConfigurable : Configurable {
    
    private var mainPanel: JPanel? = null
    private var providerCombo: ComboBox<String>? = null
    private var openaiKeyField: JBPasswordField? = null
    private var anthropicKeyField: JBPasswordField? = null
    private var customKeyField: JBPasswordField? = null
    private var customEndpointField: JBTextField? = null
    private var openaiModelField: JBTextField? = null
    private var anthropicModelField: JBTextField? = null
    private var customModelField: JBTextField? = null
    
    private val settings = AgentSettings.getInstance()
    
    override fun getDisplayName(): String = "ByteCreators AI Coding Agent"
    
    override fun createComponent(): JComponent {
        mainPanel = panel {
            group("LLM Provider") {
                row("Provider:") {
                    providerCombo = comboBox(listOf("OPENAI", "ANTHROPIC", "CUSTOM"))
                        .bindItem(settings::selectedProvider.toNullableProperty())
                        .component
                }
            }
            
            group("OpenAI Settings") {
                row("API Key:") {
                    openaiKeyField = cell(JBPasswordField())
                        .columns(COLUMNS_LARGE)
                        .component
                    openaiKeyField?.text = ApiKeySecureStore.getApiKey(ApiKeySecureStore.Provider.OPENAI) ?: ""
                }
                row("Model:") {
                    openaiModelField = textField()
                        .bindText(settings::openaiModel)
                        .comment("e.g., gpt-4o-mini, gpt-4o, gpt-4-turbo")
                        .component
                }
            }
            
            group("Anthropic Settings") {
                row("API Key:") {
                    anthropicKeyField = cell(JBPasswordField())
                        .columns(COLUMNS_LARGE)
                        .component
                    anthropicKeyField?.text = ApiKeySecureStore.getApiKey(ApiKeySecureStore.Provider.ANTHROPIC) ?: ""
                }
                row("Model:") {
                    anthropicModelField = textField()
                        .bindText(settings::anthropicModel)
                        .comment("e.g., claude-sonnet-4-20250514, claude-3-5-sonnet-20241022")
                        .component
                }
            }
            
            group("Custom/Local Provider Settings") {
                row("API Key:") {
                    customKeyField = cell(JBPasswordField())
                        .columns(COLUMNS_LARGE)
                        .comment("Leave empty if not required")
                        .component
                    customKeyField?.text = ApiKeySecureStore.getApiKey(ApiKeySecureStore.Provider.CUSTOM) ?: ""
                }
                row("Endpoint URL:") {
                    customEndpointField = textField()
                        .bindText(settings::customEndpoint)
                        .columns(COLUMNS_LARGE)
                        .comment("e.g., http://localhost:11434/v1 for Ollama")
                        .component
                }
                row("Model:") {
                    customModelField = textField()
                        .bindText(settings::customModel)
                        .comment("e.g., llama3.2, codellama")
                        .component
                }
            }
            
            group("Agent Behavior") {
                row {
                    checkBox("Auto-apply code changes")
                        .bindSelected(settings::autoApplyChanges)
                        .comment("Automatically apply file changes without confirmation")
                }
                row {
                    checkBox("Show tool calls in chat")
                        .bindSelected(settings::showToolCalls)
                }
                row {
                    checkBox("Stream responses")
                        .bindSelected(settings::streamResponses)
                }
                row {
                    checkBox("Enable debug logging")
                        .bindSelected(settings::debugLogging)
                        .comment("Log all API requests and responses to idea.log")
                }
            }
        }
        
        return mainPanel!!
    }
    
    override fun isModified(): Boolean {
        val openaiKeyModified = openaiKeyField?.let {
            String(it.password) != (ApiKeySecureStore.getApiKey(ApiKeySecureStore.Provider.OPENAI) ?: "")
        } ?: false
        
        val anthropicKeyModified = anthropicKeyField?.let {
            String(it.password) != (ApiKeySecureStore.getApiKey(ApiKeySecureStore.Provider.ANTHROPIC) ?: "")
        } ?: false
        
        val customKeyModified = customKeyField?.let {
            String(it.password) != (ApiKeySecureStore.getApiKey(ApiKeySecureStore.Provider.CUSTOM) ?: "")
        } ?: false
        
        return openaiKeyModified || anthropicKeyModified || customKeyModified ||
               providerCombo?.selectedItem != settings.selectedProvider ||
               openaiModelField?.text != settings.openaiModel ||
               anthropicModelField?.text != settings.anthropicModel ||
               customModelField?.text != settings.customModel ||
               customEndpointField?.text != settings.customEndpoint
    }
    
    override fun apply() {
        // Save API keys securely
        openaiKeyField?.let {
            val key = String(it.password)
            if (key.isNotBlank()) {
                ApiKeySecureStore.saveApiKey(ApiKeySecureStore.Provider.OPENAI, key)
            }
        }
        
        anthropicKeyField?.let {
            val key = String(it.password)
            if (key.isNotBlank()) {
                ApiKeySecureStore.saveApiKey(ApiKeySecureStore.Provider.ANTHROPIC, key)
            }
        }
        
        customKeyField?.let {
            val key = String(it.password)
            if (key.isNotBlank()) {
                ApiKeySecureStore.saveApiKey(ApiKeySecureStore.Provider.CUSTOM, key)
            }
        }
        
        // Save other settings
        settings.selectedProvider = providerCombo?.selectedItem as? String ?: "OPENAI"
        settings.openaiModel = openaiModelField?.text ?: "gpt-4o-mini"
        settings.anthropicModel = anthropicModelField?.text ?: "claude-sonnet-4-20250514"
        settings.customModel = customModelField?.text ?: ""
        settings.customEndpoint = customEndpointField?.text ?: ""
    }
    
    override fun reset() {
        providerCombo?.selectedItem = settings.selectedProvider
        openaiKeyField?.text = ApiKeySecureStore.getApiKey(ApiKeySecureStore.Provider.OPENAI) ?: ""
        anthropicKeyField?.text = ApiKeySecureStore.getApiKey(ApiKeySecureStore.Provider.ANTHROPIC) ?: ""
        customKeyField?.text = ApiKeySecureStore.getApiKey(ApiKeySecureStore.Provider.CUSTOM) ?: ""
        openaiModelField?.text = settings.openaiModel
        anthropicModelField?.text = settings.anthropicModel
        customModelField?.text = settings.customModel
        customEndpointField?.text = settings.customEndpoint
    }
}
