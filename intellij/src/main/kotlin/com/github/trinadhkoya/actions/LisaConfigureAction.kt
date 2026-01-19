package com.github.trinadhkoya.actions

import com.github.trinadhkoya.LisaLspServerSupportProvider
import com.github.trinadhkoya.lisaintellijplugin.services.MyProjectService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.intellij.platform.lsp.api.LspServerManager
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.jsonrpc.Endpoint
import java.util.concurrent.CompletableFuture

class LisaConfigureAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = com.github.trinadhkoya.lisaintellijplugin.settings.LisaPluginSettings.getInstance(project)

        val currentKey = when (settings.state.provider) {
            "openai" -> settings.state.openaiKey
            "claude" -> settings.state.claudeKey
            "gemini" -> settings.state.geminiKey
            "groq" -> settings.state.groqKey
            else -> ""
        }
        
        // Note: We pre-fill provider and model only. For security, we might not want to pre-fill the API key in the password field, 
        // or we can pre-fill it if we trust the screen privacy. Let's pre-fill it for UX.
        val provider = Messages.showInputDialog(project, "Enter AI Provider (openai, groq, gemini, claude):", "LISA Config", null, settings.state.provider, null) ?: return
        // If the user changed the provider in the first dialog, we don't have the key for the NEW provider easily available unless we re-query.
        // Simplified: The dialog flow is sequential. Provider first.
        
        // Let's get the stored key for the *selected* provider if possible? 
        // The InputDialog returns the *new* provider.
        val storedKeyForNewProvider = when (provider) {
             "openai" -> settings.state.openaiKey
             "claude" -> settings.state.claudeKey
             "gemini" -> settings.state.geminiKey
             "groq" -> settings.state.groqKey
             else -> ""
        }
        
        val apiKey = Messages.showPasswordDialog(project, "Enter API Key:", "LISA Config", null) ?: return // Note: PasswordDialog doesn't support initial value easily in this simple API
        val model = Messages.showInputDialog(project, "Enter Model Name:", "LISA Config", null, settings.state.model, null) ?: return

        // Save settings to persistent state
        settings.state.provider = provider
        settings.state.model = model
        
        if (apiKey.isNotEmpty()) {
            when (provider) {
                "openai" -> settings.state.openaiKey = apiKey
                "claude" -> settings.state.claudeKey = apiKey
                "gemini" -> settings.state.geminiKey = apiKey
                "groq" -> settings.state.groqKey = apiKey
            }
        }
        
        // Determine which key to send to server - either the new one or the stored one
        val effectiveKey = if (apiKey.isNotEmpty()) apiKey else when (provider) {
             "openai" -> settings.state.openaiKey
             "claude" -> settings.state.claudeKey
             "gemini" -> settings.state.geminiKey
             "groq" -> settings.state.groqKey
             else -> ""
        }

        val lspManager = LspServerManager.getInstance(project)
        val servers = lspManager.getServersForProvider(LisaLspServerSupportProvider::class.java)

        if (servers.isEmpty()) {
            Messages.showInfoMessage("Configuration saved! The LISA server is not currently running, but will pick up these settings when it starts (e.g. when you open a supported file).", "Configuration Saved")
            return
        }

        val server = servers.first()
        
        project.service<MyProjectService>().scope.launch {
            try {
                // Config object must match McpConfig interface on server
                val configParams = mapOf(
                    "provider" to provider,
                    "apiKey" to effectiveKey,
                    "model" to model
                )

                val method = server.javaClass.getMethod("sendRequestAsync", String::class.java, Object::class.java)
                val future = method.invoke(server, "lisa/updateConfig", configParams) as CompletableFuture<Any>
                future.get() // Wait for completion
                
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    Messages.showInfoMessage("LISA Configuration Updated and Saved!", "Success")
                }
            } catch (e: Exception) {
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(project, "Failed to update running server (but settings were saved): ${e.message}", "Error")
                }
            }
        }
    }
}
