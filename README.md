# Sound IDer

Sound IDer is a diagnostic RuneLite plugin for identifying live sound events. It displays raw sound IDs, event types and, where available, the actor associated with an event.

## Features

- Live Sound Effect, Area Sound and Ambient Sound IDs
- Start / Stop logging
- Clear history
- Pop-out resizable window
- Separate Display and Consume toggles for Effects, Area and Ambient
- ID-only mode by default
- Optional source information for NPCs, players and environment sounds
- Best-effort actor source attribution when RuneLite does not provide one directly
- Optional `[E]`, `[A]`, `[M]` type prefixes
- Out-of-range area sound detection
- Filter/search and copy actions
- Shared history between side panel and pop-out
- Optional local JSONL event export for debugging and analysis

## JSONL export

Sound IDer can optionally export detected sound events to a local JSONL file for debugging and analysis.

The file is stored at:

`.runelite/plugin-data/sound-ider/events.jsonl`

Each line contains the sound ID, event type and source information where available.

The export mirrors the live feed and does not make network requests, launch external programs or read data back from companion applications.

## Diagnostic scope

Sound IDer reports factual sound events that have already occurred. It does not assign gameplay meaning to individual sound IDs.

The plugin does not provide:

- ID-specific colours or mechanic labels
- Configurable mechanic alerts
- Replacement sounds
- Attack or mechanic predictions
- Prayer recommendations
- Attack counters
- Tile guidance
- NPC targeting or focus information
- Mouse or keyboard input
- Any other form of gameplay automation

Source attribution is best-effort only. If RuneLite does not provide a source directly, Sound IDer may use recent actor activity or an unambiguous actor on the sound tile to identify a likely source. If it cannot do so safely, the source remains `Unknown`.
