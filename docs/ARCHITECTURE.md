# Architecture

Technical routing for the current implementation, checked against game `0.98a-RC8`, LazyLib 3.0.0 (Kotlin 2.1.20) and LunaLib 2.0.1. Code paths below are relative to `src/grandcolonies/`; data paths are repository-relative. Search the named owner before adding another implementation.

| Reference | Scope |
|---|---|
| [CLAUDE.md](../CLAUDE.md) | Workflow, build gate and document upkeep |
| [RELEASE.md](RELEASE.md) | Version numbers, release builder, package contents and publishing |
| [CHANGELOG.md](CHANGELOG.md) | Change-note format and upkeep for every implementation task |

## Start here

| Change / symptom | Route |
|---|---|
| Industry list not replaced, buildings missing, or crash on the colony screen after a game update | `IndustryPanelReplacer.advance` reflection chain -> [industry panel contracts](#industry-panel) |
| Scroll position jumps when a building is added or removed | `IndustryPanelReplacer.advance` (`previousOffset`, `rowsLastFrame`, `rowsCurrentFrame`, `widgetSizeLastFrame`) |
| Build button or the credits / max industries labels missing | `IndustryPanelReplacer.advance` (children with `setEnabled` / `createMaxIndustriesLabel`) |
| Removing or reordering construction queue items does nothing | `IndustryPanelReplacer.advance` -> `ReflectionUtils.set("widgets", ...)` |
| Wrong tooltip on a building or queued item | `IndustryPanelReplacer.getAllWidgets` (`addTooltipParamClass`, `createIndustryTooltip`) |
| Upkeep penalty wrong or icon shown when it should not be | `CrampedConditionApplicator` / `PlayerOpenedMarketListener.setupImpl` -> `CrampedCondition.apply -> getColonyBuildingAmt -> applyOverCapPenalty`; [cramped contracts](#cramped-infrastructure) |
| Legacy: page key does nothing or shows the wrong page | `PlayerOpenedMarketListener.setupImpl -> ButtonPressListener.processCampaignInputPreCore -> PageMemory.showNext -> update -> displayPage -> Page.display` |
| Legacy: buildings on the wrong page or a page too full | `PageMemory.createOrDeletePageIfNeeded -> autoSortIndustriesToPages`; `Page.full` |
| Legacy: "Move to next Page" option missing or greyed out | `PageMovingOptionProvider.getIndustryOptions` (`isUnsuitable`, next page `full`) |
| Legacy: page icon or tooltip wrong | `PageNumCondition.getIconName` / `createTooltipAfterDescription`; `data/config/settings.json` `graphics.GrandColonies` |
| Switching legacy mode off leaves page data behind | `ModPlugin.onGameLoad -> cleanupAll -> cleanupImpl` |
| Setting has no effect | [Settings](#settings) |

## Registration and lifecycle

| Registry / hook | Owners |
|---|---|
| `mod_info.json` | Mod id `GrandColonies`, `modPlugin` `grandcolonies.plugins.ModPlugin`, jar `jars/GrandColonies.jar`; dependencies LazyLib (`lw_lazylib`, also the Kotlin runtime) and LunaLib (`lunalib`). Not `utility`: it stores conditions and memory in saves |
| `ModPlugin.onGameLoad()` | The only hook. Registers everything below as transient, so nothing is registered twice across loads |
| `data/campaign/market_conditions.csv` | `GrandColonies_pageCond` (`conditions.PageNumCondition`, order 3000) and `GrandColonies_CrampedCondition` (`conditions.CrampedCondition`, order 1), both `decivRemove` |
| `data/config/settings.json` | Sprite category `GrandColonies`: page icons `00` to `09`, `warn`, `warn_low`, `icon` |
| `data/config/LunaSettings.csv`, `data/config/LunaSettingsConfig.json` | LunaLib settings for mod id `GrandColonies` and its icon `_grandColonies.png` |
| `data/config/version/version_files.csv` | Version Checker entry for `GrandColonies.version` |

Load order in `ModPlugin.onGameLoad`, chosen once per game start by `LEGACY_MODE`:

- Scrolling (default): `cleanupAll` (only when `$GrandColonies_legacy` is missing or true) -> `addTransientListener(CrampedConditionApplicator)` -> `addTransientScript(IndustryPanelReplacer)`.
- Legacy: set `$GrandColonies_legacy` -> `PageMovingOptionProvider.register` (transient, `hasListenerOfClass` guard) -> `addTransientListener(PlayerOpenedMarketListener(false))` -> `addTransientScript(IndustryPanelReplacer)`.

### Settings

LunaLib mod id `GrandColonies`; `data/config/LunaSettings.csv` owns the ids, defaults, bounds and help text. There is no settings class or listener.

| Field | Type, default | Read by |
|---|---|---|
| `GrandColonies_EditEverything` | Boolean, off | `IndustryPanelReplacer.advance` (build button `setEnabled`), every paused frame |
| `GrandColonies_AddPenalty` | Boolean, on | `CrampedCondition.apply` and `showIcon`, every call |
| `GrandColonies_PenaltyThreshold` | Int, 12 (1 to 50) | `CrampedCondition.applyOverCapPenalty`, every apply |
| `GrandColonies_UpkeepPenaltyPerBuilding` | Double, 1.1 (1 to 2) | `CrampedCondition.applyOverCapPenalty`, every apply |
| `GrandColonies_LegacyMode` | Boolean, off | `ModPlugin.LEGACY_MODE`, static final |
| `GrandColonies_TogglePageButton` | String, `q` | `ModPlugin.TARGET_CHAR` (first character), static final |

`LEGACY_MODE` and `TARGET_CHAR` are read when `ModPlugin` loads, so changing them needs a game restart. `LunaSettings` loads its file on first use (`LunaSettingsLoader.load` inside every getter), which is why the static reads work at class load.

### Build and checks

`tools/ReleaseBuild.java` (`build`, `package`) and `tools/ReleaseBuildChecks.java` compile against `lib/` and never ship in the release jar; they, `lib/` and `.github/workflows/release.yml` are owned by [RELEASE.md](RELEASE.md). There are no other checks.

## Save data

Everything here is written into the save by the game's serializer. Class names, field names and keys must stay as they are ([CLAUDE.md](../CLAUDE.md#make-and-record-changes)).

| Key / object | Where | Owner |
|---|---|---|
| `$GrandColoniesMarketPageMemory` | Market memory, a `memory.PageMemory` holding `memory.Page` objects (industry ids per page, `currentPage`, `isInitialized`) | `PageMemory.get`, legacy only; removed by `PageMemory.clear` |
| `$GrandColonies_excl` | Market memory, `Set<String>` of industries already hidden by something else | `PageMemory.updatePermaInvisibleIndustries`; unset on market close and by `cleanupImpl` |
| `$GrandColonies_pageCond` | Market memory, the page condition's token | `PageNumCondition.init`; read by `getCondition` |
| `$GrandColonies_docked` | Market memory, true while the player is docked there | `PlayerOpenedMarketListener.setDocked` |
| `$GrandColonies_legacy` | Sector memory, set to true by a legacy mode load and never set false | `ModPlugin.onGameLoad` |
| `GrandColonies_pageCond`, `GrandColonies_CrampedCondition` | Market conditions with their plugin objects (`PageNumCondition`, `CrampedCondition` fields) | `PageNumCondition.init`, `CrampedCondition.init`, `CrampedConditionApplicator` |
| Hidden industries | `BaseIndustry.hiddenOverride`, set through `Industry.setHidden` | `Page.display`; `PlayerOpenedMarketListener.reportPlayerClosedMarket` shows everything again on close |

Removing the mod from a save that has these conditions is not supported (not checked in game).

## Source owners

Use `rg --files src/grandcolonies` for the complete inventory.

### `plugins`

| File | Owner / connection |
|---|---|
| `plugins/ModPlugin.java` | Mode choice and registration in `onGameLoad`; the shared ids and memory keys; `cleanupAll` / `cleanupImpl`, which remove the page condition and page memory from markets |

### `listeners`

| File | Owner / connection |
|---|---|
| `listeners/IndustryPanelReplacer.kt` | The scrolling industry panel, both modes. Every paused frame finds the colony management panel by reflection and replaces the vanilla `IndustryListPanel` with its own scroller of widgets |
| `listeners/CrampedConditionApplicator.java` | Scrolling mode: adds `GrandColonies_CrampedCondition` to a player market when the player opens it |
| `listeners/PlayerOpenedMarketListener.java` | Legacy mode: sets up pages, the page condition, the cramped condition and the key listener when a player market opens; shows all buildings and removes the key listener when it closes |
| `listeners/ButtonPressListener.java` | Legacy mode: the page key (`TARGET_CHAR`) calls `PageMemory.showNext`, then presses `M` through `java.awt.Robot` so the screen rebuilds; `trigger` presses the page key itself |
| `listeners/PageMovingOptionProvider.java` | Legacy mode: the "Move to next Page" industry option |

### `memory`

| File | Owner / connection |
|---|---|
| `memory/PageMemory.java` | Pages of one market: first sort, page count, auto sort into free slots, which page shows |
| `memory/Page.java` | Industry ids on one page; `display` hides or shows them and drops ids the market no longer has |

### `conditions`

| File | Owner / connection |
|---|---|
| `conditions/CrampedCondition.java` | The upkeep penalty and its tooltip |
| `conditions/PageNumCondition.java` | Legacy mode: page icon and tooltip, the count of buildings that cannot be hidden |

### `helper`

| File | Owner / connection |
|---|---|
| `helper/ReflectionUtils.kt` | `MethodHandle` based field and method access, used only by `IndustryPanelReplacer` |

## Contracts

### Industry panel

- Vanilla `IndustryListPanel.sizeChanged` lays out at most 12 widgets (4 columns, 3 rows) from the non-hidden industries sorted by `getSpec().getOrder()`, then the construction queue; the rest are never shown. `IndustryPanelReplacer.getAllWidgets` rebuilds the full list the same way (plus `distinctBy` spec id).
- No API callback covers the management panel being built or rebuilt: `CoreUITabListener.reportAboutToOpenCoreTab` only reports a tab opening, and the panel is recreated on economy updates, builds and queue changes. So `IndustryPanelReplacer` checks every frame while the sector is paused (`runWhilePaused`), and returns at once while unpaused. In legacy mode it also returns while a dialog shows or no core tab is open, so docked colonies keep vanilla's 12 slot panel with pages and remote colonies get the scroller.
- Reflection chain (0.98a-RC8): `CampaignState` (`AppDriver.getInstance().getCurrentState()`) -> `getEncounterDialog().getCoreUI()` or `getCore()` -> `getCurrentTab()` -> child with `getOutpostPanelParams` -> child with `showOverview` -> child with `recreateWithEconUpdate` (the management panel) -> child that is an `IndustryListPanel`. Fields `market` and `widgets` on `IndustryListPanel`. The build button is the panel child with `setEnabled`, the two labels the children with `createMaxIndustriesLabel`.
- Widgets are created with the vanilla widget class's constructors `(MarketAPI, Industry, IndustryListPanel)` and `(MarketAPI, Industry, IndustryListPanel, int)`; tooltips through `StandardTooltipV2Expandable.addTooltipRight` with the parameter type `widgetClass.superclass.superclass.interfaces[3]`. That index depends on the order the game class declares its interfaces and breaks silently on a reshuffle.
- `widgets` on the vanilla panel is set to the new widget list: queue removal and swapping compare against that list.
- The code imports `CampaignState`, `IndustryListPanel`, `StandardTooltipV2`, `StandardTooltipV2Expandable` (`starfarer_obf.jar`) and `AppDriver` (`fs.common_obf.jar`). These are not obfuscated names, but they are internal classes that a game update can change.
- The game's classloader refuses `java.lang.reflect.Field` and `Method` by name in mod code; `ReflectionUtils` loads them with `Class.forName(..., Class::class.java.classLoader)` and calls them through `MethodHandles`.

### Legacy pages

- `Industry.setHidden(true)` hides a building from the panel; `BaseIndustry.setHidden(false)` clears the override. Pages work by hiding every building not on the shown page.
- A page holds 12 (`Page.full` above 11). `PageMemory.createOrDeletePageIfNeeded` keeps at least 2 pages, adds one when visible buildings reach `(pages - 1) * 12 + 8`, and drops the last one below `(pages - 2) * 12 + 8`.
- Buildings hidden by something else are recorded in `$GrandColonies_excl` and left alone.
- `ButtonPressListener` is added per opened market and removed by class on close; the whole flow depends on `reportPlayerClosedMarket` firing, which shows every building again.

### Cramped infrastructure

- Upkeep multiplier `GrandColonies_UpkeepPenaltyPerBuilding ^ (buildings - GrandColonies_PenaltyThreshold)` on `market.getUpkeepMult()`, source `getModId()`, only above the threshold. Buildings in the construction queue are not counted.
- The condition is only added when the player opens a player market, so a colony the player never opened has no penalty.

## Dead or dormant

| Component | State |
|---|---|
| `PageNumCondition.Page` enum | Unused |
| `IndustryPanelReplacer.Companion.reset` | Set in `init`, never read |
| `ReflectionUtils.hasVariableOfName`, `instantiate`, `invokeExact` | Unused |
| `PageMemory.getPagesCopy` | Unused |
| `PageMemory` line 15 TODO | Manual page assignment, not implemented |
