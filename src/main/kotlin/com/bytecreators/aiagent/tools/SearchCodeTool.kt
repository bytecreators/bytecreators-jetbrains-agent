package com.bytecreators.aiagent.tools

import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.File
import java.util.concurrent.TimeUnit

class SearchCodeTool : Tool {
    override val name = "search_code"
    override val description = "Search for a pattern in files. Uses grep-like search to find matches in the codebase."
    override val parameters = mapOf(
        "pattern" to ToolParameter(
            type = "string",
            description = "The text pattern to search for",
            required = true
        ),
        "path" to ToolParameter(
            type = "string",
            description = "The directory or file to search in (defaults to project root)",
            required = false
        ),
        "include" to ToolParameter(
            type = "string",
            description = "File pattern to include (e.g., *.kt, *.java)",
            required = false
        ),
        "case_sensitive" to ToolParameter(
            type = "boolean",
            description = "Whether search is case-sensitive (default: false)",
            required = false
        )
    )
    
    override suspend fun execute(project: Project, arguments: JsonObject): ToolResult {
        val pattern = arguments["pattern"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: pattern")
        val pathArg = arguments["path"]?.jsonPrimitive?.content
        val include = arguments["include"]?.jsonPrimitive?.content
        val caseSensitive = arguments["case_sensitive"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        
        val basePath = if (pathArg != null) {
            resolveDir(project, pathArg)?.absolutePath ?: project.basePath
        } else {
            project.basePath
        } ?: return ToolResult.Error("Cannot determine search path")
        
        return try {
            val results = searchInDirectory(File(basePath), pattern, include, caseSensitive)
            
            if (results.isEmpty()) {
                ToolResult.Success("No matches found for pattern: $pattern")
            } else {
                val output = results.take(50).joinToString("\n") { (file, lineNum, line) ->
                    "${file.relativeTo(File(basePath))}:$lineNum: $line"
                }
                
                val suffix = if (results.size > 50) "\n... and ${results.size - 50} more matches" else ""
                ToolResult.Success(output + suffix)
            }
        } catch (e: Exception) {
            ToolResult.Error("Search failed: ${e.message}")
        }
    }
    
    private fun searchInDirectory(
        dir: File,
        pattern: String,
        include: String?,
        caseSensitive: Boolean
    ): List<Triple<File, Int, String>> {
        val results = mutableListOf<Triple<File, Int, String>>()
        val regex = if (caseSensitive) {
            Regex(Regex.escape(pattern))
        } else {
            Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE)
        }
        
        dir.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                if (include != null) {
                    val globPattern = include.replace("*", ".*")
                    file.name.matches(Regex(globPattern))
                } else {
                    !file.name.startsWith(".") && 
                    !file.path.contains(".git") &&
                    !file.path.contains("node_modules") &&
                    !file.path.contains("build") &&
                    !file.path.contains("target")
                }
            }
            .forEach { file ->
                try {
                    file.readLines().forEachIndexed { index, line ->
                        if (regex.containsMatchIn(line)) {
                            results.add(Triple(file, index + 1, line.trim().take(200)))
                        }
                    }
                } catch (e: Exception) {
                    // Skip files that can't be read as text
                }
            }
        
        return results
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
