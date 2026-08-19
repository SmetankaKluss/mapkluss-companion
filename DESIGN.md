# MapKluss Companion UI 0.13

This document is the source of truth for the Companion interface. Production screens, UI Lab fixtures, tests, and resource tokens must follow it.

## Product Character

MapKluss Companion is a focused tool for finding, inspecting, placing, and building map art. The interface should feel like a quiet console laid over the Minecraft world: image-first, direct, readable, and restrained. It is not a Minecraft-themed website and not a decorative dashboard.

## Visual Direction

- Keep the world visible under one soft neutral scrim. Do not blur it.
- Use near-black and graphite surfaces with one clear hierarchy.
- Use MapKluss lime for selection and the primary action only.
- Use cyan for live/technical state, amber for warnings, and red for destructive actions or errors.
- Do not use brass, amethyst, orange export categories, bevels, fake screws, inner shadows, glow, or permanent colored outlines.
- Preview media is the dominant visual element. Show the complete image, preserve aspect ratio, and use nearest-neighbor sampling.
- Corners are square or lightly rounded (2-4 px). Dividers are one pixel.

## Color Tokens

| Token | Value | Use |
| --- | --- | --- |
| `canvas` | `#090B0E` | Deepest app background |
| `surface` | `#11151A` | Shell and primary panels |
| `surface_raised` | `#181D23` | Selected rows and inspectors |
| `surface_input` | `#07090C` | Inputs and preview wells |
| `border` | `#303842` | Quiet dividers and boundaries |
| `border_strong` | `#536171` | Focus-independent strong boundary |
| `text` | `#F2F5F7` | Primary text |
| `text_muted` | `#A8B2BD` | Secondary text |
| `text_dim` | `#68737F` | Disabled and tertiary text |
| `lime` | `#64F58D` | Selection and primary action |
| `cyan` | `#51D7F0` | Sync, Lens, and technical state |
| `amber` | `#F4C75B` | Warning and attention |
| `red` | `#FF6673` | Error and destructive action |

## Typography

- Primary typeface: bundled Inter, with Cyrillic and Latin coverage. Use its neutral shapes to keep dense controls and metadata readable at every GUI scale.
- Vanilla font is the fallback for unsupported glyphs.
- Titles use semibold. Body, labels, and metadata use regular.
- Do not use all-caps for sentences. Section labels may use concise uppercase only when space is tight.
- One line has one purpose. Clip with an ellipsis and provide a tooltip when the full value matters.

## Spacing And Metrics

- Base spacing unit: 4 px.
- Page inset: 12 px compact, 16 px medium, 20 px wide.
- Control height: 20 px compact, 24 px standard.
- Navigation rail: 56 px wide; medium icon rail: 44 px; compact bottom bar: 34 px high.
- Top bar: 38 px wide/medium, 32 px compact.
- Preview/inspector gap: 12 px.
- Minimum pointer target: 20 x 20 px in Minecraft logical pixels.

## Responsive Modes

### Wide

- At least 760 logical pixels wide and 420 high.
- Persistent 56 px left navigation, top bar, content, and optional inspector.
- Library uses a preview strip/grid plus a large selected-art stage.
- Art uses a dominant preview and one contextual action inspector.

### Medium

- At least 500 logical pixels wide and 300 high.
- 44 px icon navigation.
- Inspector becomes an in-content tab or drawer.

### Compact

- Smaller viewports or GUI scale 3/4/Auto.
- Bottom navigation and one content region at a time.
- No squeezed multi-column button grids.

## Navigation

Top-level destinations are Library, Lens, Scan, Tracker, and Account. Collections is a Library tab. Two-layer starts from an art and becomes a resumable task. Update availability appears as a badge in Account and the global status area.

## Components

### Buttons

- Default: quiet graphite fill, no permanent accent outline.
- Hover: raised graphite fill and brighter text.
- Focus: one-pixel cyan focus ring outside the control.
- Pressed: darker surface and one-pixel inward content shift.
- Selected: lime leading indicator or underline, not a full neon box.
- Primary: lime fill with dark text; use once per decision group.
- Technical: cyan text/indicator on graphite.
- Warning: amber text/indicator.
- Danger: red text/indicator; confirmation remains explicit.
- Disabled: dim text and low-contrast surface. It must never resemble an active control.

### Tabs And Segmented Controls

- Tabs share one quiet track. Selected tab has a lime underline or leading edge.
- Segmented controls are for mutually exclusive modes such as Scan Hand/Frame/Wall/Corners.

### Art Tiles

- Thumbnail first, then title and compact metadata.
- Selecting a tile updates the large stage. It does not expose five equal row actions.
- Card actions are contextual: open on activation; favorite and more remain secondary.

### Status

- Status sits in the top bar or the relevant workflow, never between a title and its controls.
- Loading, error, warning, and success must include text or an icon, not color alone.

## Screen Rules

- Library: My Arts, Favorites, Recent, Collections; search, sort, refresh; large selected art; thumbnail strip/grid.
- Art: large complete preview; primary Install/Update, Open editor, Track build; Files/Cloud/Build tabs and More for rare actions.
- Lens: connection, revision, viewers, and live image first; code, placements, and group access below; no public mode.
- Scan: segmented mode control, preview/result next to errors, one bottom action bar.
- Tracker: one scrolling table with sticky header, icons, editable counts, and progress.
- Account: login/device code, Cloud and sync status, language, update, cloud site, logout; identifiers under Details.
- Two-layer: focused workflow, current part and stage first, HUD settings second.

## Interaction And Accessibility

- Every component implements default, hover, keyboard focus, pressed, selected, disabled, loading, warning, and error where applicable.
- Tab and Shift+Tab follow visual reading order. Enter and Space activate focused controls. Escape returns or closes.
- Do not communicate meaning through color alone. Pair state colors with a label, icon, or shape.
- Tooltip placement is clamped to viewport bounds.
- No text or controls may overlap at 320x240, 480x270, 640x360, 960x540, or 1280x720 logical pixels.

## Development Rules

- `src/main` owns theme tokens, action IDs, view models, and layout geometry.
- Minecraft source sets render the shared model and translate input only.
- UI Lab uses the production component tree and renderer.
- UI Lab, fixtures, F8, and development watchers are excluded from production JARs.
- Before removing a legacy screen, action reachability tests must prove that all registered action IDs exist in the new hierarchy.
