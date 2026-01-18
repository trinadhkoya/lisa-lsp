import * as vscode from 'vscode';

export class LisaCodeActionProvider implements vscode.CodeActionProvider {

    public static readonly providedCodeActionKinds = [
        vscode.CodeActionKind.Refactor,
        vscode.CodeActionKind.Source
    ];

    provideCodeActions(document: vscode.TextDocument, range: vscode.Range | vscode.Selection, context: vscode.CodeActionContext, token: vscode.CancellationToken): vscode.ProviderResult<(vscode.Command | vscode.CodeAction)[]> {
        const actions: vscode.CodeAction[] = [];

        // Only provide actions if there is a selection or if the cursor is on something
        if (range.isEmpty) {
            return [];
        }

        // 1. Refactor
        const refactorAction = this.createAction('LISA: Refactor Selection', 'refactor', document, range);
        actions.push(refactorAction);

        // 2. Add JSDoc / Doc
        const docAction = this.createAction('LISA: Add Documentation', 'addDocs', document, range);
        actions.push(docAction);

        // 3. Generate Tests
        const testAction = this.createAction('LISA: Generate Tests', 'generateTests', document, range);
        actions.push(testAction);

        return actions;
    }

    private createAction(title: string, actionType: string, document: vscode.TextDocument, range: vscode.Range): vscode.CodeAction {
        const action = new vscode.CodeAction(title, vscode.CodeActionKind.Empty);
        action.command = {
            command: 'lisa.runAction',
            title: title,
            arguments: [{
                action: actionType,
                file: document.uri.fsPath,
                selection: document.getText(range),
                language: document.languageId
            }]
        };
        return action;
    }
}
