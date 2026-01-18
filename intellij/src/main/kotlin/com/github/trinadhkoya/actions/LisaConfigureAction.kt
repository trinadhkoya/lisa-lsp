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

        val provider = Messages.showInputDialog(project, "Enter AI Provider (openai, groq, gemini, claude):", "LISA Config", null) ?: return
        val apiKey = Messages.showPasswordDialog(project, "Enter API Key:", "LISA Config", null) ?: return
        val model = Messages.showInputDialog(project, "Enter Model Name:", "LISA Config", null) ?: return

        val lspManager = LspServerManager.getInstance(project)
        val servers = lspManager.getServersForProvider(LisaLspServerSupportProvider::class.java)

        if (servers.isEmpty()) {
            Messages.showErrorDialog(project, "LISA Server is not running. Open a .ts file first.", "Error")
            return
        }

        val server = servers.first()
        
        project.service<MyProjectService>().scope.launch {
            try {
                // Config object must match McpConfig interface on server
                val configParams = mapOf(
                    "provider" to provider,
                    "apiKey" to apiKey,
                    "model" to model
                )

                val method = server.javaClass.getMethod("sendRequestAsync", String::class.java, Object::class.java)
                val future = method.invoke(server, "lisa/updateConfig", configParams) as CompletableFuture<Any>
                future.get() // Wait for completion
                
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    Messages.showInfoMessage("LISA Configuration Updated!", "Success")
                }
            } catch (e: Exception) {
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(project, "Failed to update config: ${e.message}", "Error")
                }
            }
        }
    }
}
