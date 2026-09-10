package art.mapkluss.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;

final class WorkshopHudDraw {
    static void lens(GuiGraphicsExtractor context, String label) {
        var client=Minecraft.getInstance();
        var theme=WorkshopHudTheme.current();
        var box=WorkshopOverlayLayout.lens(client.getWindow().getGuiScaledWidth(), WorkshopDraw.width(client.font,label));
        WorkshopChrome.frame(context::fill,box,theme);
        WorkshopDraw.text(context,client.font,label,box.x()+8,box.y()+7,box.width()-16,theme.color("text-primary"));
    }

    static void twoLayer(GuiGraphicsExtractor context, List<String> lines) {
        if(lines.isEmpty())return;
        var client=Minecraft.getInstance();
        int measured=lines.stream().mapToInt(line->WorkshopDraw.width(client.font,line)).max().orElse(0);
        var layout=SuppressionHudLayout.calculate(client.getWindow().getGuiScaledWidth(),client.getWindow().getGuiScaledHeight(),measured,lines.size());
        var theme=WorkshopHudTheme.current();
        WorkshopChrome.frame(context::fill,new WorkshopLayout.Rect(layout.x(),layout.y(),layout.width(),layout.height()),theme);
        for(int i=0;i<Math.min(4,lines.size());i++){
            WorkshopDraw.text(context,client.font,lines.get(i),layout.x()+6,layout.y()+5+i*11,
                layout.contentWidth(),theme.color(i==0?"accent":i==1?"warning":"text-primary"));
        }
    }
}
