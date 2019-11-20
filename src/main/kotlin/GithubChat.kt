import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
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
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import git4idea.repo.GitRepositoryManager
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.InputStreamReader
import javax.swing.*
import javax.swing.text.JTextComponent
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

fun log(s: String) = System.err.println(">>> $s")  ///Logger.getInstance(GithubChat::class.java).info(s)
fun err(s: String) = System.err.println("!!! $s")

class GithubChat(private val project: Project) {
    private val github = Github.create(project)!! // chat disabled if null
    private var refs: References? = null
    private var codeToShare: String? = null
    private var html: String = ""
        set(value) {
            field = value
            chatView.run {
                text = value
                (parent as JViewport).viewPosition = Point(0, size.height) // scroll to bottom
            }
            refs = References.build(project, chatView.document)
        }
    private val chatView: JEditorPane
    private val inputControl: JTextComponent
    private val inputGroup: JComponent
    private val dropCodeControl: JComponent
    private val loadingPanel: JBLoadingPanel

    init {
        chatView = createChatView()

        inputControl = createInputControl()
        val sendButton = JButton("Send")
        sendButton.addActionListener { sendPost() }
        inputGroup = JPanel()
        inputGroup.layout = BoxLayout(inputGroup, BoxLayout.X_AXIS)
        inputGroup.add(inputControl)
        inputGroup.add(sendButton)

        dropCodeControl = JButton("Drop Code")
        dropCodeControl.addActionListener { dropCode() }
        loadingPanel = JBLoadingPanel(BorderLayout(), project)

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
        chatPane.editorKit = object : HTMLEditorKit() {
            override fun getStyleSheet(): StyleSheet = loadStyleSheet()
        }

        chatPane.addMouseListener(object: MouseAdapter() {
            override fun mouseClicked(ev: MouseEvent) {
                if (ev.isControlDown) {
                    val target = refs?.resolveAt(chatView.document, chatView.viewToModel2D(ev.point))
                    target ?: return
                    log("file = ${target.containingFile}, lang = ${target.language}, valid = ${target.isValid}")
                    val fd = OpenFileDescriptor(project, target.containingFile.virtualFile, target.textOffset)
                    FileEditorManager.getInstance(project).navigateToTextEditor(fd, true)
                }
            }
        })
        return chatPane
    }

    private fun createInputControl(): JTextComponent {
        val input = JTextArea("Type here, press Ctrl+Enter to send")
        input.lineWrap = true
        input.border = BorderFactory.createLineBorder(Color.BLACK)
        input.maximumSize = Dimension(Int.MAX_VALUE, 300)

        input.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent?) {
                if (e != null && e.isControlDown && e.keyCode == KeyEvent.VK_ENTER)
                    sendPost()
                else super.keyPressed(e)
            }
        })
        return input
    }

    fun createContent(): JPanel {
        val refreshButton = JButton("Refresh")
        refreshButton.isFocusable = false
        refreshButton.addActionListener { refresh() }

        val inputCodeGroup = JPanel()
        inputCodeGroup.layout = BoxLayout(inputCodeGroup, BoxLayout.Y_AXIS)
        inputCodeGroup.add(inputGroup)
        inputCodeGroup.add(dropCodeControl)
        dropCodeControl.isVisible = false
        dropCodeControl.isFocusable = false
        dropCodeControl.run { preferredSize = maximumSize }

        val p = loadingPanel.contentPanel
        p.add(refreshButton, BorderLayout.NORTH)
        p.add(JBScrollPane(chatView), BorderLayout.CENTER)
        p.add(inputCodeGroup, BorderLayout.SOUTH)

        refresh()
        return loadingPanel
    }

    fun startPost(code: String?, lang: Language?) {
        inputControl.requestFocus()
        code ?: return
        codeToShare = code.let {
            val langAttr = lang?.run {" lang='${displayName.toLowerCase()}'"}.orEmpty()
            "<pre><code$langAttr>$it</code></pre>"
        }
        dropCodeControl.isVisible = true
    }

    private fun sendPost() {
        val post = "<p>${inputControl.text}${codeToShare.orEmpty()}"
        async("Posting",
                { github.post(post) },
                {
                    if (it != null) html += it
                    else Messages.showErrorDialog(chatView, "Failed to submit post")
                })
        inputControl.text = ""
        dropCode()
    }

    fun refresh() {
        async("Reading chat",
                { github.readChat() },
                {
                    html = it ?: "<h3>Failed to read chat contents</h3>"
                    inputGroup.isVisible = it != null
                })
    }

    private fun dropCode() {
        codeToShare = null
        dropCodeControl.isVisible = false
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

    override fun value(project: Project?): Boolean = Github.enabledFor(project)
}