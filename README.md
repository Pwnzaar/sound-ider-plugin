# Sound IDer

Sound IDer identifies RuneLite sound IDs in-game for later inspection and diagnostic cataloguing.

## What is identified

- Sound Effect events
- Area Sound events
- Ambient Sound events
- Sound ID
- Event type
- Timestamp and sequence number
- Best-effort actor/source information where available
- Whether an area sound was audible

Source attribution is best-effort only. If RuneLite does not provide a source directly, Sound IDer may use recent actor activity or an unambiguous actor on the sound tile. If it cannot determine a source safely, the source remains `Unknown`.
