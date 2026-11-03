# Release

How Grand.Colonies is built, packaged and published. Nothing here runs unless a release is asked for: no version bumps, rebuilt jars, tags or published releases for ordinary work. Changelog entry rules are in [CHANGELOG.md](CHANGELOG.md); code owners are in [ARCHITECTURE.md](ARCHITECTURE.md).

## Pieces

| Piece | Path | Role |
|---|---|---|
| Changelog | `Changelog` | Source of the version and the release notes. The newest version heading is the release |
| Mod metadata | `mod_info.json` | Version stamped into the ZIP's copy; loads `jars/GrandColonies.jar` |
| Version file | `GrandColonies.version` | Online Version Checker file on `master` (forum thread `20986`), registered in `data/config/version/version_files.csv`; stamped at packaging, committed back after a release |
| Release builder | `src/grandcolonies/tools/ReleaseBuild.java` | `build` and `package`, run with `java <file>` |
| Builder checks | `src/grandcolonies/tools/ReleaseBuildChecks.java` | Headless checks of changelog parsing, stamping, file filtering and asset references |
| Build libraries | `lib/` | Game jars from `0.98a-RC8` (`starfarer.api.jar`, `starfarer_obf.jar`, `fs.common_obf.jar`, `lwjgl.jar`, `lwjgl_util.jar`, `json.jar`, `log4j-1.2.9.jar`), `LazyLib.jar` and `Kotlin-Runtime.jar` (LazyLib 3.0.0, Kotlin 2.1.20) and `LunaLib.jar` (2.0.1), copied from Industrial.Evolution's `lib/` |
| Kotlin compiler | not in the repository | Kotlin `2.1.20`, the version of `lib/Kotlin-Runtime.jar`. The workflow downloads it; locally pass its folder to `build` |
| Release jar | `jars/release/GrandColonies.jar` | Built by `build`, gitignored, runtime classes only; shipped as `jars/GrandColonies.jar` |
| Release workflow | `.github/workflows/release.yml` | Manual only: preview without a version, publish with one |

The IDE artifact (`jars/GrandColonies.jar`, gitignored) is separate from the release jar, so an IDE build never replaces it.

The release jar is built in CI from `lib/` and never committed, as in Tidy.Panels. Do not add the house template's committed jar and source hash check.

## Versions

Versions use `major.minor.letter`: the first number for a major release, the second for changes players will notice, the letter for fixes and small patches. Reset the lower parts when a higher one changes. The current public version is `2.1.b`. The next change starts a `2.1.c - UNRELEASED` section unless the user picks another version.

## Build the release jar

From the repository root, with JDK 17:

```text
java src/grandcolonies/tools/ReleaseBuild.java build <kotlin compiler folder>
```

- `<kotlin compiler folder>` holds the Kotlin `2.1.20` compiler jars: the `lib/` folder of `kotlin-compiler-2.1.20.zip` from the Kotlin GitHub release, or the Maven Central jars the workflow's "Download Kotlin compiler" step lists with their SHA-256. `build` refuses any other compiler version.
- Compiles every `.kt` and `.java` file under `src/`: first `kotlinc -jvm-target 17` over all of them (Kotlin and Java call each other), then `javac --release 17` over the Java files with the Kotlin classes on the classpath. Both run against the jars in `lib/`; no game install is needed.
- `starfarer_obf.jar` and `fs.common_obf.jar` are required: `IndustryPanelReplacer` imports `CampaignState`, `IndustryListPanel`, `StandardTooltipV2`, `StandardTooltipV2Expandable` and `AppDriver`.
- Builds into a fresh `out/release-build-*`, keeps only runtime entries (classes under `grandcolonies/` except `grandcolonies/tools/`, plus `META-INF/Grand.Colonies.kotlin_module`), checks every class is Java 17 and `grandcolonies/plugins/ModPlugin.class` is present, and replaces the release jar only after a clean compile.
- The Kotlin runtime is not bundled: LazyLib provides it in game.
- After a game, LazyLib or LunaLib update, copy the new jars into `lib/` and update the versions in the table above. A LazyLib update that changes the Kotlin runtime also changes `KOTLIN` in `ReleaseBuild` and the jar list and hashes in `release.yml`.

## Package

```text
java src/grandcolonies/tools/ReleaseBuild.java package          # preview
java src/grandcolonies/tools/ReleaseBuild.java package 2.1.c    # release
```

- Needs a release jar from `build`.
- Preview packages the newest changelog section.
- Release requires the version (`2.1.c`, no `v`) to equal the newest changelog heading, and that section must not be `UNRELEASED`.
- Ships Git-tracked files only: `mod_info.json`, `GrandColonies.version`, `Changelog`, `_grandColonies.png` (LunaLib and settings icon), `CC-license.png`, license and credit files, `data/` (`csv json shader ship wpn proj system variant skin`), `graphics/` (`png jpg jpeg`), `sounds/` (`ogg wav`) and the release jar as `jars/GrandColonies.jar`. Source, `lib/`, docs, IDE, agent and workflow files, and any path under a dot, `unused`, `unedited`, `tools` or `docs` directory are left out.
- Fails if a tracked `graphics/`, `sounds/` or `data/` path named in `data/` is not packaged.
- Output in `out/release/`: `Grand.Colonies-<version>.zip` (one `Grand.Colonies/` folder, sorted, fixed timestamps), `release-notes.md`, the stamped version file and `version.txt`.

## Publish

1. Finish the newest `Changelog` section and remove `UNRELEASED`.
2. Run `build` and `package <version>` locally as the preview check, then merge to `master`.
3. Start the Release workflow on `master` with `version` set: Actions tab, Release, Run workflow, or `gh workflow run release.yml --ref master -f version=<version>`. GitHub lists a manual workflow only once `release.yml` is on the default branch.
4. The workflow runs the builder checks, downloads and checks the Kotlin compiler, builds the jar, packages, creates the release and its tag on the packaged commit, then commits the stamped `GrandColonies.version` to `master`.
5. Check the release ZIP and notes, and that `master`'s version file names the new version.

The workflow has no push, pull request, tag or schedule trigger. Without a version it only uploads a preview artifact. It refuses to overwrite an existing release, and run on an existing version tag it publishes that tag. It compiles from `lib/` and downloads only the pinned Kotlin compiler.

## Maintenance

Update this guide when `ReleaseBuild`, `ReleaseBuildChecks`, `.github/workflows/release.yml`, the version file or the jars in `lib/` change.
