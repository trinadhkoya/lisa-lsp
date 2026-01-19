package com.github.trinadhkoya.actions

import com.github.trinadhkoya.LisaLspServerSupportProvider
import com.github.trinadhkoya.lisaintellijplugin.services.MyProjectService
import com.github.trinadhkoya.lisaintellijplugin.settings.LisaPluginSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.intellij.platform.lsp.api.LspServerManager
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.jsonrpc.Endpoint
import java.util.concurrent.CompletableFuture

class LisaExecuteAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val command = Messages.showInputDialog(project, "What do you want LISA to do?", "LISA Command", null)

        if (!command.isNullOrBlank()) {
            // 1. Pre-check API Key Configuration (Parity with VS Code)
            val settings = LisaPluginSettings.getInstance(project).state
            val provider = settings.provider
            var apiKey = when (provider) {
                "openai" -> settings.openaiKey
                "claude" -> settings.claudeKey
                "gemini" -> settings.geminiKey
                "groq" -> settings.groqKey
                else -> ""
            }

            // Fallback to Env Vars (matches server logic)
            if (apiKey.isBlank()) {
                val env = System.getenv()
                apiKey = when (provider) {
                    "openai" -> env["OPENAI_API_KEY"] ?: ""
                    "claude" -> env["ANTHROPIC_API_KEY"] ?: ""
                    "gemini" -> env["GEMINI_API_KEY"] ?: ""
                    "groq" -> env["GROQ_API_KEY"] ?: ""
                    else -> ""
                }
            }

            if (apiKey.isBlank()) {
                val result = Messages.showYesNoDialog(
                    project,
                    "LISA Configuration Error: API Key for $provider is missing.\n\nWould you like to configure it now?",
                    "LISA Configuration Required",
                    Messages.getErrorIcon()
                )
                if (result == Messages.YES) {
                    com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction("Lisa.Configure").actionPerformed(e)
                }
                return
            }

            val lspManager = LspServerManager.getInstance(project)
            val servers = lspManager.getServersForProvider(LisaLspServerSupportProvider::class.java)

            if (servers.isEmpty()) {
                val result = Messages.showYesNoDialog(
                    project, 
                    "LISA Server is not running. You may need to configure the API key or open a supported file. Configure LISA now?",
                    "LISA Server Not Found",
                    Messages.getQuestionIcon()
                )
                if (result == Messages.YES) {
                    com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction("Lisa.Configure").actionPerformed(e)
                }
                return
            }

            val server = servers.first()
            
            project.service<MyProjectService>().scope.launch {
                try {
                    // Use reflection
                    val method = server.javaClass.getMethod("sendRequestAsync", String::class.java, Object::class.java)
                    val responseFuture = method.invoke(server, "lisa/execute", command) as CompletableFuture<Any>
                    val response = responseFuture.get()
                    val responseStr = response.toString()

                    // Check for server-side error returned as success value
                    if (responseStr.contains("success=false") || responseStr.contains("API Key") || responseStr.contains("Unauthorized")) {
                        if (responseStr.contains("API Key") || responseStr.contains("Unauthorized")) {
                             com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                val result = Messages.showYesNoDialog(
                                    project,
                                    "LISA Configuration Error: $responseStr\n\nWould you like to configure the API key?",
                                    "LISA Configuration Required",
                                    Messages.getErrorIcon()
                                )
                                if (result == Messages.YES) {
                                    com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction("Lisa.Configure").actionPerformed(e)
                                }
                            }
                        } else {
                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                Messages.showErrorDialog(project, "LISA Failed: $responseStr", "LISA Error")
                            }
                        }
                    } else {
                        // Success case
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            Messages.showInfoMessage("LISA Result: $responseStr", "Success")
                        }
                    }
                } catch (ex: Exception) {
                    val errorMsg = ex.message ?: "Unknown error"
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        if (errorMsg.contains("API Key") || errorMsg.contains("Unauthorized")) {
                             val result = Messages.showYesNoDialog(
                                project,
                                "LISA Configuration Error: $errorMsg\n\nWould you like to configure the API key?",
                                "LISA Configuration Required",
                                Messages.getErrorIcon()
                            )
                            if (result == Messages.YES) {
                                com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction("Lisa.Configure").actionPerformed(e)
                            }
                        } else {
                            Messages.showErrorDialog(project, "LISA Error: $errorMsg", "Error")
                        }
                    }
                }
            }

            Messages.showInfoMessage("Command '$command' sent to LISA.", "LISA Agent")
        }
    }
}