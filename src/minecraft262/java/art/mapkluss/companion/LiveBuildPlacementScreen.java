package art.mapkluss.companion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

/** Draft-only controls. Nothing changes in the active assembly until Apply. */
public final class LiveBuildPlacementScreen extends Screen {
    private final Screen parent;
    private final LiveBuildClient manager = LiveBuildClient.instance();
    private final LiveBuildParts.Part part;
    private final Object world;
    private final LiveBuildPreviewTexture texture = new LiveBuildPreviewTexture();
    private final String[] coordinates = {"0","0","0"};
    private final EditBox[] inputs = new EditBox[3];
    private WorkshopTheme theme = WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);
    private int rotation;
    private boolean mirror;
    private String error = "";

    public LiveBuildPlacementScreen(Screen parent) {
        super(Component.literal("MapKluss Placement"));
        this.parent = parent;
        var c = Minecraft.getInstance();
        world = c.level;
        part = manager.selectedMap();
        var id = manager.identity();
        if (id != null) {
            var origin=id.origin();
            coordinates[0] = Integer.toString(origin.x());
            coordinates[1] = Integer.toString(origin.y());
            coordinates[2] = Integer.toString(origin.z());
            rotation = id.transform().quarterTurns(); mirror = id.transform().mirrorX();
        } else here();
    }
    private String tr(String ru,String en) { return CompanionI18n.english(Minecraft.getInstance())?en:ru; }
    private boolean available() {
        return part != null && manager.selectedMap() == part && !manager.preparing()
            && Minecraft.getInstance().level == world && world != null;
    }
    private void here() {
        var c = Minecraft.getInstance();
        if(c.player == null)return;
        var p = c.player.blockPosition().immutable();
        coordinates[0]=Integer.toString(p.getX());coordinates[1]=Integer.toString(p.getY());coordinates[2]=Integer.toString(p.getZ());
        for(int i=0;i<3;i++)if(inputs[i]!=null)inputs[i].setValue(coordinates[i]);
    }
    private void button(String id,String label,WorkshopIcon icon,WorkshopLayout.Rect r,Runnable action) {
        addRenderableWidget(MapKlussButton.builder(Component.literal(label),ignored->action.run()).action(id)
            .tooltip(Component.literal(label)).dimensions(r.x(),r.y(),r.width(),r.height())
            .build().workshop(theme,icon));
    }
    @Override protected void init() {
        try{theme=WorkshopTheme.of(CompanionConfig.load(Minecraft.getInstance().gameDirectory.toPath()).theme());}catch(Exception ignored){}
        var s=WorkshopTrackerLayout.at(width,height);
        for(int i=0;i<3;i++){
            final int axis=i;
            var r=WorkshopTrackerLayout.part(s.title(),i,3);
            var input=new EditBox(font,r.x()+14,r.y(),Math.max(1,r.width()-14),20,Component.literal(new String[]{"X","Y","Z"}[i]));
            input.setMaxLength(11);input.setValue(coordinates[i]);
            input.setResponder(value->coordinates[axis]=value);
            inputs[i]=input;addRenderableWidget(input);
        }
        button("tracker.build.rotate",tr("Поворот ","Rotate ")+(rotation*90),WorkshopIcon.REFRESH,
            WorkshopTrackerLayout.part(s.modes(),0,3),()->{rotation=(rotation+1)%4;clearWidgets();init();});
        button("tracker.build.mirror",tr("Зеркало ","Mirror ")+(mirror?tr("Да","On"):tr("Нет","Off")),WorkshopIcon.LAYERS,
            WorkshopTrackerLayout.part(s.modes(),1,3),()->{mirror=!mirror;clearWidgets();init();});
        button("tracker.build.here",tr("Здесь","Here"),WorkshopIcon.TRACKER,WorkshopTrackerLayout.part(s.modes(),2,3),this::here);
        button("global.back",tr("Отмена","Cancel"),WorkshopIcon.BACK,WorkshopTrackerLayout.part(s.footer(),0,2),this::onClose);
        button("tracker.build.apply",tr("Закрепить","Anchor"),WorkshopIcon.CHECK,WorkshopTrackerLayout.part(s.footer(),1,2),this::apply);
    }
    private void apply() {
        if(!available()){error=tr("Стройка недоступна","Build unavailable");return;}
        try{
            int x=Integer.parseInt(coordinates[0].trim()),y=Integer.parseInt(coordinates[1].trim()),z=Integer.parseInt(coordinates[2].trim());
            manager.anchor(Minecraft.getInstance(),new BlockPos(x,y,z),new LiveBuildTransform(rotation,mirror));
            onClose();
        }catch(NumberFormatException invalid){error=tr("Проверьте X, Y, Z","Check X, Y, Z");}
        catch(RuntimeException invalid){error=tr("Не удалось закрепить","Could not anchor");}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        var s=WorkshopTrackerLayout.at(width,height);
        g.fill(0,0,width,height,0x88000000);WorkshopChrome.frame(g::fill,s.frame(),theme);
        WorkshopDraw.text(g,font,tr("Угол изображения: карта ","Artwork corner: map ")+(manager.selectedPart()+1),
            s.navigation().x()+4,s.navigation().y()+4,s.navigation().width()-8,theme.color("text-primary"));
        for(int i=0;i<3;i++){
            var r=WorkshopTrackerLayout.part(s.title(),i,3);
            WorkshopDraw.text(g,font,new String[]{"X","Y","Z"}[i],r.x(),r.y()+5,12,theme.color("text-secondary"));
        }
        WorkshopDraw.text(g,font,error,s.header().x()+4,s.header().y()+2,s.header().width()-8,theme.color("text-secondary"));
        if(manager.top()!=null&&manager.pixels()!=null){
            var top=manager.top();
            WorkshopDraw.image(g,texture.get(top.width(),top.height(),manager.pixels(),manager.viewRevision()),s.table(),top.width(),top.height());
        }
        super.extractRenderState(g,mouseX,mouseY,delta);
    }
    @Override public void removed(){texture.close();super.removed();}
    @Override public void onClose(){Minecraft.getInstance().gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
