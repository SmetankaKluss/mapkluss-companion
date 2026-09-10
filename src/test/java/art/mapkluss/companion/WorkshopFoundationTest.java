package art.mapkluss.companion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorkshopFoundationTest {
    @TempDir Path run;

    @Test void themeAndLanguageSurviveEachOthersChanges() throws Exception {
        var initial = new CompanionConfig();
        for (String id : WorkshopTheme.IDS) {
            initial.withTheme(id).withLanguage("en").saveForRunDir(run);
            var loaded = CompanionConfig.load(run);
            assertEquals(id, loaded.theme());
            assertEquals("en", loaded.language());
            loaded.withLanguage("ru").saveForRunDir(run);
            assertEquals(id, CompanionConfig.load(run).theme());
            assertEquals(initial.gatewayUrl(), loaded.gatewayUrl());
        }
    }

    @Test void onlySevenDarkThemesAndExactAmethystTokens() {
        assertEquals(7, WorkshopTheme.IDS.size());
        assertEquals("amethyst", WorkshopTheme.normalize("signal-white"));
        assertEquals("amethyst", WorkshopTheme.normalize(null));
        assertEquals(0xFF100E18, WorkshopTheme.of("amethyst").color("app-bg"));
        assertEquals(0xFFBC94FF, WorkshopTheme.of("amethyst").color("accent"));
        for (String id : WorkshopTheme.IDS) assertEquals(20, WorkshopTheme.of(id).colors().size());
        assertThrows(UnsupportedOperationException.class, () -> WorkshopTheme.of("midnight").colors().clear());
    }

    @Test void layoutsContainEveryRegionWithoutOverlap() {
        for (int[] size : new int[][]{{320,240},{480,270},{640,360},{735,462},{960,540},{1280,720}}) {
            var s = WorkshopLayout.library(size[0], size[1]);
            var regions = List.of(s.navigation(),s.tabs(),s.preview(),s.metadata(),s.actions(),s.footer());
            for (var r : regions) assertTrue(s.frame().contains(r), r.toString());
            for (int i=0;i<regions.size();i++) for(int j=i+1;j<regions.size();j++) assertFalse(regions.get(i).intersects(regions.get(j)));
            if (s.split()) for (var r : List.of(s.preview(),s.metadata(),s.actions())) assertFalse(s.list().intersects(r));
            assertTrue(s.preview().contains(WorkshopLayout.contain(s.preview(),128,640)));
        }
        assertEquals(610,WorkshopLayout.library(960,540).frame().width());
        assertFalse(WorkshopLayout.library(320,240).split());
    }

    @Test void disabledDoesNotPressAndChromeNeverPaintsOutsideControl() {
        var t=WorkshopTheme.of("amethyst");
        var state=new WorkshopChrome.State(false,true,true,true,true,false,UiAction.Kind.PRIMARY);
        assertEquals(0,WorkshopChrome.appearance(t,state).contentOffset());
        assertEquals(t.color("text-disabled"),WorkshopChrome.appearance(t,state).text());
        var r=new WorkshopLayout.Rect(10,10,80,24);
        WorkshopChrome.button((x,y,right,bottom,color)->assertTrue(r.contains(new WorkshopLayout.Rect(x,y,right-x,bottom-y))),r,t,state);
    }

    @Test void bitmapGlyphsAndEveryIconHavePixels() throws Exception {
        String base="/assets/mapkluss-companion/";
        var atlas=javax.imageio.ImageIO.read(getClass().getResource(base+"textures/font/workshop.png"));
        var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(getClass().getResourceAsStream(base+"font/workshop.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        var providers=json.getAsJsonArray("providers");
        assertEquals("minecraft:default",providers.get(2).getAsJsonObject().get("id").getAsString());
        var rows=providers.get(1).getAsJsonObject().getAsJsonArray("chars");
        StringBuilder all=new StringBuilder();
        for(int y=0;y<rows.size();y++) {
            String row=rows.get(y).getAsString(); all.append(row);
            assertEquals(16,row.length());
            for(int x=0;x<16;x++) if(row.charAt(x)!=0) assertTrue(hasPixels(atlas,x*16,y*16),"Missing glyph "+row.charAt(x));
        }
        for(char c='А';c<='я';c++) assertTrue(all.indexOf(String.valueOf(c))>=0);
        assertTrue(all.indexOf("Ё")>=0);assertTrue(all.indexOf("ё")>=0);
        var icons=javax.imageio.ImageIO.read(getClass().getResource(base+WorkshopIcon.TEXTURE));
        assertEquals(WorkshopIcon.atlasWidth(),icons.getWidth());assertEquals(16,icons.getHeight());
        for(var icon:WorkshopIcon.values()) assertTrue(hasPixels(icons,icon.u(),0),icon.name());
    }

    @Test void appearanceChoicesAndSeparatorsStayInTheirRegions() {
        for (int[] size : new int[][]{{320,240},{480,270},{640,360},{960,540},{1280,720}}) {
            var a = WorkshopLayout.appearance(size[0],size[1]);
            var controls = new java.util.ArrayList<>(a.themes());
            controls.add(a.language()); controls.add(a.back());
            assertEquals(7,a.themes().size());
            for (int i=0;i<controls.size();i++) {
                assertTrue(a.frame().contains(controls.get(i)));
                for(int j=i+1;j<controls.size();j++) assertFalse(controls.get(i).intersects(controls.get(j)));
            }
            var shell = WorkshopLayout.library(size[0],size[1]);
            WorkshopChrome.librarySections((x,y,right,bottom,color)-> {
                var line = new WorkshopLayout.Rect(x,y,right-x,bottom-y);
                assertTrue(shell.frame().contains(line));
                for(var r : List.of(shell.navigation(),shell.tabs(),shell.preview(),shell.actions(),shell.footer())) assertFalse(r.intersects(line));
            },shell,WorkshopTheme.of("amethyst"));
        }
    }

    private boolean hasPixels(java.awt.image.BufferedImage image,int x,int y) {
        for(int j=y;j<y+16;j++)for(int i=x;i<x+16;i++)if((image.getRGB(i,j)>>>24)>0)return true;
        return false;
    }
}
