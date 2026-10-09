# Sound IDer

Sound IDer is a diagnostic RuneLite plugin for identifying live sound events. It displays raw sound IDs, event types and, where available, the actor associated with an event.

Built from the RuneLite `example-plugin` project structure and targeted at Java 11.

## Features

- Live Sound Effect, Area Sound and Ambient Sound IDs
- Start / Stop logging
- Clear history
- Pop-out resizable window
- Separate Display and Consume toggles for Effects, Area and Ambient
- ID-only mode by default
- `ID Source` toggle for NPC/player/environment source information
- NPC ID when RuneLite supplies an NPC source
- Best-effort actor source attribution when RuneLite does not provide one directly
- Optional `[E]`, `[A]`, `[M]` type prefixes
- Sound Swapper-style out-of-range area detection
- Filter/search and copy actions
- Shared history between side panel and pop-out
- Optional local JSONL event export for debugging and analysis

## Diagnostic scope

Sound IDer reports factual sound events that have already occurred. It does not assign gameplay meaning to individual sound IDs.

The plugin does **not** provide:

- ID-specific colours or mechanic labels
- Configurable mechanic alerts
- Replacement sounds
- Attack or mechanic predictions
- Prayer recommendations
- Attack counters
- Tile guidance
- NPC targeting/focus information
- Mouse or keyboard input
- Any other form of gameplay automation

The optional JSONL export contains the same raw event information shown by the plugin. It is written locally for debugging and analysis. Sound IDer does not open sockets, make network requests, launch external programs or read data back from companion applications.

## Development run

```bash
./gradlew run
```

The development launcher follows the standard RuneLite `example-plugin` pattern and does not launch or relaunch external processes. On macOS, the Gradle `run` task supplies RuneLite's required `java.desktop/com.apple.eawt` module access flags directly to the JVM.

If you launch the built development JAR directly on macOS rather than using `./gradlew run`, use:

```bash
java --add-exports=java.desktop/com.apple.eawt=ALL-UNNAMED \
  --add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED \
  -ea -jar build/libs/sound-ider-1.1.5-all.jar
```

## Build

```bash
./gradlew clean shadowJar
```

Output:

```text
build/libs/sound-ider-1.1.5-all.jar
```

## JSONL export

Enable **Export JSONL** in Sound IDer settings. Displayed sound events are appended immediately on a dedicated background writer thread to:

```text
.runelite/plugin-data/sound-ider/events.jsonl
```

All plugin file access uses RuneLite's `Filepath` utility. Each line is a standalone JSON object, for example:

```json
{"sequence":1,"timestamp":1780912345678,"soundId":3812,"type":"AREA","source":"Guard","sourceId":3010,"sourceType":"NPC","audible":true}
```

The export mirrors the live feed: Start/Stop, Display type toggles and the inaudible-area setting determine which events are written.

## Source fallback

For area sounds where RuneLite does not provide an actor source, Sound IDer performs one lightweight fallback check against the current top-level world view. If exactly one loaded NPC or player is standing on the sound event tile, that actor's name is used as `source`. If the tile is empty or contains more than one possible actor, `source` remains `Unknown`.

### Effect source fallback

For ordinary effect sounds where RuneLite does not provide an actor source, Sound IDer keeps a very small rolling list of actors that have just fired `AnimationChanged`. It uses that history only after the sound occurs: the current interaction target is preferred when it is one of those recently animated actors, otherwise a source is used only when the recent actor is unambiguous. If neither condition is met, `source` remains `Unknown`.

This attribution is diagnostic only. It does not predict attacks, mechanics or future state.
