package com.github.gradusnikov.eclipse.assistai.mcp.servers;

import java.util.Optional;

import org.eclipse.e4.core.di.annotations.Creatable;

import com.github.gradusnikov.eclipse.assistai.mcp.annotations.McpServer;
import com.github.gradusnikov.eclipse.assistai.mcp.annotations.Tool;
import com.github.gradusnikov.eclipse.assistai.mcp.annotations.ToolParam;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitBranchResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitCheckoutResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitCommitResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitDeleteBranchResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitDiffResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitLogResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStagePatchResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStageResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStashListResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStashPopResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStashResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStatusResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.services.GitService;

import jakarta.inject.Inject;

@Creatable
@McpServer(name = "eclipse-git")
public class EclipseGitMcpServer
{
    @Inject
    private GitService gitService;

    @Tool(name = "gitStatus", description = "Reports the working tree status of the Git repository associated with the project: "
            + "separate staged, unstaged, untracked and conflicting lists, the current branch and its distance from its upstream. "
            + "Every entry names its Eclipse projectName and a project-relative filePath, which the reading and editing tools take, "
            + "plus the repository-relative repoPath the Git tools take. A clean working tree is reported as clean=true with empty lists.", type = "object",
            outputType = GitStatusResponse.class)
    public GitStatusResponse gitStatus(
            @ToolParam(name = "projectName", description = "The Eclipse project name (use listProjects to find it)", required = true) String projectName)
    {
        return gitService.getStatus(projectName);
    }

    @Tool(name = "gitLog", description = "Lists the most recent commits of the Git repository associated with the project. "
            + "Each commit reports sha, shortSha, author, authorEmail, authorTimeMillis (epoch milliseconds), the full message and its first line. "
            + "The truncated flag says whether the history goes further back than maxCount.", type = "object",
            outputType = GitLogResponse.class)
    public GitLogResponse gitLog(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "maxCount", description = "Maximum number of commits to show (default: 20)", required = false) String maxCount)
    {
        int count = Optional.ofNullable(maxCount).map(Integer::parseInt).orElse(20);
        return gitService.getLog(projectName, count);
    }

    @Tool(name = "gitAdd", description = "Stages files for the next commit. Use '.' to stage all changes (new, modified, and deleted files). "
            + "Reports the files whose index entry actually changed, each naming its Eclipse projectName and project-relative filePath "
            + "as well as the repository-relative repoPath. A pattern that matches no changed file is totalFiles=0 with an empty list - "
            + "Git does not fail on it, so check the count rather than assuming the pattern matched.", type = "object",
            outputType = GitStageResponse.class)
    public GitStageResponse gitAdd(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "filePattern", description = "File pattern to add (e.g., '.' for all, 'src/com/example/MyClass.java' for a specific file)", required = true) String filePattern)
    {
        return gitService.addFiles(projectName, filePattern);
    }

    @Tool(name = "gitCommit", description = "Commits the currently staged changes with the given message. "
            + "Returns the new commit as sha, shortSha, author, authorEmail, authorTimeMillis, message and shortMessage - "
            + "the same shape gitLog reports - so the sha is a field rather than a prefix of a sentence.", type = "object",
            outputType = GitCommitResponse.class)
    public GitCommitResponse gitCommit(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "message", description = "The commit message", required = true) String message)
    {
        return gitService.commit(projectName, message);
    }

    @Tool(name = "gitReadFile", description = "Reads a UTF-8 text file from a Git revision without changing the working tree. The path is relative to the Eclipse project. Use revision 'INDEX' to read the staged version; otherwise revision defaults to HEAD and may be a branch, tag, or commit.", type = "object")
    public String gitReadFile(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "filePath", description = "File path relative to the Eclipse project", required = true) String filePath,
            @ToolParam(name = "revision", description = "Git branch, tag, commit, or 'INDEX'. Default: HEAD", required = false) String revision)
    {
        String effectiveRevision = Optional.ofNullable(revision).filter(value -> !value.isBlank()).orElse("HEAD");
        return gitService.readFileAtRevision(projectName, filePath, effectiveRevision);
    }

    @Tool(name = "gitDiff", description = "Shows a unified diff for staged or unstaged changes, optionally limited to comma-separated project-relative "
            + "files/directories and with whitespace changes ignored. The hunks are in unifiedDiff, which names paths from the repository root; "
            + "the files list additionally resolves each of them to an Eclipse projectName and project-relative filePath that the reading and editing "
            + "tools accept, with per-file addedLines/removedLines. identical=true means the two sides are the same, and baseRevision is null in a "
            + "repository with no commits.", type = "object",
            outputType = GitDiffResponse.class)
    public GitDiffResponse gitDiff(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "staged", description = "If 'true', shows staged (cached) changes instead of unstaged. Default: false", required = false) String staged,
            @ToolParam(name = "pathFilter", description = "Optional comma-separated file or directory paths relative to the Eclipse project", required = false) String pathFilter,
            @ToolParam(name = "ignoreWhitespace", description = "If 'true', ignores whitespace when formatting hunks. Default: false", required = false) String ignoreWhitespace)
    {
        boolean isStagedDiff = Optional.ofNullable(staged).map(Boolean::parseBoolean).orElse(false);
        boolean ignoresWhitespace = Optional.ofNullable(ignoreWhitespace).map(Boolean::parseBoolean).orElse(false);
        return gitService.getDiff(projectName, isStagedDiff, pathFilter, ignoresWhitespace);
    }

    @Tool(name = "gitBranch", description = "Lists the branches of the repository. Local branches are in 'branches', each with a 'current' flag "
            + "for the checked-out one, and remote-tracking branches are in 'remoteBranches'. Branch 'name' is what gitCheckout, "
            + "gitCreateBranch and gitDeleteBranch take.", type = "object",
            outputType = GitBranchResponse.class)
    public GitBranchResponse gitBranch(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "includeRemote", description = "If 'true', includes remote-tracking branches. Default: false", required = false) String includeRemote)
    {
        boolean remote = Optional.ofNullable(includeRemote).map(Boolean::parseBoolean).orElse(false);
        return gitService.listBranches(projectName, remote);
    }

    @Tool(name = "gitCreateBranch", description = "Creates a new branch. Does not switch to it - use gitCheckout to switch.", type = "object")
    public String gitCreateBranch(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "branchName", description = "Name of the new branch to create", required = true) String branchName,
            @ToolParam(name = "startPoint", description = "Optional start point (branch name, tag, or commit SHA). Defaults to HEAD.", required = false) String startPoint)
    {
        return gitService.createBranch(projectName, branchName, startPoint);
    }

    @Tool(name = "gitDeleteBranch", description = "Deletes a branch. Cannot delete the currently checked-out branch. "
            + "deleted says whether the branch is gone and deletedRefs lists the refs that were removed. A branch that is not fully merged "
            + "is refused with deleted=false and a BRANCH_NOT_MERGED diagnostic; retry with force='true' to delete it anyway.", type = "object",
            outputType = GitDeleteBranchResponse.class)
    public GitDeleteBranchResponse gitDeleteBranch(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "branchName", description = "Name of the branch to delete", required = true) String branchName,
            @ToolParam(name = "force", description = "If 'true', force-deletes even if the branch is not fully merged. Default: false", required = false) String force)
    {
        boolean forceDelete = Optional.ofNullable(force).map(Boolean::parseBoolean).orElse(false);
        return gitService.deleteBranch(projectName, branchName, forceDelete);
    }

    @Tool(name = "gitCheckout", description = "Checks out a branch, switching the working tree to that branch. "
            + "status is SWITCHED or BLOCKED: when local changes would be overwritten nothing is switched, blockingFiles names them "
            + "(projectName, filePath, repoPath) and a CHECKOUT_CONFLICT diagnostic is attached. A checkout rewrites the whole repository, "
            + "so refreshedProjects lists every Eclipse project that was refreshed, not only the one that was named.", type = "object",
            outputType = GitCheckoutResponse.class)
    public GitCheckoutResponse gitCheckout(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "branchName", description = "The branch name to checkout", required = true) String branchName)
    {
        return gitService.checkoutBranch(projectName, branchName);
    }

    @Tool(name = "gitReset", description = "Unstages files from the index (equivalent to 'git reset HEAD <file>'). Does not modify the working tree. "
            + "Reports the index entries that actually left the staged set, each naming its Eclipse projectName and project-relative filePath "
            + "plus the repository-relative repoPath, with changeType being what the file had been staged as. A pattern matching nothing "
            + "is totalFiles=0.", type = "object",
            outputType = GitStageResponse.class)
    public GitStageResponse gitReset(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "filePattern", description = "File pattern to unstage (e.g., '.' for all, or a specific file path)", required = true) String filePattern)
    {
        return gitService.resetFiles(projectName, filePattern);
    }

    @Tool(name = "gitStash", description = "Stashes the current working directory and index changes, reverting the working tree to HEAD. "
            + "stashed=false with a null stash means the working tree was already clean - an outcome, not a failure. When something was "
            + "stashed, stash carries its index, its stash@{n} ref, the commit sha it is stored as and its message, the same shape "
            + "gitStashList reports.", type = "object",
            outputType = GitStashResponse.class)
    public GitStashResponse gitStash(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "message", description = "Optional message to describe the stash", required = false) String message)
    {
        return gitService.stash(projectName, message);
    }

    @Tool(name = "gitStashPop", description = "Applies the most recent stash entry and, if that succeeded, removes it. "
            + "status is APPLIED, CONFLICTED or NOTHING_TO_APPLY. On CONFLICTED the stash was kept (dropped=false), the working tree holds "
            + "conflict markers, conflicting names the affected files and a MERGE_CONFLICT diagnostic is attached - do not treat it as done.", type = "object",
            outputType = GitStashPopResponse.class)
    public GitStashPopResponse gitStashPop(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName)
    {
        return gitService.stashPop(projectName);
    }

    @Tool(name = "gitStashList", description = "Lists the stash entries, most recent first. Each entry reports its index, its stash@{n} ref, "
            + "the commit sha it is stored as, and its message. An empty stash is totalStashes=0 with an empty list.", type = "object",
            outputType = GitStashListResponse.class)
    public GitStashListResponse gitStashList(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName)
    {
        return gitService.stashList(projectName);
    }

    @Tool(name = "gitStagePatch", description = "Stages specific changes from a unified diff patch into the index without modifying the working tree. "
            + "Use this to stage partial file changes for selective commits. The patch must be in standard unified diff format with file headers "
            + "(--- a/path and +++ b/path) and @@ hunk headers. IMPORTANT: patch paths are relative to the REPOSITORY root, not to the Eclipse "
            + "project - unlike gitDiff, gitReadFile and the editing tools, which take project-relative paths. The two differ whenever the project "
            + "does not sit at the repository root; gitStatus reports both forms as filePath and repoPath, and the unifiedDiff of gitDiff already "
            + "uses the repository form. status is STAGED or FAILED, files lists what actually reached the index, and workingTreePreserved says "
            + "whether the uncommitted content of every touched file was put back.", type = "object",
            outputType = GitStagePatchResponse.class)
    public GitStagePatchResponse gitStagePatch(
            @ToolParam(name = "projectName", description = "The Eclipse project name", required = true) String projectName,
            @ToolParam(name = "patch", description = "A unified diff patch string to stage. Must include file headers (--- a/path, +++ b/path) and @@ hunk headers.", required = true) String patch)
    {
        return gitService.stagePatch(projectName, patch);
    }
}
