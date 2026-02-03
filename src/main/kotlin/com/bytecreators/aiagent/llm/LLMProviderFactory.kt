package com.bytecreators.aiagent.llm

import com.bytecreators.aiagent.settings.AgentSettings
import com.bytecreators.aiagent.settings.ApiKeySecureStore

/**
 * Factory for creating LLM providers based on settings
 */
object LLMProviderFactory {
    
    fun createProvider(): LLMProvider? {
        val settings = AgentSettings.getInstance()
        
        return when (settings.selectedProvider) {
            "OPENAI" -> {
                val apiKey = ApiKeySecureStore.getApiKey(ApiKeySecureStore.Provider.OPENAI)
                    ?: return null
                OpenAIProvider(
                    apiKey = apiKey,
                    model = settings.openaiModel
                )
            }
            "ANTHROPIC" -> {
                val apiKey = ApiKeySecureStore.getApiKey(ApiKeySecureStore.Provider.ANTHROPIC)
                    ?: return null
                AnthropicProvider(
                    apiKey = apiKey,
                    model = settings.anthropicModel
                )
            }
            "CUSTOM" -> {
                val apiKey = ApiKeySecureStore.getApiKey(ApiKeySecureStore.Provider.CUSTOM) ?: ""
                val endpoint = settings.customEndpoint
                if (endpoint.isBlank()) return null
                
                OpenAIProvider(
                    apiKey = apiKey,
                    model = settings.customModel,
                    baseUrl = endpoint
                )
            }
            else -> null
        }
    }
    
    fun isConfigured(): Boolean {
        val settings = AgentSettings.getInstance()
        
        return when (settings.selectedProvider) {
            "OPENAI" -> ApiKeySecureStore.hasApiKey(ApiKeySecureStore.Provider.OPENAI)
            "ANTHROPIC" -> ApiKeySecureStore.hasApiKey(ApiKeySecureStore.Provider.ANTHROPIC)
            "CUSTOM" -> settings.customEndpoint.isNotBlank()
            else -> false
        }
    }
}
