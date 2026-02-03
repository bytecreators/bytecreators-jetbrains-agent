package com.bytecreators.aiagent.tools

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

class FileWriteTool : Tool {
    override val name = "write_file"
    override val description = "Write content to a file. Creates the file and parent directories if they don't exist. Overwrites existing content."
    override val parameters = mapOf(
        "path" to ToolParameter(
            type = "string",
            description = "The absolute or relative path to the file to write",
            required = true
        ),
        "content" to ToolParameter(
            type = "string",
            description = "The content to write to the file",
            required = true
        )
    )
    
    override suspend fun execute(project: Project, arguments: JsonObject): ToolResult {
        val pathArg = arguments["path"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: path")
        val content = arguments["content"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: content")
        
        val file = resolveFile(project, pathArg)
        
        return try {
            writeAction {
                // Ensure parent directories exist
                file.parentFile?.mkdirs()
                
                // Write the file
                file.writeText(content)
                
                // Refresh virtual file system so IDE sees the change
                val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
                virtualFile?.refresh(false, false)
                
                ToolResult.Success("Successfully wrote ${content.length} characters to $pathArg")
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to write file: ${e.message}")
        }
    }
    
    private fun resolveFile(project: Project, path: String): File {
        val file = File(path)
        if (file.isAbsolute) {
            return file
        }
        
        val basePath = project.basePath ?: return file
        return File(basePath, path)
    }
}
