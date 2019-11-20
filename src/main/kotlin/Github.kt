import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.LocalFilePath
import git4idea.GitUtil
import git4idea.repo.GitRepositoryImpl
import git4idea.repo.GitRepositoryManager
import org.jetbrains.plugins.github.api.*
import org.jetbrains.plugins.github.api.data.GithubIssue
import org.jetbrains.plugins.github.api.data.GithubIssueComment
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager
import org.jetbrains.plugins.github.util.GithubGitHelper
import org.jetbrains.plugins.github.util.GithubUrlUtil

class Github(private val executor: GithubApiRequestExecutor,
             private val serverPath: GithubServerPath,
             private val repoPath: GithubFullPath)
{
    private val indicator = EmptyProgressIndicator()
    private lateinit var chatIssueId: String

    private fun ensureChatStarted(): GithubIssue? {
        ::chatIssueId.isInitialized && return null

        val found = GithubApiPagesLoader.loadAll(executor, indicator,
                GithubApiRequests.Repos.Issues.pages(serverPath, repoPath.user, repoPath.repository))
                .firstOrNull {
                    it.title == CHAT_ISSUE_TITLE &&
                            it.labels?.map { it.name }.orEmpty().contains(CHAT_ISSUE_LABEL)
                }?.number?.toString()
        if (found != null) {
            chatIssueId = found
            return null
        } else {
            val issue = executor.execute(GithubApiRequests.Repos.Issues.create(
                    serverPath, repoPath.user, repoPath.repository,
                    CHAT_ISSUE_TITLE, CHAT_START_TEXT, null, listOf(CHAT_ISSUE_LABEL)))
            chatIssueId = issue.number.toString()
            return issue
        }
    }

    fun formatIssue(issue: GithubIssue) =
            issue.run { "<p class='header'>Chat started by ${user.login} @ $createdAt" }
    fun formatComment(comment: GithubIssueComment, body: String) =
            comment.run { "<p class='postheader'>${user.login} says @ $createdAt:$body" }

    fun post(post: String): String? {
        try {
            val comment = executor.execute(GithubApiRequests.Repos.Issues.Comments.create(
                    serverPath, repoPath.user, repoPath.repository, chatIssueId, post))
            log("Comment added: $comment")
            return formatComment(comment, post)
        } catch (e: Exception) {
            return null
        }
    }

    fun readChat(): String? {
        try {
            val issue = ensureChatStarted()
                    ?: executor.execute(GithubApiRequests.Repos.Issues.get(
                            serverPath, repoPath.user, repoPath.repository, chatIssueId))!!
            val comments = GithubApiPagesLoader.loadAll(executor, indicator,
                    GithubApiRequests.Repos.Issues.Comments.pages(
                            serverPath, repoPath.user, repoPath.repository, chatIssueId))
            return formatIssue(issue) + comments
                    .map { formatComment(it, it.bodyHtml) }
                    .joinToString("")
        } catch (e: Exception) {
            return null
        }
    }

    companion object {
        val CHAT_ISSUE_LABEL = "idea-chat"
        val CHAT_ISSUE_TITLE = "[Chat]"
        val CHAT_START_TEXT = "Let's have a chat here!"

        fun enabledFor(project: Project?): Boolean {
            val enabled = project != null &&
                    !project.isDefault() &&
//                    service<GithubGitHelper>().havePossibleRemotes(project) ///doesn't work?
//                    GitRepositoryManager.getInstance(project).repositories.any()
                    create(project) != null
            log("(hack) chat enabled = $enabled")
            val poss = service<GithubGitHelper>().havePossibleRemotes(project!!) ///doesn't
            log("(hack) poss = $poss")
            val repo = GitRepositoryManager.getInstance(project).repositories
            log("(hack) repo = $repo")
            val rep1 = GitRepositoryManager.getInstance(project).getRepositoryForFile(
                    LocalFilePath("resources/chat.css", true))
            log("(hack) rep1 = $rep1")
            return enabled
        }

        fun create(project: Project): Github? {
            GitUtil.findGitDir(project.baseDir) ?: return null

            val repo = GitRepositoryImpl.getInstance(
                    project.baseDir, project, project, true)
            log("repo = $repo")
            val url = repo.remotes.firstOrNull()?.firstUrl
            log("url $url")
            val path = url?.run { GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(this) }
            log("path $path")
            path ?: return null

            val authMgr = GithubAuthenticationManager.getInstance()
            authMgr.ensureHasAccounts(project) || return null

            val account = authMgr.getSingleOrDefaultAccount(project)!!
            val executor = GithubApiRequestExecutorManager.getInstance().getExecutor(account)
            val serverPath = GithubServerPath.from(url)
                    .run { GithubServerPath(schema == "http", host, port, null) }
            return Github(executor, serverPath, path)
        }
    }
}