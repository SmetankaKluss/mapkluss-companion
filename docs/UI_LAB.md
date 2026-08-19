# MapKluss UI Lab

UI Lab is a development-only preview environment for the real Companion
renderer and widgets. It is excluded from normal builds and does not use
Cloud, a server, a world, or personal data.

## Fast loop

Windows PowerShell:

```powershell
.\scripts\ui-lab.ps1 1.21.11
.\scripts\ui-lab.ps1 1.21.11 -Debug
.\scripts\ui-lab.ps1 26.2 -Debug
```

The Windows launcher finds a compatible JetBrains Runtime or asks Gradle to
provision one. `-Debug` also starts continuous compilation and the JDI HotSwap
watcher.

macOS:

Run the primary lane:

```bash
./scripts/ui-lab.sh 1.21.11
```

Press `F8` in the Loom client. The toolbar switches between Library, Art,
Collections, Scan, Lens, Tracker, Device Login, Two-layer, and the update
notice. It also switches fixture state, RU/EN copy, compact viewports,
short/long text, component bounds, and screenshots.

The dedicated Loom client opens UI Lab automatically from the title screen;
no local world is required. `F8` still toggles it at any time.

Colors and the first shared layout metrics live in:

- `src/main/resources/assets/mapkluss-companion/ui/theme.json`
- `src/main/resources/assets/mapkluss-companion/ui/layout.json`

Edit either file and UI Lab reloads it automatically after a short debounce.
The `Reload` button remains as a manual fallback. The running client applies
the source JSON directly, without rebuilding a JAR or restarting Minecraft.

## Java HotSwap

For Java text, coordinates, and render code, start the debug client:

```bash
./scripts/ui-lab.sh 1.21.11 --debug
```

The script attaches its local JDI watcher to `localhost:5005`, resumes the
client, continuously compiles source changes, and replaces changed MapKluss
classes in the running JVM. The launcher uses JetBrains Runtime with
`-XX:+AllowEnhancedClassRedefinition`, so method-body edits and most compatible
structural edits can be applied without an IDE. IntelliJ can still attach to
the same debug port when breakpoints and stepping are useful. Changes to
mixins, entrypoints, class hierarchy, or Gradle dependencies still require a
restart.

Use `./scripts/ui-lab.sh 26.2` as the control lane for the Mojang-named
render-state UI. Normal 1.21.8 and 1.21.4 builds remain final compatibility
checks rather than part of every visual iteration.
