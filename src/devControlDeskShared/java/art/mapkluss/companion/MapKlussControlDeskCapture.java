package art.mapkluss.companion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Captures the actual dev-client window into memory and removes the temporary file immediately. */
final class MapKlussControlDeskCapture {
    private MapKlussControlDeskCapture() {
    }

    static byte[] captureWindow() throws IOException {
        Path target = Files.createTempFile("mapkluss-control-desk-", ".png");
        try {
            if (isWindows()) captureWindowsWindow(target);
            else captureMacWindow(target);
            return Files.readAllBytes(target);
        } finally {
            Files.deleteIfExists(target);
        }
    }

    private static void captureMacWindow(Path target) throws IOException {
        Process process = new ProcessBuilder("screencapture", "-x", target.toString()).start();
        waitFor(process, "screencapture");
    }

    private static void captureWindowsWindow(Path target) throws IOException {
        String escapedTarget = target.toAbsolutePath().toString().replace("'", "''");
        long processId = ProcessHandle.current().pid();
        String nativeDefinition = "using System; using System.Runtime.InteropServices; "
            + "public static class MapKlussControlDeskNative { "
            + "[StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left; public int Top; public int Right; public int Bottom; } "
            + "[DllImport(\\\"user32.dll\\\")] public static extern bool GetWindowRect(IntPtr handle, out RECT rect); }";
        String escapedDefinition = nativeDefinition.replace("'", "''");
        String script = String.join("; ",
            "Add-Type -AssemblyName System.Windows.Forms",
            "Add-Type -AssemblyName System.Drawing",
            "Add-Type -TypeDefinition '" + escapedDefinition + "'",
            "$process=Get-Process -Id " + processId,
            "$rect=New-Object MapKlussControlDeskNative+RECT",
            "if (-not [MapKlussControlDeskNative]::GetWindowRect($process.MainWindowHandle,[ref]$rect)) { throw 'GetWindowRect failed' }",
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
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
            .redirectErrorStream(true)
            .start();
        waitFor(process, "PowerShell screenshot");
    }

    private static void waitFor(Process process, String name) throws IOException {
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                throw new IOException(name + " failed" + (output.isBlank() ? "" : ": " + output));
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException(name + " was interrupted", error);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }
}
