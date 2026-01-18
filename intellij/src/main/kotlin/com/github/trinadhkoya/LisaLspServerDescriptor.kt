package com.github.trinadhkoya

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import java.io.File

class LisaLspServerDescriptor(project: Project, virtualFile: VirtualFile) : LspServerDescriptor(project, "LISA", virtualFile) {
    override fun isSupportedFile(file: VirtualFile) = file.extension != null

    override fun createCommandLine(): GeneralCommandLine {
        // Priority 1: Environment Variable
        val envPath = System.getenv("LISA_SERVER_PATH")
        if (envPath != null && File(envPath).exists()) {
            return createNodeCommand(envPath)
        }

        // Priority 2: User Home / Documents (Common for this user)
        val userHome = System.getProperty("user.home")
        val documentsPath = "$userHome/Documents/mcp-lsp/dist/server.js"
        if (File(documentsPath).exists()) {
             return createNodeCommand(documentsPath)
        }

        // Priority 3: User Home / Desktop (Fallback)
        val desktopPath = "$userHome/Desktop/mcp-lsp/dist/server.js"
        if (File(desktopPath).exists()) {
            return createNodeCommand(desktopPath)
        }
        
        // Priority 4: Development mode - Check relative to project root
        // Assuming project.basePath is mcp-lsp/intellij, then ../dist/server.js
        val basePath = project.basePath
        if (basePath != null) {
            val devPath = File(basePath).parentFile?.resolve("dist/server.js")?.absolutePath
            if (devPath != null && File(devPath).exists()) {
                 return createNodeCommand(devPath)
            }
        }

        // If we get here, we can't find the server. 
        // We'll throw an exception that might be visible in the logs, or fall back to a default that will likely fail but at least we tried.
        // For better UX, we'll return the Documents path so the error message "Stream closed" might at least hint at a missing file if someone looks at the command line.
        // But better to log it.
        println("LISA LSP ERROR: Could not find server.js in standard locations.")
        return createNodeCommand(documentsPath)
    }
    
    private fun createNodeCommand(scriptPath: String): GeneralCommandLine {
        return GeneralCommandLine("node", scriptPath, "--stdio")
            .withWorkDirectory(project.basePath)
            .withEnvironment("NODE_TLS_REJECT_UNAUTHORIZED", "0")
    }
}