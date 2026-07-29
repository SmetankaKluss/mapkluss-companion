import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.EventSet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class MapKlussHotSwap {
    private static final String PROJECT_PACKAGE = "art.mapkluss.companion.";

    private MapKlussHotSwap() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected: <debug port> <compiled classes directory>");
        }
        String port = args[0];
        Path classes = Path.of(args[1]).toAbsolutePath().normalize();
        VirtualMachine vm = attach(port);
        Map<Path, Long> stamps = snapshot(classes);
        System.out.printf("MapKluss HotSwap attached on port %s; watching %s%n", port, classes);
        vm.resume();

        try {
            while (true) {
                EventSet events = vm.eventQueue().remove(350);
                if (events != null) events.resume();
                reloadChanged(vm, classes, stamps);
            }
        } catch (VMDisconnectedException ignored) {
            System.out.println("MapKluss HotSwap stopped with the dev client.");
        }
    }

    private static VirtualMachine attach(String port) throws Exception {
        AttachingConnector connector = Bootstrap.virtualMachineManager()
            .attachingConnectors()
            .stream()
            .filter(candidate -> candidate.name().equals("com.sun.jdi.SocketAttach"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("JDI socket connector is unavailable"));
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("hostname").setValue("127.0.0.1");
        arguments.get("port").setValue(port);

        Exception last = null;
        for (int attempt = 0; attempt < 3_600; attempt++) {
            try {
                return connector.attach(arguments);
            } catch (IOException error) {
                last = error;
                TimeUnit.MILLISECONDS.sleep(250);
            }
        }
        throw new IOException("Timed out waiting for the MapKluss debug client", last);
    }

    private static Map<Path, Long> snapshot(Path classes) throws IOException {
        Map<Path, Long> stamps = new HashMap<>();
        if (!Files.isDirectory(classes)) return stamps;
        try (var files = Files.walk(classes)) {
            files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".class"))
                .forEach(path -> stamps.put(path, lastModified(path)));
        }
        return stamps;
    }

    private static void reloadChanged(
        VirtualMachine vm,
        Path classes,
        Map<Path, Long> stamps
    ) throws IOException {
        if (!Files.isDirectory(classes)) return;
        try (var files = Files.walk(classes)) {
            for (Path path : files.filter(Files::isRegularFile).filter(MapKlussHotSwap::isProjectClass).toList()) {
                long modified = lastModified(path);
                Long previous = stamps.put(path, modified);
                if (previous == null || modified <= previous) continue;

                String className = classes.relativize(path)
                    .toString()
                    .replace(path.getFileSystem().getSeparator(), ".")
                    .replaceFirst("\\.class$", "");
                List<ReferenceType> loaded = vm.classesByName(className);
                if (loaded.isEmpty()) continue;
                byte[] bytecode = Files.readAllBytes(path);
                for (ReferenceType type : loaded) {
                    try {
                        vm.redefineClasses(Map.of(type, bytecode));
                        System.out.printf("Reloaded %s%n", className);
                    } catch (UnsupportedOperationException | ClassFormatError error) {
                        System.err.printf("Could not reload %s: %s%n", className, error.getMessage());
                    }
                }
            }
        }
    }

    private static boolean isProjectClass(Path path) {
        String name = path.toString();
        return name.endsWith(".class")
            && name.contains(PROJECT_PACKAGE.replace('.', path.getFileSystem().getSeparator().charAt(0)));
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }
}
