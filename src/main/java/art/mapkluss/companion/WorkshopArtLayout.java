package art.mapkluss.companion;

final class WorkshopArtLayout {
    record Layout(WorkshopLayout.Rect frame, WorkshopLayout.Rect navigation,
        WorkshopLayout.Rect title, WorkshopLayout.Rect preview, WorkshopLayout.Rect tabs,
        WorkshopLayout.Rect controls, WorkshopLayout.Rect primary, WorkshopLayout.Rect footer,
        boolean split) {}

    static Layout at(int width, int height) {
        var base = WorkshopLayout.library(width, height);
        var nav = base.navigation();
        var title = new WorkshopLayout.Rect(nav.x(),nav.bottom()+4,nav.width(),20);
        var primary = new WorkshopLayout.Rect(nav.x(),base.footer().y()-32,nav.width(),28);
        int bodyY=title.bottom()+4, bodyHeight=primary.y()-4-bodyY;
        boolean split=nav.width()>=548 && bodyHeight>=120;
        int inspectorWidth=split ? Math.min(300,nav.width()*43/100) : nav.width();
        int inspectorX=nav.right()-inspectorWidth;
        var tabs=new WorkshopLayout.Rect(inspectorX,bodyY,inspectorWidth,24);
        var controls=new WorkshopLayout.Rect(inspectorX,tabs.bottom()+4,inspectorWidth,Math.max(0,bodyHeight-28));
        var preview=new WorkshopLayout.Rect(nav.x(),bodyY,split ? inspectorX-nav.x()-6 : nav.width(),Math.max(0,bodyHeight));
        return new Layout(base.frame(),nav,title,preview,tabs,controls,primary,base.footer(),split);
    }
}
