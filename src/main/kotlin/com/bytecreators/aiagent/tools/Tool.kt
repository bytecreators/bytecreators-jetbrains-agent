package com.bytecreators.aiagent.tools

import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject

/**
 * Base interface for all agent tools
 */
interface Tool {
    val name: String
    val description: String
    val parameters: Map<String, ToolParameter>
    
    suspend fun execute(project: Project, arguments: JsonObject): ToolResult
}

data class ToolParameter(
    val type: String,
    val description: String,
    val required: Boolean = false,
    val enumValues: List<String>? = null
)

sealed class ToolResult {
    data class Success(val output: String) : ToolResult()
    data class Error(val message: String) : ToolResult()
}

fun Tool.toDefinition(): com.bytecreators.aiagent.llm.ToolDefinition {
    return com.bytecreators.aiagent.llm.ToolDefinition(
        name = name,
        description = description,
        parameters = parameters.mapValues { (_, param) ->
            com.bytecreators.aiagent.llm.ParameterDefinition(
                type = param.type,
                description = param.description,
                required = param.required,
                enumValues = param.enumValues
            )
        }
    )
}
