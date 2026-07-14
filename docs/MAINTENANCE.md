# Maintenance Notes — API Compatibility

Tracks IntelliJ Platform API deprecations that affect this plugin, so a future
compatibility fix is a lookup rather than an investigation.

_Last updated: 2026-07-14 · against: v0.3.2_

## Status

- v0.3.2 is published on the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/32834-git-local-review).
- Marketplace verification reports **Compatible** on every checked IDE (2025.2 / 2025.3 / 2026.1 / 2026.2 RC).
- `untilBuild` is left open (`provider { null }`), so the items below keep working until the API is
  actually removed. **Nothing here is urgent.**

---

## Action needed (our code) — `SimpleListCellRenderer.create(String, Function)`

Marked **scheduled for removal** starting in 2026.2 (build 262.x). Once the platform actually
removes it, builds targeting that IDE or later would break.

### Where — `src/main/kotlin/.../ui/ReviewPanel.kt` lines 72–74 (three call sites)

```kotlin
repoCombo.renderer  = SimpleListCellRenderer.create("") { it.root.name }
modeCombo.renderer  = SimpleListCellRenderer.create("") { it.label }
scopeCombo.renderer = SimpleListCellRenderer.create("") { it.label }
```

The Marketplace report counts this as a single usage, but there are **three** call sites — fix all
three.

### Replacement API (already present in the same class; confirmed by decompiling 262 `intellij.platform.ide.jar`)

```java
// scheduled for removal
static <T> SimpleListCellRenderer<T> create(String nullValue, Function<? super T, String> getText)
// recommended (not deprecated)
static <T> SimpleListCellRenderer<T> create(Customizer<? super T>)
interface Customizer<T> { void customize(JBLabel label, T value, int index); }
```

### Migration (before → after)

The old `create(nullValue = "", getText)` rendered `""` when the value was null and `getText(value)`
otherwise. The `Customizer` form sets the label text directly; preserve the same null handling:

```kotlin
repoCombo.renderer  = SimpleListCellRenderer.create { label, value, _ -> label.text = value?.root?.name ?: "" }
modeCombo.renderer  = SimpleListCellRenderer.create { label, value, _ -> label.text = value?.label ?: "" }
scopeCombo.renderer = SimpleListCellRenderer.create { label, value, _ -> label.text = value?.label ?: "" }
```

> `value` must be treated as nullable (a combo with no selection passes null); `?: ""` reproduces the
> old `nullValue` behavior.

### Verify

After the change:

```bash
./gradlew test verifyPlugin
```

Confirm the `scheduled for removal` count drops to 0 for `IU-262.8665.176` (or later).

---

## No action needed (library-generated code) — kotlinx.serialization

Two **deprecated** usages of
`kotlinx.serialization.internal.GeneratedSerializer.typeParametersSerializers`.

- Origin: the compiler-generated serializers for `ReviewStateService.State` and
  `ReviewStateService.SessionViewedState` (`@Serializable`). **Not hand-written code.**
- Resolution: not ours to fix directly; it clears when `kotlinx-serialization` is bumped. Currently
  `compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3")`. Not urgent.

---

## Re-checking compatibility

The Marketplace re-verifies each new IDE release automatically and emails the result. To check a
specific IDE locally, add a temporary target to `pluginVerification { ides { ... } }` in
`build.gradle.kts`:

```kotlin
create(IntelliJPlatformType.IntellijIdeaUltimate, "262.8665.176")  // e.g. the flagged build
```

- List available versions: `./gradlew printProductsReleases`
- Run: `./gradlew verifyPlugin` → reports under `build/reports/pluginVerifier/<IDE>/`
- Revert the temporary target afterwards (`git checkout build.gradle.kts`); keep only the oldest
  supported (IC-2024.2.6) and the latest stable as standing targets.
- DSL note: add targets with `create(IntelliJPlatformType, "<version>")`, not `ide(...)`.

---

## Releasing a fix

1. Bump `version` in `gradle.properties` and add a new section to `CHANGELOG.md` (change notes are
   injected into the plugin descriptor automatically).
2. Branch → PR → merge.
3. Tag and create a GitHub release (attach the built zip).
4. Publish to the Marketplace with one command — `publishPlugin` reads
   `intellijPlatformPublishingToken` from `~/.gradle/gradle.properties` (never committed):

   ```bash
   ./gradlew publishPlugin
   ```

   Only the very first submission required the web UI; updates are CLI-only from here.
