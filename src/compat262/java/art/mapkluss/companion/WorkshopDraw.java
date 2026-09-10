package art.mapkluss.companion;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

final class WorkshopDraw {
    static final Identifier ICONS = Identifier.fromNamespaceAndPath(MapKlussCompanionClient.MOD_ID, WorkshopIcon.TEXTURE);
    static int width(Font font, String text) { return font.width(MapKlussText.workshop(text)); }
    static String clip(Font font, String text, int available) {
        if (text == null || available <= 0) return "";
        if (width(font,text) <= available) return text;
        String suffix = "...";
        if (width(font,suffix) > available) return "";
        int end=text.length();
        while(end>0 && width(font,text.substring(0,end)+suffix)>available) end=text.offsetByCodePoints(end,-1);
        return text.substring(0,end)+suffix;
    }
    static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int available, int color) {
        var value=MapKlussText.workshop(clip(font,text,available));
        g.text(font,value,x,y,color);
    }
    static void icon(GuiGraphicsExtractor g, WorkshopIcon icon, int x, int y, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED,ICONS,x,y,(float)icon.u(),0f,16,16,16,16,WorkshopIcon.atlasWidth(),16,color);
    }
    static void image(GuiGraphicsExtractor g, Identifier image, WorkshopLayout.Rect area, int iw, int ih) {
        var r=WorkshopLayout.contain(area,iw,ih);
        if(r.width()==0 || r.height()==0)return;
        g.blit(RenderPipelines.GUI_TEXTURED,image,r.x(),r.y(),0f,0f,r.width(),r.height(),iw,ih,iw,ih);
    }
}

