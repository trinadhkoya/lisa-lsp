package com.github.trinadhkoya

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor

class LisaLspServerDescriptor(project: Project, virtualFile: VirtualFile) : LspServerDescriptor(project, "LISA", virtualFile) {
    override fun isSupportedFile(file: VirtualFile) = file.extension == "ts" || file.extension == "js"

    override fun createCommandLine(): GeneralCommandLine {
        return GeneralCommandLine("node", "/Users/trinadhkoya/Desktop/mcp-lsp/dist/server.js", "--stdio")
            .withWorkDirectory(project.basePath)
            .withEnvironment("NODE_TLS_REJECT_UNAUTHORIZED", "0")
    }
}