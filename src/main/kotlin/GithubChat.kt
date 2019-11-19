import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Condition
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import git4idea.repo.GitRepositoryManager
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Point2D
import java.io.InputStreamReader
import javax.swing.*
import javax.swing.text.AttributeSet
import javax.swing.text.Document
import javax.swing.text.Element
import javax.swing.text.JTextComponent
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

class GithubChat(private val project: Project) {
    private val github = Github.create(project)
    private val code2psi = HashMap<Element, PsiFile>()
    private var codeToShare: String? = null
    private var html: String = ""
        set(value) {
            field = value
            chatView.text = value
            scan(chatView.document)
        }
    private val chatView = createChatView()
    private val input = createInput()
    private val codeLabel = JLabel()
    private val closeCodeLabel = JLabel("x")
    private val codePanel = JPanel()
    private val loadingPanel = JBLoadingPanel(BorderLayout(), project)

    init {
        codePanel.isVisible = false
        codePanel.layout = BoxLayout(codePanel, BoxLayout.X_AXIS)
//        codeLabel.maximumSize = codeLabel.maximumSize.apply { width = Int.MAX_VALUE }
        codePanel.add(codeLabel)

        closeCodeLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) = dropCodeToShare()
        })
//        closeCodeLabel.maximumSize = closeCodeLabel.minimumSize
        codePanel.add(closeCodeLabel)

        GithubChat.instances[project] = this
    }

    private fun loadStyleSheet(): StyleSheet {
        val css = StyleSheet()
        val r = InputStreamReader(javaClass.getResourceAsStream("/chat.css"))
        css.loadRules(r, null)
        return css
    }

    private fun createChatView(): JEditorPane {
        val chatPane = JEditorPane()
        chatPane.isEditable = false
        chatPane.isFocusable = false
//        chatPane.contentType = "text/html"
        chatPane.editorKit = object : HTMLEditorKit() {
            override fun getStyleSheet(): StyleSheet = loadStyleSheet()
        }

        chatPane.addMouseListener(object: MouseAdapter() {
            override fun mouseClicked(ev: MouseEvent) {
                if (ev.isControlDown) navigateFrom(ev.point)
            }
        })
        return chatPane
    }

    private fun createInput(): JTextComponent {
        val input = JTextArea("Type here, press Ctrl+Enter to send")
        input.lineWrap = true
        input.border = BorderFactory.createLineBorder(Color.BLACK)
        input.maximumSize = Dimension(Int.MAX_VALUE, 300)
        input.isEnabled = github != null///

        input.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(ev: KeyEvent?) {
                ev ?: return
                if (ev.isControlDown && ev.keyCode == KeyEvent.VK_ENTER) {
                    val post = "<p>${input.text}${codeToShare.orEmpty()}"
                    async("Posting",
                            { github!!.post(post) }, ///!!
                            {
                                if (it != null) html += it
                                else Messages.showErrorDialog(chatView, "Failed to submit post")
                            })
                    input.text = ""
                    dropCodeToShare()
                } else super.keyPressed(ev)
            }
        })
        return input
    }

    fun createContent(): JPanel {
        val refreshButton = JButton("Refresh")
        refreshButton.addActionListener {_ -> refresh()}

        val inputP = JPanel()
        inputP.layout = BoxLayout(inputP, BoxLayout.Y_AXIS)
        inputP.add(input)
        inputP.add(codePanel)

        val p = loadingPanel.contentPanel
        p.add(refreshButton, BorderLayout.NORTH)
        p.add(JBScrollPane(chatView), BorderLayout.CENTER)
        p.add(inputP, BorderLayout.SOUTH)

        refresh()
        return loadingPanel
    }

    fun startPost(code: String?, lang: Language?) {
        input.requestFocus()
        code ?: return
        codeToShare = code.let {
            val langAttr = lang?.run {" lang='${displayName.toLowerCase()}'"}.orEmpty()
            "<pre><code$langAttr>$it</code></pre>"
        }
        codeLabel.text = "Code" ///code.let {"<html>$it</html>"}
        codePanel.isVisible = true
    }

    fun refresh() {
        async("Reading chat",
                { github!!.readChat() },///!!
                { html = it })
    }

    private fun dropCodeToShare() {
        codeToShare = null
        codePanel.isVisible = false
    }

    private fun isCode(elem: Element) = elem.attributes.isDefined(HTML.Tag.CODE)

    private fun scan(doc: Document) {
        code2psi.clear()
        fun traverse(elem: Element) {
            if (isCode(elem)) {
                val attrs = elem.attributes.getAttribute(HTML.Tag.CODE) as AttributeSet
                val langValue = attrs.getAttribute(HTML.Attribute.LANG) as String?
                val lang = Language.findLanguageByID(langValue?.toLowerCase()) ?: Language.ANY
                val code = elem.document.getText(elem.startOffset, elem.endOffset - elem.startOffset)
                log("Found code ($lang) : $code")
                val psi = PsiFileFactory.getInstance(project).createFileFromText(lang, code)
                code2psi.put(elem, psi)
            } else {
                for (i in 0.until(elem.elementCount))
                    traverse(elem.getElement(i))
            }
        }
        traverse(doc.defaultRootElement)
        log("Found ${code2psi.size} code sections")
    }

    private fun codeElementAt(elem: Element, pos: Int): Element? {
        if (isCode(elem)) return elem
        else {
            val idx = elem.getElementIndex(pos)
            return if (idx < 0) null else codeElementAt(elem.getElement(idx), pos)
        }
    }

    private fun navigateFrom(pt: Point2D) {
        val pos = chatView.viewToModel2D(pt)
        val elem = codeElementAt(chatView.document.defaultRootElement, pos)
        if (elem == null) {
            log("Click outside <code> elem")
            return
        }
        val psiPos = pos - elem.startOffset
        val ref = code2psi.get(elem)?.findReferenceAt(psiPos)
        val target = ref?.resolve()
        log("ref elem=${ref?.element}, target=$target")
        target ?: return
        log("file = ${target.containingFile}, lang = ${target.language}, valid = ${target.isValid}")
        val fd = OpenFileDescriptor(project, target.containingFile.virtualFile, target.textOffset)
        FileEditorManager.getInstance(project).navigateToTextEditor(fd, true)
    }

    private fun <R> async(title: String, comp: () -> R, cont: (R) -> Unit) {
        loadingPanel.startLoading()
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(
                object : Task.Backgroundable(project, title, false) {
                    override fun run(indicator: ProgressIndicator) {
                        val r = comp()
                        log("async($title) result: $r")
                        ApplicationManager.getApplication().invokeLater {
                            cont(r)
                            loadingPanel.stopLoading()
                        }
                    }
                },
                EmptyProgressIndicator())
    }

    companion object {
        val ID = "Github Chat"
        ///canonical way?
        private val instances = HashMap<Project, GithubChat>()
        fun getInstance(project: Project?): GithubChat? = instances[project]
    }
}

class ChatWindowFactory : ToolWindowFactory, Condition<Project> {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatWindow = GithubChat(project)
        val cm = toolWindow.getContentManager()
        cm.addContent(
                cm.factory.createContent(
                        chatWindow.createContent(), "", false))
    }

    override fun value(project: Project?): Boolean {
        project ?: return false
        val repos = GitRepositoryManager.getInstance(project).repositories
        log("repos = $repos")
        val reps = VcsRepositoryManager.getInstance(project).repositories
        log("reps = $reps")
        return true
//    Github.enabledFor(project) /// seems called before github plugin initializes
    }
}