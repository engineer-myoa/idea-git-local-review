# Git Local Review

*Review your changes like a pull request — before it exists.*

Git Local Review is an IntelliJ Platform plugin that opens a virtual pull request over any local
diff — a branch range, the staging area, or the working tree — and tracks which files you've
already reviewed, GitHub-PR style, in a dedicated tool window.

> **Screenshots coming with v0.2.**

## Why

IntelliJ's built-in "Mark as Viewed" checkbox only exists inside the GitHub Pull Request tool
window — there's no equivalent for reviewing local changes before you open a PR. When a branch has
piled up dozens of changed files, or a staging area is full of unrelated edits, there's no reliable
way to track "did I actually look at this file yet?" Git Local Review fills that gap by bringing
the same review motion — file list → open → check as viewed → next — to any local diff.

## Features

- **Three review session types**
  - **Branch Range** (`base...HEAD`, three-dot diff) — the same diff GitHub would show for a PR.
    The base ref is auto-detected from the repository's remote default branch (via `origin/HEAD`,
    falling back to `origin/main` / `origin/master` / the first remote branch when `origin/HEAD`
    isn't set), and can also be picked from local/remote branches or typed freely.
  - **Staged** — review what's about to be committed (`HEAD` vs. the index).
  - **Working Tree** — review all uncommitted changes (`HEAD` vs. the working tree).
- **Per-file reviewed state**, tracked independently per session, with a GitHub-style checkbox and
  a live `Reviewed n / m (xx%)` progress indicator.
- **Automatic invalidation** — if a file you already reviewed changes again, it automatically
  returns to unreviewed (a visual "changed after review" badge is planned for v0.2).
- **Commit-durable review state** — in a Branch Range session, reviewing a file survives any number
  of commits landing on *other* files. Only re-editing the reviewed file itself invalidates it.
- **Native diff viewer integration** — no custom diff renderer. Opening a file uses IntelliJ's
  built-in diff viewer as a full chain, so you can navigate between files without leaving the
  viewer.
- **Mark Reviewed & Open Next** — marks the current file as reviewed and jumps straight to the next
  unreviewed one. Available from the diff viewer's right-click menu, the tool window toolbar, and
  the file tree selection, with a default shortcut of **⌃⌥⇧M** (`Control+Alt+Shift+M`).
- **Unreviewed-only filter** to focus the tree on what's left.
- **Multi-repository support** — switch between git roots from a repository selector when a
  project has more than one.
- Review state is stored per project workspace (not shared via git), so it never shows up as a
  diff for teammates.

## Comparison

|  | Built-in "Mark as Viewed" | Local Review plugin | **Git Local Review (this plugin)** |
|---|---|---|---|
| Works outside a GitHub PR | No — GitHub PR tool window only | Yes | **Yes** |
| Diff sources | GitHub PR diff only | Working tree only | **Branch range (`base...HEAD`, PR-equivalent) + staged + working tree** |
| UX location | GitHub PR tool window | Decoration inside the Commit tool window | **Dedicated tool window, mirroring the GitHub PR review flow** |
| State after commit | N/A (remote PR) | File disappears from the list | **Branch range sessions keep the reviewed state** |
| Staging area | N/A | Not explicitly supported | **First-class session type** |

## Requirements

- An IntelliJ Platform IDE (IntelliJ IDEA, and other JetBrains IDEs built on the same platform),
  build **2024.2 (242) or later**
- The bundled **Git4Idea** plugin enabled (on by default)

## Installation

1. Download `idea-git-local-review-<version>.zip` from the project's
   [Releases](https://github.com/engineer-myoa/idea-git-local-review/releases) page, or build it
   yourself (see below).
2. In your IDE, go to **Settings/Preferences → Plugins**.
3. Click the **⚙** (gear) icon → **Install Plugin from Disk...**
4. Select the downloaded zip file and restart the IDE if prompted.

### Building from source

```bash
./gradlew clean build buildPlugin
```

The installable zip is produced at `build/distributions/idea-git-local-review-<version>.zip`.

## Usage

1. Open the **Git Local Review** tool window (right sidebar).
2. If your project has more than one git repository, pick one from the repo selector.
3. Pick a session type — **Branch Range**, **Staged**, or **Working Tree**.
4. For **Branch Range**, confirm or change the auto-detected base ref.
5. Double-click a file (or press Enter) to open it in the diff viewer — you can navigate through
   every file in the session from inside the viewer.
6. Check a file off (click its checkbox, or press Space) to mark it reviewed, or use
   **Mark Reviewed & Open Next** (⌃⌥⇧M) to mark the current file and jump straight to the next
   unreviewed one.
7. Press **F4**, or right-click a file for **Show Diff** / **Edit Source** / **Mark Reviewed and
   Open Next Unreviewed**, to jump to the actual source file or open its diff without leaving the
   tree.
8. Toggle **Unreviewed only** to hide files you've already checked off.
9. The progress bar and `Reviewed n / m` label track how much of the session is done.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
