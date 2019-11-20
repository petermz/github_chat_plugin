import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.*
import com.intellij.psi.impl.file.PsiDirectoryFactoryImpl
import com.intellij.psi.impl.file.impl.FileManager
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import javax.swing.SwingUtilities

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
        val code = "println(KotlinVersion.CURRENT)"
        val offset = code.indexOf("RRE")
//        val code = "println(Euler23.solve())"
//        val offset = code.indexOf("lve(")

        fun inspect(psi: PsiElement, mode: String) {
            log("($mode): psi = $psi")
            val ref = psi.findReferenceAt(offset)
            log("($mode): ref = $ref, target = ${ref?.resolve()}")
            val t = ref?.resolve()
            log("($mode): file = ${t?.containingFile}, lang = ${t?.language}, valid = ${t?.isValid}")

            if (t != null) {
                val fd = OpenFileDescriptor(ev.project!!, t.containingFile.virtualFile, t.textOffset)
                FileEditorManager.getInstance(ev.project!!).navigateToTextEditor(fd, true)
                log("Hooray!!!")
            }
        }
        val root =
                JavaPsiFacade.getElementFactory(ev.project!!).createExpressionFromText(code, null)
        val javaPsi = JavaCodeFragmentFactory.getInstance(ev.project)
                .createExpressionCodeFragment(code, null, null, true)

        val psiFile = PsiFileFactory.getInstance(ev.project).createFileFromText(kotlin!!, code)

        inspect(root, "expr")
        inspect(javaPsi, "javaFrag")
        inspect(psiFile, "psiFile")
    }
}

class GithubAction : AnAction("Github Stuff") {
    override fun actionPerformed(e: AnActionEvent) {
        var psi: PsiElement? = e.getData(LangDataKeys.PSI_FILE)
        while (psi != null) {
            log("psi (${psi.javaClass})  $psi")
            psi = psi.parent
        }
    }
}
