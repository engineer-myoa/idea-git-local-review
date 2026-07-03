## [Unreleased]

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
  the file tree selection, with a default shortcut of `Control+Alt+Shift+V`.
- Unreviewed-only filter toggle on the tool window tree.
- Multi git-root support via a repository selector.

### Known limitations (planned for v0.2)
- Mark Reviewed & Open Next reopens the next file as a brand-new diff chain rather than navigating
  within the existing diff viewer window; in-place navigation is deferred to v0.2.
- The Mark Reviewed & Open Next toolbar button hides (rather than disables) when nothing is
  selected and no diff is open; revisiting hide-vs-disable is a v0.2 candidate.
- Right-click bulk "Mark as reviewed" on a directory is not supported yet.
