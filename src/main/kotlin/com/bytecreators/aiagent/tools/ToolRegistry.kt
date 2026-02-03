package com.bytecreators.aiagent.tools

/**
 * Registry of all available tools for the agent
 */
object ToolRegistry {
    
    private val tools: List<Tool> = listOf(
        FileReadTool(),
        FileWriteTool(),
        ListFilesTool(),
        SearchCodeTool(),
        TerminalTool()
    )
    
    fun getAllTools(): List<Tool> = tools
    
    fun getTool(name: String): Tool? = tools.find { it.name == name }
    
    fun getToolDefinitions() = tools.map { it.toDefinition() }
}
