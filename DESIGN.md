# MapKluss Companion 0.14: Pixel Workshop

## Visual Contract

The interface is an in-game pixel workshop, not a website. Approved composition:
a compact centered window, top navigation, vertical art list on the left, large
complete preview on the right, title/favorite and one action row below it.
Pixel Workshop supplies the stepped frame, restrained key-like bevels and raster
typography. This replaces the previous console/left-navigation-rail design.

Actual geometry and rendered captures are the acceptance source. Generated
concepts guide composition and appearance, not invented actions, exact font
files or permission to remove features. No background blur or decorative screws.

## Themes

Only these seven dark themes exist: Classic, Deep Ocean, Ember Forge, Amethyst,
Acid Grove, Cobalt Pulse and Midnight. Default: Amethyst. All are selectable in
Account; language and theme persist independently. No runtime website request.
Semantic colors are in ui/workshop-themes.json, copied from the editor theme
tokens. Never tint artworks, block textures or Minecraft map colors.

Amethyst anchors: background #100e18, primary #191524, secondary #231c33,
raised #2d2440, text #ebe3f7, secondary text #c1b4d8, accent #bc94ff.
Other themes change colors, not dimensions or component positions.
Focus uses its own inset outline. Selected tabs have a two-pixel underline.
Warnings/errors also have an icon or readable status, never color alone.

## Raster Resources And Type

Use MapKluss Workshop Bitmap: a pinned 8-pixel-grid bitmap conversion of the
bundled OFL source, with ASCII, Russian including Yo, and common punctuation.
The converted font has its own name; keep the original notice and OFL license.
Vanilla is the fallback for other symbols. Use the styled text object for BOTH
measurement and drawing. No smoothing, fractional text scales or web typography.
Never shrink long labels: shorten localized labels or clip with a full tooltip.
Body text is native scale; headings may use an integer scale where space permits.

The original icon atlas contains twenty 16x16 masks. Use resource icons rather
than Unicode approximations. Frame/button masks have fixed stepped corners.
Shared WorkshopChrome defines the semantic integer-grid frame/button painter;
adapters must not invent different colors, geometry or states.
Generated resources are reproducible using the scripts, not runtime generation.

## Layout

All values are Minecraft logical pixels, not physical screenshot pixels.
Reference viewport: 960x540. Window: 610x432 centered. Border inset: 6.
Navigation: 28 high; Library tabs: 24; gaps: 4; footer: 20.
Left list occupies 30.8 percent of inner width, right side gets the remainder.
Metadata: 32 high; main actions: 28. Preview uses remaining height and contains
the complete image with nearest sampling. It never crops a portrait or wide art.

At smaller viewports use the available width with 8-pixel exterior margins.
Two columns require at least 548 inner pixels and 240 body pixels.
Otherwise list and selected art become separate views with explicit Back.
Navigation changes to icons with tooltips before labels collide.
Never hide functionality to fit a size. Use pagination/scroll in data regions;
global actions and status remain outside those regions.

## Components

Buttons use a one-pixel highlight above/left and a dark lower/right edge.
Hover raises the surface; focus is a distinct inset ring. Press reverses edge
direction and offsets content by one pixel without resizing the hit target.
Selected is independent of hover/focus. Disabled controls cannot activate or
display a pressed offset. Primary uses accent fill and on-accent text.
Loading retains the same bounds and a visible busy indication.
Every actionable icon has a localized tooltip and narration.

## Screens And Functions

Library: My Arts, Favorites, Recent, Collections, search/refresh, left selection,
large right preview, Install, Lens, Tracker, Two-layer, Editor and More.
Selection is not the same as opening Art details.
Art: preserve Files/Cloud/Build operations and confirmations through groups/More.
Lens: selected image/status, sessions and placements, join, personal/group only.
Scan: mode selector, corners, preview/result/history, compact action footer.
Tracker: continuous material table, manual counts, step, undo and session history.
Account: login, theme, language, sync, updates, telemetry opt-in, Details, logout.
Two-layer: source/part selection, current stage/action, progress and explicit Stop.
Preserve K/O/J, frame interaction, map previews and existing HUDs. No invented
HUD customization from concept art. Explicit singleplayer MAP.DAT import remains;
automatic construction/demolition and storage sorting remain excluded.

## Implementation And Acceptance

Keep existing services, callbacks, ownership, cache and request-epoch protections.
New visual components live in shared main code; compatibility layers translate
draw calls and input. Old visuals remain only until replacement functionality
is verified, then are removed. This document describes the target, not proof
that every old screen has already migrated.

Use the ordinary production menu with isolated fixtures/HotSwap, never the F8
Lab menu as visual truth. Compare actual Minecraft captures against references.
Test mounted widgets, callback destinations and enabled states, not just IDs.
Cover RU/EN, seven themes, GUI scales 2/3/4/Auto, long/empty/loading/error data,
keyboard focus, clipped tooltips, no overlaps and no missing actions.
Build all four targets sequentially. Dev fixtures, Control Desk and watchers
must not be present in production JARs. Owner Library review precedes the other
screen migrations. Publishing requires separate authorization.
