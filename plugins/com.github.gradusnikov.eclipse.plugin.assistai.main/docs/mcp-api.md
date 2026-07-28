# MCP API reference

The tools this plugin exposes over MCP, and the shape of what each returns.

**This file is generated.** It is produced from the `@McpServer`, `@Tool` and
`@ToolParam` annotations by `McpApiDoc`, over the server list in
`McpServerBuiltins`, and `McpApiDocPDETest` fails when it is out of date. Do not
edit it by hand - change the annotations and regenerate:

```
tools/generate-mcp-api.sh
```

Every tool argument is passed as a string, whatever the parameter means; a
required parameter is marked `*`. Tools marked *long* run asynchronously and
return an operation id to poll with `getOperationStatus`.

## Servers

| Server | Tools |
|---|---|
| [duck-duck-search](#duck-duck-search) | 1 |
| [eclipse-coder](#eclipse-coder) | 21 |
| [eclipse-context](#eclipse-context) | 7 |
| [eclipse-git](#eclipse-git) | 15 |
| [eclipse-ide](#eclipse-ide) | 35 |
| [eclipse-pde](#eclipse-pde) | 6 |
| [eclipse-runner](#eclipse-runner) | 17 |
| [memory](#memory) | 2 |
| [time](#time) | 2 |
| [webpage-reader](#webpage-reader) | 1 |

## duck-duck-search

### `webSearch` *(long)*

Searches the web with DuckDuckGo. Returns totalResults and, for each hit, its title, absolute url and snippet, ranked as the engine ranked them. totalResults of 0 means the search matched nothing. The url of a hit is what webpage-reader takes to fetch the page.

| Parameter | | Description |
|---|---|---|
| `query` | \* | A search query |

**Returns** [`WebSearchResponse`](#shape-WebSearchResponse)

## eclipse-coder

### `applyPatch`

Atomically applies a unified diff with one or more hunks to a workspace file. Validates all hunk context before writing, preserves the file's line delimiter, and writes once so the whole patch is one Local History entry and one undo point. File headers are optional. A hunk whose context is not in the file rejects the whole patch with TEXT_NOT_FOUND and writes nothing; a malformed patch is rejected with INVALID_RANGE. Pass expectedModificationStamp from a previous read to reject the patch if the file changed since.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the file |
| `filePath` | \* | The path to the file relative to the project root. Do not include project name! |
| `patch` | \* | The unified diff content to apply. Should contain @@ hunk headers and lines prefixed with ' ' (context), '-' (remove), or '+' (add). File headers (--- and +++) are optional. |
| `showDialog` |  | If 'true', shows Eclipse's Apply Patch wizard dialog for user review instead of applying directly, and the result is a PREVIEW. Default is 'false'. |
| `expectedModificationStamp` |  | The modificationStamp reported by an earlier read or edit of this file. When supplied, the patch is rejected with VERSION_CONFLICT if the file has changed since. |
| `preview` |  | If 'true', report what the patch would change without modifying the file. Default: false |

**Returns** [`EditResult`](#shape-EditResult)

### `applyTextEdits`

Applies several replacements to one file as a single transaction: either all of them apply or none do. Overlapping ranges are rejected. The file is written once, so the whole batch is one Local History entry and one undo point. Prefer this over repeated replaceString calls when changing several places in the same file.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the file |
| `filePath` | \* | The path to the file relative to the project root. Do not include project name! |
| `edits` | \* | A JSON array of edits, each {"startLine":n,"startColumn":n,"endLine":n,"endColumn":n,"replacement":"...","expectedText":"..."}. Lines and columns are 1-based; endColumn is exclusive. expectedText is optional and, when given, must match the current text of the range or the whole batch is rejected. Ranges refer to the file as it is now, not as it becomes after earlier edits in the list - the platform shifts them for you. |
| `expectedModificationStamp` |  | The modificationStamp reported by an earlier read or edit of this file. When supplied, the batch is rejected with VERSION_CONFLICT if the file has changed since. |
| `preview` |  | If 'true', report the resulting diff without modifying the file. Default: false |

**Returns** [`EditResult`](#shape-EditResult)

### `createDirectories`

Creates a directory structure (recursively) in the specified project. Idempotent: a directory that already exists is reported with versionBefore equal to versionAfter.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project where directories should be created |
| `directoryPath` | \* | The path of directories to create, relative to the project root. Do not include project name! |

**Returns** [`EditResult`](#shape-EditResult)

### `createFile`

Create and open a new file in a specified project, creating any missing parent folders. Fails if the file already exists - use replaceFileContent to overwrite one.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project where the file should be created |
| `filePath` | \* | The path to the file relative to the project root. Do not include project name! |
| `content` | \* | The content to write to the file |

**Returns** [`EditResult`](#shape-EditResult)

### `deleteFile`

Deletes a file from the specified project. The content stays recoverable from Eclipse's Local History, and undoHistoryTimestamp in the result identifies the state holding it.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the file |
| `filePath` | \* | The path to the file relative to the project root. Do not include project name! |

**Returns** [`EditResult`](#shape-EditResult)

### `deleteLinesInFile`

Deletes a range of lines in a file, using 1-based line indexing. A range the file cannot satisfy is rejected with INVALID_RANGE rather than clamped, so lines outside the range you named are never touched. Pass expectedModificationStamp from a previous read to reject the edit if the file changed since.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the file |
| `filePath` | \* | The path to the file relative to the project root. Do not include project name! |
| `startLine` | \* | The line number to start deletion from (1-based index) |
| `endLine` | \* | The line number to end deletion at (inclusive, 1-based index) |
| `expectedModificationStamp` |  | The modificationStamp reported by an earlier read or edit of this file. When supplied, the edit is rejected with VERSION_CONFLICT if the file has changed since. |
| `preview` |  | If 'true', report what would change without modifying the file. Default: false |

**Returns** [`EditResult`](#shape-EditResult)

### `formatFile`

Formats an entire file using its registered Eclipse editor's formatter (equivalent to Ctrl/Cmd+Shift+F). Java files use JDT directly; formats such as XML, JSON, HTML, and SQL use the formatter contributed by the installed editor. The unifiedDiff shows exactly what the formatter touched.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the file |
| `filePath` | \* | The path to the file relative to the project root. Do not include project name! |

**Returns** [`EditResult`](#shape-EditResult)

### `getLineDelimiterPreference`

Reports the line delimiter Eclipse is configured to write in a project - the same value the editor uses, resolved from the project setting, then the workspace setting, then the platform default. source says which of the three supplied it, so a deliberate project-specific choice is distinguishable from an inherited one. name is LF, CRLF or CR, which is easier to branch on than the escaped delimiter string.

| Parameter | | Description |
|---|---|---|
| `projectName` |  | The name of the project. Omit to ask the workspace rather than a project. |

**Returns** [`LineDelimiterPreference`](#shape-LineDelimiterPreference)

### `insertIntoFile`

Insert content into a file at a specified line position, using 1-based line indexing. The new content will be inserted BEFORE the specified line, and existing content at that line and below will be shifted down. A line beyond the end of the file is rejected with INVALID_RANGE rather than clamped. Pass expectedModificationStamp from a previous read to reject the edit if the file changed since.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the file |
| `filePath` | \* | The path to the file relative to the project root. Do not include project name! |
| `content` | \* | The content to insert into the file |
| `line` |  | The line number before which to insert the text (1-based index). Existing content at this line and below will be shifted down. Use line=1 to insert at the beginning of the file, or one past the last line to append. Default: 1 |
| `expectedModificationStamp` |  | The modificationStamp reported by an earlier read or edit of this file. When supplied, the edit is rejected with VERSION_CONFLICT if the file has changed since. |
| `preview` |  | If 'true', report what would change without modifying the file. Default: false |

**Returns** [`EditResult`](#shape-EditResult)

### `moveResource`

Moves a file or folder to a different location within the project. The result names the destination, and affectedResources lists the source as DELETED beside the destination as MOVED. For Java files, prefer using refactorMoveJavaType instead to ensure all references are updated.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the resource |
| `sourcePath` | \* | The path to the file or folder relative to the project root |
| `targetPath` | \* | The target directory path relative to the project root where the resource should be moved to |

**Returns** [`EditResult`](#shape-EditResult)

### `normalizeLineDelimiters`

Rewrites a file so every line ends with the delimiter Eclipse is configured to use, leaving the text itself unchanged. Use this on a file with mixed line endings: applyPatch rejoins every line with a single delimiter, so patching such a file rewrites the whole file and buries a small change in a whole-file diff. A file that already matches the preference is left untouched and reported with an empty diff. Pass expectedModificationStamp from a previous read to reject the edit if the file changed since.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the file |
| `filePath` | \* | The path to the file relative to the project root. Do not include project name! |
| `expectedModificationStamp` |  | The modificationStamp reported by an earlier read or edit of this file. When supplied, the edit is rejected with VERSION_CONFLICT if the file has changed since. |
| `preview` |  | If 'true', report what would change without modifying the file. Default: false |

**Returns** [`EditResult`](#shape-EditResult)

### `organizeImports`

Cleans up existing imports in a Java file using Eclipse's organize imports mechanism: removes unused imports and sorts the remaining imports according to project settings. This tool does NOT add imports for unresolved types. To add a missing import, use eclipse-ide getImportSuggestions and then edit the file explicitly. The unifiedDiff shows what changed, and an empty edits list means nothing needed changing.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the Java file |
| `filePath` | \* | The path to the Java file relative to the project root (e.g., 'src/com/example/MyClass.java') |

**Returns** [`EditResult`](#shape-EditResult)

### `organizeImportsInPackage` *(long)*

Cleans up existing imports in all Java files within a package by removing unused imports and sorting the remaining imports. This tool does NOT add imports for unresolved types. The result names the package folder, and affectedResources lists only the files that actually changed, with the version each one now has. A file that could not be organized is one diagnostic naming it, and a package in which every file failed is REJECTED rather than reported as a success.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the package |
| `packageName` | \* | The fully qualified package name (e.g., 'com.example.mypackage') |

**Returns** [`EditResult`](#shape-EditResult)

### `refactorExtractTypeToNewFile` *(long)*

Extracts a nested Java class, interface, enum, or record into a new top-level Java file using Eclipse's Move Type to New File refactoring. The type name must be relative to the source compilation unit, for example 'Outer.Inner'. Eclipse validates the change and updates all required references. The result names the new file, and affectedResources lists it as CREATED beside the source file and every other file whose references changed, with the version each one now has. A failed precondition is reported as REFACTORING_PRECONDITION_FAILED.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the Java file |
| `filePath` | \* | The path to the Java file relative to the project root (e.g., 'src/com/example/Outer.java') |
| `nestedTypeName` | \* | The nested type to extract, relative to the compilation unit (e.g., 'Outer.Inner') |

**Returns** [`EditResult`](#shape-EditResult)

### `refactorMoveJavaType` *(long)*

Moves a Java class/interface/enum to a different package using Eclipse's refactoring mechanism. This updates the package declaration and ALL references throughout the workspace. The target package will be created if it doesn't exist. The result names the moved file at its new location, and affectedResources lists every file the refactoring rewrote - in any project - with the version each one now has. A failed precondition is reported as REFACTORING_PRECONDITION_FAILED.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the Java file |
| `filePath` | \* | The path to the Java file relative to the project root (e.g., 'src/com/example/MyClass.java') |
| `targetPackage` | \* | The fully qualified target package name (e.g., 'com.example.newpackage') |

**Returns** [`EditResult`](#shape-EditResult)

### `refactorRenameJavaType` *(long)*

Renames a Java class/interface/enum using Eclipse's refactoring mechanism. This updates the type name, file name, and ALL references throughout the workspace. Use this instead of renameFile for Java files to ensure all references are updated correctly. The result names the renamed file, and affectedResources lists every file the refactoring rewrote - in any project - with the version each one now has, so there is no need to guess which files to re-read. A failed precondition is reported as REFACTORING_PRECONDITION_FAILED.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the Java file |
| `filePath` | \* | The path to the Java file relative to the project root (e.g., 'src/com/example/MyClass.java') |
| `newTypeName` | \* | The new name for the Java type (without .java extension, e.g., 'NewClassName') |

**Returns** [`EditResult`](#shape-EditResult)

### `refactorRenamePackage` *(long)*

Renames a Java package using Eclipse's refactoring mechanism. This renames the package directory, updates all package declarations in contained files, and updates ALL references throughout the workspace. The result names the renamed package folder, and affectedResources lists every file the refactoring rewrote - in any project - with the version each one now has. A failed precondition is reported as REFACTORING_PRECONDITION_FAILED.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the package |
| `packageName` | \* | The current fully qualified package name (e.g., 'com.example.oldpackage') |
| `newPackageName` | \* | The new package name - can be fully qualified (e.g., 'com.example.newpackage') or just the last segment to rename |

**Returns** [`EditResult`](#shape-EditResult)

### `renameFile`

Renames a file in the specified project. The result names the renamed file as projectName + filePath, and affectedResources lists the old path as DELETED beside the new one as MOVED. For Java types use refactorRenameJavaType instead: this does not update references.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the file |
| `filePath` | \* | The path to the file relative to the project root. Do not include project name! |
| `newFileName` | \* | The new name for the file |

**Returns** [`EditResult`](#shape-EditResult)

### `replaceFileContent`

Replaces the entire content of a file with new content. Pass expectedModificationStamp from a previous read to reject the edit if the file changed since.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the file |
| `filePath` | \* | The path to the file relative to the project root. Do not include project name! |
| `content` | \* | The new content to write to the file |
| `expectedModificationStamp` |  | The modificationStamp reported by an earlier read or edit of this file. When supplied, the edit is rejected with VERSION_CONFLICT if the file has changed since. |
| `preview` |  | If 'true', report what would change without modifying the file. Default: false |

**Returns** [`EditResult`](#shape-EditResult)

### `replaceString`

Find and replace a specific string in a file, with optional line range for targeted replacement. Fails with AMBIGUOUS_MATCH and lists the candidate ranges when the text occurs more than once, rather than silently replacing every occurrence - pass occurrence to say which one you mean. Pass expectedModificationStamp from a previous read to reject the edit if the file changed since.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the file |
| `filePath` | \* | The path to the file relative to the project root. Do not include project name! |
| `oldString` | \* | The text to replace (must match exactly, including whitespace and indentation) |
| `newString` | \* | The new text to insert in place of the old text |
| `startLine` |  | Optional line number to start searching from (1-based index) |
| `endLine` |  | Optional line number to end searching at (1-based index) |
| `occurrence` |  | Which match to replace when there is more than one: UNIQUE (default, fails if not exactly one), FIRST, LAST, ALL, or INDEX with occurrenceIndex |
| `occurrenceIndex` |  | The 1-based match to replace when occurrence=INDEX |
| `expectedModificationStamp` |  | The modificationStamp reported by an earlier read or edit of this file. When supplied, the edit is rejected with VERSION_CONFLICT if the file has changed since. |
| `preview` |  | If 'true', report what would change without modifying the file. Default: false |

**Returns** [`EditResult`](#shape-EditResult)

### `undoEdit`

Undoes the last edit to a file by restoring the newest state from Eclipse's Local History, and reports what was rolled back as a diff. Rejected with HISTORY_UNAVAILABLE when the file has no stored history.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the file |
| `filePath` | \* | The path to the file relative to the project root. Do not include project name! |

**Returns** [`EditResult`](#shape-EditResult)

## eclipse-context

### `compareWithHistory`

Shows a unified diff between the current file content and a Local History version, with the line counts and both versions compared. Use getFileHistory to find the historyTimestamp.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project |
| `filePath` | \* | Path to the file relative to the project root |
| `historyTimestamp` | \* | The historyTimestamp to compare against, from getFileHistory |

**Returns** [`DiffResponse`](#shape-DiffResponse)

### `getCacheStats`

Gets resource cache statistics: the number of cached resources and the estimated tokens they occupy, each against the limit at which the cache starts evicting the least recently used entry. Use listCachedResources for what is actually in there.

**Returns** [`CacheStatsResponse`](#shape-CacheStatsResponse)

### `getCachedResource`

Gets the content of a specific cached resource by URI without re-reading from disk. Use listCachedResources first to see available URIs. Returns the cached version - fast, no I/O - and says whether it is still what the workspace holds.

| Parameter | | Description |
|---|---|---|
| `resourceUri` | \* | The URI of the cached resource (e.g. 'workspace:///ProjectName/src/File.java' or 'jdt:///com.example.MyClass') |

**Returns** [`ResourceReadResult`](#shape-ResourceReadResult)

### `getFileHistory`

Lists the Local History versions of a file maintained by Eclipse. Shows the historyTimestamp, date and size of each stored version. Eclipse saves file history on every modification through the IDE. Pass a historyTimestamp from this listing to the other history tools.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project |
| `filePath` | \* | Path to the file relative to the project root |
| `maxEntries` |  | Maximum number of history entries to show (default: 20) |

**Returns** [`FileHistoryResponse`](#shape-FileHistoryResponse)

### `getFileHistoryContent`

Gets the content of a specific Local History version of a file. Returns the exact stored content, the range it covers and the version that addresses it again. Use getFileHistory first to see the available historyTimestamp values.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project |
| `filePath` | \* | Path to the file relative to the project root |
| `historyTimestamp` | \* | The historyTimestamp of the version, from getFileHistory. Identifies the same content even after further saves, which a positional index does not. |

**Returns** [`ResourceReadResult`](#shape-ResourceReadResult)

### `listCachedResources`

Lists all resources currently cached in the Eclipse workspace context. Each entry gives the URI getCachedResource takes, the resource type, the project and project-relative filePath when it is a workspace file, when it was cached, its modificationStamp and an estimated token count. Use this to see what files, classes, and data the user has been working with.

**Returns** [`CachedResourcesResponse`](#shape-CachedResourcesResponse)

### `restoreFileVersion`

Restores a file to a specific Local History version. The current content becomes a new history entry first, so the restore is itself undoable: the returned undoHistoryTimestamp addresses it. Use getFileHistory to find the historyTimestamp.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project |
| `filePath` | \* | Path to the file relative to the project root |
| `historyTimestamp` | \* | The historyTimestamp of the version to restore, from getFileHistory |

**Returns** [`EditResult`](#shape-EditResult)

## eclipse-git

### `gitAdd`

Stages files for the next commit. Use '.' to stage all changes (new, modified, and deleted files). Reports the files whose index entry actually changed, each naming its Eclipse projectName and project-relative filePath as well as the repository-relative repoPath. A pattern that matches no changed file is totalFiles=0 with an empty list - Git does not fail on it, so check the count rather than assuming the pattern matched.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The Eclipse project name |
| `filePattern` | \* | File pattern to add (e.g., '.' for all, 'src/com/example/MyClass.java' for a specific file) |

**Returns** [`GitStageResponse`](#shape-GitStageResponse)

### `gitBranch`

Lists the branches of the repository. Local branches are in 'branches', each with a 'current' flag for the checked-out one, and remote-tracking branches are in 'remoteBranches'. Branch 'name' is what gitCheckout, gitCreateBranch and gitDeleteBranch take.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The Eclipse project name |
| `includeRemote` |  | If 'true', includes remote-tracking branches. Default: false |

**Returns** [`GitBranchResponse`](#shape-GitBranchResponse)

### `gitCheckout`

Checks out a branch, switching the working tree to that branch. status is SWITCHED or BLOCKED: when local changes would be overwritten nothing is switched, blockingFiles names them (projectName, filePath, repoPath) and a CHECKOUT_CONFLICT diagnostic is attached. A checkout rewrites the whole repository, so refreshedProjects lists every Eclipse project that was refreshed, not only the one that was named.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The Eclipse project name |
| `branchName` | \* | The branch name to checkout |

**Returns** [`GitCheckoutResponse`](#shape-GitCheckoutResponse)

### `gitCommit`

Commits the currently staged changes with the given message. Returns the new commit as sha, shortSha, author, authorEmail, authorTimeMillis, message and shortMessage - the same shape gitLog reports - so the sha is a field rather than a prefix of a sentence.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The Eclipse project name |
| `message` | \* | The commit message |

**Returns** [`GitCommitResponse`](#shape-GitCommitResponse)

### `gitCreateBranch`

Creates a new branch. Does not switch to it - use gitCheckout to switch.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The Eclipse project name |
| `branchName` | \* | Name of the new branch to create |
| `startPoint` |  | Optional start point (branch name, tag, or commit SHA). Defaults to HEAD. |

**Returns** `String`

### `gitDeleteBranch`

Deletes a branch. Cannot delete the currently checked-out branch. deleted says whether the branch is gone and deletedRefs lists the refs that were removed. A branch that is not fully merged is refused with deleted=false and a BRANCH_NOT_MERGED diagnostic; retry with force='true' to delete it anyway.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The Eclipse project name |
| `branchName` | \* | Name of the branch to delete |
| `force` |  | If 'true', force-deletes even if the branch is not fully merged. Default: false |

**Returns** [`GitDeleteBranchResponse`](#shape-GitDeleteBranchResponse)

### `gitDiff`

Shows a unified diff for staged or unstaged changes, optionally limited to comma-separated project-relative files/directories and with whitespace changes ignored. The hunks are in unifiedDiff, which names paths from the repository root; the files list additionally resolves each of them to an Eclipse projectName and project-relative filePath that the reading and editing tools accept, with per-file addedLines/removedLines. identical=true means the two sides are the same, and baseRevision is null in a repository with no commits.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The Eclipse project name |
| `staged` |  | If 'true', shows staged (cached) changes instead of unstaged. Default: false |
| `pathFilter` |  | Optional comma-separated file or directory paths relative to the Eclipse project |
| `ignoreWhitespace` |  | If 'true', ignores whitespace when formatting hunks. Default: false |

**Returns** [`GitDiffResponse`](#shape-GitDiffResponse)

### `gitLog`

Lists the most recent commits of the Git repository associated with the project. Each commit reports sha, shortSha, author, authorEmail, authorTimeMillis (epoch milliseconds), the full message and its first line. The truncated flag says whether the history goes further back than maxCount.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The Eclipse project name |
| `maxCount` |  | Maximum number of commits to show (default: 20) |

**Returns** [`GitLogResponse`](#shape-GitLogResponse)

### `gitReadFile`

Reads a UTF-8 text file from a Git revision without changing the working tree. The path is relative to the Eclipse project. Use revision 'INDEX' to read the staged version; otherwise revision defaults to HEAD and may be a branch, tag, or commit.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The Eclipse project name |
| `filePath` | \* | File path relative to the Eclipse project |
| `revision` |  | Git branch, tag, commit, or 'INDEX'. Default: HEAD |

**Returns** `String`

### `gitReset`

Unstages files from the index (equivalent to 'git reset HEAD <file>'). Does not modify the working tree. Reports the index entries that actually left the staged set, each naming its Eclipse projectName and project-relative filePath plus the repository-relative repoPath, with changeType being what the file had been staged as. A pattern matching nothing is totalFiles=0.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The Eclipse project name |
| `filePattern` | \* | File pattern to unstage (e.g., '.' for all, or a specific file path) |

**Returns** [`GitStageResponse`](#shape-GitStageResponse)

### `gitStagePatch`

Stages specific changes from a unified diff patch into the index without modifying the working tree. Use this to stage partial file changes for selective commits. The patch must be in standard unified diff format with file headers (--- a/path and +++ b/path) and @@ hunk headers. IMPORTANT: patch paths are relative to the REPOSITORY root, not to the Eclipse project - unlike gitDiff, gitReadFile and the editing tools, which take project-relative paths. The two differ whenever the project does not sit at the repository root; gitStatus reports both forms as filePath and repoPath, and the unifiedDiff of gitDiff already uses the repository form. status is STAGED or FAILED, files lists what actually reached the index, and workingTreePreserved says whether the uncommitted content of every touched file was put back.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The Eclipse project name |
| `patch` | \* | A unified diff patch string to stage. Must include file headers (--- a/path, +++ b/path) and @@ hunk headers. |

**Returns** [`GitStagePatchResponse`](#shape-GitStagePatchResponse)

### `gitStash`

Stashes the current working directory and index changes, reverting the working tree to HEAD. stashed=false with a null stash means the working tree was already clean - an outcome, not a failure. When something was stashed, stash carries its index, its stash@{n} ref, the commit sha it is stored as and its message, the same shape gitStashList reports.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The Eclipse project name |
| `message` |  | Optional message to describe the stash |

**Returns** [`GitStashResponse`](#shape-GitStashResponse)

### `gitStashList`

Lists the stash entries, most recent first. Each entry reports its index, its stash@{n} ref, the commit sha it is stored as, and its message. An empty stash is totalStashes=0 with an empty list.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The Eclipse project name |

**Returns** [`GitStashListResponse`](#shape-GitStashListResponse)

### `gitStashPop`

Applies the most recent stash entry and, if that succeeded, removes it. status is APPLIED, CONFLICTED or NOTHING_TO_APPLY. On CONFLICTED the stash was kept (dropped=false), the working tree holds conflict markers, conflicting names the affected files and a MERGE_CONFLICT diagnostic is attached - do not treat it as done.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The Eclipse project name |

**Returns** [`GitStashPopResponse`](#shape-GitStashPopResponse)

### `gitStatus`

Reports the working tree status of the Git repository associated with the project: separate staged, unstaged, untracked and conflicting lists, the current branch and its distance from its upstream. Every entry names its Eclipse projectName and a project-relative filePath, which the reading and editing tools take, plus the repository-relative repoPath the Git tools take. A clean working tree is reported as clean=true with empty lists.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The Eclipse project name (use listProjects to find it) |

**Returns** [`GitStatusResponse`](#shape-GitStatusResponse)

## eclipse-ide

### `executeQuickFix` *(long)*

Applies one quick fix proposal to a compilation problem. Use getCompilationErrors first for the markerId and the proposal index. status is APPLIED, MARKER_NOT_FOUND (the id is stale - re-run getCompilationErrors), NO_PROPOSALS, INVALID_PROPOSAL_INDEX (pick from availableProposals) or APPLY_FAILED. On APPLIED, markerResolved says whether the problem actually went away.

| Parameter | | Description |
|---|---|---|
| `markerId` | \* | The Marker ID of the problem (from getCompilationErrors or getQuickFixes) |
| `proposalIndex` | \* | The 0-based index of the quick fix proposal to apply (from the quick fixes list) |

**Returns** [`QuickFixResponse`](#shape-QuickFixResponse)

### `explainTypeResolution`

Explains how a Java type resolves on one Eclipse project's classpath: which classpath root and entry supplied it, whether that root is a workspace folder or an external archive, whether source is attached, and where its class file is. sourceOrigin is the same enum getSource and readProjectResource report - WORKSPACE_SOURCE, ATTACHED_SOURCE or DECOMPILED_CLASS - and says what getSource would return. A type backed by a workspace file also reports projectName and a project-relative filePath the reading and editing tools take. status separates a type that is not on the classpath from a project name that does not exist.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The exact open Eclipse Java project name |
| `fullyQualifiedClassName` | \* | The fully qualified Java type name |

**Returns** [`TypeResolutionResponse`](#shape-TypeResolutionResponse)

### `fileSearch` *(long)*

Searches for a plain substring in workspace files using Eclipse's text search engine. Each match reports projectName, filePath and a 1-based lineNumber, which can be passed straight to the reading and editing tools.

| Parameter | | Description |
|---|---|---|
| `containingText` | \* | Text that must be contained in a line (plain substring, not regex) |
| `fileNamePatterns` |  | Optional comma-separated file name patterns (e.g. "*.java,*.xml"). If omitted, all files are searched. |
| `maxResults` |  | Maximum number of matches to return (default: 200). The response reports whether it was truncated. |

**Returns** [`SearchResponse`](#shape-SearchResponse)

### `fileSearchRegExp` *(long)*

Searches workspace files using a Java regular expression via Eclipse's text search engine. Each match reports projectName, filePath and a 1-based lineNumber.

| Parameter | | Description |
|---|---|---|
| `pattern` | \* | Java regular expression |
| `fileNamePatterns` |  | Optional comma-separated file name patterns (e.g. "*.java,*.xml"). If omitted, all files are searched. |
| `maxResults` |  | Maximum number of matches to return (default: 200). The response reports whether it was truncated. |

**Returns** [`SearchResponse`](#shape-SearchResponse)

### `findFiles`

Finds workspace files matching the given glob patterns. Each file reports projectName and a project-relative filePath, which is what the reading and editing tools take.

| Parameter | | Description |
|---|---|---|
| `fileNamePatterns` |  | Comma-separated glob patterns (e.g. "*.java, pom.xml"). If omitted, defaults to '*' |
| `maxResults` |  | Maximum number of results to return (default: 200) |

**Returns** [`FileListResponse`](#shape-FileListResponse)

### `findReferences` *(long)*

Finds all references/usages of a Java type, method, or field across the entire workspace. Essential before renaming or deleting code elements: totalReferences of 0 means nothing uses it. Each reference reports projectName, filePath and a 1-based lineNumber.

| Parameter | | Description |
|---|---|---|
| `fullyQualifiedClassName` | \* | The fully qualified name of the class containing the element |
| `elementName` |  | Optional method or field name to search for. If omitted, searches for references to the class itself. |

**Returns** [`ReferencesResponse`](#shape-ReferencesResponse)

### `findTestClasses`

Finds test classes and separates plain JUnit tests from PDE harness tests, which must follow the *PDETest naming convention. Flags likely PDE runtime usage in incorrectly named tests. Each class carries the project-relative path of its source file.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The exact Eclipse project name to search (use listProjects to find it) |

**Returns** [`TestClassesResponse`](#shape-TestClassesResponse)

### `formatCode`

Formats code according to the current Eclipse formatter settings.

| Parameter | | Description |
|---|---|---|
| `code` | \* | The code to be formatted |
| `projectName` |  | Optional project name to use project-specific formatter settings |

**Returns** `String`

### `getClassOutline`

Returns the outline of a Java class: its declaration plus fields, method signatures (no bodies) and inner types. Every entry carries a 1-based startLine and endLine, so one member can be read with readProjectResource(projectName, filePath, startLine, endLine) instead of fetching the whole file. Much cheaper than getSource; use this first, then getMethodSource or readProjectResource for the member you want. status reports TYPE_NOT_FOUND, NO_SOURCE or ACCESS_DENIED rather than an empty outline.

| Parameter | | Description |
|---|---|---|
| `fullyQualifiedClassName` | \* | A fully qualified class name (e.g. 'com.example.MyClass') |
| `includeFields` |  | Whether to include field declarations (default: true) |

**Returns** [`ClassOutlineResponse`](#shape-ClassOutlineResponse)

### `getCompilationErrors`

Retrieves compilation errors and problems from the current workspace or a specific project. Reports errorCount/warningCount for everything that matched, before any truncation, so 'are there errors?' is answerable even from a shortened listing. Each problem carries its markerId and quick-fix indices for executeQuickFix.

| Parameter | | Description |
|---|---|---|
| `projectName` |  | The name of the specific project to check (optional, leave empty for all projects) |
| `severity` |  | Filter by severity level: 'ERROR', 'WARNING', or 'ALL' (default) |
| `maxResults` |  | Maximum number of problems to return (default: 50) |

**Returns** [`CompilationProblemsResponse`](#shape-CompilationProblemsResponse)

### `getConsoleOutput`

Retrieves the recent output of Eclipse console(s). A console is read from its end, so returnedRange says which lines came back out of totalLines and truncated says whether maxLines left earlier ones out - raise maxLines to reach them, a console has no line-range read. totalConsoles says how many consoles exist, so you can tell the only console from one of several.

| Parameter | | Description |
|---|---|---|
| `consoleName` |  | Name of the specific console to retrieve (optional, leave empty for all or most recent console) |
| `maxLines` |  | Maximum number of lines to retrieve (default: 100) |
| `includeAllConsoles` |  | If 'true', includes output from all available consoles. Default: 'false' |

**Returns** [`ConsoleOutputResponse`](#shape-ConsoleOutputResponse)

### `getCurrentlyOpenedFile`

Gets the file the user currently has open in the Eclipse editor, with its exact content. projectName and filePath are what the reading and editing tools take, and version.modificationStamp is the token an edit passes as expectedModificationStamp. status is FAILED when no workspace file is open - a state of the workbench, not an error.

**Returns** [`ResourceReadResult`](#shape-ResourceReadResult)

### `getEditorSelection`

Gets the text the user has selected in the active editor, as a range read of the open file. returnedRange gives the exact 1-based start and end line and column of the selection, and totalLines the size of the whole file. Nothing selected is an OK result with a zero-width range and empty content; status is FAILED only when no text editor is open.

**Returns** [`ResourceReadResult`](#shape-ResourceReadResult)

### `getEffectivePom` *(long)*

Gets the effective POM for a Maven project.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the Maven project |

**Returns** `String`

### `getFilteredSource`

Returns one class's source with the import block and the bodies of the methods you did not ask for left out. The content is exact - no line-number prefixes and no '// ... collapsed' comments - and every omission is a range in omittedRanges, so a caller that wants one back reads it with readProjectResource(projectName, filePath, startLine, endLine). status is PARTIAL whenever anything was omitted.

| Parameter | | Description |
|---|---|---|
| `fullyQualifiedClassName` | \* | A fully qualified class name (e.g. 'com.example.MyClass') |
| `excludeImports` |  | Whether to collapse the import block (default: true) |
| `methodNames` |  | Comma-separated method names to fully expand. Methods not listed are collapsed to signatures. If omitted, all methods are expanded. |

**Returns** [`ResourceReadResult`](#shape-ResourceReadResult)

### `getImportSuggestions` *(long)*

Finds import candidates for the unresolved types in a Java file. Each candidate is a bare fully qualified name, ready to use. totalUnresolvedTypes of 0 means the file has no unresolved names; totalCandidates of 0 means it has some but the workspace offers nothing for them. status separates PROJECT_NOT_FOUND from PROJECT_CLOSED, which is one openProject call away.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the file |
| `filePath` | \* | The path to the Java file relative to the project root |

**Returns** [`ImportSuggestionsResponse`](#shape-ImportSuggestionsResponse)

### `getJavaDoc`

Gets the JavaDoc of a Java type as Markdown, with each of its members' declarations. A member type of class A in package x.y is named x.y.A.B, and a type name must match its compilation unit name to be found. status separates the three cases that used to share one sentence: OK, NO_JAVADOC (the type exists and is undocumented - read the source instead) and TYPE_NOT_FOUND (no open project resolves the name - fix it). projectName says which project answered.

| Parameter | | Description |
|---|---|---|
| `fullyQualifiedName` | \* | A fully qualified name of the compilation unit |

**Returns** [`JavaDocResponse`](#shape-JavaDocResponse)

### `getMarkdownOutline`

Returns the heading structure (table of contents) of a Markdown file. Each heading carries its level, its 1-based index - which is what getMarkdownSection takes, and unambiguous where two sections share a title - and the line range of the section it opens. A file with no headings comes back as an empty list, not as a failure. Use this to understand a large Markdown document before fetching sections with getMarkdownSection.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the Markdown file |
| `resourcePath` | \* | The path to the Markdown file relative to the project root (e.g., 'docs/README.md') |

**Returns** [`MarkdownOutlineResponse`](#shape-MarkdownOutlineResponse)

### `getMarkdownSection`

Reads one section of a Markdown file, addressed by heading text or by its 1-based index in the outline. Returns the exact section text with no line-number prefixes: returnedRange says which lines of the file it is, out of totalLines, and version.modificationStamp is the token an edit passes as expectedModificationStamp. Use getMarkdownOutline first to see available headings.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the Markdown file |
| `resourcePath` | \* | The path to the Markdown file relative to the project root |
| `heading` | \* | The heading to find â either a 1-based index from the outline, or a text substring to match (case-insensitive) |
| `includeSubsections` |  | If 'true', includes all subsections under the matched heading. If 'false', returns only the content up to the next heading of any level. Default: true |

**Returns** [`ResourceReadResult`](#shape-ResourceReadResult)

### `getMethodCallHierarchy` *(long)*

Finds the callers of a method, and what that method calls, to understand how it is used. Each node reports projectName, filePath and a 1-based lineNumber - the same location triple findReferences returns - so a caller can be opened without a follow-up search. depth is a field: 1 is a direct caller, 2 a caller of one of those. status distinguishes an unknown type from an unknown method.

| Parameter | | Description |
|---|---|---|
| `fullyQualifiedClassName` | \* | The fully qualified name of the class containing the method |
| `methodName` | \* | The name of the method to analyze |
| `methodSignature` |  | The signature of the method (optional, required if method is overloaded) |
| `maxDepth` |  | Maximum depth of the call hierarchy to retrieve (default: 3) |

**Returns** [`CallHierarchyResponse`](#shape-CallHierarchyResponse)

### `getMethodSource`

Returns the source of specific method(s) of one class. Accepts comma-separated method names to retrieve several in one call. Each method comes back as exact source with its own 1-based range, so its lines can be passed straight to the editing tools; a requested name that matches nothing is listed in notFound rather than mentioned in a comment. version.modificationStamp is the token an edit passes as expectedModificationStamp. Use after getClassOutline to read only the methods you need.

| Parameter | | Description |
|---|---|---|
| `fullyQualifiedClassName` | \* | A fully qualified class name (e.g. 'com.example.MyClass') |
| `methodNames` | \* | Comma-separated method names to retrieve (e.g. 'findById,save,delete') |
| `methodSignature` |  | Optional parameter type hint to disambiguate overloaded methods (e.g. 'String') |
| `includeJavadoc` |  | Whether to include Javadoc comments (default: true) |

**Returns** [`MethodSourceResponse`](#shape-MethodSourceResponse)

### `getProjectDependencies` *(long)*

Lists the dependencies one project's pom declares. These come from the Maven project model - what the pom declares after inheritance from its parent - and not from the resolved transitive graph; for the fully resolved form use getEffectivePom. version is null when the pom does not state one here, which is the ordinary case for a dependency managed by a parent's dependencyManagement. scope is 'compile' when the pom omits it, the default Maven itself applies.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the Maven project |

**Returns** [`MavenDependenciesResponse`](#shape-MavenDependenciesResponse)

### `getProjectLayout`

Gets the file and folder tree of a project as nested nodes. Every node carries the project-relative filePath the reading and editing tools take, and a folder reports childCount even when the walk stopped at it - so 'is there more under here?' is answerable. truncated says whether maxDepth cut the listing short, and excludedCount how many entries .aiignore kept out. For large projects use scopePath to limit to a subdirectory and/or maxDepth to limit tree depth.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project to analyze |
| `scopePath` |  | Optional path relative to the project root to limit the listing (e.g., 'src/main/java/com/example'). If omitted, shows the entire project. |
| `maxDepth` |  | Optional maximum depth of the directory tree to display (e.g., '3' for 3 levels deep). If omitted, shows all levels. |

**Returns** [`ProjectLayoutResponse`](#shape-ProjectLayoutResponse)

### `getProjectProperties`

Gets how a project is configured: its nature ids, the build descriptors in its root, and for a Java project its compiler compliance level, output location and source folders. sourceFolders is the answer to 'where may a new class go?' and, like outputLocation, is project-relative - the form the reading and editing tools take. status separates a name that does not exist (fix the name; listProjects has the real ones) from a project that is closed (call openProject on its directory).

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project to analyze |

**Returns** [`ProjectPropertiesResponse`](#shape-ProjectPropertiesResponse)

### `getSource`

Get source for a workspace or referenced-library class. Prefers original/attached source and decompiles binary classes when source is unavailable. origin says which of the three it is: only WORKSPACE_SOURCE can be edited, and version.modificationStamp is the token an edit passes as expectedModificationStamp.

| Parameter | | Description |
|---|---|---|
| `fullyQualifiedClassName` | \* | A fully qualified class name of the Java class |

**Returns** [`ResourceReadResult`](#shape-ResourceReadResult)

### `getTypeHierarchy` *(long)*

Retrieves the type hierarchy of a Java class or interface as three separate lists: superclasses (nearest first), implemented interfaces and subtypes. A type whose source is in the workspace also reports the projectName and project-relative filePath the reading and editing tools take; one from a JAR or the JRE reports neither. status is TYPE_NOT_FOUND when no open Java project knows the name.

| Parameter | | Description |
|---|---|---|
| `fullyQualifiedClassName` | \* | The fully qualified name of the class (e.g., 'com.example.MyClass') |

**Returns** [`TypeHierarchyResponse`](#shape-TypeHierarchyResponse)

### `listMavenProjects`

Lists the Maven projects m2e knows about in the workspace. Each entry reports both names: the Eclipse projectName every other tool takes, and the groupId/artifactId/version/packaging a Maven command line takes. The two are frequently different strings.

**Returns** [`MavenProjectListResponse`](#shape-MavenProjectListResponse)

### `listProjects`

Lists the workspace projects. Each entry reports the projectName every other tool takes, whether the project is open (a closed one cannot be read, searched or built until openProject runs), its nature ids (org.eclipse.jdt.core.javanature for Java, org.eclipse.m2e.core.maven2Nature for Maven) and its filesystem location.

**Returns** [`ProjectListResponse`](#shape-ProjectListResponse)

### `openProject`

Opens or imports a directory into the Eclipse workspace as a project. If the directory contains a .project file it is imported as-is; if not, a description is created from the directory name. projectName is the name Eclipse assigned - taken from .project or from the directory name, and not necessarily the last segment of directoryPath - and it is the argument every other tool takes next. status says which of three things happened: IMPORTED (the workspace did not have it), OPENED (it had it, closed) or ALREADY_OPEN (nothing changed, which is an answer and not a failure).

| Parameter | | Description |
|---|---|---|
| `directoryPath` | \* | The absolute filesystem path to the directory to open as a project |

**Returns** [`OpenProjectResponse`](#shape-OpenProjectResponse)

### `readImageResource`

Reads a raster image from an Eclipse workspace project and returns it as MCP image content. Supported extensions: png, jpg, jpeg, gif, bmp, tif, tiff and ico. Maximum size: 20 MiB.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the image |
| `resourcePath` | \* | The image path relative to the project root |

**Returns** [`McpSchema.ImageContent`](#shape-McpSchema-ImageContent)

### `readProjectResource`

Read the content of a text resource from a specified project. Returns the exact source text with no fence or line-number prefixes: the line the content starts at is returnedRange.startLine. version.modificationStamp is the token to pass back as expectedModificationStamp when editing, so a write is rejected if the file changed since the read. Supports line ranges and collapsing Java imports, which are reported in omittedRanges.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the resource |
| `resourcePath` | \* | The path to the resource relative to the project root |
| `startLine` |  | Optional 1-based start line to read from. If omitted, reads from the beginning. |
| `endLine` |  | Optional 1-based end line to read to (inclusive). If omitted, reads to the end. |
| `excludeImports` |  | If 'true', omits a Java import block to save tokens. The omitted lines are reported in omittedRanges. Default: 'false' |

**Returns** [`ResourceReadResult`](#shape-ResourceReadResult)

### `runJUnitTests` *(long)*

Starts a JUnit test run asynchronously and returns an operationId for polling. Scope is inferred from parameters: className+methodName=single method, className=single class, packageName=package, none=all tests in project. Use getOperationStatus to poll progress and results. For PDE plug-in tests, use runJUnitPluginTests in the eclipse-pde server instead. Publishes typed intermediate results while running: 'summary' (pass/fail counts) and 'results' (per-test details). getOperationStatus will show these automatically while the run is in progress.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The exact Eclipse project name containing the test classes (use listProjects to find it) |
| `className` |  | The fully qualified class name (e.g. 'com.example.MyServiceTest'). If omitted, runs all tests or package tests. |
| `methodName` |  | The test method name (e.g. 'testCreate'). Requires className. |
| `packageName` |  | The fully qualified package name (e.g. 'com.example.service'). Ignored if className is set. |
| `timeout` |  | Maximum time in seconds to wait for test completion (default: 60) |
| `withCoverage` |  | If 'true', runs tests with code coverage (requires EclEmma/JaCoCo installed). Default: false |
| `launcherName` |  | Optional name of a saved launch configuration to use as the base (use (eclipse-runner MCP server).listLaunchConfigurations with typeFilter='junit' to find it). When set, all settings from that config are reused (VM args, classpath, env vars, etc.) and only the test target is overridden. |

**Returns** [`TestRunResponse`](#shape-TestRunResponse)

### `runMavenBuild` *(long)*

Runs a Maven build with the specified goals on a project.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project to build |
| `goals` | \* | The Maven goals to execute (e.g., "clean install") |
| `profiles` |  | Optional Maven profiles to activate |
| `timeout` |  | Maximum time in seconds to wait for build completion (0 for no timeout) |

**Returns** `String`

### `searchAndReplace` *(long)*

Search and replace across multiple files in the workspace using Eclipse's text search engine. Reports per file how many occurrences were found and how many were replaced; the two differ when a file could not be fully updated.

| Parameter | | Description |
|---|---|---|
| `containingText` | \* | Plain text to find (not regex) |
| `replacementText` | \* | Replacement text (can be empty) |
| `fileNamePatterns` |  | Optional comma-separated file name patterns (e.g. "*.java,*.xml"). If omitted, all files are searched. |

**Returns** [`SearchReplaceResponse`](#shape-SearchReplaceResponse)

### `updateMavenProject` *(long)*

Runs the equivalent of the IDE's 'Maven > Update Project' action: re-reads the pom, re-resolves dependencies and reconfigures the project's classpath. Use this after editing a pom.xml - until it runs, the workspace does not see the change, so a newly added dependency is not on the classpath and code using it still fails to compile.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the Maven project to update (use listMavenProjects to find it) |
| `forceDependencyUpdate` |  | If 'true', re-resolves snapshots and releases even when already cached (the 'Force Update of Snapshots/Releases' checkbox). Default: false |
| `offline` |  | If 'true', resolves only from the local repository without reaching the network. Default: false |

**Returns** `String`

## eclipse-pde

### `getActiveTarget`

Gets the Eclipse target platform the workspace is building against: its name, memento, whether it still exists, whether it is resolved and how many bundles it resolved to. status is RUNNING_PLATFORM when no .target file is set - an ordinary state, not a failure - and bundleCount is null unless the target is resolved.

**Returns** [`ActiveTargetResponse`](#shape-ActiveTargetResponse)

### `reloadTarget` *(long)*

Reloads the currently active Eclipse target platform and describes the result. Useful after target contents change on disk. With no .target file set there is nothing to reload: that is reported as status RUNNING_PLATFORM, not as an error.

**Returns** [`ActiveTargetResponse`](#shape-ActiveTargetResponse)

### `reloadWorkspaceBundle`

Schedules an OSGi update of a bundle backed by an open Eclipse workspace project. The reload starts after the current response completes; MCP clients may need to reconnect when reloading AssistAI itself.

| Parameter | | Description |
|---|---|---|
| `symbolicName` | \* | Bundle symbolic name; it must also name an open workspace project |
| `delayMillis` |  | Delay before reload, from 500 to 30000 ms. Default: 1500 |

**Returns** `String`

### `restartMcpServers`

Safely rebuilds the AssistAI HTTP MCP servers after the current response completes. Existing MCP connections are interrupted and may need to reconnect.

| Parameter | | Description |
|---|---|---|
| `delayMillis` |  | Delay before restart, from 500 to 30000 ms. Default: 1500 |

**Returns** `String`

### `runJUnitPluginTests` *(long)*

Starts a JUnit Plug-in Test run asynchronously using the PDE launcher and returns an operationId for polling. Scope is inferred from parameters: className+methodName=single method, className=single class (or comma-separated for multiple classes in one launch), packageName=package, none=all tests in project. Use getOperationStatus to poll progress and results. Publishes typed intermediate results while running: 'summary' (pass/fail counts) and 'results' (per-test details). getOperationStatus will show these automatically while the run is in progress.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The exact Eclipse project name containing the plug-in test classes |
| `className` |  | Fully qualified class name (e.g. 'com.example.MyPluginTest'), or comma-separated names for running multiple classes in one PDE launch. If omitted, runs all tests or the packageName scope. |
| `packageName` |  | The fully qualified package name (e.g. 'com.example.service'). Ignored if className is set. |
| `timeout` |  | Maximum time in seconds to wait for test completion (default: 60) |
| `withCoverage` |  | If 'true', runs tests with code coverage (requires EclEmma/JaCoCo installed). Default: false |
| `includeAllPlugins` |  | If 'true', launches with all workspace and target platform plug-ins (USE_DEFAULT mode). If 'false' (default), auto-resolves required dependencies. |
| `additionalBundles` |  | Comma-separated additional bundle/plug-in symbolic names to include (only used when includeAllPlugins is false). |
| `launcherName` |  | Optional name of a saved launch configuration to use as the base (use (eclipse-runner MCP server).listLaunchConfigurations with typeFilter='junit-plugin' to find it). When set, all settings from that config are reused (VM args, program args, bundle selection, etc.) and only the test target is overridden. includeAllPlugins and additionalBundles are ignored when set. |

**Returns** [`TestRunResponse`](#shape-TestRunResponse)

### `setActiveTarget` *(long)*

Sets the active Eclipse target platform from a .target file, waits for it to load, and describes the target that is in force afterwards. status FAILED with a diagnostic means the load did not happen and the previous target is still active - check it before launching anything.

| Parameter | | Description |
|---|---|---|
| `targetFilePath` | \* | The workspace-relative or absolute path to the .target file (e.g., '/MyProject/myplatform.target') |

**Returns** [`ActiveTargetResponse`](#shape-ActiveTargetResponse)

## eclipse-runner

### `debugJavaApplication` *(long)*

Launches a Java application in debug mode. The application will stop at breakpoints. Use toggleBreakpoint to set breakpoints before launching. Same result shape as runJavaApplication: status, exitCode (null when still running), timedOut, and separate stdout/stderr with truncation counts.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the main class |
| `mainClass` | \* | The fully qualified name of the main class (e.g., 'com.example.Main') |
| `programArgs` |  | Optional program arguments passed to the main method |
| `vmArgs` |  | Optional JVM arguments (e.g., '-Xmx512m -Dfoo=bar') |
| `timeout` |  | Timeout in seconds to wait for completion. Use '0' to launch in background without waiting. Default: '0' |

**Returns** [`LaunchResponse`](#shape-LaunchResponse)

### `evaluateExpression`

Evaluates a Java expression in a suspended debug frame. The application must be stopped at a breakpoint. value and declaredType are separate fields, so a result whose toString() contains a parenthesis is still readable, and nullResult distinguishes the null reference from a String holding "null". status is OK only when there is a value: COMPILE_ERROR puts the compiler's own messages in errorMessages, EVALUATION_FAILED means the expression threw, and TIMED_OUT / NO_SUSPENDED_THREAD / THREAD_NOT_FOUND / SESSION_NOT_FOUND each say why there is none. threadName and frame name the context the expression was evaluated in.

| Parameter | | Description |
|---|---|---|
| `nameOrClass` | \* | A substring to match against the debug session name or main class |
| `expression` | \* | The Java expression to evaluate (e.g., 'myList.size()', 'x + y', 'this.toString()') |
| `threadName` |  | Optional: the suspended thread whose top frame to evaluate in. Omit to take the first suspended thread |

**Returns** [`EvaluationResponse`](#shape-EvaluationResponse)

### `getStackTrace`

Gets the stack trace of every thread of a debug session, plus the local variables of the top frame. Each frame reports declaringType, methodName, projectName and a project-relative filePath with a 1-based lineNumber, so it can be opened with the reading tools; a frame outside the workspace, such as a JRE or library frame, reports no path. sessionFound says whether any debug session matched and anyThreadSuspended whether the program is stopped at a breakpoint - neither is an error.

| Parameter | | Description |
|---|---|---|
| `nameOrClass` | \* | A substring to match against the debug session name or main class |

**Returns** [`StackTraceResponse`](#shape-StackTraceResponse)

### `hotCodeReplace` *(long)*

Rebuilds the debugged project and reports whether the new bytecode actually reached the running JVM - the observed outcome, not that a build was triggered. status is SUCCEEDED when the VM took it, OBSOLETE_METHODS when it did but frames already on the stack still run the old code, FAILED when the VM refused (a schema change: the running code is unchanged), NOT_SUPPORTED when the VM cannot hot swap at all, IN_SYNC when nothing needed replacing, and TIMED_OUT when the VM is out of sync and reported nothing. projectName is the project that was rebuilt; null means the launch named none and the whole workspace was built.

| Parameter | | Description |
|---|---|---|
| `nameOrClass` | \* | A substring to match against the debug session name or main class |

**Returns** [`HotCodeReplaceResponse`](#shape-HotCodeReplaceResponse)

### `launchConfiguration` *(long)*

Launches an existing saved launch configuration by name, exactly as it would run from Eclipse's Run/Debug Configurations dialog (reusing its classpath, program/VM arguments, environment variables, working directory, and agent settings such as JRebel). Use listLaunchConfigurations to find the name. Unlike runJavaApplication/debugJavaApplication, this does NOT create a throwaway configuration. If timeout > 0, waits for the process to finish; if timeout = 0, launches in background and returns immediately. Reports status, exitCode (null when still running), timedOut, and separate stdout/stderr with truncation counts. For JUnit test launches (plain tests or plug-in tests), use the dedicated runJUnitTests (eclipse-ide) or runJUnitPluginTests (eclipse-pde) tools instead — they provide structured test results, per-test status, and polling support that this generic launcher does not.

| Parameter | | Description |
|---|---|---|
| `configurationName` | \* | The exact name of the launch configuration to launch (e.g., 'Run Snapshot App No Data Compass Local') |
| `mode` |  | Launch mode: 'run' or 'debug'. Default: 'run' |
| `timeout` |  | Timeout in seconds to wait for completion. Use '0' to launch in background without waiting. Default: '0' |

**Returns** [`LaunchResponse`](#shape-LaunchResponse)

### `listActiveLaunches`

Lists the applications Eclipse is currently running or debugging. Each launch reports name, mode (run/debug), mainType, projectName, a terminated flag, and its processes with the operating system pid where the debug plug-in recorded one. Nothing running is an empty launches list with totalLaunches = 0, not a message.

**Returns** [`ActiveLaunchesResponse`](#shape-ActiveLaunchesResponse)

### `listBreakpoints`

Lists all breakpoints currently set in the workspace. Each breakpoint reports projectName and a project-relative filePath, which the reading and editing tools take directly, plus typeName, a 1-based lineNumber, enabled, condition and hitCount. No breakpoints is an empty breakpoints list with totalBreakpoints = 0, not a message.

**Returns** [`BreakpointsResponse`](#shape-BreakpointsResponse)

### `listLaunchConfigurations`

Lists all saved launch configurations in the workspace (name, type, and for Java applications the project and main class). Each entry has: name, typeId, typeName, projectName, mainClass. Use this to discover the exact name to pass to launchConfiguration, (eclipse-ide MCP server).runJUnitTests (launcherName), or (eclipse-pde MCP server).runJUnitPluginTests (launcherName). Use typeFilter to narrow results: 'junit' for plain JUnit runs, 'junit-plugin' for PDE plug-in tests, or any substring of the type ID for other types.

| Parameter | | Description |
|---|---|---|
| `typeFilter` |  | Optional filter: 'junit' (org.eclipse.jdt.junit.launchconfig), 'junit-plugin' (org.eclipse.pde.ui.JunitLaunchConfig), 'all' or omit for everything, or any substring of the type ID. |

**Returns** [`LaunchConfigurationsResponse`](#shape-LaunchConfigurationsResponse)

### `removeAllBreakpoints`

Removes all breakpoints from the workspace.

**Returns** `String`

### `resumeDebug` *(long)*

Resumes a suspended debug session and waits for it to stop at the next breakpoint. Returns the location the program is at afterwards, not a confirmation: frame carries declaringType, methodName, projectName, a project-relative filePath, a 1-based lineNumber and the frame's local variables, so no follow-up getStackTrace is needed. status is SUSPENDED when it stopped again, TERMINATED when the program ended, TIMED_OUT when it had not stopped within the timeout (retryable), RUNNING when timeout was 0 and nothing was waited for, NO_SUSPENDED_THREAD / THREAD_NOT_FOUND when there was nothing to act on, and SESSION_NOT_FOUND when no debug session matched - which a caller must never read as success. threadName names the thread acted on.

| Parameter | | Description |
|---|---|---|
| `nameOrClass` | \* | A substring to match against the debug session name or main class |
| `threadName` |  | Optional: the thread to resume. Omit to resume the whole session |
| `timeout` |  | Seconds to wait for the next suspend. Use '0' to resume without waiting. Default: '10' |

**Returns** [`StepResponse`](#shape-StepResponse)

### `runJavaApplication` *(long)*

Launches a Java application in run mode. Specify the project and fully qualified main class. If timeout > 0, waits for the process to finish; if timeout = 0, launches in background and returns immediately. exitCode is the one fact that says whether the program worked and is a field of its own, null when the process is still running or the VM reported none - never a sentinel. timedOut says the wait ran out rather than the program finishing. stdout and stderr are separate, each with its own truncated flag and pre-truncation totalChars.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the main class |
| `mainClass` | \* | The fully qualified name of the main class (e.g., 'com.example.Main') |
| `programArgs` |  | Optional program arguments passed to the main method |
| `vmArgs` |  | Optional JVM arguments (e.g., '-Xmx512m -Dfoo=bar') |
| `timeout` |  | Timeout in seconds to wait for completion. Use '0' to launch in background without waiting. Default: '30' |

**Returns** [`LaunchResponse`](#shape-LaunchResponse)

### `setConditionalBreakpoint`

Sets a breakpoint that only triggers when a condition evaluates to true, replacing any breakpoint already at that location. The condition comes back in its own field of the reported breakpoint, so a condition containing ':' no longer has to be recovered by splitting a sentence. action is SET or REPLACED. The location is validated first: TYPE_NOT_FOUND or INVALID_LINE means nothing was created.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the source file |
| `typeName` | \* | The fully qualified type name (e.g., 'com.example.Main') |
| `lineNumber` | \* | The 1-based line number where the breakpoint should be set |
| `condition` | \* | A Java boolean expression (e.g., 'i > 100', 'name.equals("test")') |
| `hitCount` |  | Optional: breakpoint triggers only after being hit N times. Default: '0' (disabled) |

**Returns** [`BreakpointResponse`](#shape-BreakpointResponse)

### `stepInto` *(long)*

Steps into the method call at the current line in a suspended debug session. Returns the location the program is at afterwards, not a confirmation: frame carries declaringType, methodName, projectName, a project-relative filePath, a 1-based lineNumber and the frame's local variables, so no follow-up getStackTrace is needed. status is SUSPENDED when it stopped again, TERMINATED when the program ended, TIMED_OUT when it had not stopped within the timeout (retryable), RUNNING when timeout was 0 and nothing was waited for, NO_SUSPENDED_THREAD / THREAD_NOT_FOUND when there was nothing to act on, and SESSION_NOT_FOUND when no debug session matched - which a caller must never read as success. threadName names the thread acted on.

| Parameter | | Description |
|---|---|---|
| `nameOrClass` | \* | A substring to match against the debug session name or main class |
| `threadName` |  | Optional: the thread to step. Omit to take the first suspended thread |
| `timeout` |  | Seconds to wait for the step to complete. Use '0' not to wait. Default: '10' |

**Returns** [`StepResponse`](#shape-StepResponse)

### `stepOver` *(long)*

Steps over the current line in a suspended debug session, executing it without entering method calls. Returns the location the program is at afterwards, not a confirmation: frame carries declaringType, methodName, projectName, a project-relative filePath, a 1-based lineNumber and the frame's local variables, so no follow-up getStackTrace is needed. status is SUSPENDED when it stopped again, TERMINATED when the program ended, TIMED_OUT when it had not stopped within the timeout (retryable), RUNNING when timeout was 0 and nothing was waited for, NO_SUSPENDED_THREAD / THREAD_NOT_FOUND when there was nothing to act on, and SESSION_NOT_FOUND when no debug session matched - which a caller must never read as success. threadName names the thread acted on.

| Parameter | | Description |
|---|---|---|
| `nameOrClass` | \* | A substring to match against the debug session name or main class |
| `threadName` |  | Optional: the thread to step. Omit to take the first suspended thread |
| `timeout` |  | Seconds to wait for the step to complete. Use '0' not to wait. Default: '10' |

**Returns** [`StepResponse`](#shape-StepResponse)

### `stepReturn` *(long)*

Runs until the current method returns to its caller, in a suspended debug session. Returns the location the program is at afterwards, not a confirmation: frame carries declaringType, methodName, projectName, a project-relative filePath, a 1-based lineNumber and the frame's local variables, so no follow-up getStackTrace is needed. status is SUSPENDED when it stopped again, TERMINATED when the program ended, TIMED_OUT when it had not stopped within the timeout (retryable), RUNNING when timeout was 0 and nothing was waited for, NO_SUSPENDED_THREAD / THREAD_NOT_FOUND when there was nothing to act on, and SESSION_NOT_FOUND when no debug session matched - which a caller must never read as success. threadName names the thread acted on.

| Parameter | | Description |
|---|---|---|
| `nameOrClass` | \* | A substring to match against the debug session name or main class |
| `threadName` |  | Optional: the thread to step. Omit to take the first suspended thread |
| `timeout` |  | Seconds to wait for the step to complete. Use '0' not to wait. Default: '10' |

**Returns** [`StepResponse`](#shape-StepResponse)

### `stopApplication`

Stops the running or debugging Java applications matching the launch configuration name or main class name (substring match, case-insensitive). status is NO_MATCH when nothing was running that matched - a state, not a failure - OK when at least one was stopped, FAILED when matches existed and none could be. terminated is a list of launches, so a name containing a comma is still one entry; totalMatched beside it shows a partial stop.

| Parameter | | Description |
|---|---|---|
| `nameOrClass` | \* | A substring to match against the application name or main class (e.g., 'Main' or 'com.example') |

**Returns** [`StopApplicationResponse`](#shape-StopApplicationResponse)

### `toggleBreakpoint`

Sets a line breakpoint at the given location, or removes the one already there. action says which way it went - SET, REMOVED or NONE - so a caller never has to read that out of a sentence. The location is validated first: status is TYPE_NOT_FOUND when the project resolves no such type (a breakpoint there would never bind) and INVALID_LINE when the line is past the end of the file; in both cases nothing is created. The resulting breakpoint is reported in the same shape listBreakpoints returns, with projectName and a project-relative filePath.

| Parameter | | Description |
|---|---|---|
| `projectName` | \* | The name of the project containing the source file |
| `typeName` | \* | The fully qualified type name (e.g., 'com.example.Main') |
| `lineNumber` | \* | The 1-based line number where the breakpoint should be set |

**Returns** [`BreakpointResponse`](#shape-BreakpointResponse)

## memory

### `completion_meta`

Internal sink for code completion. Use this tool to output any non-code text (markdown, explanations, reasoning, meta commentary) instead of writing it into the completion CONTENT stream. The code completion CONTENT stream must contain ONLY the exact source code to insert.

| Parameter | | Description |
|---|---|---|
| `text` | \* | Non-code meta text that should not appear in the completion output |

**Returns** `String`

### `think`

Use this tool to think about something. It will not obtain new information or perform changes, but will put your thought into a log, so that it is accessible to you. Use it for complex reasoning or as memory cache when you need to store some temporary information that you may consider useful to complete the task.

| Parameter | | Description |
|---|---|---|
| `thought` | \* | A thought or information worth using in solving a task |

**Returns** `String`

## time

### `convertTimeZone`

Converts time from one time zone to another. Returns a converted time in the yyyy-MM-dd HH:mm:ss z format.

| Parameter | | Description |
|---|---|---|
| `time` | \* | Date/time in the format yyyy-MM-dd HH:mm:ss |
| `sourceZone` |  | Source time zone id such as, such as Europe/Paris or CST. Default: system time zone |
| `targetZone` |  | Target time zone id, such as Europer/Paris or CST. Default: UTC |

**Returns** `String`

### `currentTime`

Returns the current date and time in the following format: yyyy-MM-dd HH:mm:ss

**Returns** `String`

## webpage-reader

### `readWebPage`

Reads the content of the given web page and returns it as markdown, together with the HTTP status, the URL the request ended at after redirects, the content type and the page title. Check statusCode: an error page converts to plausible-looking prose just as a real one does.

| Parameter | | Description |
|---|---|---|
| `url` | \* | A web site URL |

**Returns** [`WebPageResponse`](#shape-WebPageResponse)

## Result shapes

<a id="shape-WebSearchResponse"></a>
### `WebSearchResponse`

| Field | Type |
|---|---|
| `query` | `String` |
| `totalResults` | `int` |
| `results` | [`WebSearchResponse.Result`](#shape-WebSearchResponse-Result)[] |
| `summaryText` | `String` |

<a id="shape-EditResult"></a>
### `EditResult`

| Field | Type |
|---|---|
| `status` | [`EditResult.EditStatus`](#shape-EditResult-EditStatus) |
| `projectName` | `String` |
| `filePath` | `String` |
| `versionBefore` | [`ResourceVersion`](#shape-ResourceVersion) |
| `versionAfter` | [`ResourceVersion`](#shape-ResourceVersion) |
| `edits` | [`EditResult.AppliedEdit`](#shape-EditResult-AppliedEdit)[] |
| `unifiedDiff` | `String` |
| `affectedResources` | [`EditResult.AffectedResource`](#shape-EditResult-AffectedResource)[] |
| `editorReveal` | [`EditResult.EditorReveal`](#shape-EditResult-EditorReveal) |
| `undoHistoryTimestamp` | `long` |
| `workspaceState` | [`EditResult.WorkspaceSync`](#shape-EditResult-WorkspaceSync) |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-LineDelimiterPreference"></a>
### `LineDelimiterPreference`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `delimiter` | `String` |
| `name` | [`LineDelimiterPreference.DelimiterName`](#shape-LineDelimiterPreference-DelimiterName) |
| `source` | [`LineDelimiterPreference.Source`](#shape-LineDelimiterPreference-Source) |

<a id="shape-DiffResponse"></a>
### `DiffResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `filePath` | `String` |
| `fromLabel` | `String` |
| `toLabel` | `String` |
| `fromVersion` | [`ResourceVersion`](#shape-ResourceVersion) |
| `toVersion` | [`ResourceVersion`](#shape-ResourceVersion) |
| `identical` | `boolean` |
| `addedLines` | `int` |
| `removedLines` | `int` |
| `unifiedDiff` | `String` |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-CacheStatsResponse"></a>
### `CacheStatsResponse`

| Field | Type |
|---|---|
| `resourceCount` | `int` |
| `maxResources` | `int` |
| `totalEstimatedTokens` | `int` |
| `maxTotalTokens` | `int` |
| `summaryText` | `String` |

<a id="shape-ResourceReadResult"></a>
### `ResourceReadResult`

| Field | Type |
|---|---|
| `status` | [`ResourceReadResult.ReadStatus`](#shape-ResourceReadResult-ReadStatus) |
| `uri` | `String` |
| `projectName` | `String` |
| `filePath` | `String` |
| `language` | `String` |
| `version` | [`ResourceVersion`](#shape-ResourceVersion) |
| `returnedRange` | [`ContentRange`](#shape-ContentRange) |
| `totalLines` | `int` |
| `content` | `String` |
| `origin` | [`SourceOrigin`](#shape-SourceOrigin) |
| `readOnly` | `boolean` |
| `truncated` | `boolean` |
| `omittedRanges` | [`ContentRange`](#shape-ContentRange)[] |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-FileHistoryResponse"></a>
### `FileHistoryResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `filePath` | `String` |
| `totalVersions` | `int` |
| `versions` | [`FileHistoryResponse.HistoryEntry`](#shape-FileHistoryResponse-HistoryEntry)[] |
| `truncated` | `boolean` |
| `summaryText` | `String` |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-CachedResourcesResponse"></a>
### `CachedResourcesResponse`

| Field | Type |
|---|---|
| `totalResources` | `int` |
| `totalEstimatedTokens` | `int` |
| `resources` | [`CachedResourcesResponse.CachedEntry`](#shape-CachedResourcesResponse-CachedEntry)[] |
| `summaryText` | `String` |

<a id="shape-GitStageResponse"></a>
### `GitStageResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `operation` | [`GitStageResponse.StageOperation`](#shape-GitStageResponse-StageOperation) |
| `pathspec` | `String` |
| `totalFiles` | `int` |
| `files` | [`GitStatusResponse.GitFileChange`](#shape-GitStatusResponse-GitFileChange)[] |
| `summaryText` | `String` |

<a id="shape-GitBranchResponse"></a>
### `GitBranchResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `currentBranch` | `String` |
| `branches` | [`GitBranchResponse.GitBranch`](#shape-GitBranchResponse-GitBranch)[] |
| `remoteBranches` | [`GitBranchResponse.GitBranch`](#shape-GitBranchResponse-GitBranch)[] |
| `totalBranches` | `int` |
| `summaryText` | `String` |

<a id="shape-GitCheckoutResponse"></a>
### `GitCheckoutResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `status` | [`GitCheckoutResponse.CheckoutStatus`](#shape-GitCheckoutResponse-CheckoutStatus) |
| `requestedBranch` | `String` |
| `previousBranch` | `String` |
| `currentBranch` | `String` |
| `headSha` | `String` |
| `blockingFiles` | [`GitStatusResponse.GitFileChange`](#shape-GitStatusResponse-GitFileChange)[] |
| `refreshedProjects` | `String`[] |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |
| `summaryText` | `String` |

<a id="shape-GitCommitResponse"></a>
### `GitCommitResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `branch` | `String` |
| `commit` | [`GitLogResponse.GitCommit`](#shape-GitLogResponse-GitCommit) |
| `summaryText` | `String` |

<a id="shape-GitDeleteBranchResponse"></a>
### `GitDeleteBranchResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `branchName` | `String` |
| `forced` | `boolean` |
| `deleted` | `boolean` |
| `deletedRefs` | `String`[] |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |
| `summaryText` | `String` |

<a id="shape-GitDiffResponse"></a>
### `GitDiffResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `staged` | `boolean` |
| `fromLabel` | `String` |
| `toLabel` | `String` |
| `baseRevision` | `String` |
| `identical` | `boolean` |
| `totalFiles` | `int` |
| `addedLines` | `int` |
| `removedLines` | `int` |
| `files` | [`GitDiffResponse.GitFileDiff`](#shape-GitDiffResponse-GitFileDiff)[] |
| `unifiedDiff` | `String` |
| `summaryText` | `String` |

<a id="shape-GitLogResponse"></a>
### `GitLogResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `branch` | `String` |
| `commitCount` | `int` |
| `commits` | [`GitLogResponse.GitCommit`](#shape-GitLogResponse-GitCommit)[] |
| `truncated` | `boolean` |
| `summaryText` | `String` |

<a id="shape-GitStagePatchResponse"></a>
### `GitStagePatchResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `status` | [`GitStagePatchResponse.PatchStatus`](#shape-GitStagePatchResponse-PatchStatus) |
| `totalFiles` | `int` |
| `files` | [`GitStatusResponse.GitFileChange`](#shape-GitStatusResponse-GitFileChange)[] |
| `workingTreePreserved` | `boolean` |
| `restoredPaths` | `String`[] |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |
| `summaryText` | `String` |

<a id="shape-GitStashResponse"></a>
### `GitStashResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `stashed` | `boolean` |
| `stash` | [`GitStashListResponse.GitStash`](#shape-GitStashListResponse-GitStash) |
| `totalStashes` | `int` |
| `summaryText` | `String` |

<a id="shape-GitStashListResponse"></a>
### `GitStashListResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `totalStashes` | `int` |
| `stashes` | [`GitStashListResponse.GitStash`](#shape-GitStashListResponse-GitStash)[] |
| `summaryText` | `String` |

<a id="shape-GitStashPopResponse"></a>
### `GitStashPopResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `status` | [`GitStashPopResponse.PopStatus`](#shape-GitStashPopResponse-PopStatus) |
| `dropped` | `boolean` |
| `stashRef` | `String` |
| `stashSha` | `String` |
| `stashMessage` | `String` |
| `conflicting` | [`GitStatusResponse.GitFileChange`](#shape-GitStatusResponse-GitFileChange)[] |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |
| `summaryText` | `String` |

<a id="shape-GitStatusResponse"></a>
### `GitStatusResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `branch` | `String` |
| `upstreamBranch` | `String` |
| `aheadCount` | `Integer` |
| `behindCount` | `Integer` |
| `staged` | [`GitStatusResponse.GitFileChange`](#shape-GitStatusResponse-GitFileChange)[] |
| `unstaged` | [`GitStatusResponse.GitFileChange`](#shape-GitStatusResponse-GitFileChange)[] |
| `untracked` | [`GitStatusResponse.GitFileChange`](#shape-GitStatusResponse-GitFileChange)[] |
| `conflicting` | [`GitStatusResponse.GitFileChange`](#shape-GitStatusResponse-GitFileChange)[] |
| `totalChanges` | `int` |
| `clean` | `boolean` |
| `summaryText` | `String` |

<a id="shape-QuickFixResponse"></a>
### `QuickFixResponse`

| Field | Type |
|---|---|
| `status` | [`QuickFixResponse.Status`](#shape-QuickFixResponse-Status) |
| `markerId` | `long` |
| `projectName` | `String` |
| `filePath` | `String` |
| `requestedIndex` | `int` |
| `appliedLabel` | `String` |
| `markerResolved` | `Boolean` |
| `availableProposals` | [`CompilationProblemsResponse.QuickFixOption`](#shape-CompilationProblemsResponse-QuickFixOption)[] |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-TypeResolutionResponse"></a>
### `TypeResolutionResponse`

| Field | Type |
|---|---|
| `status` | [`TypeResolutionResponse.Status`](#shape-TypeResolutionResponse-Status) |
| `requestedTypeName` | `String` |
| `resolvedTypeName` | `String` |
| `searchedProjectName` | `String` |
| `sourceOrigin` | [`SourceOrigin`](#shape-SourceOrigin) |
| `projectName` | `String` |
| `filePath` | `String` |
| `rootKind` | [`TypeResolutionResponse.RootKind`](#shape-TypeResolutionResponse-RootKind) |
| `packageFragmentRoot` | `String` |
| `sourceAttachmentPath` | `String` |
| `classpathEntryKind` | [`TypeResolutionResponse.ClasspathEntryKind`](#shape-TypeResolutionResponse-ClasspathEntryKind) |
| `classpathEntryPath` | `String` |
| `classFilePath` | `String` |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-SearchResponse"></a>
### `SearchResponse`

| Field | Type |
|---|---|
| `query` | `String` |
| `totalMatches` | `int` |
| `filesMatched` | `int` |
| `matches` | [`SearchResponse.SearchMatch`](#shape-SearchResponse-SearchMatch)[] |
| `truncated` | `boolean` |
| `summaryText` | `String` |

<a id="shape-FileListResponse"></a>
### `FileListResponse`

| Field | Type |
|---|---|
| `patterns` | `String`[] |
| `totalFiles` | `int` |
| `files` | [`FileListResponse.WorkspaceFile`](#shape-FileListResponse-WorkspaceFile)[] |
| `truncated` | `boolean` |
| `summaryText` | `String` |

<a id="shape-ReferencesResponse"></a>
### `ReferencesResponse`

| Field | Type |
|---|---|
| `target` | `String` |
| `totalReferences` | `int` |
| `filesAffected` | `int` |
| `references` | [`ReferencesResponse.Reference`](#shape-ReferencesResponse-Reference)[] |
| `truncated` | `boolean` |
| `summaryText` | `String` |

<a id="shape-TestClassesResponse"></a>
### `TestClassesResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `totalClasses` | `int` |
| `plainTests` | [`TestClassesResponse.TestClass`](#shape-TestClassesResponse-TestClass)[] |
| `pdeTests` | [`TestClassesResponse.TestClass`](#shape-TestClassesResponse-TestClass)[] |
| `namingWarnings` | `String`[] |
| `summaryText` | `String` |

<a id="shape-ClassOutlineResponse"></a>
### `ClassOutlineResponse`

| Field | Type |
|---|---|
| `typeName` | `String` |
| `status` | [`ClassOutlineResponse.Status`](#shape-ClassOutlineResponse-Status) |
| `projectName` | `String` |
| `filePath` | `String` |
| `declaration` | [`ClassOutlineResponse.Member`](#shape-ClassOutlineResponse-Member) |
| `fields` | [`ClassOutlineResponse.Member`](#shape-ClassOutlineResponse-Member)[] |
| `methods` | [`ClassOutlineResponse.Member`](#shape-ClassOutlineResponse-Member)[] |
| `innerTypes` | [`ClassOutlineResponse.Member`](#shape-ClassOutlineResponse-Member)[] |
| `summaryText` | `String` |

<a id="shape-CompilationProblemsResponse"></a>
### `CompilationProblemsResponse`

| Field | Type |
|---|---|
| `scope` | `String` |
| `totalProblems` | `int` |
| `errorCount` | `int` |
| `warningCount` | `int` |
| `infoCount` | `int` |
| `files` | [`CompilationProblemsResponse.FileProblems`](#shape-CompilationProblemsResponse-FileProblems)[] |
| `truncated` | `boolean` |
| `summaryText` | `String` |

<a id="shape-ConsoleOutputResponse"></a>
### `ConsoleOutputResponse`

| Field | Type |
|---|---|
| `status` | [`ConsoleOutputResponse.Status`](#shape-ConsoleOutputResponse-Status) |
| `totalConsoles` | `int` |
| `consoles` | [`ConsoleOutputResponse.ConsoleOutput`](#shape-ConsoleOutputResponse-ConsoleOutput)[] |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-ImportSuggestionsResponse"></a>
### `ImportSuggestionsResponse`

| Field | Type |
|---|---|
| `status` | [`ImportSuggestionsResponse.Status`](#shape-ImportSuggestionsResponse-Status) |
| `projectName` | `String` |
| `filePath` | `String` |
| `totalUnresolvedTypes` | `int` |
| `totalCandidates` | `int` |
| `unresolvedTypes` | [`ImportSuggestionsResponse.UnresolvedType`](#shape-ImportSuggestionsResponse-UnresolvedType)[] |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-JavaDocResponse"></a>
### `JavaDocResponse`

| Field | Type |
|---|---|
| `status` | [`JavaDocResponse.Status`](#shape-JavaDocResponse-Status) |
| `typeName` | `String` |
| `projectName` | `String` |
| `markdown` | `String` |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-MarkdownOutlineResponse"></a>
### `MarkdownOutlineResponse`

| Field | Type |
|---|---|
| `status` | [`MarkdownOutlineResponse.Status`](#shape-MarkdownOutlineResponse-Status) |
| `projectName` | `String` |
| `filePath` | `String` |
| `totalLines` | `int` |
| `headings` | [`MarkdownOutlineResponse.Heading`](#shape-MarkdownOutlineResponse-Heading)[] |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-CallHierarchyResponse"></a>
### `CallHierarchyResponse`

| Field | Type |
|---|---|
| `status` | [`CallHierarchyResponse.Status`](#shape-CallHierarchyResponse-Status) |
| `target` | `String` |
| `methodName` | `String` |
| `declaringType` | `String` |
| `maxDepth` | `int` |
| `totalCallers` | `int` |
| `totalCallees` | `int` |
| `callers` | [`CallHierarchyResponse.CallNode`](#shape-CallHierarchyResponse-CallNode)[] |
| `callees` | [`CallHierarchyResponse.CallNode`](#shape-CallHierarchyResponse-CallNode)[] |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-MethodSourceResponse"></a>
### `MethodSourceResponse`

| Field | Type |
|---|---|
| `status` | [`MethodSourceResponse.Status`](#shape-MethodSourceResponse-Status) |
| `className` | `String` |
| `projectName` | `String` |
| `filePath` | `String` |
| `version` | [`ResourceVersion`](#shape-ResourceVersion) |
| `methods` | [`MethodSourceResponse.MethodSource`](#shape-MethodSourceResponse-MethodSource)[] |
| `notFound` | `String`[] |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-MavenDependenciesResponse"></a>
### `MavenDependenciesResponse`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `totalDependencies` | `int` |
| `dependencies` | [`MavenDependenciesResponse.MavenDependency`](#shape-MavenDependenciesResponse-MavenDependency)[] |
| `summaryText` | `String` |

<a id="shape-ProjectLayoutResponse"></a>
### `ProjectLayoutResponse`

| Field | Type |
|---|---|
| `status` | [`ProjectLayoutResponse.Status`](#shape-ProjectLayoutResponse-Status) |
| `projectName` | `String` |
| `scopePath` | `String` |
| `maxDepth` | `Integer` |
| `root` | [`ProjectLayoutResponse.Node`](#shape-ProjectLayoutResponse-Node) |
| `listedFiles` | `int` |
| `listedFolders` | `int` |
| `excludedCount` | `int` |
| `truncated` | `boolean` |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-ProjectPropertiesResponse"></a>
### `ProjectPropertiesResponse`

| Field | Type |
|---|---|
| `status` | [`ProjectPropertiesResponse.Status`](#shape-ProjectPropertiesResponse-Status) |
| `projectName` | `String` |
| `location` | `String` |
| `natures` | `String`[] |
| `buildFiles` | `String`[] |
| `java` | [`ProjectPropertiesResponse.JavaProperties`](#shape-ProjectPropertiesResponse-JavaProperties) |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-TypeHierarchyResponse"></a>
### `TypeHierarchyResponse`

| Field | Type |
|---|---|
| `typeName` | `String` |
| `status` | [`TypeHierarchyResponse.Status`](#shape-TypeHierarchyResponse-Status) |
| `superclasses` | [`TypeHierarchyResponse.HierarchyType`](#shape-TypeHierarchyResponse-HierarchyType)[] |
| `interfaces` | [`TypeHierarchyResponse.HierarchyType`](#shape-TypeHierarchyResponse-HierarchyType)[] |
| `subtypes` | [`TypeHierarchyResponse.HierarchyType`](#shape-TypeHierarchyResponse-HierarchyType)[] |
| `summaryText` | `String` |

<a id="shape-MavenProjectListResponse"></a>
### `MavenProjectListResponse`

| Field | Type |
|---|---|
| `totalProjects` | `int` |
| `projects` | [`MavenProjectListResponse.MavenProject`](#shape-MavenProjectListResponse-MavenProject)[] |
| `summaryText` | `String` |

<a id="shape-ProjectListResponse"></a>
### `ProjectListResponse`

| Field | Type |
|---|---|
| `totalProjects` | `int` |
| `openProjects` | `int` |
| `projects` | [`ProjectListResponse.WorkspaceProject`](#shape-ProjectListResponse-WorkspaceProject)[] |
| `summaryText` | `String` |

<a id="shape-OpenProjectResponse"></a>
### `OpenProjectResponse`

| Field | Type |
|---|---|
| `status` | [`OpenProjectResponse.Status`](#shape-OpenProjectResponse-Status) |
| `projectName` | `String` |
| `directoryPath` | `String` |
| `location` | `String` |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-McpSchema-ImageContent"></a>
### `McpSchema.ImageContent`

| Field | Type |
|---|---|
| `annotations` | [`McpSchema.Annotations`](#shape-McpSchema-Annotations) |
| `data` | `String` |
| `mimeType` | `String` |
| `meta` | Map&lt;`String`, `Object`&gt; |

<a id="shape-TestRunResponse"></a>
### `TestRunResponse`

| Field | Type |
|---|---|
| `status` | [`TestRunResponse.RunStatus`](#shape-TestRunResponse-RunStatus) |
| `projectName` | `String` |
| `requestedClasses` | `String`[] |
| `summary` | [`TestRunResponse.TestSummary`](#shape-TestRunResponse-TestSummary) |
| `failedTests` | [`TestRunResponse.TestCaseResult`](#shape-TestRunResponse-TestCaseResult)[] |
| `skippedTests` | [`TestRunResponse.SkippedTestResult`](#shape-TestRunResponse-SkippedTestResult)[] |
| `coverage` | [`TestRunResponse.CoverageResult`](#shape-TestRunResponse-CoverageResult) |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |
| `summaryText` | `String` |
| `durationMillis` | `long` |

<a id="shape-SearchReplaceResponse"></a>
### `SearchReplaceResponse`

| Field | Type |
|---|---|
| `searchText` | `String` |
| `replacementText` | `String` |
| `filesChanged` | `int` |
| `totalMatches` | `int` |
| `totalReplacements` | `int` |
| `files` | [`SearchReplaceResponse.FileReplacement`](#shape-SearchReplaceResponse-FileReplacement)[] |
| `summaryText` | `String` |

<a id="shape-ActiveTargetResponse"></a>
### `ActiveTargetResponse`

| Field | Type |
|---|---|
| `status` | [`ActiveTargetResponse.TargetStatus`](#shape-ActiveTargetResponse-TargetStatus) |
| `explicitTarget` | `boolean` |
| `name` | `String` |
| `memento` | `String` |
| `exists` | `boolean` |
| `resolved` | `boolean` |
| `bundleCount` | `Integer` |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |

<a id="shape-LaunchResponse"></a>
### `LaunchResponse`

| Field | Type |
|---|---|
| `status` | [`LaunchResponse.Status`](#shape-LaunchResponse-Status) |
| `launchName` | `String` |
| `mode` | `String` |
| `projectName` | `String` |
| `mainClass` | `String` |
| `pid` | `Long` |
| `exitCode` | `Integer` |
| `timedOut` | `boolean` |
| `durationMillis` | `long` |
| `stdout` | [`LaunchResponse.ProcessOutput`](#shape-LaunchResponse-ProcessOutput) |
| `stderr` | [`LaunchResponse.ProcessOutput`](#shape-LaunchResponse-ProcessOutput) |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |
| `summaryText` | `String` |

<a id="shape-EvaluationResponse"></a>
### `EvaluationResponse`

| Field | Type |
|---|---|
| `status` | [`EvaluationResponse.Status`](#shape-EvaluationResponse-Status) |
| `nameOrClass` | `String` |
| `expression` | `String` |
| `launchName` | `String` |
| `threadName` | `String` |
| `frame` | [`StackTraceResponse.Frame`](#shape-StackTraceResponse-Frame) |
| `value` | `String` |
| `declaredType` | `String` |
| `nullResult` | `boolean` |
| `errorMessages` | `String`[] |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |
| `summaryText` | `String` |

<a id="shape-StackTraceResponse"></a>
### `StackTraceResponse`

| Field | Type |
|---|---|
| `nameOrClass` | `String` |
| `sessionFound` | `boolean` |
| `launchName` | `String` |
| `mainType` | `String` |
| `anyThreadSuspended` | `boolean` |
| `totalThreads` | `int` |
| `threads` | [`StackTraceResponse.ThreadTrace`](#shape-StackTraceResponse-ThreadTrace)[] |
| `summaryText` | `String` |

<a id="shape-HotCodeReplaceResponse"></a>
### `HotCodeReplaceResponse`

| Field | Type |
|---|---|
| `status` | [`HotCodeReplaceResponse.Status`](#shape-HotCodeReplaceResponse-Status) |
| `nameOrClass` | `String` |
| `launchName` | `String` |
| `projectName` | `String` |
| `waitedMillis` | `long` |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |
| `summaryText` | `String` |

<a id="shape-ActiveLaunchesResponse"></a>
### `ActiveLaunchesResponse`

| Field | Type |
|---|---|
| `totalLaunches` | `int` |
| `launches` | [`ActiveLaunchesResponse.ActiveLaunch`](#shape-ActiveLaunchesResponse-ActiveLaunch)[] |
| `summaryText` | `String` |

<a id="shape-BreakpointsResponse"></a>
### `BreakpointsResponse`

| Field | Type |
|---|---|
| `totalBreakpoints` | `int` |
| `enabledCount` | `int` |
| `breakpoints` | [`BreakpointsResponse.BreakpointInfo`](#shape-BreakpointsResponse-BreakpointInfo)[] |
| `summaryText` | `String` |

<a id="shape-LaunchConfigurationsResponse"></a>
### `LaunchConfigurationsResponse`

| Field | Type |
|---|---|
| `typeFilter` | `String` |
| `totalConfigurations` | `int` |
| `configurations` | [`LaunchConfigurationsResponse.LaunchConfigurationInfo`](#shape-LaunchConfigurationsResponse-LaunchConfigurationInfo)[] |
| `summaryText` | `String` |

<a id="shape-StepResponse"></a>
### `StepResponse`

| Field | Type |
|---|---|
| `status` | [`StepResponse.Status`](#shape-StepResponse-Status) |
| `kind` | [`StepResponse.Kind`](#shape-StepResponse-Kind) |
| `nameOrClass` | `String` |
| `launchName` | `String` |
| `threadName` | `String` |
| `frame` | [`StackTraceResponse.Frame`](#shape-StackTraceResponse-Frame) |
| `waitedMillis` | `long` |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |
| `summaryText` | `String` |

<a id="shape-BreakpointResponse"></a>
### `BreakpointResponse`

| Field | Type |
|---|---|
| `status` | [`BreakpointResponse.Status`](#shape-BreakpointResponse-Status) |
| `action` | [`BreakpointResponse.Action`](#shape-BreakpointResponse-Action) |
| `projectName` | `String` |
| `typeName` | `String` |
| `lineNumber` | `int` |
| `breakpoint` | [`BreakpointsResponse.BreakpointInfo`](#shape-BreakpointsResponse-BreakpointInfo) |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |
| `summaryText` | `String` |

<a id="shape-StopApplicationResponse"></a>
### `StopApplicationResponse`

| Field | Type |
|---|---|
| `status` | [`StopApplicationResponse.Status`](#shape-StopApplicationResponse-Status) |
| `nameOrClass` | `String` |
| `totalMatched` | `int` |
| `terminated` | [`StopApplicationResponse.TerminatedLaunch`](#shape-StopApplicationResponse-TerminatedLaunch)[] |
| `diagnostics` | [`Diagnostic`](#shape-Diagnostic)[] |
| `summaryText` | `String` |

<a id="shape-WebPageResponse"></a>
### `WebPageResponse`

| Field | Type |
|---|---|
| `requestedUrl` | `String` |
| `finalUrl` | `String` |
| `statusCode` | `int` |
| `contentType` | `String` |
| `title` | `String` |
| `content` | `String` |

<a id="shape-WebSearchResponse-Result"></a>
### `WebSearchResponse.Result`

| Field | Type |
|---|---|
| `title` | `String` |
| `url` | `String` |
| `snippet` | `String` |

<a id="shape-EditResult-EditStatus"></a>
### `EditResult.EditStatus`

`APPLIED` \| `APPLIED_WITH_WARNINGS` \| `REJECTED` \| `PREVIEW`

<a id="shape-ResourceVersion"></a>
### `ResourceVersion`

| Field | Type |
|---|---|
| `modificationStamp` | `Long` |
| `localTimeStamp` | `long` |
| `historyTimestamp` | `Long` |
| `inSyncWithFileSystem` | `boolean` |

<a id="shape-EditResult-AppliedEdit"></a>
### `EditResult.AppliedEdit`

| Field | Type |
|---|---|
| `oldRange` | [`ContentRange`](#shape-ContentRange) |
| `newRange` | [`ContentRange`](#shape-ContentRange) |
| `insertedCharacters` | `int` |
| `deletedCharacters` | `int` |

<a id="shape-EditResult-AffectedResource"></a>
### `EditResult.AffectedResource`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `filePath` | `String` |
| `kind` | [`EditResult.ChangeKind`](#shape-EditResult-ChangeKind) |
| `version` | [`ResourceVersion`](#shape-ResourceVersion) |

<a id="shape-EditResult-EditorReveal"></a>
### `EditResult.EditorReveal`

| Field | Type |
|---|---|
| `opened` | `boolean` |
| `revealedRange` | [`ContentRange`](#shape-ContentRange) |
| `caret` | [`EditResult.EditorPosition`](#shape-EditResult-EditorPosition) |

<a id="shape-EditResult-WorkspaceSync"></a>
### `EditResult.WorkspaceSync`

| Field | Type |
|---|---|
| `savedToDisk` | `boolean` |
| `cacheUpdated` | `boolean` |
| `jdtConsistent` | `String` |

<a id="shape-Diagnostic"></a>
### `Diagnostic`

| Field | Type |
|---|---|
| `code` | [`DiagnosticCode`](#shape-DiagnosticCode) |
| `message` | `String` |
| `retryable` | `boolean` |

<a id="shape-LineDelimiterPreference-DelimiterName"></a>
### `LineDelimiterPreference.DelimiterName`

`LF` \| `CRLF` \| `CR` \| `OTHER`

<a id="shape-LineDelimiterPreference-Source"></a>
### `LineDelimiterPreference.Source`

`PROJECT` \| `WORKSPACE` \| `DEFAULT`

<a id="shape-ResourceReadResult-ReadStatus"></a>
### `ResourceReadResult.ReadStatus`

`OK` \| `PARTIAL` \| `FAILED`

<a id="shape-ContentRange"></a>
### `ContentRange`

| Field | Type |
|---|---|
| `startLine` | `int` |
| `startColumn` | `int` |
| `endLine` | `int` |
| `endColumn` | `int` |

<a id="shape-SourceOrigin"></a>
### `SourceOrigin`

`WORKSPACE_SOURCE` \| `ATTACHED_SOURCE` \| `DECOMPILED_CLASS` \| `LOCAL_HISTORY`

<a id="shape-FileHistoryResponse-HistoryEntry"></a>
### `FileHistoryResponse.HistoryEntry`

| Field | Type |
|---|---|
| `historyTimestamp` | `long` |
| `storedAt` | `String` |
| `sizeBytes` | `long` |
| `exists` | `boolean` |

<a id="shape-CachedResourcesResponse-CachedEntry"></a>
### `CachedResourcesResponse.CachedEntry`

| Field | Type |
|---|---|
| `uri` | `String` |
| `type` | [`ResourceDescriptor.ResourceType`](#shape-ResourceDescriptor-ResourceType) |
| `displayName` | `String` |
| `projectName` | `String` |
| `filePath` | `String` |
| `cachedAt` | `String` |
| `cachedAtEpochMilli` | `long` |
| `modificationStamp` | `Long` |
| `estimatedTokens` | `int` |
| `cacheRevision` | `int` |

<a id="shape-GitStageResponse-StageOperation"></a>
### `GitStageResponse.StageOperation`

`STAGE` \| `UNSTAGE`

<a id="shape-GitStatusResponse-GitFileChange"></a>
### `GitStatusResponse.GitFileChange`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `filePath` | `String` |
| `repoPath` | `String` |
| `changeType` | [`GitStatusResponse.ChangeType`](#shape-GitStatusResponse-ChangeType) |

<a id="shape-GitBranchResponse-GitBranch"></a>
### `GitBranchResponse.GitBranch`

| Field | Type |
|---|---|
| `name` | `String` |
| `fullName` | `String` |
| `sha` | `String` |
| `current` | `boolean` |

<a id="shape-GitCheckoutResponse-CheckoutStatus"></a>
### `GitCheckoutResponse.CheckoutStatus`

`SWITCHED` \| `BLOCKED`

<a id="shape-GitLogResponse-GitCommit"></a>
### `GitLogResponse.GitCommit`

| Field | Type |
|---|---|
| `sha` | `String` |
| `shortSha` | `String` |
| `author` | `String` |
| `authorEmail` | `String` |
| `authorTimeMillis` | `long` |
| `message` | `String` |
| `shortMessage` | `String` |

<a id="shape-GitDiffResponse-GitFileDiff"></a>
### `GitDiffResponse.GitFileDiff`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `filePath` | `String` |
| `repoPath` | `String` |
| `oldRepoPath` | `String` |
| `changeType` | [`GitDiffResponse.FileChangeType`](#shape-GitDiffResponse-FileChangeType) |
| `addedLines` | `int` |
| `removedLines` | `int` |
| `binary` | `boolean` |

<a id="shape-GitStagePatchResponse-PatchStatus"></a>
### `GitStagePatchResponse.PatchStatus`

`STAGED` \| `FAILED`

<a id="shape-GitStashListResponse-GitStash"></a>
### `GitStashListResponse.GitStash`

| Field | Type |
|---|---|
| `index` | `int` |
| `ref` | `String` |
| `sha` | `String` |
| `message` | `String` |

<a id="shape-GitStashPopResponse-PopStatus"></a>
### `GitStashPopResponse.PopStatus`

`APPLIED` \| `CONFLICTED` \| `NOTHING_TO_APPLY`

<a id="shape-QuickFixResponse-Status"></a>
### `QuickFixResponse.Status`

`APPLIED` \| `MARKER_NOT_FOUND` \| `NO_PROPOSALS` \| `INVALID_PROPOSAL_INDEX` \| `APPLY_FAILED`

<a id="shape-CompilationProblemsResponse-QuickFixOption"></a>
### `CompilationProblemsResponse.QuickFixOption`

| Field | Type |
|---|---|
| `index` | `int` |
| `label` | `String` |
| `description` | `String` |

<a id="shape-TypeResolutionResponse-Status"></a>
### `TypeResolutionResponse.Status`

`OK` \| `TYPE_NOT_RESOLVED` \| `PROJECT_NOT_FOUND`

<a id="shape-TypeResolutionResponse-RootKind"></a>
### `TypeResolutionResponse.RootKind`

`WORKSPACE_FOLDER` \| `WORKSPACE_ARCHIVE` \| `EXTERNAL_FOLDER` \| `EXTERNAL_ARCHIVE`

<a id="shape-TypeResolutionResponse-ClasspathEntryKind"></a>
### `TypeResolutionResponse.ClasspathEntryKind`

`SOURCE` \| `PROJECT` \| `LIBRARY` \| `VARIABLE` \| `CONTAINER` \| `UNKNOWN`

<a id="shape-SearchResponse-SearchMatch"></a>
### `SearchResponse.SearchMatch`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `filePath` | `String` |
| `lineNumber` | `int` |
| `lineContent` | `String` |

<a id="shape-FileListResponse-WorkspaceFile"></a>
### `FileListResponse.WorkspaceFile`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `filePath` | `String` |

<a id="shape-ReferencesResponse-Reference"></a>
### `ReferencesResponse.Reference`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `filePath` | `String` |
| `lineNumber` | `int` |
| `enclosingElement` | `String` |
| `lineContent` | `String` |

<a id="shape-TestClassesResponse-TestClass"></a>
### `TestClassesResponse.TestClass`

| Field | Type |
|---|---|
| `className` | `String` |
| `filePath` | `String` |
| `likelyRequiresPdeHarness` | `boolean` |

<a id="shape-ClassOutlineResponse-Status"></a>
### `ClassOutlineResponse.Status`

`OK` \| `TYPE_NOT_FOUND` \| `NO_SOURCE` \| `ACCESS_DENIED`

<a id="shape-ClassOutlineResponse-Member"></a>
### `ClassOutlineResponse.Member`

| Field | Type |
|---|---|
| `name` | `String` |
| `label` | `String` |
| `startLine` | `int` |
| `endLine` | `int` |

<a id="shape-CompilationProblemsResponse-FileProblems"></a>
### `CompilationProblemsResponse.FileProblems`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `filePath` | `String` |
| `problems` | [`CompilationProblemsResponse.Problem`](#shape-CompilationProblemsResponse-Problem)[] |

<a id="shape-ConsoleOutputResponse-Status"></a>
### `ConsoleOutputResponse.Status`

`OK` \| `FAILED`

<a id="shape-ConsoleOutputResponse-ConsoleOutput"></a>
### `ConsoleOutputResponse.ConsoleOutput`

| Field | Type |
|---|---|
| `consoleName` | `String` |
| `returnedRange` | [`ContentRange`](#shape-ContentRange) |
| `totalLines` | `int` |
| `truncated` | `boolean` |
| `text` | `String` |

<a id="shape-ImportSuggestionsResponse-Status"></a>
### `ImportSuggestionsResponse.Status`

`OK` \| `PROJECT_NOT_FOUND` \| `PROJECT_CLOSED` \| `FILE_NOT_FOUND` \| `FAILED`

<a id="shape-ImportSuggestionsResponse-UnresolvedType"></a>
### `ImportSuggestionsResponse.UnresolvedType`

| Field | Type |
|---|---|
| `typeName` | `String` |
| `lineNumber` | `int` |
| `message` | `String` |
| `candidates` | `String`[] |

<a id="shape-JavaDocResponse-Status"></a>
### `JavaDocResponse.Status`

`OK` \| `NO_JAVADOC` \| `TYPE_NOT_FOUND`

<a id="shape-MarkdownOutlineResponse-Status"></a>
### `MarkdownOutlineResponse.Status`

`OK` \| `FAILED`

<a id="shape-MarkdownOutlineResponse-Heading"></a>
### `MarkdownOutlineResponse.Heading`

| Field | Type |
|---|---|
| `index` | `int` |
| `level` | `int` |
| `text` | `String` |
| `range` | [`ContentRange`](#shape-ContentRange) |

<a id="shape-CallHierarchyResponse-Status"></a>
### `CallHierarchyResponse.Status`

`OK` \| `TYPE_NOT_FOUND` \| `METHOD_NOT_FOUND` \| `FAILED`

<a id="shape-CallHierarchyResponse-CallNode"></a>
### `CallHierarchyResponse.CallNode`

| Field | Type |
|---|---|
| `depth` | `int` |
| `methodName` | `String` |
| `declaringType` | `String` |
| `signature` | `String` |
| `projectName` | `String` |
| `filePath` | `String` |
| `lineNumber` | `int` |

<a id="shape-MethodSourceResponse-Status"></a>
### `MethodSourceResponse.Status`

`OK` \| `PARTIAL` \| `FAILED`

<a id="shape-MethodSourceResponse-MethodSource"></a>
### `MethodSourceResponse.MethodSource`

| Field | Type |
|---|---|
| `methodName` | `String` |
| `parameters` | `String` |
| `range` | [`ContentRange`](#shape-ContentRange) |
| `source` | `String` |

<a id="shape-MavenDependenciesResponse-MavenDependency"></a>
### `MavenDependenciesResponse.MavenDependency`

| Field | Type |
|---|---|
| `groupId` | `String` |
| `artifactId` | `String` |
| `version` | `String` |
| `scope` | `String` |

<a id="shape-ProjectLayoutResponse-Status"></a>
### `ProjectLayoutResponse.Status`

`OK` \| `FAILED`

<a id="shape-ProjectLayoutResponse-Node"></a>
### `ProjectLayoutResponse.Node`

| Field | Type |
|---|---|
| `name` | `String` |
| `filePath` | `String` |
| `type` | [`ProjectLayoutResponse.NodeType`](#shape-ProjectLayoutResponse-NodeType) |
| `childCount` | `int` |
| `children` | [`ProjectLayoutResponse.Node`](#shape-ProjectLayoutResponse-Node)[] |

<a id="shape-ProjectPropertiesResponse-Status"></a>
### `ProjectPropertiesResponse.Status`

`OK` \| `PROJECT_NOT_FOUND` \| `PROJECT_CLOSED` \| `FAILED`

<a id="shape-ProjectPropertiesResponse-JavaProperties"></a>
### `ProjectPropertiesResponse.JavaProperties`

| Field | Type |
|---|---|
| `complianceLevel` | `String` |
| `sourceCompatibility` | `String` |
| `targetCompatibility` | `String` |
| `outputLocation` | `String` |
| `sourceFolders` | `String`[] |
| `referencedProjects` | `String`[] |
| `referencedLibraries` | `String`[] |

<a id="shape-TypeHierarchyResponse-Status"></a>
### `TypeHierarchyResponse.Status`

`OK` \| `TYPE_NOT_FOUND`

<a id="shape-TypeHierarchyResponse-HierarchyType"></a>
### `TypeHierarchyResponse.HierarchyType`

| Field | Type |
|---|---|
| `fullyQualifiedName` | `String` |
| `projectName` | `String` |
| `filePath` | `String` |

<a id="shape-MavenProjectListResponse-MavenProject"></a>
### `MavenProjectListResponse.MavenProject`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `groupId` | `String` |
| `artifactId` | `String` |
| `version` | `String` |
| `packaging` | `String` |

<a id="shape-ProjectListResponse-WorkspaceProject"></a>
### `ProjectListResponse.WorkspaceProject`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `open` | `boolean` |
| `natures` | `String`[] |
| `location` | `String` |

<a id="shape-OpenProjectResponse-Status"></a>
### `OpenProjectResponse.Status`

`IMPORTED` \| `OPENED` \| `ALREADY_OPEN` \| `FAILED`

<a id="shape-McpSchema-Annotations"></a>
### `McpSchema.Annotations`

| Field | Type |
|---|---|
| `audience` | [`McpSchema.Role`](#shape-McpSchema-Role)[] |
| `priority` | `Double` |
| `lastModified` | `String` |

<a id="shape-TestRunResponse-RunStatus"></a>
### `TestRunResponse.RunStatus`

`RUNNING` \| `COMPLETED` \| `COMPLETED_WITH_FAILURES` \| `FAILED_TO_START` \| `TIMED_OUT` \| `CANCELLED`

<a id="shape-TestRunResponse-TestSummary"></a>
### `TestRunResponse.TestSummary`

| Field | Type |
|---|---|
| `total` | `int` |
| `passed` | `int` |
| `failed` | `int` |
| `errors` | `int` |
| `skipped` | `int` |

<a id="shape-TestRunResponse-TestCaseResult"></a>
### `TestRunResponse.TestCaseResult`

| Field | Type |
|---|---|
| `className` | `String` |
| `methodName` | `String` |
| `status` | [`TestRunResponse.TestStatus`](#shape-TestRunResponse-TestStatus) |
| `message` | `String` |
| `failureTrace` | `String` |
| `traceTruncated` | `boolean` |
| `source` | [`TestRunResponse.SourceLocation`](#shape-TestRunResponse-SourceLocation) |
| `durationSeconds` | `double` |

<a id="shape-TestRunResponse-SkippedTestResult"></a>
### `TestRunResponse.SkippedTestResult`

| Field | Type |
|---|---|
| `className` | `String` |
| `methodName` | `String` |
| `reason` | `String` |

<a id="shape-TestRunResponse-CoverageResult"></a>
### `TestRunResponse.CoverageResult`

| Field | Type |
|---|---|
| `requested` | `boolean` |
| `available` | `boolean` |
| `execFilePath` | `String` |
| `report` | `String` |

<a id="shape-SearchReplaceResponse-FileReplacement"></a>
### `SearchReplaceResponse.FileReplacement`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `filePath` | `String` |
| `matchesFound` | `int` |
| `replacementsMade` | `int` |

<a id="shape-ActiveTargetResponse-TargetStatus"></a>
### `ActiveTargetResponse.TargetStatus`

`ACTIVE` \| `RUNNING_PLATFORM` \| `FAILED`

<a id="shape-LaunchResponse-Status"></a>
### `LaunchResponse.Status`

`RUNNING` \| `COMPLETED` \| `FAILED_TO_START`

<a id="shape-LaunchResponse-ProcessOutput"></a>
### `LaunchResponse.ProcessOutput`

| Field | Type |
|---|---|
| `text` | `String` |
| `truncated` | `boolean` |
| `totalChars` | `int` |

<a id="shape-EvaluationResponse-Status"></a>
### `EvaluationResponse.Status`

`OK` \| `COMPILE_ERROR` \| `EVALUATION_FAILED` \| `TIMED_OUT` \| `NO_SUSPENDED_THREAD` \| `THREAD_NOT_FOUND` \| `SESSION_NOT_FOUND`

<a id="shape-StackTraceResponse-Frame"></a>
### `StackTraceResponse.Frame`

| Field | Type |
|---|---|
| `index` | `int` |
| `declaringType` | `String` |
| `methodName` | `String` |
| `projectName` | `String` |
| `filePath` | `String` |
| `lineNumber` | `int` |
| `nativeMethod` | `boolean` |
| `synthetic` | `boolean` |
| `variables` | [`StackTraceResponse.Variable`](#shape-StackTraceResponse-Variable)[] |

<a id="shape-StackTraceResponse-ThreadTrace"></a>
### `StackTraceResponse.ThreadTrace`

| Field | Type |
|---|---|
| `name` | `String` |
| `suspended` | `boolean` |
| `totalFrames` | `int` |
| `frames` | [`StackTraceResponse.Frame`](#shape-StackTraceResponse-Frame)[] |

<a id="shape-HotCodeReplaceResponse-Status"></a>
### `HotCodeReplaceResponse.Status`

`SUCCEEDED` \| `OBSOLETE_METHODS` \| `FAILED` \| `NOT_SUPPORTED` \| `IN_SYNC` \| `TIMED_OUT` \| `SESSION_NOT_FOUND` \| `NO_JAVA_TARGET`

<a id="shape-ActiveLaunchesResponse-ActiveLaunch"></a>
### `ActiveLaunchesResponse.ActiveLaunch`

| Field | Type |
|---|---|
| `name` | `String` |
| `mode` | `String` |
| `terminated` | `boolean` |
| `mainType` | `String` |
| `projectName` | `String` |
| `pid` | `Long` |
| `processes` | [`ActiveLaunchesResponse.LaunchProcess`](#shape-ActiveLaunchesResponse-LaunchProcess)[] |

<a id="shape-BreakpointsResponse-BreakpointInfo"></a>
### `BreakpointsResponse.BreakpointInfo`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `filePath` | `String` |
| `typeName` | `String` |
| `lineNumber` | `int` |
| `enabled` | `boolean` |
| `condition` | `String` |
| `hitCount` | `int` |
| `modelIdentifier` | `String` |

<a id="shape-LaunchConfigurationsResponse-LaunchConfigurationInfo"></a>
### `LaunchConfigurationsResponse.LaunchConfigurationInfo`

| Field | Type |
|---|---|
| `name` | `String` |
| `typeId` | `String` |
| `typeName` | `String` |
| `projectName` | `String` |
| `mainClass` | `String` |

<a id="shape-StepResponse-Status"></a>
### `StepResponse.Status`

`SUSPENDED` \| `RUNNING` \| `TERMINATED` \| `TIMED_OUT` \| `NO_SUSPENDED_THREAD` \| `THREAD_NOT_FOUND` \| `SESSION_NOT_FOUND` \| `FAILED`

<a id="shape-StepResponse-Kind"></a>
### `StepResponse.Kind`

`STEP_OVER` \| `STEP_INTO` \| `STEP_RETURN` \| `RESUME`

<a id="shape-BreakpointResponse-Status"></a>
### `BreakpointResponse.Status`

`OK` \| `PROJECT_NOT_FOUND` \| `TYPE_NOT_FOUND` \| `INVALID_LINE` \| `FAILED`

<a id="shape-BreakpointResponse-Action"></a>
### `BreakpointResponse.Action`

`SET` \| `REMOVED` \| `REPLACED` \| `NONE`

<a id="shape-StopApplicationResponse-Status"></a>
### `StopApplicationResponse.Status`

`OK` \| `NO_MATCH` \| `FAILED`

<a id="shape-StopApplicationResponse-TerminatedLaunch"></a>
### `StopApplicationResponse.TerminatedLaunch`

| Field | Type |
|---|---|
| `launchName` | `String` |
| `mainType` | `String` |
| `mode` | `String` |

<a id="shape-EditResult-ChangeKind"></a>
### `EditResult.ChangeKind`

`MODIFIED` \| `CREATED` \| `DELETED` \| `MOVED`

<a id="shape-EditResult-EditorPosition"></a>
### `EditResult.EditorPosition`

| Field | Type |
|---|---|
| `line` | `int` |
| `column` | `int` |

<a id="shape-DiagnosticCode"></a>
### `DiagnosticCode`

`RESOURCE_NOT_FOUND` \| `RESOURCE_NOT_ACCESSIBLE` \| `RESOURCE_ALREADY_EXISTS` \| `READ_ONLY_RESOURCE` \| `INVALID_RANGE` \| `VERSION_CONFLICT` \| `RESOURCE_VERSION_EXPIRED` \| `RESOURCE_OUT_OF_SYNC` \| `HISTORY_UNAVAILABLE` \| `TEXT_NOT_FOUND` \| `AMBIGUOUS_MATCH` \| `OVERLAPPING_EDITS` \| `INVALID_JAVA_EDIT` \| `REFACTORING_PRECONDITION_FAILED` \| `EDITOR_REVEAL_FAILED` \| `FORMATTER_FAILED` \| `PATCH_APPLY_FAILED` \| `MERGE_CONFLICT` \| `CHECKOUT_CONFLICT` \| `BRANCH_NOT_MERGED` \| `PROJECT_NOT_FOUND` \| `TEST_CLASS_NOT_FOUND` \| `TEST_PACKAGE_NOT_FOUND` \| `PDE_LAUNCH_TYPE_MISSING` \| `LAUNCH_CONFIGURATION_NOT_FOUND` \| `WORKSPACE_LOCKED` \| `OPERATION_TIMED_OUT` \| `DEPENDENCY_RESOLUTION_FAILED` \| `TEST_RESULTS_NOT_REPORTED` \| `COVERAGE_UNAVAILABLE` \| `INTERNAL_ERROR`

<a id="shape-ResourceDescriptor-ResourceType"></a>
### `ResourceDescriptor.ResourceType`

`WORKSPACE_FILE` \| `JAVA_TYPE` \| `PROJECT_LAYOUT` \| `CONSOLE_OUTPUT` \| `EXTERNAL_FILE` \| `QUERY_RESULT` \| `TRANSIENT`

<a id="shape-GitStatusResponse-ChangeType"></a>
### `GitStatusResponse.ChangeType`

`ADDED` \| `MODIFIED` \| `DELETED` \| `UNTRACKED` \| `CONFLICTING`

<a id="shape-GitDiffResponse-FileChangeType"></a>
### `GitDiffResponse.FileChangeType`

`ADDED` \| `MODIFIED` \| `DELETED` \| `RENAMED` \| `COPIED`

<a id="shape-CompilationProblemsResponse-Problem"></a>
### `CompilationProblemsResponse.Problem`

| Field | Type |
|---|---|
| `severity` | [`CompilationProblemsResponse.Severity`](#shape-CompilationProblemsResponse-Severity) |
| `lineNumber` | `int` |
| `message` | `String` |
| `markerId` | `long` |
| `problemId` | `Integer` |
| `contextSnippet` | `String` |
| `contextLanguage` | `String` |
| `quickFixes` | [`CompilationProblemsResponse.QuickFixOption`](#shape-CompilationProblemsResponse-QuickFixOption)[] |

<a id="shape-ProjectLayoutResponse-NodeType"></a>
### `ProjectLayoutResponse.NodeType`

`PROJECT` \| `FOLDER` \| `FILE`

<a id="shape-McpSchema-Role"></a>
### `McpSchema.Role`

`USER` \| `ASSISTANT`

<a id="shape-TestRunResponse-TestStatus"></a>
### `TestRunResponse.TestStatus`

`PASSED` \| `FAILED` \| `ERROR` \| `SKIPPED` \| `UNKNOWN`

<a id="shape-TestRunResponse-SourceLocation"></a>
### `TestRunResponse.SourceLocation`

| Field | Type |
|---|---|
| `projectName` | `String` |
| `filePath` | `String` |
| `line` | `Integer` |

<a id="shape-StackTraceResponse-Variable"></a>
### `StackTraceResponse.Variable`

| Field | Type |
|---|---|
| `name` | `String` |
| `typeName` | `String` |
| `value` | `String` |

<a id="shape-ActiveLaunchesResponse-LaunchProcess"></a>
### `ActiveLaunchesResponse.LaunchProcess`

| Field | Type |
|---|---|
| `label` | `String` |
| `terminated` | `boolean` |
| `pid` | `Long` |

<a id="shape-CompilationProblemsResponse-Severity"></a>
### `CompilationProblemsResponse.Severity`

`ERROR` \| `WARNING` \| `INFO` \| `UNKNOWN`
