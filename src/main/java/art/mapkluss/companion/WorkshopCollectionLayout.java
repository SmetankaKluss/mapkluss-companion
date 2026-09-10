package art.mapkluss.companion;

final class WorkshopCollectionLayout {
    static final int ROW = 32;
    record Layout(WorkshopLayout.Rect frame, WorkshopLayout.Rect navigation, WorkshopLayout.Rect heading,
        WorkshopLayout.Rect manage, WorkshopLayout.Rect search, WorkshopLayout.Rect list, WorkshopLayout.Rect footer) {
        int rows() { return Math.max(1,list.height()/ROW); }
        WorkshopLayout.Rect row(int index) {
            if(index<0 || index>=rows()) throw new IllegalArgumentException("Row outside visible page");
            return new WorkshopLayout.Rect(list.x(),list.y()+index*ROW,list.width(),28);
        }
    }
    static Layout at(int width,int height) {
        int w=Math.min(680,width-16), h=Math.min(420,height-16);
        var frame=new WorkshopLayout.Rect((width-w)/2,(height-h)/2,w,h);
        var nav=new WorkshopLayout.Rect(frame.x()+6,frame.y()+6,w-12,28);
        var heading=new WorkshopLayout.Rect(nav.x(),nav.bottom()+4,nav.width(),20);
        var manage=new WorkshopLayout.Rect(nav.x(),heading.bottom()+4,nav.width(),24);
        var search=new WorkshopLayout.Rect(nav.x(),manage.bottom()+4,nav.width(),24);
        var footer=new WorkshopLayout.Rect(nav.x(),frame.bottom()-26,nav.width(),20);
        var list=new WorkshopLayout.Rect(nav.x(),search.bottom()+6,nav.width(),Math.max(0,footer.y()-search.bottom()-10));
        return new Layout(frame,nav,heading,manage,search,list,footer);
    }
}
