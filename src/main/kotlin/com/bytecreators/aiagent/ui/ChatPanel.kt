package com.bytecreators.aiagent.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.text.html.HTMLEditorKit

class ChatPanel(
    private val onSendMessage: (String) -> Unit,
    private val onStopGeneration: () -> Unit,
    private val onClearChat: () -> Unit
) {
    val component: JComponent
    
    private val messagesPanel: JPanel
    private val inputArea: JBTextArea
    private val sendButton: JButton
    private val stopButton: JButton
    private val clearButton: JButton
    private val scrollPane: JBScrollPane
    private val statusBar: AgentStatusBar
    
    private var isLoading = false
    private var isConfigured = true
    private var currentMessageBuilder: StringBuilder? = null
    private var currentMessagePanel: JPanel? = null
    private var currentToolCard: ToolCallCard? = null
    private var stepCount = 0
    
    private val markdownParser = Parser.builder().build()
    private val htmlRenderer = HtmlRenderer.builder().build()
    
    init {
        // Status bar at top
        statusBar = AgentStatusBar().apply {
            onCancel = { onStopGeneration() }
        }
        
        // Messages container with modern styling
        messagesPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = JBColor.namedColor("Panel.background", JBColor.background())
            border = EmptyBorder(10, 10, 10, 10)
        }
        
        scrollPane = JBScrollPane(messagesPanel).apply {
            border = null
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            background = JBColor.namedColor("Panel.background", JBColor.background())
        }
        
        // Modern input area
        inputArea = JBTextArea(3, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            border = EmptyBorder(12, 12, 12, 12)
            font = Font("JetBrains Mono", Font.PLAIN, 13)
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER && e.isControlDown) {
                        sendMessage()
                        e.consume()
                    }
                }
            })
        }
        
        val inputScrollPane = JBScrollPane(inputArea).apply {
            preferredSize = Dimension(0, 90)
            border = BorderFactory.createCompoundBorder(
                LineBorder(JBColor.border(), 1),
                EmptyBorder(0, 0, 0, 0)
            )
        }
        
        // Modern buttons
        sendButton = createModernButton("Send", AllIcons.Actions.Execute, JBColor(0x3574F0, 0x3574F0)).apply {
            addActionListener { sendMessage() }
        }
        
        stopButton = createModernButton("Stop", AllIcons.Actions.Suspend, JBColor(0xE53935, 0xE53935)).apply {
            addActionListener { onStopGeneration() }
            isVisible = false
        }
        
        clearButton = JButton(AllIcons.Actions.GC).apply {
            toolTipText = "Clear chat"
            isContentAreaFilled = false
            isBorderPainted = false
            addActionListener { onClearChat() }
        }
        
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 5)).apply {
            isOpaque = false
            add(clearButton)
            add(stopButton)
            add(sendButton)
        }
        
        val inputPanel = JPanel(BorderLayout(0, 5)).apply {
            isOpaque = false
            border = EmptyBorder(10, 10, 10, 10)
            add(inputScrollPane, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }
        
        // Main panel with header
        val headerPanel = JPanel(BorderLayout()).apply {
            background = JBColor.namedColor("ToolWindow.Header.background", JBColor.background())
            border = EmptyBorder(0, 0, 0, 0)
            
            val titleLabel = JBLabel("🤖 AI Agent").apply {
                font = font.deriveFont(Font.BOLD, 14f)
                border = EmptyBorder(10, 15, 10, 10)
            }
            
            val settingsBtn = JButton(AllIcons.General.GearPlain).apply {
                isContentAreaFilled = false
                isBorderPainted = false
                toolTipText = "Settings"
            }
            
            add(titleLabel, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                isOpaque = false
                add(settingsBtn)
            }, BorderLayout.EAST)
        }
        
        component = JPanel(BorderLayout()).apply {
            add(headerPanel, BorderLayout.NORTH)
            add(JPanel(BorderLayout()).apply {
                add(statusBar, BorderLayout.NORTH)
                add(scrollPane, BorderLayout.CENTER)
            }, BorderLayout.CENTER)
            add(inputPanel, BorderLayout.SOUTH)
            preferredSize = Dimension(420, 650)
        }
        
        showWelcomeMessage()
    }
    
    private fun createModernButton(text: String, icon: Icon, color: JBColor): JButton {
        return JButton(text, icon).apply {
            background = color
            foreground = Color.WHITE
            isFocusPainted = false
            font = font.deriveFont(Font.BOLD, 12f)
            border = EmptyBorder(8, 16, 8, 16)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
    }
    
    private fun showWelcomeMessage() {
        val welcomeCard = createMessageCard(
            """
            <h3>👋 Welcome to ByteCreators AI Agent</h3>
            <p>I can help you with:</p>
            <ul>
                <li>📂 Reading and writing files</li>
                <li>🔍 Searching through your codebase</li>
                <li>💻 Running terminal commands</li>
                <li>📝 Generating and refactoring code</li>
            </ul>
            <p><b>Tip:</b> Press <kbd>Ctrl+Enter</kbd> to send</p>
            """.trimIndent(),
            isUser = false,
            isWelcome = true
        )
        messagesPanel.add(welcomeCard)
        messagesPanel.add(Box.createVerticalStrut(10))
    }
    
    private fun sendMessage() {
        if (!isConfigured) {
            Messages.showWarningDialog(
                "Please configure your API keys in Settings > Tools > ByteCreators AI Coding Agent",
                "AI Agent Not Configured"
            )
            return
        }
        
        val message = inputArea.text.trim()
        if (message.isNotEmpty() && !isLoading) {
            inputArea.text = ""
            stepCount = 0
            onSendMessage(message)
        }
    }
    
    fun addUserMessage(message: String) {
        val card = createMessageCard(
            "<p style=\"margin: 0;\">${escapeHtml(message).replace("\n", "<br/>")}</p>",
            isUser = true
        )
        messagesPanel.add(card)
        messagesPanel.add(Box.createVerticalStrut(10))
        scrollToBottom()
    }
    
    fun addAssistantMessage(message: String) {
        currentMessageBuilder = null
        currentMessagePanel = null
        
        val htmlContent = renderMarkdown(message)
        val card = createMessageCard(htmlContent, isUser = false)
        messagesPanel.add(card)
        messagesPanel.add(Box.createVerticalStrut(10))
        scrollToBottom()
    }
    
    fun appendToCurrentMessage(delta: String) {
        if (currentMessageBuilder == null) {
            currentMessageBuilder = StringBuilder()
            currentMessagePanel = createMessageCard("", isUser = false)
            messagesPanel.add(currentMessagePanel)
            messagesPanel.add(Box.createVerticalStrut(10))
        }
        
        currentMessageBuilder!!.append(delta)
        updateCurrentMessage()
    }
    
    private fun updateCurrentMessage() {
        val content = currentMessageBuilder?.toString() ?: return
        val htmlContent = renderMarkdown(content)
        
        // Find the editor pane inside the message panel and update it
        currentMessagePanel?.let { panel ->
            val editorPane = findEditorPane(panel)
            editorPane?.text = wrapHtml(htmlContent, isUser = false)
        }
        scrollToBottom()
    }
    
    private fun findEditorPane(container: Container): JEditorPane? {
        for (comp in container.components) {
            if (comp is JEditorPane) return comp
            if (comp is Container) {
                findEditorPane(comp)?.let { return it }
            }
        }
        return null
    }
    
    fun showThinking() {
        stepCount++
        statusBar.setPhase(AgentStatusBar.Phase.PLANNING, stepCount)
    }
    
    fun hideThinking() {
        // Status handled by setPhase
    }
    
    fun showToolCall(toolName: String, arguments: String) {
        statusBar.setPhase(AgentStatusBar.Phase.EXECUTING, stepCount)
        
        val card = ToolCallCard(toolName, arguments)
        card.setLoading()
        currentToolCard = card
        
        messagesPanel.add(card)
        messagesPanel.add(Box.createVerticalStrut(8))
        messagesPanel.revalidate()
        scrollToBottom()
    }
    
    fun showToolResult(toolName: String, result: String, success: Boolean) {
        currentToolCard?.setResult(result, success)
        currentToolCard = null
        scrollToBottom()
    }
    
    fun showMessage(message: String, isError: Boolean = false) {
        val color = if (isError) "#FFCDD2" else "#E3F2FD"
        val icon = if (isError) "⚠️" else "ℹ️"
        val borderColor = if (isError) "#E57373" else "#64B5F6"
        
        val card = JPanel(BorderLayout()).apply {
            background = JBColor.decode(color)
            border = BorderFactory.createCompoundBorder(
                LineBorder(JBColor.decode(borderColor), 1, true),
                EmptyBorder(10, 12, 10, 12)
            )
            maximumSize = Dimension(Int.MAX_VALUE, 60)
            
            add(JLabel("$icon ${escapeHtml(message)}").apply {
                foreground = if (isError) JBColor.decode("#B71C1C") else JBColor.decode("#1565C0")
            }, BorderLayout.CENTER)
        }
        
        messagesPanel.add(card)
        messagesPanel.add(Box.createVerticalStrut(8))
        messagesPanel.revalidate()
        scrollToBottom()
        
        if (isError) {
            statusBar.setPhase(AgentStatusBar.Phase.ERROR)
        }
    }
    
    private fun createMessageCard(content: String, isUser: Boolean, isWelcome: Boolean = false): JPanel {
        val cardColor = when {
            isWelcome -> JBColor.namedColor("Panel.background", JBColor.background())
            isUser -> JBColor(0xE3F2FD, 0x1E3A5F)
            else -> JBColor.namedColor("EditorPane.background", JBColor(0xFAFAFA, 0x2B2D30))
        }
        
        val borderColor = when {
            isWelcome -> JBColor.border()
            isUser -> JBColor(0x90CAF9, 0x3574F0)
            else -> JBColor.border()
        }
        
        val avatarText = if (isUser) "👤" else "🤖"
        val labelText = if (isUser) "You" else "AI Agent"
        
        return JPanel(BorderLayout()).apply {
            background = cardColor
            border = BorderFactory.createCompoundBorder(
                LineBorder(borderColor, 1, true),
                EmptyBorder(12, 14, 12, 14)
            )
            maximumSize = Dimension(Int.MAX_VALUE, Short.MAX_VALUE.toInt())
            
            // Header with avatar
            val header = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                isOpaque = false
                add(JLabel(avatarText))
                add(JBLabel(labelText).apply {
                    font = font.deriveFont(Font.BOLD, 12f)
                })
            }
            
            // Content
            val editorPane = JEditorPane().apply {
                contentType = "text/html"
                isEditable = false
                background = cardColor
                border = EmptyBorder(8, 0, 0, 0)
                
                val kit = HTMLEditorKit()
                editorKit = kit
                text = wrapHtml(content, isUser)
            }
            
            add(header, BorderLayout.NORTH)
            add(editorPane, BorderLayout.CENTER)
        }
    }
    
    private fun wrapHtml(content: String, isUser: Boolean): String {
        val textColor = if (isUser) "#1A237E" else "#333333"
        return """
            <html>
            <head>
                <style>
                    body { 
                        font-family: sans-serif; 
                        font-size: 12px; 
                        color: $textColor;
                        margin: 0;
                        padding: 0;
                    }
                    pre { 
                        background: #1E1F22; 
                        color: #A9B7C6;
                        padding: 10px; 
                        font-family: monospace;
                        font-size: 11px;
                    }
                    code { 
                        background: #E8E8E8; 
                        padding: 2px; 
                        font-family: monospace;
                    }
                    a { color: #3574F0; }
                    p { margin: 0; }
                    ul, ol { margin: 5px; padding-left: 20px; }
                </style>
            </head>
            <body>$content</body>
            </html>
        """.trimIndent()
    }
    
    fun clearMessages() {
        messagesPanel.removeAll()
        showWelcomeMessage()
        currentMessageBuilder = null
        currentMessagePanel = null
        currentToolCard = null
        stepCount = 0
        statusBar.setPhase(AgentStatusBar.Phase.IDLE)
        messagesPanel.revalidate()
        messagesPanel.repaint()
    }
    
    fun setLoading(loading: Boolean) {
        isLoading = loading
        sendButton.isVisible = !loading
        stopButton.isVisible = loading
        inputArea.isEnabled = !loading
        
        if (loading) {
            statusBar.setPhase(AgentStatusBar.Phase.PLANNING)
        } else {
            statusBar.setPhase(AgentStatusBar.Phase.COMPLETE)
            // Reset to idle after a delay
            Timer(2000) { 
                if (!isLoading) statusBar.setPhase(AgentStatusBar.Phase.IDLE) 
            }.apply { isRepeats = false; start() }
        }
    }
    
    fun setConfigured(configured: Boolean) {
        isConfigured = configured
    }
    
    private fun scrollToBottom() {
        SwingUtilities.invokeLater {
            val scrollBar = scrollPane.verticalScrollBar
            scrollBar.value = scrollBar.maximum
        }
    }
    
    private fun renderMarkdown(markdown: String): String {
        return try {
            val document = markdownParser.parse(markdown)
            htmlRenderer.render(document)
        } catch (e: Exception) {
            escapeHtml(markdown).replace("\n", "<br/>")
        }
    }
    
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
