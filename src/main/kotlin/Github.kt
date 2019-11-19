import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitUtil
import git4idea.repo.GitRepositoryManager
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubIssue
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.util.GithubGitHelper
import org.jetbrains.plugins.github.util.GithubProjectSettings

class Github(private val project: Project, ///
             private val executor: GithubApiRequestExecutor)
{
    companion object {
        val CHAT_ISSUE_LABEL = "idea-chat"
        val CHAT_ISSUE_TITLE = "[Chat]"
        val CHAT_START_TEXT = "Let's have a chat here!"

        fun enabledFor(project: Project?): Boolean {
            val enabled = project != null &&
                    !project.isDefault() &&
                    service<GithubGitHelper>().havePossibleRemotes(project)
            log("TW enabled = $enabled")
            return enabled
        }

        fun create(project: Project): Github? {
            val repo = GithubProjectSettings.getInstance(project).createPullRequestDefaultRepo
            log("repo ${repo?.fullName} user=${repo?.user} path=${repo?.repository}")
            val vcsman = GitRepositoryManager.getInstance(project).getRepositoryForFile(project.baseDir)
            val rep0 = GitRepositoryManager.getInstance(project).repositories
            log("rep0 $rep0 first ${rep0.firstOrNull()?.presentableUrl}")
            val root = GitRepositoryManager.getInstance(project).getRepositoryForRoot(project.baseDir)
            log("root $root for ${project.baseDir}")
            val rm = GitUtil.getRepositoryManager(project)
            log("repman $rm (${rm.repositories} forroot=${rm.getRepositoryForRoot(project.baseDir)}")
            val have = service<GithubGitHelper>().havePossibleRemotes(project)
            log("havePossible = $have")

            val authMgr = GithubAuthenticationManager.getInstance()
            if (authMgr.ensureHasAccounts(project)) {
                val account = authMgr.getSingleOrDefaultAccount(project)!!
                val executor = GithubApiRequestExecutorManager.getInstance().getExecutor(account)
                return Github(project, executor)
            }
            return null
        }
    }

    private val server = GithubServerPath.DEFAULT_SERVER /// damn
    private val user = "petermz"
    private val repo = "hello-kotlin"
    private val indicator = EmptyProgressIndicator() ///
    private lateinit var chatIssueId: String

    private fun ensureChatStarted(): GithubIssue? {
        ::chatIssueId.isInitialized && return null

        val found = GithubApiPagesLoader.loadAll(executor, indicator,
                GithubApiRequests.Repos.Issues.pages(server, user, repo))
                .firstOrNull {
                    it.title == CHAT_ISSUE_TITLE &&
                            it.labels?.map { it.name }.orEmpty().contains(CHAT_ISSUE_LABEL)
                }?.number?.toString()
        if (found != null) {
            chatIssueId = found
            return null
        } else {
            val issue = executor.execute(GithubApiRequests.Repos.Issues.create(server, user, repo,
                    CHAT_ISSUE_TITLE, CHAT_START_TEXT, null, listOf(CHAT_ISSUE_LABEL)))
            chatIssueId = issue.number.toString()
            return issue
        }
    }

    fun post(post: String): String? {
        try {
            val comment = executor.execute(GithubApiRequests.Repos.Issues.Comments.create(
                    server, user, repo, chatIssueId, post))
            log("Comment added: $comment")
            return comment.run { "<p>${user.login} says @ $createdAt:$post" }
        } catch (e: Exception) {
            return null
        }
    }

    fun readChat(): String {
        try {
            val issue = ensureChatStarted()
                    ?: executor.execute(GithubApiRequests.Repos.Issues.get(server, user, repo, chatIssueId))!!
            val issueText = issue.run { "<p class='header'>Chat started by ${user.login} @ $createdAt" }
            val comments = GithubApiPagesLoader.loadAll(executor, indicator,
                    GithubApiRequests.Repos.Issues.Comments.pages(
                            server, user, repo, chatIssueId))
            return issueText + comments
                    .map { it.run { "<p class='postheader'>${user.login} says @ $createdAt:$bodyHtml" } }
                    .joinToString("<br>")
        } catch (e: Exception) {
            return "<h3>Failed to read chat contents</h3>"
        }
    }
}