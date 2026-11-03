# Changelog guide

How agents write entries in the root `Changelog`. Read it for every implementation task. Version numbers, release steps and how the release tooling reads the file are in [RELEASE.md](RELEASE.md). Where this guide and the `house-style` skill's "Changelog entries" section differ on wording, the skill wins.

## When to write an entry

- Every implementation task adds or amends its entries in the current unreleased section before merge, in the same commit as the change.
- Read-only work and documentation-only changes add nothing. Say why in the PR when a change needs no entry.
- Check whether the feature has been released: look for it under every version heading without `UNRELEASED`.
  - Not released yet: edit its `Added -` entry so it describes the feature as it now stands. No `Fixed -`, `Adjusted -` or `Removed -` line for it.
  - Released: the change gets its own entry.
- Record only completed changes from the actual diff. Never list plans or unfinished work.

## File layout

`Changelog` at the repository root, plain text, no extension. This file predates the house layout: it has no `Known issues` or `Notes` sections, only versions newest first with a blank line between them. Keep that shape; do not add the house header sections unless asked.

```text
2.1.c - UNRELEASED
Adjusted - Scroll bar keeps its position after building something

2.1.b
Adjusted - 0.98a Compatibility
Fixed - Add Industry button now functions correctly
```

- Version heading: the version alone on its line, optionally followed by ` - UNRELEASED`. No `v` prefix, no date, no Markdown. The release tooling reads these headings, so keep them in this form.
- If no unreleased section exists, add one above the newest version. The next one is `2.1.c` unless the user picks another; do not invent a new version per commit.
- Entries: one change per line, starting `Added - `, `Adjusted - `, `Fixed - ` or `Removed - `, in that order.
- A `Note - ` line directly below an entry explains a limit or something the player must do.
- Older sections prefix entries with the mode (`Fixed - Legacy - Crash when abandoning a colony`, `Added - Default - ...`). Use the same `Legacy - ` or `Default - ` prefix when a change only affects one mode.
- Use a `Development` category line for useful changes to the build or authoring tools. Do not present a refactor or a documentation edit as a gameplay change.

## Wording

Entries are player-facing, in the user's change note voice (`house-style`, "Changelog entries").

- Short, under 80 characters. Capital letter after the dash, no full stop on a one-clause line.
- Say what changed and name the screen or feature so a player can find it (`colony screen`, `Cramped Infrastructure`, `page toggle key`).
- For a fix, describe what the player saw, not the call chain: `Fixed - Crash when opening a colony with an empty build queue`, `Fixed - Scroll bar no longer resets after building something`.
- Numbers as old `->` new with the unit: `Adjusted - Default penalty threshold 12 -> 15 buildings`.
- No semicolons, no curly quotes, no hyphen as a dash inside the text.
- No agent or model names, links, validation logs or commit hashes. No first person, jokes or hedges about whether a fix works; untested status goes in the PR.
- Keep the user's own lines verbatim, including their commentary. Credits name real people in the user's forms (`Note - Both of these were fixed by Float, who worked on the mod on his own. Many thanks!`).

## Amending versus adding

- Combine entries when several edits fix the same player-visible problem.
- An unreleased feature removed again before release loses its `Added -` entry; it gets no `Removed -` line.
- Never repeat a change once per file, step or PR.

## Maintenance

Update this guide when the `Changelog` layout or entry format changes. Release mechanics belong in [RELEASE.md](RELEASE.md).
