package com.bytecreators.aiagent.tools

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class FileReadTool : Tool {
    override val name = "read_file"
    override val description = "Read the contents of a file. Returns the file content as text."
    override val parameters = mapOf(
        "path" to ToolParameter(
            type = "string",
            description = "The absolute or relative path to the file to read",
            required = true
        ),
        "start_line" to ToolParameter(
            type = "integer",
            description = "Optional start line number (1-indexed) to read from",
            required = false
        ),
        "end_line" to ToolParameter(
            type = "integer",
            description = "Optional end line number (1-indexed) to read to",
            required = false
        )
    )
    
    override suspend fun execute(project: Project, arguments: JsonObject): ToolResult {
        val pathArg = arguments["path"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: path")
        
        val startLine = arguments["start_line"]?.jsonPrimitive?.content?.toIntOrNull()
        val endLine = arguments["end_line"]?.jsonPrimitive?.content?.toIntOrNull()
        
        val file = resolveFile(project, pathArg)
            ?: return ToolResult.Error("File not found: $pathArg")
        
        return try {
            readAction {
                val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file)
                    ?: return@readAction ToolResult.Error("Cannot access file: $pathArg")
                
                if (virtualFile.isDirectory) {
                    return@readAction ToolResult.Error("Path is a directory, not a file: $pathArg")
                }
                
                // Check file size to prevent token explosion (limit to ~100KB)
                val maxFileSize = 100_000L
                if (virtualFile.length > maxFileSize) {
                    return@readAction ToolResult.Error(
                        "File too large (${virtualFile.length} bytes). Maximum size is $maxFileSize bytes. " +
                        "Use start_line and end_line parameters to read specific sections."
                    )
                }
                
                val content = String(virtualFile.contentsToByteArray(), Charsets.UTF_8)
                val lines = content.lines()
                
                // Limit output to prevent massive token consumption
                val maxLines = 500
                
                if (startLine != null || endLine != null) {
                    val start = (startLine ?: 1).coerceAtLeast(1) - 1
                    val end = (endLine ?: lines.size).coerceAtMost(lines.size)
                    
                    val selectedLines = lines.subList(start, end)
                    if (selectedLines.size > maxLines) {
                        val truncated = selectedLines.take(maxLines)
                        ToolResult.Success(
                            truncated.joinToString("\n") + 
                            "\n\n[Truncated: showing $maxLines of ${selectedLines.size} lines. Use start_line/end_line to navigate.]"
                        )
                    } else {
                        ToolResult.Success(selectedLines.joinToString("\n"))
                    }
                } else {
                    if (lines.size > maxLines) {
                        val truncated = lines.take(maxLines)
                        ToolResult.Success(
                            truncated.joinToString("\n") + 
                            "\n\n[Truncated: showing $maxLines of ${lines.size} lines. Use start_line/end_line to read more.]"
                        )
                    } else {
                        ToolResult.Success(content)
                    }
                }
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to read file: ${e.message}")
        }
    }
    
    private fun resolveFile(project: Project, path: String): File? {
        val file = File(path)
        if (file.isAbsolute && file.exists()) {
            return file
        }
        
        val basePath = project.basePath ?: return null
        val resolved = File(basePath, path)
        return if (resolved.exists()) resolved else null
    }
}
