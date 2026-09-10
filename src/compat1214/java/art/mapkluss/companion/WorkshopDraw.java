package art.mapkluss.companion;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.RenderLayer;

final class WorkshopDraw {
    static final Identifier ICONS = Identifier.of(MapKlussCompanionClient.MOD_ID, WorkshopIcon.TEXTURE);
    static int width(TextRenderer font, String text) { return font.getWidth(MapKlussText.workshop(text)); }
    static String clip(TextRenderer font, String text, int available) {
        if (text == null || available <= 0) return "";
        if (width(font,text) <= available) return text;
        String suffix = "...";
        if (width(font,suffix) > available) return "";
        int end=text.length();
        while(end>0 && width(font,text.substring(0,end)+suffix)>available) end=text.offsetByCodePoints(end,-1);
        return text.substring(0,end)+suffix;
    }
    static void text(DrawContext g, TextRenderer font, String text, int x, int y, int available, int color) {
        var value=MapKlussText.workshop(clip(font,text,available));
        g.drawText(font,value,x,y,color,false);
    }
    static void icon(DrawContext g, WorkshopIcon icon, int x, int y, int color) {
        g.drawTexture(RenderLayer::getGuiTextured,ICONS,x,y,(float)icon.u(),0f,16,16,16,16,WorkshopIcon.atlasWidth(),16,color);
    }
    static void image(DrawContext g, Identifier image, WorkshopLayout.Rect area, int iw, int ih) {
        var r=WorkshopLayout.contain(area,iw,ih);
        if(r.width()==0 || r.height()==0)return;
        g.drawTexture(RenderLayer::getGuiTextured,image,r.x(),r.y(),0f,0f,r.width(),r.height(),iw,ih,iw,ih);
    }
}

