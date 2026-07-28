package com.github.gradusnikov.eclipse.assistai.mcp.services;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import java.nio.file.Path;

import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.treewalk.TreeWalk;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.e4.core.di.annotations.Creatable;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.api.ApplyResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.NotMergedException;
import org.eclipse.jgit.api.errors.StashApplyFailureException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.Patch;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.ui.IEditorPart;

import com.github.gradusnikov.eclipse.assistai.mcp.results.Diagnostic;
import com.github.gradusnikov.eclipse.assistai.mcp.results.DiagnosticCode;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitBranchResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitCheckoutResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitCommitResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitDeleteBranchResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitDiffResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitLogResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStagePatchResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStageResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStageResponse.StageOperation;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStashListResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStashListResponse.GitStash;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStashPopResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStashResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStatusResponse;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStatusResponse.ChangeType;
import com.github.gradusnikov.eclipse.assistai.mcp.results.GitStatusResponse.GitFileChange;
import com.github.gradusnikov.eclipse.assistai.tools.UISynchronizeCallable;

import jakarta.inject.Inject;

@Creatable
@SuppressWarnings("restriction")
public class GitService
{
    private static final ConcurrentHashMap<String, ReentrantLock> repoLocks = new ConcurrentHashMap<>();

    @Inject
    private ILog logger;

    @Inject
    public UISynchronizeCallable uiSync;

    @Inject
    public EditorService editorService;

    private ReentrantLock getRepositoryLock(Repository repository)
    {
        return repoLocks.computeIfAbsent(repository.getDirectory().getAbsolutePath(), k -> new ReentrantLock());
    }

    private Repository getRepository(String projectName)
    {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (!project.exists())
        {
            throw new RuntimeException("Project not found: " + projectName);
        }
        RepositoryMapping mapping = RepositoryMapping.getMapping(project);
        if (mapping == null)
        {
            throw new RuntimeException("Project is not mapped to a Git repository: " + projectName);
        }
        return mapping.getRepository();
    }

    private boolean refreshProject(String projectName)
    {
        try
        {
            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
            project.refreshLocal(IResource.DEPTH_INFINITE, null);
            return true;
        }
        catch (CoreException e)
        {
            logger.error("Failed to refresh project: " + projectName, e);
            return false;
        }
    }

    /**
     * Refreshes every project mapped into the repository, and says which ones it
     * managed.
     * <p>
     * An operation that rewrites the working tree - a checkout, a stash pop, the
     * save-and-restore cycle of a patch stage - rewrites it for the whole repository,
     * and one repository routinely holds several Eclipse projects. Refreshing only the
     * project that happened to be named left every sibling stale, with nothing saying
     * so.
     */
    private List<String> refreshMappedProjects(Repository repository)
    {
        List<String> refreshed = new ArrayList<>();
        for (String projectName : mappedProjects(repository).values())
        {
            if (refreshProject(projectName))
            {
                refreshed.add(projectName);
            }
        }
        refreshed.sort(Comparator.naturalOrder());
        return refreshed;
    }

    /**
     * The project's folder relative to the repository root, empty for a project sitting
     * at that root.
     */
    private String projectPrefix(String projectName)
    {
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        RepositoryMapping mapping = RepositoryMapping.getMapping(project);
        String prefix = mapping == null ? "" : mapping.getRepoRelativePath(project);
        return prefix == null ? "" : prefix;
    }

    /** The commit HEAD points at, or null in a repository with no commits. */
    private String headSha(Repository repository)
    {
        try
        {
            ObjectId head = repository.resolve(Constants.HEAD);
            return head == null ? null : head.getName();
        }
        catch (IOException e)
        {
            return null;
        }
    }

    /** The checked-out branch, or null when it cannot be read. */
    private String currentBranch(Repository repository)
    {
        try
        {
            return repository.getBranch();
        }
        catch (IOException e)
        {
            return null;
        }
    }

    /**
     * The projects mapped into a repository, keyed by their folder relative to the
     * repository root - the empty string for a project sitting at that root.
     * <p>
     * Git reports every path from the repository root, but the reading and editing tools
     * address a file by project and project-relative path, and one repository routinely
     * holds several projects. Without this map a status entry names a file that no other
     * tool can open.
     */
    private Map<String, String> mappedProjects(Repository repository)
    {
        Map<String, String> prefixes = new LinkedHashMap<>();
        java.io.File repositoryDirectory = repository.getDirectory();

        for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects())
        {
            if (!project.isAccessible())
            {
                continue;
            }
            RepositoryMapping mapping = RepositoryMapping.getMapping(project);
            if (mapping == null || mapping.getRepository() == null
                    || !Objects.equals(repositoryDirectory, mapping.getRepository().getDirectory()))
            {
                continue;
            }
            String prefix = mapping.getRepoRelativePath(project);
            prefixes.putIfAbsent(prefix == null ? "" : prefix, project.getName());
        }
        return prefixes;
    }

    /**
     * The working tree state: staged, unstaged, untracked and conflicting files, the
     * current branch, and how far it has drifted from its upstream.
     * <p>
     * A clean working tree is reported as a clean flag with empty lists, not as a
     * sentence, so a caller can branch on it.
     */
    public GitStatusResponse getStatus(String projectName)
    {
        Repository repository = getRepository(projectName);
        try (Git git = new Git(repository))
        {
            var status = git.status().call();
            String branch = repository.getBranch();

            String upstreamBranch = null;
            Integer aheadCount = null;
            Integer behindCount = null;
            try
            {
                BranchTrackingStatus tracking = BranchTrackingStatus.of(repository, branch);
                if (tracking != null)
                {
                    String remoteTracking = tracking.getRemoteTrackingBranch();
                    upstreamBranch = remoteTracking != null && remoteTracking.startsWith(Constants.R_REMOTES)
                            ? remoteTracking.substring(Constants.R_REMOTES.length())
                            : remoteTracking;
                    aheadCount = tracking.getAheadCount();
                    behindCount = tracking.getBehindCount();
                }
            }
            catch (Exception e)
            {
                // A branch that tracks nothing. Left as a null upstream, which is what it
                // is, rather than reported as a failure to produce a status.
            }

            return GitStatusResponse.from(projectName, branch, upstreamBranch, aheadCount, behindCount, status,
                    mappedProjects(repository));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to get git status: " + e.getMessage(), e);
        }
    }

    /**
     * The most recent commits on the current branch.
     * <p>
     * One more commit than asked for is walked, so that "the history goes further back"
     * can be answered without counting the whole graph.
     */
    public GitLogResponse getLog(String projectName, int maxCount)
    {
        Repository repository = getRepository(projectName);
        int limit = Math.max(maxCount, 0);

        try (Git git = new Git(repository))
        {
            List<RevCommit> commits = new ArrayList<>();
            for (RevCommit commit : git.log().setMaxCount(limit + 1).call())
            {
                commits.add(commit);
            }

            boolean truncated = commits.size() > limit;
            if (truncated)
            {
                commits = commits.subList(0, limit);
            }

            return GitLogResponse.from(projectName, repository.getBranch(), commits, truncated);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to get git log: " + e.getMessage(), e);
        }
    }

    /**
     * Stages a pathspec, and reports what the index actually gained.
     * <p>
     * JGit succeeds against a pathspec that matches no file, so echoing the caller's own
     * pattern back - "Added: src/Typo.java" - confirmed a stage that never happened and
     * the commit that followed was silently wrong. The index is compared before and
     * after instead.
     */
    public GitStageResponse addFiles(String projectName, String filePattern)
    {
        Repository repository = getRepository(projectName);
        ReentrantLock lock = getRepositoryLock(repository);
        lock.lock();

        List<GitFileChange> staged;
        try (Git git = new Git(repository))
        {
            IndexSnapshot before = indexSnapshot(repository);
            git.add().addFilepattern(filePattern).call();
            git.add().setUpdate(true).addFilepattern(filePattern).call();
            staged = indexDelta(before, indexSnapshot(repository), mappedProjects(repository));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to add files: " + e.getMessage(), e);
        }
        finally
        {
            lock.unlock();
        }
        refreshProject(projectName);
        return GitStageResponse.of(projectName, StageOperation.STAGE, filePattern, staged);
    }

    /**
     * Stages the changes a unified diff describes, leaving the working tree as it was.
     * <p>
     * It works by saving the working-tree content of every file the patch names,
     * checking those files out clean, applying the patch to the clean copies, staging
     * the result and putting the saved content back. The caller's uncommitted edits are
     * therefore off disk for the length of the middle three steps, which is why the
     * restore runs in a {@code finally} rather than at the end of the happy path: it
     * used to sit inside the {@code try}, so a patch that failed to apply - the common
     * case for a generated patch - left every file it touched reverted and the edits
     * gone, while the caller saw only "Failed to stage patch".
     */
    public GitStagePatchResponse stagePatch(String projectName, String patch)
    {
        Repository repository = getRepository(projectName);
        Map<String, String> projects = mappedProjects(repository);
        ReentrantLock lock = getRepositoryLock(repository);
        lock.lock();

        GitStagePatchResponse response;
        try (Git gitCmd = new Git(repository))
        {
            File workTree = repository.getWorkTree();

            Patch parsedPatch = new Patch();
            parsedPatch.parse(new ByteArrayInputStream(patch.getBytes(StandardCharsets.UTF_8)));

            if (parsedPatch.getFiles().isEmpty())
            {
                // Reported as a success until now, so an unparseable patch and a staged
                // one were told apart only by reading the sentence back.
                String detail = parsedPatch.getErrors().isEmpty()
                        ? ""
                        : " First parser error: " + parsedPatch.getErrors().get(0).getMessage();
                return GitStagePatchResponse.rejected(projectName,
                        Diagnostic.fatal(DiagnosticCode.PATCH_APPLY_FAILED,
                                "The patch parsed to no file headers, so nothing was staged. A patch needs"
                                        + " '--- a/<path>' and '+++ b/<path>' lines and at least one '@@' hunk"
                                        + " header." + detail));
            }

            List<Diagnostic> pathProblems = validatePatchPaths(repository, projectName, parsedPatch);
            if (!pathProblems.isEmpty())
            {
                // Nothing has been touched yet, so the working tree is intact.
                return GitStagePatchResponse.failed(projectName, true, List.of(), pathProblems);
            }

            Map<File, byte[]> savedWorkingTree = new LinkedHashMap<>();
            for (FileHeader fileHeader : parsedPatch.getFiles())
            {
                File file = new File(workTree, patchPath(fileHeader));
                if (file.isFile())
                {
                    savedWorkingTree.put(file, Files.readAllBytes(file.toPath()));
                }
            }

            IndexSnapshot before = indexSnapshot(repository);
            Exception failure = null;
            List<Diagnostic> restoreProblems;
            try
            {
                var checkoutCmd = gitCmd.checkout();
                for (FileHeader fileHeader : parsedPatch.getFiles())
                {
                    checkoutCmd.addPath(patchPath(fileHeader));
                }
                checkoutCmd.call();

                ApplyResult result = gitCmd.apply()
                    .setPatch(new ByteArrayInputStream(patch.getBytes(StandardCharsets.UTF_8)))
                    .call();

                stageFiles(repository, workTree, result.getUpdatedFiles());
            }
            catch (Exception e)
            {
                failure = e;
            }
            finally
            {
                restoreProblems = restoreWorkingTree(savedWorkingTree);
            }

            List<String> restoredPaths = repoRelativePaths(workTree, savedWorkingTree.keySet());
            boolean workingTreePreserved = restoreProblems.isEmpty();

            if (failure != null)
            {
                List<Diagnostic> diagnostics = new ArrayList<>();
                diagnostics.add(Diagnostic.fatal(DiagnosticCode.PATCH_APPLY_FAILED,
                        "The patch did not apply: " + failure.getMessage()));
                diagnostics.addAll(restoreProblems);
                response = GitStagePatchResponse.failed(projectName, workingTreePreserved, restoredPaths, diagnostics);
            }
            else
            {
                response = GitStagePatchResponse.staged(projectName,
                        indexDelta(before, indexSnapshot(repository), projects), workingTreePreserved, restoredPaths,
                        restoreProblems);
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to stage patch: " + e.getMessage(), e);
        }
        finally
        {
            lock.unlock();
        }

        refreshMappedProjects(repository);
        return response;
    }

    /** The repository-relative path a patch file header addresses. */
    private static String patchPath(FileHeader fileHeader)
    {
        return fileHeader.getChangeType() == DiffEntry.ChangeType.DELETE
                ? fileHeader.getOldPath()
                : fileHeader.getNewPath();
    }

    /**
     * Checks that every path the patch names exists where the patch says it does.
     * <p>
     * A patch addresses files from the repository root, while {@code gitDiff},
     * {@code gitReadFile} and every editing tool take a path relative to the Eclipse
     * project - and one repository routinely holds several projects, so the two forms
     * differ for all but a project sitting at the repository root. A patch written in
     * the project's terms used to check out and apply against paths that do not exist,
     * stage nothing, and still report success. Where the project-relative reading of a
     * path does resolve, the diagnostic says so and gives the repository path to use.
     */
    private List<Diagnostic> validatePatchPaths(Repository repository, String projectName, Patch parsedPatch)
    {
        File workTree = repository.getWorkTree();
        String projectPrefix = projectPrefix(projectName);
        List<Diagnostic> problems = new ArrayList<>();

        for (FileHeader fileHeader : parsedPatch.getFiles())
        {
            if (fileHeader.getChangeType() == DiffEntry.ChangeType.ADD)
            {
                // A patch that creates a file names a path that is not there yet.
                continue;
            }
            String path = patchPath(fileHeader);
            if (new File(workTree, path).isFile())
            {
                continue;
            }

            String repositoryPath = projectPrefix.isEmpty() ? null : projectPrefix + "/" + path;
            if (repositoryPath != null && new File(workTree, repositoryPath).isFile())
            {
                problems.add(Diagnostic.fatal(DiagnosticCode.PATCH_APPLY_FAILED,
                        "Patch path '" + path + "' does not exist in the repository. Patch paths are"
                                + " repository-relative, and project '" + projectName + "' sits at '" + projectPrefix
                                + "' inside the repository: write the header as '" + repositoryPath + "'."));
            }
            else
            {
                problems.add(Diagnostic.fatal(DiagnosticCode.PATCH_APPLY_FAILED,
                        "Patch path '" + path + "' does not exist in the repository working tree."));
            }
        }
        return problems;
    }

    /** Writes the patched content of each file into the index, leaving the file alone. */
    private void stageFiles(Repository repository, File workTree, List<File> files) throws IOException
    {
        DirCache dirCache = repository.lockDirCache();
        try
        {
            DirCacheEditor editor = dirCache.editor();
            ObjectInserter inserter = repository.newObjectInserter();

            for (File file : files)
            {
                byte[] content = Files.readAllBytes(file.toPath());
                ObjectId blobId = inserter.insert(Constants.OBJ_BLOB, content);

                String repoRelativePath = workTree.toPath()
                    .relativize(file.toPath()).toString().replace('\\', '/');

                editor.add(new DirCacheEditor.PathEdit(repoRelativePath)
                {
                    @Override
                    public void apply(DirCacheEntry ent)
                    {
                        ent.setObjectId(blobId);
                        ent.setFileMode(org.eclipse.jgit.lib.FileMode.REGULAR_FILE);
                        ent.setLength(content.length);
                        ent.setLastModified(java.time.Instant.now());
                    }
                });
            }

            inserter.flush();
            editor.commit();
        }
        finally
        {
            dirCache.unlock();
        }
    }

    /**
     * Puts back the exact bytes each file held before the patch was applied.
     * <p>
     * Never throws. It runs on the failure path, where an exception raised here would
     * replace the report of what went wrong, and the caller would learn neither that
     * the patch failed nor that its edits are missing.
     *
     * @return one diagnostic per file whose content could not be written back, empty
     *         when the working tree is exactly as it was
     */
    private List<Diagnostic> restoreWorkingTree(Map<File, byte[]> savedWorkingTree)
    {
        List<Diagnostic> problems = new ArrayList<>();
        for (Map.Entry<File, byte[]> entry : savedWorkingTree.entrySet())
        {
            try
            {
                Files.write(entry.getKey().toPath(), entry.getValue());
            }
            catch (Exception e)
            {
                logger.error("Failed to restore working tree file: " + entry.getKey(), e);
                problems.add(Diagnostic.fatal(DiagnosticCode.INTERNAL_ERROR,
                        "The working tree content of '" + entry.getKey().getName() + "' could not be restored after"
                                + " staging the patch, and the file now holds its committed content: "
                                + e.getMessage()));
            }
        }
        return problems;
    }

    private static List<String> repoRelativePaths(File workTree, Collection<File> files)
    {
        List<String> paths = new ArrayList<>();
        for (File file : files)
        {
            paths.add(workTree.toPath().relativize(file.toPath()).toString().replace('\\', '/'));
        }
        paths.sort(Comparator.naturalOrder());
        return paths;
    }

    /**
     * The commit a {@code gitCommit} produced.
     * <p>
     * The sha used to be glued to the commit's subject by a single space, and subjects
     * contain spaces themselves - so the obvious split recovered the sha and mangled the
     * message. It is the handle for every next action, and is now a field.
     */
    public GitCommitResponse commit(String projectName, String message)
    {
        Repository repository = getRepository(projectName);
        ReentrantLock lock = getRepositoryLock(repository);
        lock.lock();
        try (Git git = new Git(repository))
        {
            RevCommit commit = git.commit().setMessage(message).call();
            String branch = repository.getBranch();
            refreshProject(projectName);
            return GitCommitResponse.of(projectName, branch, GitLogResponse.toCommit(commit));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to commit: " + e.getMessage(), e);
        }
        finally
        {
            lock.unlock();
        }
    }

    public String readFileAtRevision(String projectName, String filePath, String revision)
    {
        Objects.requireNonNull(projectName, "projectName");
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(revision, "revision");

        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
        if (!project.exists())
        {
            throw new RuntimeException("Project not found: " + projectName);
        }

        RepositoryMapping mapping = RepositoryMapping.getMapping(project);
        if (mapping == null)
        {
            throw new RuntimeException("Project is not mapped to a Git repository: " + projectName);
        }

        String inputPath = filePath.replace('\\', '/');
        Path normalizedPath = Path.of(inputPath).normalize();
        String normalized = normalizedPath.toString().replace('\\', '/');
        if (inputPath.isBlank() || normalizedPath.isAbsolute() || normalized.equals("..") || normalized.startsWith("../")
                || inputPath.matches("^[A-Za-z]:.*"))
        {
            throw new IllegalArgumentException("File path must be relative to the Eclipse project: " + filePath);
        }

        String projectPrefix = mapping.getRepoRelativePath(project);
        String repositoryPath = projectPrefix == null || projectPrefix.isBlank() ? normalized : projectPrefix + "/" + normalized;
        Repository repository = mapping.getRepository();

        try
        {
            ObjectLoader loader;
            if ("INDEX".equalsIgnoreCase(revision))
            {
                DirCacheEntry entry = repository.readDirCache().getEntry(repositoryPath);
                if (entry == null)
                {
                    throw new IllegalArgumentException("File '" + filePath + "' was not found in the Git index.");
                }
                loader = repository.open(entry.getObjectId());
            }
            else
            {
                ObjectId treeId = repository.resolve(revision + "^{tree}");
                if (treeId == null)
                {
                    throw new IllegalArgumentException("Git revision could not be resolved: " + revision);
                }
                try (TreeWalk treeWalk = TreeWalk.forPath(repository, repositoryPath, treeId))
                {
                    if (treeWalk == null)
                    {
                        throw new IllegalArgumentException("File '" + filePath + "' was not found at revision '" + revision + "'.");
                    }
                    loader = repository.open(treeWalk.getObjectId(0));
                }
            }

            if (loader.getSize() > 5 * 1024 * 1024)
            {
                throw new IllegalArgumentException("Git file is larger than the 5 MiB read limit: " + filePath);
            }

            byte[] content = loader.getBytes();
            for (byte value : content)
            {
                if (value == 0)
                {
                    throw new IllegalArgumentException("Git file appears to be binary: " + filePath);
                }
            }
            return new String(content, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to read Git file: " + e.getMessage(), e);
        }
    }

    /**
     * The staged or unstaged differences of the repository a project is mapped into.
     * <p>
     * Three outcomes used to share the one returned String - hunks, an empty string, and
     * the sentence "No commits yet." - so an unchanged tree, a fresh repository and a
     * failure were told apart by reading prose. The hunks are still a string, because a
     * unified diff is a machine format with a parser everywhere, but each file in it is
     * also listed with the project and project-relative path the reading and editing
     * tools take, since Git names paths from the repository root and none of them accept
     * that form.
     */
    public GitDiffResponse getDiff(String projectName, boolean staged, String pathFilter, boolean ignoreWhitespace)
    {
        Repository repository = getRepository(projectName);
        List<String> repositoryPaths = resolveDiffPaths(projectName, pathFilter);
        Map<String, String> projects = mappedProjects(repository);

        try (var out = new ByteArrayOutputStream();
             var formatter = new DiffFormatter(out))
        {
            ObjectId head = repository.resolve(Constants.HEAD);

            RawTextComparator comparator = ignoreWhitespace ? RawTextComparator.WS_IGNORE_ALL : RawTextComparator.DEFAULT;
            formatter.setRepository(repository);
            formatter.setDiffComparator(comparator);
            formatter.setDetectRenames(true);
            if (!repositoryPaths.isEmpty())
            {
                formatter.setPathFilter(PathFilterGroup.createFromStrings(repositoryPaths));
            }

            // A repository with no commits has no HEAD to compare against. The empty
            // tree is what Git itself compares a fresh index with, so the staged changes
            // are still reported rather than replaced by a sentence.
            AbstractTreeIterator oldTree = staged
                    ? (head == null ? new EmptyTreeIterator() : prepareTreeParser(repository, head))
                    : prepareIndexTreeParser(repository);
            AbstractTreeIterator newTree = staged
                    ? prepareIndexTreeParser(repository)
                    : new FileTreeIterator(repository);

            List<DiffEntry> diffs = formatter.scan(oldTree, newTree);

            // The line counts come from the EditList JGit builds to render each file, so
            // they agree with the body by construction.
            List<GitDiffResponse.GitFileDiff> files = new ArrayList<>();
            for (DiffEntry diff : diffs)
            {
                files.add(GitDiffResponse.file(formatter.toFileHeader(diff), projects));
            }
            formatter.format(diffs);

            String fromLabel = staged ? (head == null ? "EMPTY_TREE" : "HEAD") : "INDEX";
            String toLabel = staged ? "INDEX" : "WORKING_TREE";

            return GitDiffResponse.of(projectName, staged, fromLabel, toLabel, head == null ? null : head.getName(),
                    files, out.toString(StandardCharsets.UTF_8));
        }
        catch (IllegalArgumentException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to get diff: " + e.getMessage(), e);
        }
    }

    private List<String> resolveDiffPaths(String projectName, String pathFilter)
    {
        if (pathFilter == null || pathFilter.isBlank())
        {
            return List.of();
        }

        String projectPrefix = projectPrefix(projectName);

        return java.util.Arrays.stream(pathFilter.split(","))
                .map(String::trim)
                .filter(path -> !path.isEmpty())
                .map(path -> {
                    String input = path.replace('\\', '/');
                    Path normalizedPath = Path.of(input).normalize();
                    String normalized = normalizedPath.toString().replace('\\', '/');
                    if (normalizedPath.isAbsolute() || normalized.equals("..") || normalized.startsWith("../")
                            || input.matches("^[A-Za-z]:.*"))
                    {
                        throw new IllegalArgumentException("Diff path must be relative to the Eclipse project: " + path);
                    }
                    return projectPrefix == null || projectPrefix.isBlank() ? normalized : projectPrefix + "/" + normalized;
                })
                .toList();
    }

    /**
     * The repository's branches, with the checked-out one flagged.
     * <p>
     * Remote-tracking branches are kept in their own list: only a local branch can be
     * checked out or deleted, and a caller choosing one should not have to notice a
     * {@code origin/} prefix to know that.
     */
    public GitBranchResponse listBranches(String projectName, boolean includeRemote)
    {
        Repository repository = getRepository(projectName);
        try (Git git = new Git(repository))
        {
            var cmd = git.branchList();
            if (includeRemote)
            {
                cmd.setListMode(ListBranchCommand.ListMode.ALL);
            }
            List<Ref> refs = cmd.call();

            return GitBranchResponse.from(projectName, repository.getBranch(), refs);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to list branches: " + e.getMessage(), e);
        }
    }

    public String createBranch(String projectName, String branchName, String startPoint)
    {
        Repository repository = getRepository(projectName);
        try (Git git = new Git(repository))
        {
            var cmd = git.branchCreate().setName(branchName);
            if (startPoint != null && !startPoint.isEmpty())
            {
                cmd.setStartPoint(startPoint);
            }
            cmd.call();
            refreshProject(projectName);
            return "Created branch: " + branchName;
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to create branch: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a branch, or says why it did not.
     * <p>
     * Refusing to delete unmerged work is the safety check doing its job, not a fault,
     * and it is the one failure here whose remedy is mechanical - so it arrives as
     * {@code deleted: false} with a code naming the retry, rather than as an exception a
     * caller has to read.
     */
    public GitDeleteBranchResponse deleteBranch(String projectName, String branchName, boolean force)
    {
        Repository repository = getRepository(projectName);
        try (Git git = new Git(repository))
        {
            List<String> deleted = git.branchDelete().setBranchNames(branchName).setForce(force).call();
            return GitDeleteBranchResponse.deleted(projectName, branchName, force, deleted);
        }
        catch (NotMergedException e)
        {
            return GitDeleteBranchResponse.failed(projectName, branchName, force,
                    Diagnostic.fatal(DiagnosticCode.BRANCH_NOT_MERGED,
                            "Branch '" + branchName + "' is not fully merged into HEAD, so deleting it would lose"
                                    + " commits. Call again with force=true to delete it anyway."));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to delete branch: " + e.getMessage(), e);
        }
    }

    /**
     * Switches the working tree to another branch.
     * <p>
     * Two things were prose. A checkout blocked by local changes threw with JGit's
     * conflicting paths flattened into the message, so the one actionable fact - which
     * files stand in the way - had to be recovered by splitting a sentence. And a
     * checkout rewrites the working tree of the whole repository, which routinely holds
     * more than one project, while only the named project was refreshed.
     */
    public GitCheckoutResponse checkoutBranch(String projectName, String branchName)
    {
        Repository repository = getRepository(projectName);
        Map<String, String> projects = mappedProjects(repository);
        String previousBranch = currentBranch(repository);

        ReentrantLock lock = getRepositoryLock(repository);
        lock.lock();
        try (Git git = new Git(repository))
        {
            git.checkout().setName(branchName).call();
        }
        catch (CheckoutConflictException e)
        {
            List<GitFileChange> blocking = new ArrayList<>();
            for (String path : e.getConflictingPaths())
            {
                blocking.add(GitStatusResponse.locate(path, ChangeType.CONFLICTING, projects));
            }
            return GitCheckoutResponse.blocked(projectName, branchName, previousBranch, headSha(repository), blocking,
                    Diagnostic.fatal(DiagnosticCode.CHECKOUT_CONFLICT,
                            "Local changes to " + blocking.size() + " file(s) would be overwritten by checking out '"
                                    + branchName + "'. Commit, stash or reset them first."));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to checkout branch: " + e.getMessage(), e);
        }
        finally
        {
            lock.unlock();
        }

        List<String> refreshed = refreshMappedProjects(repository);
        return GitCheckoutResponse.switched(projectName, branchName, previousBranch, currentBranch(repository),
                headSha(repository), refreshed);
    }

    /**
     * Resets index entries to HEAD, and reports what actually left the staged set.
     * <p>
     * As with {@link #addFiles}, echoing the caller's own pathspec back confirmed an
     * unstage that a non-matching pattern never performed.
     */
    public GitStageResponse resetFiles(String projectName, String filePattern)
    {
        Repository repository = getRepository(projectName);
        ReentrantLock lock = getRepositoryLock(repository);
        lock.lock();

        List<GitFileChange> unstaged;
        try (Git git = new Git(repository))
        {
            IndexSnapshot before = indexSnapshot(repository);
            git.reset().addPath(filePattern).call();
            unstaged = indexDelta(before, indexSnapshot(repository), mappedProjects(repository));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to reset files: " + e.getMessage(), e);
        }
        finally
        {
            lock.unlock();
        }
        refreshProject(projectName);
        return GitStageResponse.of(projectName, StageOperation.UNSTAGE, filePattern, unstaged);
    }

    /**
     * Stashes the working tree.
     * <p>
     * The stash commit - the only durable handle on the work just taken off the working
     * tree - used to be returned inside a sentence, and having nothing to stash was
     * returned as another sentence in place of a result, while the sibling
     * {@link #stashList} already reported an empty stash as a count.
     */
    public GitStashResponse stash(String projectName, String message)
    {
        Repository repository = getRepository(projectName);
        ReentrantLock lock = getRepositoryLock(repository);
        lock.lock();

        GitStashResponse response;
        try (Git git = new Git(repository))
        {
            var cmd = git.stashCreate();
            if (message != null && !message.isEmpty())
            {
                cmd.setWorkingDirectoryMessage(message);
            }
            RevCommit stashRef = cmd.call();
            int totalStashes = git.stashList().call().size();

            response = stashRef == null
                    ? GitStashResponse.nothingToStash(projectName, totalStashes)
                    : GitStashResponse.stashed(projectName,
                            new GitStash(0, "stash@{0}", stashRef.getName(), stashRef.getShortMessage()),
                            totalStashes);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to stash: " + e.getMessage(), e);
        }
        finally
        {
            lock.unlock();
        }
        refreshMappedProjects(repository);
        return response;
    }

    /**
     * Applies the most recent stash entry and, if that succeeded, removes it.
     * <p>
     * "Applied and dropped stash." was a lie in the one case a caller must notice: a
     * stash that does not apply cleanly throws out of the apply, so the drop never runs
     * - the entry is still there, the working tree carries conflict markers, and none of
     * that reached the caller. Applying and dropping are two facts and are reported as
     * two.
     */
    public GitStashPopResponse stashPop(String projectName)
    {
        Repository repository = getRepository(projectName);
        Map<String, String> projects = mappedProjects(repository);
        ReentrantLock lock = getRepositoryLock(repository);
        lock.lock();

        GitStashPopResponse response;
        try (Git git = new Git(repository))
        {
            List<RevCommit> stashes = new ArrayList<>(git.stashList().call());
            if (stashes.isEmpty())
            {
                // An empty stash is a result, exactly as it is for gitStashList.
                return GitStashPopResponse.nothingToApply(projectName);
            }
            RevCommit top = stashes.get(0);

            try
            {
                git.stashApply().call();
                git.stashDrop().call();
                response = GitStashPopResponse.applied(projectName, "stash@{0}", top.getName(), top.getShortMessage());
            }
            catch (StashApplyFailureException e)
            {
                List<GitFileChange> conflicting = new ArrayList<>();
                for (String path : git.status().call().getConflicting())
                {
                    conflicting.add(GitStatusResponse.locate(path, ChangeType.CONFLICTING, projects));
                }
                response = GitStashPopResponse.conflicted(projectName, "stash@{0}", top.getName(),
                        top.getShortMessage(), conflicting,
                        Diagnostic.fatal(DiagnosticCode.MERGE_CONFLICT,
                                "The stash did not apply cleanly, so it was kept as stash@{0} and the working tree"
                                        + " holds conflict markers. Resolve the conflicting files, then drop it."));
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to pop stash: " + e.getMessage(), e);
        }
        finally
        {
            lock.unlock();
        }

        refreshMappedProjects(repository);
        return response;
    }

    /**
     * The stash entries, most recent first.
     * <p>
     * An empty stash is a result: the response says so with a count of zero, so a caller
     * never has to tell an empty stash from a failed call by reading a sentence.
     */
    public GitStashListResponse stashList(String projectName)
    {
        Repository repository = getRepository(projectName);
        try (Git git = new Git(repository))
        {
            return GitStashListResponse.from(projectName, git.stashList().call());
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to list stashes: " + e.getMessage(), e);
        }
    }

    public String getCurrentDiff()
    {
        return uiSync.syncCall(() -> {
            var activeResource = editorService.getActiveEditor()
                    .map(IEditorPart::getEditorInput)
                    .map(editorInput -> editorInput.getAdapter(IResource.class))
                    .orElseThrow(() -> new RuntimeException("No active resource available."));
            var mapping = RepositoryMapping.getMapping(activeResource);
            var repository = mapping.getRepository();
            try (var git = new Git(repository))
            {
                var head = repository.resolve("HEAD");
                if (Objects.isNull(head))
                {
                    return "Initial commit: No previous commits found.";
                }
                else
                {
                    var headTree = prepareTreeParser(repository, head);
                    var indexTree = prepareIndexTreeParser(repository);
                    var stagedChanges = git.diff().setOldTree(headTree).setNewTree(indexTree).call();
                    return formatDiffEntries(git.getRepository(), stagedChanges);
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * The index at a moment in time: the blob each path points at, and what each path is
     * staged as relative to HEAD.
     * <p>
     * Both halves are needed to say what an operation actually staged. The blob answers
     * "did this entry change at all" - restaging an edited file leaves the kind of change
     * alone, so comparing kinds would miss it - and the kind answers "as what", which is
     * what a caller reads.
     */
    private record IndexSnapshot(Map<String, ObjectId> blobs, Map<String, ChangeType> stagedTypes)
    {
    }

    private IndexSnapshot indexSnapshot(Repository repository) throws IOException
    {
        Map<String, ObjectId> blobs = new LinkedHashMap<>();
        DirCache dirCache = repository.readDirCache();
        for (int i = 0; i < dirCache.getEntryCount(); i++)
        {
            DirCacheEntry entry = dirCache.getEntry(i);
            blobs.put(entry.getPathString(), entry.getObjectId());
        }

        Map<String, ChangeType> stagedTypes = new LinkedHashMap<>();
        try (var out = new ByteArrayOutputStream();
             var formatter = new DiffFormatter(out))
        {
            formatter.setRepository(repository);
            formatter.setDetectRenames(false);

            ObjectId head = repository.resolve(Constants.HEAD);
            AbstractTreeIterator headTree = head == null
                    ? new EmptyTreeIterator()
                    : prepareTreeParser(repository, head);

            for (DiffEntry diff : formatter.scan(headTree, prepareIndexTreeParser(repository)))
            {
                switch (diff.getChangeType())
                {
                    case ADD, COPY -> stagedTypes.put(diff.getNewPath(), ChangeType.ADDED);
                    case DELETE -> stagedTypes.put(diff.getOldPath(), ChangeType.DELETED);
                    default -> stagedTypes.put(diff.getNewPath(), ChangeType.MODIFIED);
                }
            }
        }
        return new IndexSnapshot(blobs, stagedTypes);
    }

    /**
     * The index entries an operation created, changed or dropped.
     * <p>
     * This is what {@code gitAdd} and {@code gitReset} report instead of echoing the
     * caller's own pathspec: a pattern that matches nothing produces an empty list rather
     * than a confirmation.
     */
    private static List<GitFileChange> indexDelta(IndexSnapshot before, IndexSnapshot after,
            Map<String, String> projectsByPrefix)
    {
        Set<String> paths = new TreeSet<>();
        paths.addAll(before.blobs().keySet());
        paths.addAll(after.blobs().keySet());

        List<GitFileChange> changed = new ArrayList<>();
        for (String path : paths)
        {
            if (Objects.equals(before.blobs().get(path), after.blobs().get(path)))
            {
                continue;
            }
            ChangeType changeType = after.stagedTypes().get(path);
            if (changeType == null)
            {
                // The entry no longer differs from HEAD - it was unstaged - so what it
                // had been staged as is the useful thing to report.
                changeType = before.stagedTypes().getOrDefault(path, ChangeType.MODIFIED);
            }
            changed.add(GitStatusResponse.locate(path, changeType, projectsByPrefix));
        }
        return changed;
    }

    private static AbstractTreeIterator prepareTreeParser(Repository repository, ObjectId objectId) throws IOException
    {
        try (RevWalk walk = new RevWalk(repository))
        {
            var commit = walk.parseCommit(objectId);
            var treeId = commit.getTree().getId();
            try (var reader = repository.newObjectReader())
            {
                return new CanonicalTreeParser(null, reader, treeId);
            }
        }
    }

    private static AbstractTreeIterator prepareIndexTreeParser(Repository repository) throws IOException
    {
        try (var inserter = repository.newObjectInserter();
             var reader = repository.newObjectReader())
        {
            var treeId = repository.readDirCache().writeTree(inserter);
            return new CanonicalTreeParser(null, reader, treeId);
        }
    }

    private String formatDiffEntries(Repository repository, List<DiffEntry> diffs) throws IOException
    {
        return formatDiffEntries(repository, diffs, RawTextComparator.DEFAULT);
    }

    private String formatDiffEntries(Repository repository, List<DiffEntry> diffs, RawTextComparator comparator) throws IOException
    {
        try (var out = new ByteArrayOutputStream();
             var formatter = new DiffFormatter(out))
        {
            formatter.setRepository(repository);
            formatter.setDiffComparator(comparator);
            formatter.setDetectRenames(true);
            for (DiffEntry diff : diffs)
            {
                formatter.format(diff);
            }
            return out.toString("UTF-8");
        }
    }
}
