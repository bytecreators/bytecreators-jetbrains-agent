package com.bytecreators.aiagent.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Secure storage for API keys using IntelliJ's PasswordSafe
 */
object ApiKeySecureStore {
    
    private const val SUBSYSTEM = "ByteCreatorsAIAgent"
    
    enum class Provider {
        OPENAI,
        ANTHROPIC,
        CUSTOM
    }
    
    private fun createCredentialAttributes(provider: Provider): CredentialAttributes {
        return CredentialAttributes(
            generateServiceName(SUBSYSTEM, provider.name)
        )
    }
    
    fun saveApiKey(provider: Provider, apiKey: String) {
        val credentialAttributes = createCredentialAttributes(provider)
        val credentials = Credentials("api-key", apiKey)
        PasswordSafe.instance.set(credentialAttributes, credentials)
    }
    
    fun getApiKey(provider: Provider): String? {
        val credentialAttributes = createCredentialAttributes(provider)
        return PasswordSafe.instance.getPassword(credentialAttributes)
    }
    
    fun removeApiKey(provider: Provider) {
        val credentialAttributes = createCredentialAttributes(provider)
        PasswordSafe.instance.set(credentialAttributes, null)
    }
    
    fun hasApiKey(provider: Provider): Boolean {
        return !getApiKey(provider).isNullOrBlank()
    }
}
