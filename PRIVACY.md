# Privacy and network behavior

MapKluss Companion is a client-side Minecraft mod. It does not require a server-side plugin and does not give a Minecraft server access to a player's MapKluss account.

## Data stored locally

Depending on the features used, Companion may store the following inside the Minecraft instance:

- the MapKluss account session used by device login;
- selected language and service routing configuration;
- downloaded artifact metadata and installed-file indexes;
- map IDs, colour fingerprints, tile bindings, and compressed map previews;
- Lens preferences;
- Two-layer plan files, anchor, selected map ID, and build progress;
- scan and build-tracker history.

These files stay in the instance's `config`, `schematics`, or related local folders. Anyone with access to the Minecraft instance files may be able to read them. Signing out clears the active MapKluss session through the mod interface.

## Network requests

Companion connects to MapKluss services for actions initiated by the player or required by an active feature:

- device authorization and account session validation;
- Cloud library, art, version, collection, favorite, and artifact operations;
- signed artifact and preview downloads;
- map-scan uploads explicitly started by the player;
- build-tracker synchronization;
- Lens sessions, presence, revisions, previews, and Realtime wakeups while Lens is active;
- gateway readiness checks with direct service fallback.

Lens performs no background network or disk work while it is inactive. Active Lens sessions use a bounded heartbeat, Realtime-first updates, and a slower recovery poll.

## Data that remains local

Companion does not upload Two-layer world coordinates, anchors, demolition progress, AutoFrame bindings, local map-preview caches, or ordinary inventory contents. It does not place or break blocks automatically.

## Account deletion

Cloud data and account deletion are managed through [mapkluss.art/cloud](https://mapkluss.art/cloud). Removing the mod does not delete server-side account data. Delete the local `mapkluss-companion` configuration folder separately if the instance itself must retain no local Companion state.

## Logs and reports

Minecraft logs may contain technical error messages, non-secret identifiers, and file names. Review and redact logs before posting them publicly. Never publish account tokens, device codes, private art files, server addresses, or personal file-system paths.
