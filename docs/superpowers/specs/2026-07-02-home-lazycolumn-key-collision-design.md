# Home screen crash — duplicate LazyColumn keys

## Problem

The app crashes reproducibly in this sequence:
1. Create a new catalogue item, or validate a portion as "consumed today" from the home screen.
2. Scroll the home screen.
3. The app closes.

## Root cause

`HomeScreen.kt` renders a single `LazyColumn` containing two separate lists, each with an explicit `key`:

```kotlin
items(filteredItems, key = { it.id })        // NutItem.id
...
items(dailyEntries, key = { it.entry.id })   // DailyEntry.id
```

`NutItem.id` and `DailyEntry.id` are autoIncrement primary keys from two independent Room tables (`nut_items` and `daily_entries`), so both sequences start at 1. Compose requires keys to be unique across the *entire* `LazyColumn`, not just within each `items(...)` block — as soon as a catalogue item and a daily entry share the same numeric id (near-guaranteed on first use: the first item created gets id 1, the first entry logged also gets id 1), the list holds two rows with the same key.

Compose Foundation's key-uniqueness check (`NearestRangeKeyIndexMap`) only validates keys near the current viewport rather than the full list up front, for performance. This is why the crash doesn't happen at the moment the colliding row is added, but only once scrolling brings both colliding rows into the same computed range, throwing `IllegalArgumentException: Key ... was already used`.

No other screen in the app (`CalendarScreen`) uses explicit `key` values in its lazy layouts, so this is the only place affected.

## Fix

Prefix each key with a string discriminator identifying its source list, so catalogue items and daily entries can never collide regardless of their underlying numeric id:

```kotlin
items(filteredItems, key = { "item_${it.id}" })
...
items(dailyEntries, key = { "entry_${it.entry.id}" })
```

This is a two-line change confined to `app/src/main/java/com/alexm/mynut/ui/home/HomeScreen.kt`, with no behavioral or visual impact.

## Alternatives considered

- **Negative-offset numeric keys** (e.g. `-it.entry.id`): works but less explicit/readable, and fragile if an id of 0 is ever introduced.
- **Unify both sections into one list via a sealed class** (`CatalogueRow` / `EntryRow`): structurally "cleaner" but a disproportionate refactor for this bug — the two sections have distinct headers, a divider, and different row composables, so keeping them as separate `items()` blocks is the right shape for this screen.

## Scope

In scope:
- `HomeScreen.kt`: update the two `key = { ... }` lambdas as above.

Out of scope:
- No changes to data layer, ViewModels, or other screens.
- No new automated Compose UI test is added (the project currently has no Compose UI test infrastructure — only JVM unit tests and Room `androidTest` DAO tests). Verification is manual.

## Verification plan

Manual repro/regression check on an emulator or device:
1. Launch the app with an empty catalogue.
2. Create a new catalogue item (form → save) — confirm no crash, return to home.
3. Select that item and validate a portion as consumed today — confirm no crash.
4. Scroll the home screen up and down across both the catalogue and "consumed today" sections — confirm no crash.
5. Repeat after adding a few more items/entries so both sections have several rows, to further stress-test key uniqueness while scrolling.
