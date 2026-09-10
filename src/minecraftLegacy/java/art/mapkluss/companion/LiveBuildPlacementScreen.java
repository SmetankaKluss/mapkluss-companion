package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Draft-only controls. Nothing changes in the active assembly until Apply. */
public final class LiveBuildPlacementScreen extends Screen {
    private final Screen parent;
    private final LiveBuildClient manager = LiveBuildClient.instance();
    private final LiveBuildParts.Part part;
    private final Object world;
    private final LiveBuildPreviewTexture texture = new LiveBuildPreviewTexture();
    private final String[] coordinates = {"0","0","0"};
    private final TextFieldWidget[] inputs = new TextFieldWidget[3];
    private WorkshopTheme theme = WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);
    private int rotation;
    private boolean mirror;
    private String error = "";

    public LiveBuildPlacementScreen(Screen parent) {
        super(Text.literal("MapKluss Placement"));
        this.parent = parent;
        var c = MinecraftClient.getInstance();
        world = c.world;
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
    private String tr(String ru,String en) { return CompanionI18n.english(MinecraftClient.getInstance())?en:ru; }
    private boolean available() {
        return part != null && manager.selectedMap() == part && !manager.preparing()
            && MinecraftClient.getInstance().world == world && world != null;
    }
    private void here() {
        var c = MinecraftClient.getInstance();
        if(c.player == null)return;
        var p = c.player.getBlockPos().toImmutable();
        coordinates[0]=Integer.toString(p.getX());coordinates[1]=Integer.toString(p.getY());coordinates[2]=Integer.toString(p.getZ());
        for(int i=0;i<3;i++)if(inputs[i]!=null)inputs[i].setText(coordinates[i]);
    }
    private void button(String id,String label,WorkshopIcon icon,WorkshopLayout.Rect r,Runnable action) {
        addDrawableChild(MapKlussButton.builder(Text.literal(label),ignored->action.run()).action(id)
            .tooltip(Text.literal(label)).dimensions(r.x(),r.y(),r.width(),r.height())
            .build().workshop(theme,icon));
    }
    @Override protected void init() {
        try{theme=WorkshopTheme.of(CompanionConfig.load(MinecraftClient.getInstance().runDirectory.toPath()).theme());}catch(Exception ignored){}
        var s=WorkshopTrackerLayout.at(width,height);
        for(int i=0;i<3;i++){
            final int axis=i;
            var r=WorkshopTrackerLayout.part(s.title(),i,3);
            var input=new TextFieldWidget(textRenderer,r.x()+14,r.y(),Math.max(1,r.width()-14),20,Text.literal(new String[]{"X","Y","Z"}[i]));
            input.setMaxLength(11);input.setText(coordinates[i]);
            input.setChangedListener(value->coordinates[axis]=value);
            inputs[i]=input;addDrawableChild(input);
        }
        button("tracker.build.rotate",tr("Поворот ","Rotate ")+(rotation*90),WorkshopIcon.REFRESH,
            WorkshopTrackerLayout.part(s.modes(),0,3),()->{rotation=(rotation+1)%4;clearChildren();init();});
        button("tracker.build.mirror",tr("Зеркало ","Mirror ")+(mirror?tr("Да","On"):tr("Нет","Off")),WorkshopIcon.LAYERS,
            WorkshopTrackerLayout.part(s.modes(),1,3),()->{mirror=!mirror;clearChildren();init();});
        button("tracker.build.here",tr("Здесь","Here"),WorkshopIcon.TRACKER,WorkshopTrackerLayout.part(s.modes(),2,3),this::here);
        button("global.back",tr("Отмена","Cancel"),WorkshopIcon.BACK,WorkshopTrackerLayout.part(s.footer(),0,2),this::close);
        button("tracker.build.apply",tr("Закрепить","Anchor"),WorkshopIcon.CHECK,WorkshopTrackerLayout.part(s.footer(),1,2),this::apply);
    }
    private void apply() {
        if(!available()){error=tr("Стройка недоступна","Build unavailable");return;}
        try{
            int x=Integer.parseInt(coordinates[0].trim()),y=Integer.parseInt(coordinates[1].trim()),z=Integer.parseInt(coordinates[2].trim());
            manager.anchor(MinecraftClient.getInstance(),new BlockPos(x,y,z),new LiveBuildTransform(rotation,mirror));
            close();
        }catch(NumberFormatException invalid){error=tr("Проверьте X, Y, Z","Check X, Y, Z");}
        catch(RuntimeException invalid){error=tr("Не удалось закрепить","Could not anchor");}
    }
    @Override public void render(DrawContext g,int mouseX,int mouseY,float delta) {
        var s=WorkshopTrackerLayout.at(width,height);
        g.fill(0,0,width,height,0x88000000);WorkshopChrome.frame(g::fill,s.frame(),theme);
        WorkshopDraw.text(g,textRenderer,tr("Угол изображения: карта ","Artwork corner: map ")+(manager.selectedPart()+1),
            s.navigation().x()+4,s.navigation().y()+4,s.navigation().width()-8,theme.color("text-primary"));
        for(int i=0;i<3;i++){
            var r=WorkshopTrackerLayout.part(s.title(),i,3);
            WorkshopDraw.text(g,textRenderer,new String[]{"X","Y","Z"}[i],r.x(),r.y()+5,12,theme.color("text-secondary"));
        }
        WorkshopDraw.text(g,textRenderer,error,s.header().x()+4,s.header().y()+2,s.header().width()-8,theme.color("text-secondary"));
        if(manager.top()!=null&&manager.pixels()!=null){
            var top=manager.top();
            WorkshopDraw.image(g,texture.get(top.width(),top.height(),manager.pixels(),manager.viewRevision()),s.table(),top.width(),top.height());
        }
        super.render(g,mouseX,mouseY,delta);
    }
    @Override public void removed(){texture.close();super.removed();}
    @Override public void close(){MinecraftClient.getInstance().setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
