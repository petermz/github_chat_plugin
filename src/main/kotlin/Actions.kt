import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import javax.swing.SwingUtilities

fun log(s: String) = System.err.println(">>> $s")  ///Logger.getInstance(">>>").info(s)
fun err(s: String) = System.err.println("!!! $s")

class ShareSelectionAction: AnAction("Share in Chat") {
    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val lang = PsiDocumentManager.getInstance(e.project!!).getPsiFile(editor.document)?.language
        val toolWindow = ToolWindowManager.getInstance(e.project!!).getToolWindow(GithubChat.ID)
        log("toolWindow = $toolWindow")
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

class ViewChatAction: AnAction("View Chat") {
    override fun actionPerformed(ev: AnActionEvent) {
        val project = ProjectManager.getInstance().defaultProject
        log("prj = $project")
        val html = Language.findLanguageByID("HTML")
        log("html = $html")
        val psiFile = PsiFileFactory.getInstance(project).createFileFromText(html!!,
                """<p>peterz says @Thu Nov 4
                <code>KotlinVersion.CURRENT</code><br>
                <p>sam says @Fri Nov 15
                <code>generateSequence(1) { it+1 }""")
        log("psi = $psiFile")
        val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile)
        log("doc = $doc")

//        MultiplePsiFilesPerDocumentFileViewProvider.
    }
}

class PsiLookupAction: AnAction("PSI Lookup") {
    override fun actionPerformed(ev: AnActionEvent) {
        val kotlin = Language.findLanguageByID("kotlin")
//        val code = "println(KotlinVersion.CURRENT)"
//        val offset = code.indexOf("RRE")
        val code = "println(Euler23.solve())"
        val offset = code.indexOf("23")
        val psiFile = PsiFileFactory.getInstance(ev.project).createFileFromText(kotlin!!, code)
        log("psi = $psiFile")
        val ref = psiFile.findReferenceAt(offset)
        log("ref = $ref, target = ${ref?.resolve()}")
        val t = ref?.resolve()!!
        log("file = ${t.containingFile}, lang = ${t.language}, valid = ${t.isValid}")
        val fd = OpenFileDescriptor(ev.project!!, t.containingFile.virtualFile, t.textOffset)
        FileEditorManager.getInstance(ev.project!!).navigateToTextEditor(fd, true)
    }
}

class GithubAction : AnAction("Github Stuff") {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        if (project == null) {
            err("No project")
            return
        }

        if (SwingUtilities.isEventDispatchThread()) {
            val authMgr = GithubAuthenticationManager.getInstance()
            if (! authMgr.ensureHasAccounts(project)) return
            val account = authMgr.getAccounts().first()
            log("acc $account")

            val executor = GithubApiRequestExecutorManager.getInstance().getExecutor(account)
//            val issue = executor.execute(GithubApiRequests.Repos.Issues.get(
//                    account.server, "petermz", "github_chat_plugin", "1"))
//            if (issue == null) {
//                err("Issue not found")
//                return
//            }
//            val comments = executor.execute(GithubApiRequests.Repos.Issues.Comments.get(
//                    account.server, "petermz", "github_chat_plugin", "1",
//                    GithubRequestPagination()))
//            comments.items.forEach {log(it.bodyHtml)}

            val post = executor.execute(GithubApiRequests.Repos.Issues.Comments.create(
                    account.server, account.name, "github_chat_plugin", "1",
                    """<p>Here's what I call some good code!
                        Note that <code lang='Java'>System.out.println</code> is never used.
                        <p><code lang='Kotlin'>MergeSort.solve()</code>"""))
            log("Posted $post")

            val pages =
                    GithubApiPagesLoader.loadAll(executor, EmptyProgressIndicator(),
                            GithubApiRequests.Repos.Issues.Comments.pages(
                                    account.server, "petermz", "github_chat_plugin", "1"))
            pages.forEach { log("comment: ${it.bodyHtml}") }
        }
    }
}

