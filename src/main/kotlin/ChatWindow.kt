import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.ContentFactory
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.layout.*
import javax.swing.*

class ChatWindow(toolWindow: ToolWindow) {
    val chatPane = JEditorPane("text/html", "<h1>Github Chat</h1><p>Chat content goes here")
    val input = JTextArea("Type here...")
    val content = panel {
        row { chatPane }
        row {
            label(">>")
            input
        }
    }
}

class ChatWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatWindow = ChatWindow(toolWindow)
        val contentFactory = ContentFactory.SERVICE.getInstance()
        val content = contentFactory.createContent(chatWindow.content, "", false)
        toolWindow.getContentManager().addContent(content)
    }
}