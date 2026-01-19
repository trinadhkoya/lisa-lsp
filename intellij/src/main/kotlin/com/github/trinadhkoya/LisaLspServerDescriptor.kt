package com.github.trinadhkoya

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import java.io.File

class LisaLspServerDescriptor(project: Project, virtualFile: VirtualFile) : LspServerDescriptor(project, "LISA", virtualFile) {
    override fun isSupportedFile(file: VirtualFile) = file.extension != null

    override fun createCommandLine(): GeneralCommandLine {
        // Priority 1: Bundled Server (Production)
        val pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.trinadhkoya.lisaintellijplugin")
        val plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(pluginId)
        if (plugin != null) {
            val bundledPath = File(plugin.path, "server/server.js")
            if (bundledPath.exists()) {
                return createNodeCommand(bundledPath.absolutePath)
            }
            // If running from IDE with Gradle, resources might be in 'classes' or 'resources'
            val devBundledPath = File(plugin.path, "classes/kotlin/main/server/server.js")
             if (devBundledPath.exists()) {
                return createNodeCommand(devBundledPath.absolutePath)
            }
        }

        // Priority 2: Environment Variable
        val envPath = System.getenv("LISA_SERVER_PATH")
        if (envPath != null && File(envPath).exists()) {
            return createNodeCommand(envPath)
        }

        // Priority 3: Development mode - Context check
        val projectBase = project.basePath
        if (projectBase != null) {
             val distPath = File(projectBase).parentFile?.resolve("dist/server.js")
             if (distPath != null && distPath.exists()) {
                 return createNodeCommand(distPath.absolutePath)
             }
        }

        // Fallback or Error
        println("LISA LSP ERROR: Could not find server.js in bundled or standard locations.")
        return createNodeCommand("") // Will likely fail with clear error
    }
    
    private fun createNodeCommand(scriptPath: String): GeneralCommandLine {
        val settings = com.github.trinadhkoya.lisaintellijplugin.settings.LisaPluginSettings.getInstance(project).state
        
        val cmd = GeneralCommandLine("node", scriptPath, "--stdio")
            .withWorkDirectory(project.basePath)
            .withEnvironment("NODE_TLS_REJECT_UNAUTHORIZED", "0")

        val apiKey = when (settings.provider) {
            "openai" -> settings.openaiKey
            "claude" -> settings.claudeKey
            "gemini" -> settings.geminiKey
            "groq" -> settings.groqKey
            else -> ""
        }

        if (apiKey.isNotEmpty()) {
            when (settings.provider) {
                "openai" -> cmd.withEnvironment("OPENAI_API_KEY", apiKey)
                "claude" -> cmd.withEnvironment("ANTHROPIC_API_KEY", apiKey)
                "gemini" -> cmd.withEnvironment("GEMINI_API_KEY", apiKey)
                "groq" -> cmd.withEnvironment("GROQ_API_KEY", apiKey)
            }
        }
        return cmd
    }
}