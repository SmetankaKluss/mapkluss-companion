package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Ordinary group controls; entering a code never silently shares the local world. */
public final class LiveBuildGroupScreen extends Screen {
    private final Screen parent;
    private final LiveBuildClient manager=LiveBuildClient.instance();
    private final LiveBuildGroupController groups=manager.groups();
    private WorkshopTheme theme=WorkshopTheme.of(WorkshopTheme.DEFAULT_ID);
    private TextFieldWidget code;
    private String draft="";
    private String shownInvite="";
    private Runnable confirmation;
    private boolean closingGroup;
    private String confirmationTitle;
    private String confirmationDetail;
    private String shownRole="";
    private String role(){return groups.group()==null?"":groups.group().role();}

    public LiveBuildGroupScreen(Screen parent) { super(Text.literal("MapKluss Group"));this.parent=parent; }
    private String tr(String ru,String en) { return CompanionI18n.english(MinecraftClient.getInstance())?en:ru; }
    private WorkshopLayout.Rect frame() {
        int w=Math.min(420,Math.max(200,width-16)),h=Math.min(244,Math.max(180,height-16));
        return new WorkshopLayout.Rect((width-w)/2,(height-h)/2,w,h);
    }
    private void button(String id,String label,WorkshopIcon icon,int x,int y,int w,java.util.function.BooleanSupplier enabled,Runnable action) {
        addDrawableChild(MapKlussButton.builder(Text.literal(label),ignored->{if(enabled.getAsBoolean())action.run();})
            .action(id).tooltip(Text.literal(label)).dimensions(x,y,w,20).enabledWhen(enabled).build().workshop(theme,icon));
    }
    private void redraw() { clearChildren();init(); }
    private void confirm(Runnable action,boolean close) { confirmation=action;closingGroup=close;confirmationTitle=null;confirmationDetail=null;redraw(); }
    @Override protected void init() {
        var c=MinecraftClient.getInstance();
        try{theme=WorkshopTheme.of(CompanionConfig.load(c.runDirectory.toPath()).theme());}catch(Exception ignored){}
        manager.configureGroup(c);
        shownRole=role();
        var r=frame();int x=r.x()+12,w=r.width()-24,y=r.y()+50,half=(w-8)/2;
        if(confirmation!=null){
            button("tracker.group.confirm",tr("Подтвердить","Confirm"),WorkshopIcon.CHECK,x,y+52,half,()->groups.available()&&!groups.busy()&&!manager.sourceBusy()&&!manager.groupPlacementBusy(),()->{
                Runnable action=confirmation;confirmation=null;action.run();redraw();
            });
            button("tracker.group.cancel",tr("Отмена","Cancel"),WorkshopIcon.BACK,x+half+8,y+52,half,()->true,()->{confirmation=null;redraw();});
        }else{
            code=new TextFieldWidget(textRenderer,x,y,w,20,Text.literal(tr("Код приглашения","Invite code")));
            code.setMaxLength(32);code.setText(draft);code.setChangedListener(value->draft=value);addDrawableChild(code);
            if(groups.group()!=null&&!groups.matches()){
                button("tracker.build.load",tr("Открыть схему","Open schematic"),WorkshopIcon.LIBRARY,x,y+24,half,()->!groups.busy(),()->{
                    var screen=new LiveBuildScreen(parent);c.setScreen(screen);screen.chooseGroupSource();
                });
            }else if(groups.group()!=null&&groups.group().role().equals("owner")){
                button("tracker.group.source_upload",tr("Отправить схему","Share source"),WorkshopIcon.LINK,x,y+24,half,()->manager.canTransferSource(true),()->{
                    var group=groups.group();
                    confirm(()->{if(java.util.Objects.equals(group,groups.group()))manager.transferSource(c,true);},false);
                    confirmationTitle=tr("Отправить схему участникам?","Share source with members?");
                    confirmationDetail=tr("Файл будет доступен вашей группе.","The file will be available to your group.");
                });
            }else button("tracker.group.create",tr("Создать","Create"),WorkshopIcon.TRACKER,x,y+24,half,()->groups.available()&&manager.groupSource()!=null&&!groups.busy()&&groups.group()==null,
                ()->confirm(()->groups.create(true),false));
            if(groups.group()!=null)button("tracker.group.source_download",tr("Скачать схему","Download source"),WorkshopIcon.LIBRARY,x+half+8,y+24,half,
                ()->manager.canTransferSource(false)&&!groups.matches(),()->manager.transferSource(c,false));
            else button("tracker.group.join",tr("Войти по коду","Join by code"),WorkshopIcon.ACCOUNT,x+half+8,y+24,half,()->groups.available()&&!groups.busy()&&groups.group()==null&&!draft.isBlank(),
                ()->confirm(()->groups.join(draft,true),false));
            button("tracker.group.invite",tr("Пригласить","Invite"),WorkshopIcon.LINK,x,y+48,half,()->!groups.busy()&&groups.group()!=null&&groups.group().role().equals("owner"),groups::inviteCode);
            button("tracker.group.copy",tr("Копировать код","Copy code"),WorkshopIcon.LINK,x+half+8,y+48,half,()->!groups.invite().isBlank(),
                ()->c.keyboard.setClipboard(groups.invite()));
            button("tracker.group.refresh",tr("Обновить","Refresh"),WorkshopIcon.REFRESH,x,y+72,half,()->!groups.busy()&&groups.group()!=null,groups::refresh);
            button("tracker.group.leave",tr("Выйти","Leave"),WorkshopIcon.CLOSE,x+half+8,y+72,half,()->!groups.busy()&&groups.group()!=null,
                ()->{if(groups.group().role().equals("owner"))confirm(groups::leave,true);else groups.leave();});
            if(shownRole.equals("member")){
                button("tracker.group.adopt",tr("Принять закреп","Accept placement"),WorkshopIcon.TRACKER,x,y+96,half,
                    manager::canAdoptGroupPlacement,()->{
                        var remote=manager.sharedPlacement();var group=groups.group();
                        if(remote==null)return;
                        confirm(()->{if(java.util.Objects.equals(group,groups.group()))manager.acceptGroupPlacement(c,remote);},false);
                        confirmationTitle=tr("Разместить схему по закрепу?","Place schematic at shared anchor?");
                        var p=remote.origin();
                        confirmationDetail=tr("Карта ","Map ")+(remote.tile()+1)+" : "+p.x()+" / "+p.y()+" / "+p.z();
                    });
            }else button("tracker.group.place",tr("Поделиться закрепом","Share placement"),WorkshopIcon.LINK,x,y+96,half,
                ()->groups.canPublish()&&manager.canTransferSource(true)&&manager.groupPublication()!=null,()->{
                    var placement=manager.groupPublication();var group=groups.group();
                    if(placement==null)return;
                    confirm(()->{if(java.util.Objects.equals(group,groups.group()))manager.publishGroupPlacement(placement);},false);
                    confirmationTitle=tr("Отправить схему и закреп группе?","Share schematic and placement?");
                    var p=placement.identity().origin();
                    confirmationDetail=tr("Карта ","Map ")+(placement.tile()+1)+" : "+p.x()+" / "+p.y()+" / "+p.z();
                });
            button("tracker.group.unplace",tr("Убрать из группы","Unshare map"),WorkshopIcon.CLOSE,x+half+8,y+96,half,
                ()->groups.canPublish()&&groups.placements().stream().anyMatch(p->p.tile()==manager.selectedPart()),()->{
                    int tile=manager.selectedPart();var group=groups.group();
                    confirm(()->{if(java.util.Objects.equals(group,groups.group()))groups.unpublish(tile);},false);
                    confirmationTitle=tr("Убрать карту из группы?","Unshare this map?");
                    confirmationDetail=tr("Карта ","Map ")+(tile+1);
                });
            if(shownRole.equals("member")){
                button("tracker.group.previous",tr("Пред.","Prev."),WorkshopIcon.BACK,x,y+120,64,
                    ()->groups.placements().size()>1&&!manager.groupPlacementBusy(),()->manager.cycleSharedPlacement(-1));
                button("tracker.group.next",tr("След.","Next"),WorkshopIcon.MORE,x+w-64,y+120,64,
                    ()->groups.placements().size()>1&&!manager.groupPlacementBusy(),()->manager.cycleSharedPlacement(1));
            }
        }
        button("global.back",tr("Назад","Back"),WorkshopIcon.BACK,x,r.bottom()-28,w,()->true,this::close);
    }
    @Override public void tick() {
        super.tick();
        if(!shownRole.equals(role())){confirmation=null;redraw();return;}
        if(code!=null&&groups.invite().isBlank()&&!shownInvite.isBlank()){
            shownInvite="";draft="";code.setText("");
        }
        if(code!=null&&!groups.invite().isBlank()&&!shownInvite.equals(groups.invite())){
            shownInvite=groups.invite();draft=shownInvite;code.setText(draft);
        }
    }
    private String status() {
        var placement=manager.groupPlacementStatus();
        if(placement!=LiveBuildClient.GroupPlacementStatus.NONE)return switch(placement){
            case SHARING->tr("Отправка схемы и закрепа…","Sharing schematic and placement…");
            case SHARED->tr("Схема и закреп доступны группе","Schematic and placement shared");
            case ACCEPTING->tr("Размещение схемы…","Placing schematic…");
            case PLACED->tr("Схема размещена, трекер включён","Schematic placed, tracker active");
            case FAILED->tr("Не удалось применить закреп. Повторите.","Could not apply placement. Retry.");
            case STALE->tr("Закреп изменился. Примите его заново.","Placement changed. Accept it again.");
            case LITEMATICA->tr("Не удалось разместить. Проверьте Litematica.","Could not place. Check Litematica.");
            case ANCHOR_ONLY->tr("Закреп принят, схема не размещена. Повторите.","Anchor accepted, schematic not placed. Retry.");
            case DIMENSION->tr("Перейдите в нужное измерение","Enter the matching dimension");
            default->"";
        };
        if(manager.sourceBusy())return tr("Передача схемы ","Transferring source ")+manager.sourcePercent()+"%";
        if(manager.sourceFailed())return tr("Не удалось передать схему","Source transfer failed");
        if(manager.sourceComplete())return tr("Схема готова","Source ready");
        if(manager.groupSource()==null&&groups.group()==null)return tr("Схема не загружена","No schematic loaded");
        if(groups.busy())return tr("Подключение…","Connecting…");
        return switch(groups.error()){
            case LOGIN->tr("Нужен вход в аккаунт","Sign in required");
            case CONSENT->tr("Нужно подтверждение","Confirmation required");
            case CODE->tr("Неверный код","Invalid code");
            case MISMATCH->tr("В группе другая схема","Group uses a different schematic");
            case DIMENSION->tr("Другое измерение","Different dimension");
            case DENIED->tr("Доступ закрыт","Access denied");
            case CONFLICT->tr("Данные изменились","Group state changed");
            case LIMIT->tr("Достигнут лимит","Limit reached");
            case UNAVAILABLE->tr("Сервис недоступен","Service unavailable");
            case NONE->!groups.available()?tr("Нужен вход в аккаунт","Sign in required"):groups.group()==null?tr("Нет группы","No group"):
                manager.groupSyncFailed()?tr("Синхронизация прервана","Sync interrupted"):
                groups.group().role().equals("owner")?tr("Владелец стройки","Build owner"):tr("Участник стройки","Build member");
        };
    }
    @Override public void render(DrawContext g,int mouseX,int mouseY,float delta) {
        var r=frame();g.fill(0,0,width,height,0x88000000);WorkshopChrome.frame(g::fill,r,theme);
        WorkshopDraw.text(g,textRenderer,tr("Совместная стройка","Group build"),r.x()+12,r.y()+12,r.width()-24,theme.color("text-primary"));
        WorkshopDraw.text(g,textRenderer,status(),r.x()+12,r.y()+36,r.width()-24,theme.color("text-secondary"));
        if(confirmation!=null){
            WorkshopDraw.text(g,textRenderer,confirmationTitle!=null?confirmationTitle:closingGroup?tr("Закрыть стройку для всех?","Close the build for everyone?"):tr("Делиться данными стройки?","Share build data?"),
                r.x()+12,r.y()+64,r.width()-24,theme.color("text-primary"));
            if(!closingGroup)WorkshopDraw.text(g,textRenderer,confirmationDetail!=null?confirmationDetail:tr("Только с участниками по приглашению.","Only with invited members."),
                r.x()+12,r.y()+82,r.width()-24,theme.color("text-secondary"));
        }else if(shownRole.equals("member")){
            var remote=manager.sharedPlacement();
            String label=remote==null?tr("Нет закрепов","No placements"):tr("Карта ","Map ")+(remote.tile()+1)+" / "+(groups.group().source().wide()*groups.group().source().tall());
            WorkshopDraw.text(g,textRenderer,label,r.x()+84,r.y()+176,r.width()-168,theme.color("text-primary"));
        }
        super.render(g,mouseX,mouseY,delta);
    }
    @Override public void close(){MinecraftClient.getInstance().setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
