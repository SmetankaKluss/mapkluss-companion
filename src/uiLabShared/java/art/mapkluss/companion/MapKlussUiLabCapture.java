package art.mapkluss.companion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class MapKlussUiLabCapture {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private MapKlussUiLabCapture() {
    }

    static Path capture(MapKlussUiLabModel model) throws IOException {
        Path root = findRepositoryRoot();
        Path output = root.resolve("run/ui-lab-captures");
        Files.createDirectories(output);
        String name = "%s-%s-%s-%s.png".formatted(
            STAMP.format(LocalDateTime.now()),
            model.page().name().toLowerCase(java.util.Locale.ROOT),
            model.state().name().toLowerCase(java.util.Locale.ROOT),
            model.viewport().name().toLowerCase(java.util.Locale.ROOT)
        );
        Path target = output.resolve(name);
        if (isWindows()) {
            captureWindowsWindow(target);
            return target;
        }
        Process process = new ProcessBuilder("screencapture", "-x", target.toString()).start();
        try {
            if (process.waitFor() != 0) throw new IOException("screencapture exited with " + process.exitValue());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Screenshot capture was interrupted", error);
        }
        return target;
    }

    private static void captureWindowsWindow(Path target) throws IOException {
        String escapedTarget = target.toAbsolutePath().toString().replace("'", "''");
        long processId = ProcessHandle.current().pid();
        String nativeDefinition = "using System; using System.Runtime.InteropServices; "
            + "public static class MapKlussNativeWindow { "
            + "[StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left; public int Top; public int Right; public int Bottom; } "
            + "[DllImport(\"user32.dll\")] public static extern bool GetWindowRect(IntPtr handle, out RECT rect); }";
        String escapedDefinition = nativeDefinition.replace("'", "''");
        String script = String.join("; ",
            "Add-Type -AssemblyName System.Windows.Forms",
            "Add-Type -AssemblyName System.Drawing",
            "Add-Type -TypeDefinition '" + escapedDefinition + "'",
            "$process=Get-Process -Id " + processId,
            "$rect=New-Object MapKlussNativeWindow+RECT",
            "if (-not [MapKlussNativeWindow]::GetWindowRect($process.MainWindowHandle,[ref]$rect)) { throw 'GetWindowRect failed' }",
            "$width=$rect.Right-$rect.Left",
            "$height=$rect.Bottom-$rect.Top",
            "if ($width -le 0 -or $height -le 0) { throw 'Minecraft window has invalid bounds' }",
            "$bitmap=New-Object System.Drawing.Bitmap $width,$height",
            "$graphics=[System.Drawing.Graphics]::FromImage($bitmap)",
            "$graphics.CopyFromScreen((New-Object System.Drawing.Point $rect.Left,$rect.Top),[System.Drawing.Point]::Empty,(New-Object System.Drawing.Size $width,$height))",
            "$bitmap.Save('" + escapedTarget + "',[System.Drawing.Imaging.ImageFormat]::Png)",
            "$graphics.Dispose()",
            "$bitmap.Dispose()"
        );
        Process process = new ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script
        ).redirectErrorStream(true).start();
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                throw new IOException("PowerShell screenshot exited with " + exitCode + (output.isBlank() ? "" : ": " + output));
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Screenshot capture was interrupted", error);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 5 && current != null; depth++, current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("gradlew"))) return current;
        }
        return Path.of("").toAbsolutePath().normalize();
    }
}
