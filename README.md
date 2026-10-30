# Donut Spawner Helper (Fabric, Minecraft Java 1.21.11)

A **client-side, read-only** helper for stacked skeleton spawners on DonutSMP.
It never sends its own packets, never auto-clicks, never cancels or changes your interactions.

## What it does

| Feature | Source of truth |
|---|---|
| Prints exactly `Added another spawner into the stack.` when the **server** confirms a stack add ("You just added another spawner! There are now N spawners in the stack.") | Server chat message |
| Shows `Spawner stack: N` on the action bar (keeps chat clean) | Same server message |
| Skips duplicates (same count < 400 ms) and rate-limits printing (250 ms) | Local logic |
| Nothing is printed if the server doesn't send the confirmation (failed add) | Server chat message |
| Animated panel beside the server's "N Skeleton Spawners" menu: pink spinning cage, yellow skeleton, particles | Count and mob from the menu title; animation is **visual only** (labelled in the panel) |
| HUD when your crosshair is on a spawner: mob name, last confirmed stack for that spawner | Block-entity data the server synced to you |
| HUD note on yellow stained glass | Plain vanilla block check |
| Spawn egg on a spawner: reports "mob type is now X (server-confirmed)" only if the synced spawner data actually changes | Block-entity data |
| `/spawnerui` opens a preview of the panel | Client only |

## What it can NOT do (limitations)

* **Yellow stained glass cannot be turned into a spawner by a client mod.** Block types live on the
  server; showing a spawner there would be a fake visual, which you asked me not to build. The mod only
  recognises the glass and says so. Any real conversion would need a server plugin or an in-game mechanic
  DonutSMP provides.
* **Spawn-egg mob changes** are decided by the server. The mod just observes and reports the result.
  If DonutSMP handles eggs through a menu or hides spawner data, you'll see the "no change reported" note.
* **Stack count** is plugin data, not vanilla block data. The mod only knows it from the server's messages
  and menu titles, so after a restart it shows "unknown" until you add a spawner or open the menu.
* **Your screenshots**: I recreated the *look* (dark card, yellow title, pink/yellow accents) with Minecraft's
  default font. I can't reproduce the server's custom font, custom item icons, or a resource-pack texture
  of the in-world spawner. The spinning spawner/skeleton are procedurally drawn (no texture files).
* **Server rules**: I could not verify DonutSMP's current mod policy. Display-only mods are usually
  tolerated, but check their rules/Discord before using any mod there. I found no official statement either way.

## Versions (Minecraft 1.21.11)

| Component | Version | Note |
|---|---|---|
| Java | 21 | |
| Gradle | 9.2.1 | |
| Fabric Loom | 1.14-SNAPSHOT | Fabric's recommended Loom for 1.21.11 |
| Fabric Loader | 0.18.4 | |
| Fabric API | 0.141.1+1.21.11 | a newer 0.14x+1.21.11 build may exist; bump `fabric_version` if you like |
| Mappings | Mojang official | Yarn ends at 1.21.11 |

## Build

1. Install JDK 21 and Gradle 9.2.1 (or run `gradle wrapper --gradle-version 9.2.1` once to create `gradlew`).
2. `gradle build` (or `./gradlew build`)
3. The mod is `build/libs/donut-spawner-helper-1.0.0.jar` (not the `-sources` jar).

## Install

1. Install Fabric Loader 0.18.4+ for 1.21.11 and Fabric API 0.141.1+1.21.11 (or newer for 1.21.11).
2. Put the jar in `.minecraft/mods/`.

## Test on DonutSMP

1. Join and look at a spawner: the HUD shows the mob (if the server syncs it) or "no data from server".
2. Right-click the spawner holding another spawner. If the server accepts, you get the green
   `Added another spawner into the stack.` line once, plus the action-bar count.
3. Spam right-click quickly: no repeated lines for the same count.
4. Try an add that the server rejects: you should get nothing from this mod.
5. Open the spawner menu (title like "8 Skeleton Spawners"): the animated panel appears to the side
   (needs a window wide enough; lower GUI scale if it's missing).
6. Hold a spawn egg and right-click a spawner: you get a confirmation only if the spawner data changes.
7. `/spawnerui` shows the preview any time.

## Status of verification

* `StackMessageParser` and `StackTracker` (pure Java) compiled and passed 5 unit tests here.
* `SpawnerArt` (pure Java renderer) was compiled and rendered offline to `docs/art-preview-offline-render.png`.
* The Minecraft-facing code (entrypoint, HUD, screens, `SpawnerInspector`) was **not compiled or run**: the build
  sandbox had no network, so Loom could not download Minecraft/Fabric. It is written against 1.21.11 Mojang
  names, but a few APIs are the likeliest to need a one-line fix on first build:
  `HudElementRegistry.attachElementAfter`, `ScreenEvents.afterRender`, `BaseSpawner#getOrCreateDisplayEntity`,
  `GuiGraphics#drawCenteredString`. If the compiler complains, send me the error and I'll adjust.
