package art.mapkluss.companion;

final class WorkshopTwoLayerLayout {
    record Layout(WorkshopLayout.Rect frame, WorkshopLayout.Rect navigation,
        WorkshopLayout.Rect title, WorkshopLayout.Rect list, WorkshopLayout.Rect paging,
        WorkshopLayout.Rect preview, WorkshopLayout.Rect metadata,
        WorkshopLayout.Rect actions, WorkshopLayout.Rect footer, boolean split) {
        int rows() { return Math.max(1, list.height() / 28); }
        WorkshopLayout.Rect row(int index) {
            if (index < 0 || index >= rows()) throw new IndexOutOfBoundsException(index);
            return new WorkshopLayout.Rect(list.x(), list.y() + index * 28, list.width(), 24);
        }
        WorkshopLayout.Rect source(int index) {
            if (index < 0 || index > 1) throw new IndexOutOfBoundsException(index);
            if (rows() >= 2) return row(index);
            int w = (list.width() - 4) / 2;
            return new WorkshopLayout.Rect(list.x() + index * (w + 4), list.y(), w, 24);
        }
    }

    static Layout at(int width, int height) {
        var base = WorkshopLayout.library(width, height);
        var nav = base.navigation();
        var title = new WorkshopLayout.Rect(nav.x(), nav.bottom() + 4, nav.width(), height < 220 ? 14 : 20);
        var actions = new WorkshopLayout.Rect(nav.x(), base.footer().y() - 28, nav.width(), 24);
        int y = title.bottom() + 4;
        boolean split = nav.width() >= 548 && actions.y() - y >= 128;
        int lw = split ? Math.max(160, nav.width() * 30 / 100) : nav.width();
        var paging = new WorkshopLayout.Rect(nav.x(), actions.y() - 26, lw, 22);
        var list = new WorkshopLayout.Rect(nav.x(), y, lw, Math.max(24, paging.y() - y - 4));
        int px = split ? nav.x() + lw + 6 : nav.x();
        int pw = split ? nav.width() - lw - 6 : nav.width();
        var metadata = new WorkshopLayout.Rect(px, paging.y(), pw, 22);
        var preview = new WorkshopLayout.Rect(px, y, pw, list.height());
        return new Layout(base.frame(), nav, title, list, paging, preview, metadata, actions, base.footer(), split);
    }
}
