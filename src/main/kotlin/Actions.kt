import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.PsiDocumentManager

class ShareSelectionAction: AnAction("Share in Chat") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val lang = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.language
        GithubChat.getInstance(e.project)?.startPost(editor.selectionModel.selectedText, lang)
    }

    override fun update(ev: AnActionEvent) {
        val editor = ev.getData(CommonDataKeys.EDITOR) ?: return
        ev.presentation.isEnabledAndVisible =
                Github.enabledFor(ev.project) &&
                        editor.selectionModel.hasSelection()
    }
}

class RefreshAction: AnAction("Refresh") {
    override fun actionPerformed(e: AnActionEvent) {
        GithubChat.getInstance(e.project)?.refresh()
    }
}
