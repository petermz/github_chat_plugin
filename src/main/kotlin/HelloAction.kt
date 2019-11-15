import com.intellij.configurationStore.getStateSpecOrError
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.data.GithubIssue
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import javax.swing.SwingUtilities

class HelloAction : AnAction("Hello Kotlin") {
    fun log(s: String) = System.err.println(">>> $s")
    fun err(s: String) = System.err.println("!!! $s")

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
            val issue = executor.execute(GithubApiRequests.Repos.Issues.get(
                    account.server, "bos", "aeson", "687"))
            if (issue == null) {
                err("Issue not found")
                return
            }
            val comments = executor.execute(GithubApiRequests.Repos.Issues.Comments.get(issue.commentsUrl)).items
            log("issue: $issue")
            log("html: ${comments.first().bodyHtml}")
//            Messages.showMessageDialog(project, "User Login $login", "Kotlin Greets", Messages.getInformationIcon())
        }
    }
}