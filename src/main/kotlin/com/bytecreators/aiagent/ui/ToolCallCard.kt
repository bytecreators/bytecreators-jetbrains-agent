package com.bytecreators.aiagent.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Collapsible card for displaying tool calls with expand/collapse functionality
 */
class ToolCallCard(
    private val toolName: String,
    private val arguments: String,
    private var result: String? = null,
    private var success: Boolean? = null
) : JPanel() {
    
    private var isExpanded = false
    private val headerPanel: JPanel
    private val contentPanel: JPanel
    private val expandIcon: JLabel
    private val statusIcon: JLabel
    private val resultPanel: JPanel
    
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    
    init {
        layout = BorderLayout()
        background = JBColor.background()
        border = BorderFactory.createCompoundBorder(
            LineBorder(JBColor.border(), 1, true),
            EmptyBorder(0, 0, 0, 0)
        )
        
        // Header with tool name and status
        expandIcon = JLabel(AllIcons.General.ArrowRight)
        statusIcon = JLabel(AllIcons.General.InlineRefreshHover)
        
        val toolIcon = getToolIcon(toolName)
        val toolLabel = JBLabel(toolName).apply {
            font = font.deriveFont(Font.BOLD, 12f)
            foreground = JBColor.foreground()
        }
        
        val headerLeft = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            isOpaque = false
            add(expandIcon)
            add(JLabel(toolIcon))
            add(toolLabel)
        }
        
        val headerRight = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
            isOpaque = false
            add(statusIcon)
        }
        
        headerPanel = JPanel(BorderLayout()).apply {
            background = JBColor.namedColor("EditorPane.background", JBColor(0xF7F8FA, 0x2B2D30))
            border = EmptyBorder(8, 10, 8, 10)
            add(headerLeft, BorderLayout.WEST)
            add(headerRight, BorderLayout.EAST)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    toggleExpanded()
                }
                override fun mouseEntered(e: MouseEvent) {
                    background = JBColor.namedColor("EditorPane.background", JBColor(0xE8EAED, 0x3C3F41))
                }
                override fun mouseExited(e: MouseEvent) {
                    background = JBColor.namedColor("EditorPane.background", JBColor(0xF7F8FA, 0x2B2D30))
                }
            })
        }
        
        // Content panel with arguments
        val argumentsText = formatJson(arguments)
        val argumentsArea = createCodeArea(argumentsText, "Arguments")
        
        // Result panel (initially hidden)
        resultPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            isVisible = false
        }
        
        contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = JBColor.background()
            border = EmptyBorder(10, 15, 10, 15)
            isVisible = false
            add(argumentsArea)
            add(Box.createVerticalStrut(10))
            add(resultPanel)
        }
        
        add(headerPanel, BorderLayout.NORTH)
        add(contentPanel, BorderLayout.CENTER)
        
        // Set max size to prevent stretching
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }
    
    fun setResult(resultText: String, isSuccess: Boolean) {
        result = resultText
        success = isSuccess
        
        SwingUtilities.invokeLater {
            statusIcon.icon = if (isSuccess) AllIcons.General.InspectionsOK else AllIcons.General.Error
            
            resultPanel.removeAll()
            val truncated = if (resultText.length > 500) resultText.take(500) + "\n... (truncated)" else resultText
            resultPanel.add(createCodeArea(truncated, if (isSuccess) "Result ✓" else "Error ✗"), BorderLayout.CENTER)
            resultPanel.isVisible = true
            
            revalidate()
            repaint()
        }
    }
    
    fun setLoading() {
        SwingUtilities.invokeLater {
            statusIcon.icon = AllIcons.Process.Step_1
            // Animate spinner
            val icons = arrayOf(
                AllIcons.Process.Step_1, AllIcons.Process.Step_2, AllIcons.Process.Step_3,
                AllIcons.Process.Step_4, AllIcons.Process.Step_5, AllIcons.Process.Step_6,
                AllIcons.Process.Step_7, AllIcons.Process.Step_8
            )
            var index = 0
            Timer(100) { 
                statusIcon.icon = icons[index % icons.size]
                index++
            }.start()
        }
    }
    
    private fun toggleExpanded() {
        isExpanded = !isExpanded
        contentPanel.isVisible = isExpanded
        expandIcon.icon = if (isExpanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
        revalidate()
        repaint()
    }
    
    private fun createCodeArea(code: String, label: String): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            background = JBColor.namedColor("EditorPane.background", JBColor(0xF0F0F0, 0x1E1F22))
            border = BorderFactory.createCompoundBorder(
                LineBorder(JBColor.border(), 1, true),
                EmptyBorder(8, 10, 8, 10)
            )
        }
        
        val labelPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(JBLabel(label).apply {
                foreground = JBColor.GRAY
                font = font.deriveFont(10f)
            }, BorderLayout.WEST)
            
            val copyBtn = JButton(AllIcons.Actions.Copy).apply {
                isContentAreaFilled = false
                isBorderPainted = false
                preferredSize = Dimension(20, 20)
                toolTipText = "Copy to clipboard"
                addActionListener {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(java.awt.datatransfer.StringSelection(code), null)
                }
            }
            add(copyBtn, BorderLayout.EAST)
        }
        
        val codeArea = JTextArea(code).apply {
            isEditable = false
            font = Font("JetBrains Mono", Font.PLAIN, 11)
            background = JBColor.namedColor("EditorPane.background", JBColor(0xF0F0F0, 0x1E1F22))
            foreground = JBColor.foreground()
            lineWrap = true
            wrapStyleWord = true
            border = EmptyBorder(5, 0, 0, 0)
        }
        
        panel.add(labelPanel, BorderLayout.NORTH)
        panel.add(codeArea, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun formatJson(jsonStr: String): String {
        return try {
            val element = Json.parseToJsonElement(jsonStr)
            json.encodeToString(JsonElement.serializer(), element)
        } catch (e: Exception) {
            jsonStr
        }
    }
    
    private fun getToolIcon(name: String): Icon {
        return when {
            name.contains("file", ignoreCase = true) -> AllIcons.Actions.MenuOpen
            name.contains("search", ignoreCase = true) -> AllIcons.Actions.Search
            name.contains("terminal", ignoreCase = true) || name.contains("command", ignoreCase = true) -> AllIcons.Debugger.Console
            name.contains("list", ignoreCase = true) -> AllIcons.Actions.ListFiles
            name.contains("write", ignoreCase = true) -> AllIcons.Actions.Edit
            else -> AllIcons.General.GearPlain
        }
    }
}
