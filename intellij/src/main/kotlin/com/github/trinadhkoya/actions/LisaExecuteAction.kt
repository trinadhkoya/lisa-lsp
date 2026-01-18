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

class LisaExecuteAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val command = Messages.showInputDialog(project, "What do you want LISA to do?", "LISA Command", null)

        if (!command.isNullOrBlank()) {
            val lspManager = LspServerManager.getInstance(project)
            val servers = lspManager.getServersForProvider(LisaLspServerSupportProvider::class.java)

            if (servers.isEmpty()) {
                Messages.showErrorDialog(project, "LISA Server is not running. Open a .ts file first.", "Error")
                return
            }

            val server = servers.first()
            
            project.service<MyProjectService>().scope.launch {
                try {
                    // Use reflection
                    val method = server.javaClass.getMethod("sendRequestAsync", String::class.java, Object::class.java)
                    val responseFuture = method.invoke(server, "lisa/execute", command) as CompletableFuture<Any>
                    val response = responseFuture.get()
                    
                    // This ensures the response is handled on the UI thread
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage("LISA Result: $response", "Success")
                    }
                } catch (e: Exception) {
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "LISA Error: ${e.message}", "Error")
                    }
                }
            }

            Messages.showInfoMessage("Command '$command' sent to LISA.", "LISA Agent")
        }
    }
}