# Sound IDer

This branch is a JSON-only test version of Sound IDer.

Sound IDer logs raw RuneLite sound events to a local JSONL file for later inspection. It does not display sound IDs in the RuneLite side panel or in a pop-out window.

## What is logged

- Sound Effect events
- Area Sound events
- Ambient Sound events
- Sound ID
- Event type
- Timestamp and sequence number
- Best-effort actor/source information where available
- Whether an area sound was audible

Source attribution is best-effort only. If RuneLite does not provide a source directly, Sound IDer may use recent actor activity or an unambiguous actor on the sound tile. If it cannot determine a source safely, the source remains `Unknown`.

## JSONL file

Events are appended to:

`~/.runelite/plugin-data/sound-ider/events.jsonl`

Example:

```json
{"sequence":1,"timestamp":1780912345678,"soundId":3812,"type":"AREA","source":"Guard","sourceId":3010,"sourceType":"NPC","audible":true}
```

To watch the test output live from a terminal:

```bash
tail -f ~/.runelite/plugin-data/sound-ider/events.jsonl
```

The plugin itself does not display the IDs in RuneLite. The JSONL file is intended for later inspection and diagnostic cataloguing.
