package com.bytecreators.aiagent.tools

import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.relativeTo

class ListFilesTool : Tool {
    override val name = "list_files"
    override val description = "List files and directories in a given path. Returns file names with indicators for directories."
    override val parameters = mapOf(
        "path" to ToolParameter(
            type = "string",
            description = "The directory path to list files from (relative to project root or absolute)",
            required = true
        ),
        "recursive" to ToolParameter(
            type = "boolean",
            description = "Whether to list files recursively (default: false)",
            required = false
        ),
        "pattern" to ToolParameter(
            type = "string",
            description = "Optional glob pattern to filter files (e.g., *.kt, **/*.java)",
            required = false
        )
    )
    
    override suspend fun execute(project: Project, arguments: JsonObject): ToolResult {
        val pathArg = arguments["path"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: path")
        val recursive = arguments["recursive"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val pattern = arguments["pattern"]?.jsonPrimitive?.content
        
        val dir = resolveDir(project, pathArg)
            ?: return ToolResult.Error("Directory not found: $pathArg")
        
        if (!dir.isDirectory) {
            return ToolResult.Error("Path is not a directory: $pathArg")
        }
        
        return try {
            val files = mutableListOf<String>()
            val basePath = dir.toPath()
            
            val stream = if (recursive) {
                Files.walk(basePath)
            } else {
                Files.list(basePath)
            }
            
            stream.use { paths ->
                paths.filter { it != basePath }
                    .filter { path ->
                        if (pattern != null) {
                            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
                            matcher.matches(path.fileName) || matcher.matches(path)
                        } else {
                            true
                        }
                    }
                    .forEach { path ->
                        val relativePath = path.relativeTo(basePath).toString()
                        val indicator = if (path.isDirectory()) "/" else ""
                        files.add("$relativePath$indicator")
                    }
            }
            
            if (files.isEmpty()) {
                ToolResult.Success("Directory is empty or no files match the pattern")
            } else {
                ToolResult.Success(files.sorted().joinToString("\n"))
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to list files: ${e.message}")
        }
    }
    
    private fun resolveDir(project: Project, path: String): File? {
        val file = File(path)
        if (file.isAbsolute && file.exists()) {
            return file
        }
        
        val basePath = project.basePath ?: return null
        val resolved = File(basePath, path)
        return if (resolved.exists()) resolved else null
    }
}
