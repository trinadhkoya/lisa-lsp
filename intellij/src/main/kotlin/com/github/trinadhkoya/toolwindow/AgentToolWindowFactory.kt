package com.github.trinadhkoya.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import javax.swing.JPanel

import com.github.trinadhkoya.LisaLspServerSupportProvider
import com.github.trinadhkoya.lisaintellijplugin.services.MyProjectService
import com.intellij.openapi.components.service
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.openapi.fileEditor.FileEditorManager
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.jsonrpc.Endpoint
import java.util.concurrent.CompletableFuture
import com.intellij.openapi.application.ApplicationManager

class AgentToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val agentPanel = AgentPanel(project)
        val content = ContentFactory.getInstance().createContent(agentPanel.component, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private class AgentPanel(private val project: Project) {
        val component = JPanel(BorderLayout())
        private val browser = JBCefBrowser()

        init {
            component.add(browser.component, BorderLayout.CENTER)
            setupBrowser()
        }

        private fun setupBrowser() {
            // Register JS Query handler to receive messages from UI
            val jsQuery = JBCefJSQuery.create(browser as JBCefBrowser)
            jsQuery.addHandler { msg ->
                handleMessage(msg)
                null
            }

            // Inject JS bridge when page loads
            browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    val injection = jsQuery.inject("msg")
                    val script = "window.lisa = { postMessage: function(msg) { $injection } };"
                    browser?.executeJavaScript(script, browser.url, 0)
                }
            }, browser.cefBrowser)

            loadContent()
        }

        private fun loadContent() {
            // Same HTML as VS Code, slightly adapted for JCEF/IntelliJ
            val htmlContent = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <style>
                    body {
                        background-color: #2b2b2b; /* Dark theme default */
                        color: #a9b7c6;
                        font-family: sans-serif;
                        padding: 20px;
                    }
                    .container {
                        display: flex;
                        flex-direction: column;
                        gap: 15px;
                    }
                    label {
                        font-weight: bold;
                        margin-bottom: 5px;
                        display: block;
                    }
                    select, textarea, input {
                        width: 100%;
                        background-color: #3c3f41;
                        color: #bababa;
                        border: 1px solid #646464;
                        padding: 8px;
                        font-family: inherit;
                    }
                    button {
                        background-color: #4c5052;
                        color: #ffffff;
                        border: none;
                        padding: 10px;
                        cursor: pointer;
                        font-weight: bold;
                    }
                    button:hover {
                        background-color: #5f6366;
                    }
                  </style>
                </head>
                <body>
                  <h2>🤖 LISA Agent Manager</h2>
                  <div class="container">
                    
                    <div>
                        <label>Choose Agent Capability</label>
                        <select id="agent-type">
                            <option value="chat">💬 General Chat</option>
                            <option value="generateTests">🧪 QA Automation (Test Gen)</option>
                            <option value="addJsDoc">📝 Documentation Expert</option>
                            <option value="refactor">🛠️ Code Refactorer</option>
                        </select>
                    </div>

                    <div>
                        <label>Instructions / Prompt</label>
                        <textarea id="instruction" rows="4" placeholder="Enter instructions..."></textarea>
                    </div>

                    <button id="run-btn">Run Agent</button>
                    <div id="status"></div>

                  </div>

                  <script>
                    const runBtn = document.getElementById('run-btn');
                    const agentSelect = document.getElementById('agent-type');
                    const instructionInput = document.getElementById('instruction');

                    runBtn.addEventListener('click', () => {
                        const agent = agentSelect.value;
                        const instruction = instructionInput.value;
                        // Use bridge
                        if (window.lisa) {
                            window.lisa.postMessage(JSON.stringify({
                                command: 'runAgent',
                                agent: agent,
                                instruction: instruction
                            }));
                            document.getElementById('status').innerText = 'Request sent...';
                        }
                    });
                  </script>
                </body>
                </html>
            """.trimIndent()
            
            browser.loadHTML(htmlContent)
        }
        
        private fun handleMessage(jsonStr: String) {
            // parsing json manually to avoid deps
            val agent = extractJsonValue(jsonStr, "agent")
            val instruction = extractJsonValue(jsonStr, "instruction")
            
            val lspManager = LspServerManager.getInstance(project)
            val servers = lspManager.getServersForProvider(LisaLspServerSupportProvider::class.java)

            if (servers.isEmpty()) {
                updateStatus("Error: LISA Server not found. Open a supported file.")
                return
            }
            val server = servers.first()

            // Get Editor Context
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            val contextData = mutableMapOf<String, String>()
            if (editor != null) {
                contextData["selection"] = editor.selectionModel.selectedText ?: ""
                contextData["fileContent"] = editor.document.text
                contextData["uri"] = editor.virtualFile?.url ?: ""
                contextData["languageId"] = editor.virtualFile?.fileType?.name?.lowercase() ?: ""
            }

            // Prepare Prompt
            var prompt = instruction
            if (agent == "generateTests") prompt = "Generate unit tests for this code"
            if (agent == "addJsDoc") prompt = "Add JSDoc documentation"
            if (agent == "refactor") prompt = "Refactor this code: $instruction"

            updateStatus("Running $agent...")

            project.service<MyProjectService>().scope.launch {
                try {
                    val params = mapOf(
                        "command" to prompt,
                        "context" to contextData
                    )
                    
                    val response = server.sendRequest<Any> {
                        (it as Endpoint).request("lisa/execute", params) as CompletableFuture<Any>
                    }
                    
                    ApplicationManager.getApplication().invokeLater {
                        updateStatus("Done: $response")
                        // TODO: Handle file creation action if response indicates it
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        updateStatus("Error: ${e.message}")
                    }
                }
            }
        }

        private fun updateStatus(msg: String) {
             val escaped = msg.replace("'", "\\'").replace("\n", " ")
             browser.cefBrowser.executeJavaScript("document.getElementById('status').innerText = '$escaped';", browser.cefBrowser.url, 0)
        }

        private fun extractJsonValue(json: String, key: String): String {
            val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
            return regex.find(json)?.groupValues?.get(1) ?: ""
        }
    }
}
