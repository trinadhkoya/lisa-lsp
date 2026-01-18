package com.github.trinadhkoya

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor

class LisaLspServerDescriptor(project: Project, virtualFile: VirtualFile) : LspServerDescriptor(project, "LISA", virtualFile) {
    override fun isSupportedFile(file: VirtualFile) = file.extension != null

    override fun createCommandLine(): GeneralCommandLine {
        // Try to find server.js relative to the project or use environment variable
        val serverPath = System.getenv("LISA_SERVER_PATH") 
            ?: "${System.getProperty("user.home")}/Desktop/mcp-lsp/dist/server.js"
        
        return GeneralCommandLine("node", serverPath, "--stdio")
            .withWorkDirectory(project.basePath)
            .withEnvironment("NODE_TLS_REJECT_UNAUTHORIZED", "0")
    }
}