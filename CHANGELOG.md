## [Unreleased]

## [0.3.1]

### Changed
- The **Mark as Reviewed** diff toolbar button moved earlier in the toolbar, next to the file
  navigation arrows' block rather than trailing after it — it's now registered declaratively on
  the platform's `Diff.ViewerToolbar` extension point instead of being injected per diff request.

### Fixed
- The tool window toolbar's **Mark as Reviewed** button no longer flickers in and out of existence
  as the tree selection changes — it now stays visible and simply disables when nothing is
  selected. Diff toolbars and context menus still hide the action entirely when it doesn't apply.

## [0.3.0]

### Added
- A **Mark as Reviewed** button in the native diff viewer's toolbar, so the reviewed state can be
  toggled without leaving the diff or reaching for the context menu.
- A tool window icon (monochrome, light/dark variants) for the **Git Local Review** stripe button.

### Changed
- **Mark Reviewed and Open Next Unreviewed** is replaced by **Mark as Reviewed**, a plain toggle of
  the current file's reviewed state with no automatic navigation. Its label flips to
  **Unmark as Reviewed** when the current file is already reviewed. Navigating between files is now
  always done manually, with the diff viewer's own previous/next controls.

### Removed
- Mark-and-open-next behavior and the `nextUnreviewed` navigation helper. Automatic navigation on
  mark was a source of surprise when reviewing out of order; manual navigation is simpler and more
  predictable.
- Note: the action id changed from `GitLocalReview.MarkReviewedAndOpenNext` to
  `GitLocalReview.ToggleReviewed`. If you customized the shortcut in Settings → Keymap, reassign it
  to the new action; the default Ctrl+Alt+Shift+M is unaffected.

## [0.2.0]

### Added
- **Git** submenu in the tool window tree's right-click context menu, exposing the platform's
  standard VCS actions on the selected file(s): **Show History**, **Annotate**,
  **Compare with Branch**, and **Rollback**.
- **Compare Refs** mode: pick any two refs (local/remote branches, tags, or typed commit hashes) as
  **A** and **B** and review the diff between them, with **B** defaulting to `HEAD`. Tags are now
  offered alongside branches in the ref pickers.

### Changed
- The tool window toolbar is restructured around two top-level modes, **Local Changes** and
  **Compare Refs**, replacing the old three-way Branch Range / Staged / Working Tree selector.
  **Local Changes** offers a **Staged** / **Working Tree** scope, matching the previous behavior of
  those two session types. **Compare Refs** replaces Branch Range with a general **A** / **B** ref
  comparison (**B** defaults to `HEAD`, reproducing the old `base...HEAD` behavior), so existing
  Branch Range reviewed state is preserved and shown under Compare Refs with **B** set to `HEAD`.

## [0.1.1]

### Fixed
- Toggling a file's reviewed checkbox (or using Mark Reviewed & Open Next) no longer collapses
  other expanded folders in the tool window tree. The tree now only rebuilds when the displayed
  file list actually changes, and preserves the tree's expansion state across rebuilds that do
  change the list.

### Added
- Jump to Source: press **F4** (or use **Edit Source** from the right-click menu) on a selected
  file to open it directly in the editor, at its current on-disk location.
- Right-click context menu on the tool window tree with **Show Diff**, **Edit Source**, and
  **Mark Reviewed and Open Next Unreviewed**.

### Changed
- The tool window's default anchor is now **right** instead of **left**.

## [0.1.0]

### Added
- Review tool window with three session types: **Branch Range** (`base...HEAD`, with base ref
  auto-detection and manual override), **Staged**, and **Working Tree**.
- Per-file reviewed ("Viewed") state, tracked independently per session, with GitHub-style
  checkboxes and a `Reviewed n / m (xx%)` progress indicator.
- Automatic invalidation of a file's reviewed state when its content changes again after being
  marked reviewed.
- Reviewed state persisted per project workspace, surviving IDE restarts; Branch Range sessions
  keep reviewed state across commits to other files.
- Native diff viewer integration — files open as a navigable chain using IntelliJ's built-in diff
  viewer, with no custom diff rendering.
- Mark Reviewed & Open Next action: marks the current file as reviewed and opens the next
  unreviewed file. Available from the diff viewer's right-click menu, the tool window toolbar, and
  the file tree selection, with a default shortcut of `Control+Alt+Shift+M`.
- Unreviewed-only filter toggle on the tool window tree.
- Multi git-root support via a repository selector.

### Known limitations (planned for v0.2)
- Mark Reviewed & Open Next reopens the next file as a brand-new diff chain rather than navigating
  within the existing diff viewer window; in-place navigation is deferred to v0.2.
- The Mark Reviewed & Open Next toolbar button hides (rather than disables) when nothing is
  selected and no diff is open; revisiting hide-vs-disable is a v0.2 candidate.
- Right-click bulk "Mark as reviewed" on a directory is not supported yet.
