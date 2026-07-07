# Git Local Review

**Review your changes like a pull request — before it exists.**

Git Local Review is an IntelliJ-platform plugin that adds a dedicated review tool window with GitHub-style per-file *viewed* tracking for any local diff — staged changes, the working tree, or any two refs (`A...B`, the same diff a PR would show).

> 📸 Screenshots coming soon.

## Features

- **Two review modes**
  - **Local Changes** — review the staging area or working tree before you commit
  - **Compare Refs** — review `A...B` between any branches, tags, or commit hashes (merge-base diff, identical to a GitHub PR)
- **Viewed checks that actually track content** — each check stores a content fingerprint; state survives commits and restarts, and automatically resets when a file changes again
- **Native diff experience** — files open in the built-in diff viewer as a chain; `Mark Reviewed and Open Next Unreviewed` (<kbd>Ctrl</kbd><kbd>Alt</kbd><kbd>Shift</kbd><kbd>M</kbd>) keeps the flow going
- **Navigation & Git actions** — jump to source (<kbd>F4</kbd>), context menu with Show History, Annotate, Compare with Branch, Rollback
- Review state is stored per diff range in your local workspace — never committed, never shared

## Install

Requires an IntelliJ-based IDE **2024.2+** with the bundled Git plugin.

1. Download the zip from [Releases](https://github.com/engineer-myoa/idea-git-local-review/releases)
2. `Settings → Plugins → ⚙ → Install Plugin from Disk…` → select the zip → restart

## Usage

1. Open the **Git Local Review** tool window (docked right by default)
2. Pick a mode: **Local Changes** (Staged / Working Tree) or **Compare Refs** (A defaults to your remote default branch, B to `HEAD`)
3. Double-click a file to open the diff, review, and mark it viewed — the progress indicator tracks the rest

## Comparison

| | Built-in "Mark as Viewed" | Git Local Review |
|---|---|---|
| Works on | GitHub PRs only | Any local diff: staged, working tree, `A...B` |
| Survives commits | — | Yes (Compare Refs mode) |
| Invalidation | on new PR commits | content fingerprint per file |

## Built with AI

This plugin was designed and implemented with [Claude Code](https://claude.com/claude-code), using a multi-agent workflow: every change was written, adversarially reviewed, and verified against decompiled IntelliJ Platform sources before merge. A human (the author) directed the design, tested every build, and owns every line.

## Credits

The persistence and action-dispatch design mirrors patterns from [JetBrains/intellij-community](https://github.com/JetBrains/intellij-community) (Apache-2.0) — notably the GitLab plugin's viewed-state component and the GitHub plugin's review actions. No source files were copied; the structural approach is gratefully acknowledged. Copyright (C) JetBrains s.r.o.

## License

[Apache-2.0](LICENSE)
