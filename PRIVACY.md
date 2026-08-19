# Privacy and network behavior

MapKluss Companion is a client-side Minecraft mod. It does not require a server-side plugin and does not give a Minecraft server access to a player's MapKluss account.

## Data stored locally

Depending on the features used, Companion may store the following inside the Minecraft instance:

- the MapKluss account session used by device login;
- selected language and service routing configuration;
- downloaded artifact metadata and installed-file indexes;
- map IDs, colour fingerprints, tile bindings, and compressed map previews;
- Lens preferences;
- the anonymous-statistics choice and, only after opt-in, a random local installation identifier;
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
- one bounded request to the public GitHub Releases API during startup to check whether a newer Companion version exists.

Lens performs no background network or disk work while it is inactive. Active Lens sessions use a bounded heartbeat, Realtime-first updates, and a slower recovery poll.

The update check sends no MapKluss account, art, world, server, inventory, or gameplay data. GitHub receives the ordinary network metadata of an HTTPS request. The last release notice shown is stored locally so the same popup is not repeated.

## Optional anonymous statistics

On first launch, Companion asks whether the player wants to share anonymous usage statistics. No telemetry request is made and no telemetry identifier is created before the player explicitly accepts. The choice can be changed in Account at any time. Turning statistics off deletes the local telemetry identifier.

When enabled, Companion may send only one of these event names: launch, completed login, opened library, installed schematic, started Lens, or created tracker. Every event also contains only the Companion version, Minecraft version, interface language, and broad operating-system family. It never contains a MapKluss account, Minecraft server or world, coordinates, art, map ID, file name, inventory contents, or error text.

The service immediately replaces the random installation identifier with a salted hash that changes every UTC day. The original identifier and IP address are not written to the analytics tables. Individual event rows are retained for no more than 30 days; long-term reporting uses daily aggregate counts.

## Data that remains local

Companion does not upload Two-layer world coordinates, anchors, demolition progress, AutoFrame bindings, local map-preview caches, or ordinary inventory contents. It does not place or break blocks automatically.

## Account deletion

Cloud data and account deletion are managed through [mapkluss.art/cloud](https://mapkluss.art/cloud). Removing the mod does not delete server-side account data. Delete the local `mapkluss-companion` configuration folder separately if the instance itself must retain no local Companion state.

## Logs and reports

Minecraft logs may contain technical error messages, non-secret identifiers, and file names. Review and redact logs before posting them publicly. Never publish account tokens, device codes, private art files, server addresses, or personal file-system paths.
