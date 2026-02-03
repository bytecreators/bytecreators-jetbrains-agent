package com.bytecreators.aiagent.tools

import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit

class TerminalTool : Tool {
    override val name = "run_terminal"
    override val description = "Execute a shell command in the terminal. Use for running builds, tests, git commands, etc. Be careful with destructive commands."
    override val parameters = mapOf(
        "command" to ToolParameter(
            type = "string",
            description = "The command to execute",
            required = true
        ),
        "cwd" to ToolParameter(
            type = "string",
            description = "Working directory for the command (defaults to project root)",
            required = false
        ),
        "timeout" to ToolParameter(
            type = "integer",
            description = "Timeout in seconds (default: 60)",
            required = false
        )
    )
    
    override suspend fun execute(project: Project, arguments: JsonObject): ToolResult {
        val command = arguments["command"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: command")
        val cwdArg = arguments["cwd"]?.jsonPrimitive?.content
        val timeout = arguments["timeout"]?.jsonPrimitive?.content?.toLongOrNull() ?: 60
        
        val workingDir = if (cwdArg != null) {
            File(cwdArg).let { 
                if (it.isAbsolute) it else File(project.basePath ?: ".", cwdArg)
            }
        } else {
            File(project.basePath ?: ".")
        }
        
        if (!workingDir.exists()) {
            return ToolResult.Error("Working directory does not exist: ${workingDir.absolutePath}")
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val isWindows = System.getProperty("os.name").lowercase().contains("win")
                val processBuilder = if (isWindows) {
                    ProcessBuilder("cmd.exe", "/c", command)
                } else {
                    ProcessBuilder("sh", "-c", command)
                }
                
                processBuilder.directory(workingDir)
                processBuilder.redirectErrorStream(true)
                
                val process = processBuilder.start()
                val output = StringBuilder()
                
                process.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    var lineCount = 0
                    while (reader.readLine().also { line = it } != null && lineCount < 500) {
                        output.appendLine(line)
                        lineCount++
                    }
                    if (lineCount >= 500) {
                        output.appendLine("... output truncated (exceeded 500 lines)")
                    }
                }
                
                val completed = process.waitFor(timeout, TimeUnit.SECONDS)
                
                if (!completed) {
                    process.destroyForcibly()
                    return@withContext ToolResult.Error("Command timed out after $timeout seconds")
                }
                
                val exitCode = process.exitValue()
                val result = output.toString().trim()
                
                if (exitCode == 0) {
                    ToolResult.Success(if (result.isEmpty()) "Command completed successfully" else result)
                } else {
                    ToolResult.Error("Command exited with code $exitCode:\n$result")
                }
            } catch (e: Exception) {
                ToolResult.Error("Failed to execute command: ${e.message}")
            }
        }
    }
}
