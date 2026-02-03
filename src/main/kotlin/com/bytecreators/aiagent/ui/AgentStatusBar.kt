package com.bytecreators.aiagent.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Status bar showing the current agent phase and progress
 */
class AgentStatusBar : JPanel() {
    
    enum class Phase {
        IDLE,
        PLANNING,
        EXECUTING,
        COMPLETE,
        ERROR
    }
    
    private val phaseLabel = JLabel("Ready")
    private val stepLabel = JLabel("")
    private val progressBar = JProgressBar()
    private val cancelButton = JButton(AllIcons.Actions.Suspend)
    private val timeLabel = JLabel("")
    
    private var currentPhase = Phase.IDLE
    private var startTime: Long = 0
    private var timer: Timer? = null
    
    var onCancel: (() -> Unit)? = null
    
    init {
        layout = BorderLayout(10, 0)
        background = JBColor.namedColor("ToolWindow.HeaderTab.selectedBackground", JBColor(0x3574F0, 0x3574F0))
        border = EmptyBorder(8, 12, 8, 12)
        
        // Phase icon and label
        val phasePanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            isOpaque = false
            add(phaseLabel.apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = JBColor.namedColor("ToolWindow.HeaderTab.selectedForeground", Color.WHITE)
            })
        }
        
        // Progress section
        progressBar.apply {
            isIndeterminate = true
            preferredSize = Dimension(100, 4)
            isVisible = false
        }
        
        // Step counter
        stepLabel.apply {
            foreground = JBColor.namedColor("ToolWindow.HeaderTab.selectedForeground", Color.WHITE)
            font = font.deriveFont(11f)
        }
        
        // Time elapsed
        timeLabel.apply {
            foreground = JBColor.namedColor("ToolWindow.HeaderTab.selectedForeground", Color.WHITE).darker()
            font = font.deriveFont(10f)
        }
        
        // Cancel button
        cancelButton.apply {
            isVisible = false
            isBorderPainted = false
            isContentAreaFilled = false
            toolTipText = "Cancel generation"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener { onCancel?.invoke() }
        }
        
        val centerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 0)).apply {
            isOpaque = false
            add(progressBar)
            add(stepLabel)
            add(timeLabel)
        }
        
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
            isOpaque = false
            add(cancelButton)
        }
        
        add(phasePanel, BorderLayout.WEST)
        add(centerPanel, BorderLayout.CENTER)
        add(rightPanel, BorderLayout.EAST)
        
        setPhase(Phase.IDLE)
    }
    
    fun setPhase(phase: Phase, step: Int = 0, totalSteps: Int = 0) {
        currentPhase = phase
        
        SwingUtilities.invokeLater {
            when (phase) {
                Phase.IDLE -> {
                    phaseLabel.text = "🟢 Ready"
                    phaseLabel.icon = null
                    progressBar.isVisible = false
                    cancelButton.isVisible = false
                    stepLabel.text = ""
                    timeLabel.text = ""
                    stopTimer()
                    background = JBColor.namedColor("Panel.background", JBColor.background())
                    phaseLabel.foreground = JBColor.foreground()
                }
                Phase.PLANNING -> {
                    phaseLabel.text = "🎯 Planning..."
                    progressBar.isVisible = true
                    progressBar.isIndeterminate = true
                    cancelButton.isVisible = true
                    updateStepLabel(step, totalSteps)
                    startTimer()
                    background = JBColor(0x1E3A5F, 0x1E3A5F)
                    phaseLabel.foreground = Color.WHITE
                    stepLabel.foreground = Color.WHITE
                }
                Phase.EXECUTING -> {
                    phaseLabel.text = "⚡ Executing..."
                    progressBar.isVisible = true
                    progressBar.isIndeterminate = false
                    if (totalSteps > 0) {
                        progressBar.maximum = totalSteps
                        progressBar.value = step
                    }
                    cancelButton.isVisible = true
                    updateStepLabel(step, totalSteps)
                    background = JBColor(0x2E5D3B, 0x2E5D3B)
                    phaseLabel.foreground = Color.WHITE
                    stepLabel.foreground = Color.WHITE
                }
                Phase.COMPLETE -> {
                    phaseLabel.text = "✅ Complete"
                    progressBar.isVisible = false
                    cancelButton.isVisible = false
                    stepLabel.text = ""
                    stopTimer()
                    background = JBColor(0x3B7D4F, 0x3B7D4F)
                    phaseLabel.foreground = Color.WHITE
                }
                Phase.ERROR -> {
                    phaseLabel.text = "❌ Error"
                    progressBar.isVisible = false
                    cancelButton.isVisible = false
                    stopTimer()
                    background = JBColor(0x8B3A3A, 0x8B3A3A)
                    phaseLabel.foreground = Color.WHITE
                }
            }
            revalidate()
            repaint()
        }
    }
    
    fun updateProgress(step: Int, totalSteps: Int) {
        SwingUtilities.invokeLater {
            updateStepLabel(step, totalSteps)
            if (totalSteps > 0 && !progressBar.isIndeterminate) {
                progressBar.maximum = totalSteps
                progressBar.value = step
            }
        }
    }
    
    private fun updateStepLabel(step: Int, totalSteps: Int) {
        stepLabel.text = if (totalSteps > 0) "Step $step of $totalSteps" else if (step > 0) "Step $step" else ""
    }
    
    private fun startTimer() {
        startTime = System.currentTimeMillis()
        timer?.stop()
        timer = Timer(1000) { updateTimeLabel() }.apply { start() }
        updateTimeLabel()
    }
    
    private fun stopTimer() {
        timer?.stop()
        timer = null
    }
    
    private fun updateTimeLabel() {
        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        val minutes = elapsed / 60
        val seconds = elapsed % 60
        timeLabel.text = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }
}
