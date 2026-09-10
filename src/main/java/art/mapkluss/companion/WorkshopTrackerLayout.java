package art.mapkluss.companion;

final class WorkshopTrackerLayout {
    record Layout(WorkshopLayout.Rect frame, WorkshopLayout.Rect navigation,
        WorkshopLayout.Rect title, WorkshopLayout.Rect modes, WorkshopLayout.Rect header,
        WorkshopLayout.Rect table, WorkshopLayout.Rect footer, int rowHeight, boolean compact) {
        int rows() { return table.height() / rowHeight; }
        WorkshopLayout.Rect row(int i) {
            if (i < 0 || i >= rows()) throw new IndexOutOfBoundsException(i);
            return new WorkshopLayout.Rect(table.x(), table.y() + i * rowHeight, table.width(), rowHeight - 4);
        }
        WorkshopLayout.Rect controls(WorkshopLayout.Rect row) {
            int w = compact ? row.width() : 232;
            return new WorkshopLayout.Rect(row.right() - w, compact ? row.y() + 21 : row.y() + 1, w, 18);
        }
    }
    static Layout at(int width, int height) {
        var base = WorkshopLayout.library(width,height);
        var nav = base.navigation();
        var title = new WorkshopLayout.Rect(nav.x(),nav.bottom()+4,nav.width(),20);
        var modes = new WorkshopLayout.Rect(nav.x(),title.bottom()+4,nav.width(),20);
        int hh = height < 220 ? 0 : 16;
        var header = new WorkshopLayout.Rect(nav.x(),modes.bottom()+4,nav.width(),hh);
        var table = new WorkshopLayout.Rect(nav.x(),header.bottom(),nav.width(),
            Math.max(0,base.footer().y()-4-header.bottom()));
        boolean compact = nav.width() < 548;
        return new Layout(base.frame(),nav,title,modes,header,table,base.footer(),compact?44:26,compact);
    }
    static WorkshopLayout.Rect part(WorkshopLayout.Rect row,int index,int count) {
        return WorkshopScanLayout.part(row,index,count);
    }
}
