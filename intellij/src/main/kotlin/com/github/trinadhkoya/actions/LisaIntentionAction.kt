package com.github.trinadhkoya.actions

import com.github.trinadhkoya.toolwindow.AgentToolWindowFactory
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.util.IncorrectOperationException

abstract class LisaBaseIntentionAction(private val actionName: String, private val actionType: String) : IntentionAction {

    override fun getText(): String = "LISA: $actionName"
    override fun getFamilyName(): String = "LISA Actions"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        // Available if selection exists or caret is present
        return true
    }

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText ?: ""
        val fileName = file.name
        val language = file.fileType.name

        // 1. Open Tool Window
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("LISA Agent Manager")
        toolWindow?.show {
            // 2. Send Action
            val panel = AgentToolWindowFactory.getPanel(project)
            panel?.sendAction(actionType, mapOf(
                "file" to fileName,
                "selection" to selectedText,
                "language" to language
            ))
        }
    }

    override fun startInWriteAction(): Boolean = false
}

class LisaRefactorAction : LisaBaseIntentionAction("Refactor Selection", "refactor")
class LisaAddDocsAction : LisaBaseIntentionAction("Add Documentation", "addDocs")
class LisaGenerateTestsAction : LisaBaseIntentionAction("Generate Tests", "generateTests")
