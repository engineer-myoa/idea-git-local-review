# Manual Test Checklist — Git Local Review v0.3

The automated test suite covers pure logic in `session/` and `git/` (fingerprinting, invalidation,
`SessionKey` round-tripping, diff parsing). Everything below is UI-driven and requires a human
running a real IDE — it cannot be exercised headlessly.

## How to run

```bash
export JAVA_HOME=$(ls -d ~/.sdkman/candidates/java/21* | head -1)
./gradlew runIde
```

Open a project with real git history, and ideally with both staged changes and pending working
tree edits, so both the **Local Changes** and **Compare Refs** modes have something to show. A
repository with more than one commit, at least one tag, and a remote (`origin`) branch is needed
to exercise Compare Refs ref auto-detection and the tag pickers.

## Checklist

### Session types

- [ ] **① Staged session lists changed files** — Stage a handful of files (`git add`), open the
      **Git Local Review** tool window, set **Mode** to **Local Changes** and **Scope** to
      **Staged**, and verify every staged file appears in the tree with the correct A/M/D status
      and directory grouping.
- [ ] **④ Compare Refs mode (A auto-detect / manual)** — Switch **Mode** to **Compare Refs**.
      Verify the **A** ref combo auto-detects a sensible default (e.g. `origin/develop` or
      `origin/main`) and **B** defaults to `HEAD`. Then manually pick a different branch for **A**
      from the combo (or type one in) and verify the file list updates to the new diff.
      Also test with a repository whose origin/HEAD points to a non-main default (e.g. develop):
      run `git remote set-head origin develop`, reopen the panel, and verify **A** auto-selects
      origin/develop.

### Persistence & invalidation

- [ ] **② Reviewed state survives an IDE restart** — In a Staged (or Compare Refs) session, check
      off several files, restart the IDE (or re-run `runIde`), reopen the tool window on the same
      session, and confirm the same files are still shown as reviewed. This also validates the
      persisted-state XML round-trip (serialize on shutdown, deserialize on startup).
- [ ] **⑤ Reviewed file reverts to unreviewed on change** — Mark a file reviewed, then modify its
      contents on disk, refresh (manual **⟳** or wait for auto-refresh), and confirm its checkbox
      clears automatically after refresh.

### Diff viewer

- [ ] **③ Double-click opens the native diff chain** — Double-click any file in the tree (or press
      Enter). Confirm the built-in IntelliJ diff viewer opens and that you can move to the
      previous/next file in the session from inside the viewer, without going back to the tool
      window.

### Mark as Reviewed toggle

- [ ] **⑥ Toggle from the diff viewer context menu / shortcut** — With a diff open, right-click
      inside it and choose **Mark as Reviewed** (or press **⌃⌥⇧M** / `Control+Alt+Shift+M`). Confirm
      the current file is checked off in the tool window tree and the diff stays open on the same
      file (no automatic navigation). Trigger the action again (menu now reads **Unmark as
      Reviewed**) and confirm the file is unchecked.
- [ ] **⑦ Diff viewer toolbar button** — With a diff open, confirm a **Mark as Reviewed** button is
      visible in the diff toolbar (not just the context menu). Click it and confirm it toggles the
      same reviewed state as ⑥, with its icon/text reflecting the pressed/selected state once
      reviewed. Then use the diff viewer's own previous/next arrows to move to a different file in
      the chain and confirm the button's label and pressed state update to reflect *that* file's
      reviewed status (not the one you just toggled).
- [ ] **⑧ Toggle from the toolbar / tree selection** — Without opening a diff, select a file in the
      tool window tree and trigger **Mark as Reviewed** from the tool window toolbar button. Confirm
      it toggles that file's reviewed state the same way as ⑥, with no diff opened.

### Filtering

- [ ] **⑨ Unreviewed-only filter toggle** — Turn on **Unreviewed only** and confirm already-reviewed
      files disappear from the tree. Turn it off and confirm they reappear with their checkbox
      state intact (no state loss or corruption from filtering).

### Multi-repository

- [ ] **⑩ Multi git-root switching** — In a project with more than one git repository, use the repo
      selector to switch roots. Confirm the file tree, session type, and reviewed state shown all
      correspond to the newly selected repository, and that each root's state is independent (e.g.
      reviewed files in one root are not shown as reviewed in another).

### Known UX observation (not a defect)

- [ ] **⑪ Toolbar button visibility with no selection** — With nothing selected in the tree and no
      diff open, observe that the **Mark as Reviewed** toolbar button *disappears* rather than
      appearing disabled (current behavior: `isEnabledAndVisible = false`). Note whether a
      disabled-but-visible affordance would communicate the action's existence more clearly. This
      remains a UX trade-off to revisit, not a bug to fix now.

### Tree state & navigation

- [ ] **⑫ Folder expansion survives a checkbox toggle** — In a session with files spread across
      multiple directories, expand two or three folders in the tree, then check off (or uncheck) a
      file's reviewed checkbox in a *different* folder, and also try **Mark as Reviewed** from the
      tree context menu. Confirm the folders you expanded stay expanded — only the toggled file's
      own row changes.
- [ ] **⑬ F4 and right-click Jump to Source** — Select a file in the tree and press **F4**. Confirm
      it opens the file directly in the editor at its current on-disk location (not a diff view).
      Then right-click a file and confirm the context menu shows **Show Diff**, **Edit Source**, and
      **Mark as Reviewed**; verify **Edit Source** from that menu does the same as F4, and
      **Show Diff** opens the same diff chain as double-click.
- [ ] **⑭ Default docking on a fresh install** — Install the plugin fresh (or reset the tool window
      layout) and confirm the **Git Local Review** tool window docks on the **right** side of the
      IDE by default.

### Git submenu (v0.2)

- [ ] **⑮ Git submenu actions** — Right-click a file in the tree and open the **Git** submenu.
      Confirm it lists **Show History**, **Annotate**, **Compare with Branch**, a separator, and
      **Rollback**. Verify each opens the expected platform dialog/view for the selected file:
      **Show History** opens the file history tool window, **Annotate** toggles the editor gutter
      annotations (opening the file in the editor if it wasn't already), **Compare with Branch**
      prompts for a branch and shows a diff, and **Rollback** (with a Working Tree or Staged
      session showing an actual local modification selected) prompts to revert that file's local
      changes.

### Compare Refs (v0.2)

- [ ] **⑯ Compare Refs A/B tag and commit-hash comparison** — In **Compare Refs** mode, confirm the
      **A** and **B** combos both list local/remote branches and tags. Pick a tag for **A** and
      leave **B** at `HEAD`, and verify the diff matches `git diff <tag>...HEAD`. Then type a raw
      commit hash into **A** (or **B**) that isn't in the dropdown and confirm the diff updates
      against that commit too.
- [ ] **⑰ Upgraded Branch Range reviewed state carries over to Compare Refs (B = HEAD)** — Using a
      workspace state persisted before this release (or by first reviewing files in Compare Refs
      with **B** left at `HEAD`, then reopening the panel), confirm the previously reviewed files
      for a given **A** ref still show as reviewed when **Mode** is **Compare Refs** and **B** is
      `HEAD` — i.e. the storage key for `CompareRefs(a, "HEAD")` matches the old Branch Range
      storage key for the same base ref.

## Reporting results

When running this checklist, record the IDE build/version used and pass/fail per item so any
regression can be tied to a specific platform version.
