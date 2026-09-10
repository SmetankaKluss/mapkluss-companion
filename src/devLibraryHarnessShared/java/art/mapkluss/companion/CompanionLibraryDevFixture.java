package art.mapkluss.companion;

import java.util.List;

/** Development-only data for rendering the ordinary Library screen without Cloud or player data. */
public final class CompanionLibraryDevFixture {
    private static final String FIXTURE_PROPERTY = "mapkluss.dev.libraryFixture";

    private CompanionLibraryDevFixture() {
    }

    public static void applyTwoLayerStart(Object screen) throws Exception {
        if (screen instanceof SuppressionStartScreen start) {
            start.applyDevelopmentSession(twoLayerCatalog(),
                List.of(copy("Волна", "The wave"), copy("Этап 12/64", "Stage 12/64"),
                    copy("Проверка карты", "Checking map")), SuppressionStage.VERIFY);
        }
    }

    public static SuppressionBundleCatalog twoLayerCatalog() throws Exception {
        Class<?> palette;
        String method;
        try { palette = Class.forName("net.minecraft.block.MapColor"); method = "getRenderColor"; }
        catch (ClassNotFoundException missing) { palette = Class.forName("net.minecraft.world.level.material.MapColor"); method = "getColorFromPackedId"; }
        var convert = palette.getMethod(method, int.class);
        int[] colors = new int[248];
        for (int i = 4; i < colors.length; i++) colors[i] = (int) convert.invoke(null, i);
        java.awt.image.BufferedImage image;
        try (var input = CompanionLibraryDevFixture.class.getResourceAsStream("/assets/mapkluss-companion/textures/dev/library/great-wave.png")) {
            if (input == null) throw new java.io.IOException("Missing fixture image");
            image = javax.imageio.ImageIO.read(input);
        }
        var tiles = new java.util.ArrayList<SuppressionBundleCatalog.Tile>();
        for (int row = 0; row < 2; row++) for (int col = 0; col < 3; col++) {
            byte[] pixels = new byte[128 * 128];
            for (int p = 0; p < pixels.length; p++) {
                int rgb = image.getRGB((col * 128 + p % 128) * image.getWidth() / 384,
                    (row * 128 + p / 128) * image.getHeight() / 256);
                int best = 4, distance = Integer.MAX_VALUE;
                for (int c = 4; c < colors.length; c++) {
                    int dr = ((rgb >> 16) & 255) - ((colors[c] >> 16) & 255);
                    int dg = ((rgb >> 8) & 255) - ((colors[c] >> 8) & 255);
                    int db = (rgb & 255) - (colors[c] & 255);
                    int d = dr * dr + dg * dg + db * db;
                    if (d < distance) { distance = d; best = c; }
                }
                pixels[p] = (byte) best;
            }
            int index = row * 3 + col + 1;
            var bundle = new SuppressionBundle(new SuppressionPlanParser.Parsed(null, pixels, pixels),
                new byte[0], new byte[0], "", "", "", "", copy("Волна", "The wave"), "fixture");
            tiles.add(new SuppressionBundleCatalog.Tile("fixture-" + index, index, col, row, bundle));
        }
        return new SuppressionBundleCatalog("", "", copy("Волна", "The wave"), "fixture", "", 3, 2, tiles);
    }

    public static void applyCollections(Object screen) {
        String fixture=System.getProperty(FIXTURE_PROPERTY,"");
        boolean populated=fixture.equals("populated")||fixture.equals("long-name");
        String status=fixture.equals("loading")?copy("Загрузка...","Loading..."):
            fixture.equals("error")?copy("Не удалось обновить коллекции","Could not refresh collections"):"";
        var collections=populated?List.of(
            new CompanionCollection("fixture-collection-1",copy("Пейзажи","Landscapes"),"","",4),
            new CompanionCollection("fixture-collection-2",copy("Строим вместе","Building together"),"","",2),
            new CompanionCollection("fixture-collection-3",fixture.equals("long-name")?"A deliberately long collection name for checking clipping and keyboard focus":copy("Архив","Archive"),"","",0)
        ):List.<CompanionCollection>of();
        if(screen instanceof CompanionCollectionsScreen s)s.applyDevelopmentData(collections,status);
        if(screen instanceof CompanionCollectionItemsScreen s)s.applyDevelopmentData(populated?(fixture.equals("long-name")?longNameItems():sampleItems()):List.of(),status);
        if(screen instanceof CompanionArtCollectionsScreen s) {
            s.applyDevelopmentData(collections,status);
            s.applyDevelopmentManifest(new CompanionManifest("fixture-art","fixture-version","fixture-owner","Workshop fixture","unlisted",
                new CompanionManifest.Grid(2,2),"2d","1.21.11",null,"",false,List.of("fixture-collection-1"),List.of(),""));
        }
    }

    public static void applyTrackerOpen(Object screen) {
        if (!(screen instanceof TrackerOpenScreen tracker)) return;
        String f = System.getProperty(FIXTURE_PROPERTY, "");
        var entries = new java.util.ArrayList<TrackerHistoryEntry>();
        if (f.equals("populated") || f.equals("long-name")) for(int i=0;i<12;i++)
            entries.add(new TrackerHistoryEntry("fixture-tracker-"+i, copy("Волна","The wave")+" "+(i+1),
                "fixture-art",i%2==0?"gathering":"building","2026-09-07T10:00:00Z"));
        tracker.applyDevelopmentData(entries, f.equals("error") ? copy("Нет соединения","Disconnected") : "");
    }

    public static void applyTrackerSession(Object screen) {
        if (!(screen instanceof TrackerSessionScreen tracker)) return;
        String f = System.getProperty(FIXTURE_PROPERTY, "");
        boolean ready = f.equals("populated") || f.equals("long-name");
        var materials = new java.util.ArrayList<BuildSessionMaterial>();
        String[] ids={"stone","oak_planks","white_wool","sand","grass_block","cobblestone","glass","red_wool","black_wool","birch_log","snow_block","dirt","gold_block","blue_wool","yellow_wool","terracotta"};
        for(int i=0;i<ids.length;i++) materials.add(new BuildSessionMaterial("minecraft:"+ids[i],
            f.equals("long-name")?"A very long material name for checking full tooltip and clipping "+i:ids[i].replace('_',' '),128+i*64));
        BuildSessionState state = ready ? new BuildSessionState("fixture-tracker",
            new CompanionManifest.Grid(3,2),trackerPreview(),materials,java.util.Map.of("minecraft:stone",128,"minecraft:oak_planks",64),
            java.util.Map.of("minecraft:stone",32),"gathering",
            new BuildSessionInfo(copy("Волна","The wave"),"fixture","",null),"fixture-art","fixture-version") : null;
        tracker.applyDevelopmentData(state,f.equals("loading")?copy("Загрузка...","Loading..."):
            f.equals("error")?copy("Нет соединения","Disconnected"):"",f.equals("error"));
    }

    private static String trackerPreview() {
        try (var input = CompanionLibraryDevFixture.class.getResourceAsStream(
                "/assets/mapkluss-companion/textures/dev/library/great-wave.png")) {
            if (input == null) throw new IllegalStateException("Missing Tracker fixture preview");
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(input.readAllBytes());
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read Tracker fixture preview", error);
        }
    }

    public static void applyScan(Object screen) throws Exception {
        if (!(screen instanceof ScanScreen scan)) return;
        String fixture = System.getProperty(FIXTURE_PROPERTY, "");
        boolean populated = fixture.equals("populated") || fixture.equals("long-name");
        MapScanDraft draft = null;
        if (populated) {
            java.awt.image.BufferedImage image;
            try (var input = CompanionLibraryDevFixture.class.getResourceAsStream("/assets/mapkluss-companion/textures/dev/library/great-wave.png")) {
                image = javax.imageio.ImageIO.read(input);
            }
            var tiles = new java.util.ArrayList<MapScanAssembler.Tile>();
            for (int row = 0; row < 2; row++) for (int col = 0; col < 3; col++) {
                if (row == 1 && col == 2) continue;
                int[] pixels = new int[128 * 128];
                for (int p = 0; p < pixels.length; p++) pixels[p] = image.getRGB(
                    (col * 128 + p % 128) * image.getWidth() / 384,
                    (row * 128 + p / 128) * image.getHeight() / 256);
                tiles.add(new MapScanAssembler.Tile(col, row, pixels));
            }
            draft = new MapScanDraft(fixture.equals("long-name") ? "A long scan title for checking the ordinary menu without shrinking text" : copy("Волна", "The wave"),
                "wall", 3, 2, 1, MapScanAssembler.assemblePng(tiles, 3, 2));
        }
        var entries = populated ? List.of(
            new ScanHistoryEntry(draft.title(), "wall", 3, 2, 1, "fixture-wave.png", "", "", "", "", ""),
            new ScanHistoryEntry(copy("Карта из рамки", "Framed map"), "frame", 1, 1, 0, "fixture-frame.png", "", "", "", "", "")
        ) : List.<ScanHistoryEntry>of();
        scan.applyDevelopmentData(draft, entries, fixture.equals("loading") ? copy("Загрузка...", "Loading...")
            : fixture.equals("error") ? copy("Превью недоступно", "Preview unavailable") : "");
    }

    public static void applyLens(Object screen) {
        if (!(screen instanceof LensScreen lens)) return;
        String fixture = System.getProperty(FIXTURE_PROPERTY, "");
        boolean populated = fixture.equals("populated") || fixture.equals("long-name");
        var grid = new LensDtos.Grid(3, 2);
        String title = fixture.equals("long-name") ? "A very long session title for testing clipping without smaller text" : copy("Волна", "The wave");
        var sessions = populated ? List.of(
            new LensDtos.Session("fixture-lens", title, "active", grid, "2d", 12, 128, 384, 256, 2, "", "", "SAMPLE", true, null),
            new LensDtos.Session("fixture-guest", copy("Совместный арт", "Shared art"), "offline", grid, "2d", 3, 128, 384, 256, 1, "", "", "", false, null)
        ) : List.<LensDtos.Session>of();
        var placements = populated ? List.of(
            new LensDtos.Placement("fixture-placement", "fixture-lens", "fixture-owner", title, "personal", "", "", new LensDtos.Anchor(0,0,0), "north", grid, 12, 128, "", 0.0, true, null),
            new LensDtos.Placement("fixture-other", "fixture-guest", "fixture-other-owner", copy("Совместный арт", "Shared art"), "group", "", "", new LensDtos.Anchor(0,0,0), "up", grid, 3, 128, "", 2.0, false, null)
        ) : List.<LensDtos.Placement>of();
        lens.applyDevelopmentData(sessions, placements, fixture.equals("loading") ? copy("Загрузка...", "Loading...")
            : fixture.equals("error") ? copy("Нет соединения", "Disconnected") : "");
    }

    public static boolean applyIfConfigured(CompanionLibraryScreen screen) {
        String fixture = System.getProperty(FIXTURE_PROPERTY, "");
        if (fixture.isBlank()) return false;

        return switch (fixture) {
            case "empty" -> screen.applyDevelopmentFixture(List.of(), "", copy("Cloud подключён", "Cloud connected"));
            case "loading" -> screen.applyDevelopmentFixture(
                List.of(),
                copy("Загрузка: Мои арты...", "Loading: My arts..."),
                copy("Cloud подключён", "Cloud connected")
            );
            case "error" -> screen.applyDevelopmentFixture(
                List.of(),
                copy("Не удалось обновить библиотеку", "Could not refresh the library"),
                copy("Cloud подключён", "Cloud connected")
            );
            case "populated" -> screen.applyDevelopmentFixture(sampleItems(), "", copy("Cloud подключён", "Cloud connected"));
            case "long-name" -> screen.applyDevelopmentFixture(longNameItems(), "", copy("Cloud подключён", "Cloud connected"));
            default -> false;
        };
    }

    private static List<CompanionLibraryItem> sampleItems() {
        return List.of(
            item("fixture-aurora", "Aurora over the bay", "unlisted", 4, 3, "3d", true),
            item("fixture-garden", "Garden study", "private", 3, 2, "2d", false),
            item("fixture-station", "Station at dusk", "public", 5, 4, "3d", false),
            item("fixture-tiles", "Tiles and glass", "unlisted", 2, 3, "2d", true)
        );
    }

    private static List<CompanionLibraryItem> longNameItems() {
        return List.of(
            item(
                "fixture-long-name",
                "A deliberately long MapKluss Library fixture title for checking wrapping, clipping and keyboard focus at every GUI scale",
                "unlisted",
                10,
                10,
                "3d",
                true
            ),
            item("fixture-short", "Short title", "private", 1, 1, "2d", false)
        );
    }

    private static CompanionLibraryItem item(
        String id,
        String title,
        String privacy,
        int wide,
        int tall,
        String mode,
        boolean favorite
    ) {
        return new CompanionLibraryItem(
            id,
            "fixture-version",
            title,
            privacy,
            new CompanionManifest.Grid(wide, tall),
            mode,
            "",
            "2026-01-01T00:00:00Z",
            favorite
        );
    }

    private static String copy(String ru, String en) {
        return "en".equals(System.getProperty("mapkluss.dev.language", "")) ? en : ru;
    }
}
