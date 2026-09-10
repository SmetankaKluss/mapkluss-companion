package art.mapkluss.companion;

final class WorkshopLensLayout {
    record Layout(WorkshopLayout.Rect frame, WorkshopLayout.Rect navigation,
        WorkshopLayout.Rect status, WorkshopLayout.Rect join, WorkshopLayout.Rect tabs,
        WorkshopLayout.Rect list, WorkshopLayout.Rect visibility, WorkshopLayout.Rect preview,
        WorkshopLayout.Rect metadata, WorkshopLayout.Rect actions, WorkshopLayout.Rect footer,
        boolean split) {
        int rows() { return Math.max(1, list.height() / 26); }
        WorkshopLayout.Rect row(int index) {
            if (index < 0 || index >= rows()) throw new IndexOutOfBoundsException(index);
            return new WorkshopLayout.Rect(list.x(), list.y() + index * 26, list.width(), 22);
        }
    }
    static Layout at(int width, int height) {
        var base = WorkshopLayout.library(width, height);
        var nav = base.navigation();
        var status = new WorkshopLayout.Rect(nav.x(), nav.bottom() + 4, nav.width(), 20);
        int y = status.bottom() + 4;
        var actions = new WorkshopLayout.Rect(nav.x(), base.footer().y() - 28, nav.width(), 24);
        boolean split = nav.width() >= 548 && actions.y() - y >= 148;
        int sideWidth = split ? Math.max(180, nav.width() * 31 / 100) : nav.width();
        var join = new WorkshopLayout.Rect(nav.x(), y, sideWidth, 24);
        var tabs = new WorkshopLayout.Rect(nav.x(), join.bottom() + 4, sideWidth, 22);
        var visibility = new WorkshopLayout.Rect(nav.x(), actions.y() - 26, sideWidth, 22);
        var list = new WorkshopLayout.Rect(nav.x(), tabs.bottom() + 4, sideWidth,
            Math.max(22, visibility.y() - tabs.bottom() - 8));
        int px = split ? nav.x() + sideWidth + 6 : nav.x();
        int pw = split ? nav.width() - sideWidth - 6 : nav.width();
        var meta = new WorkshopLayout.Rect(px, actions.y() - 26, pw, 22);
        var preview = new WorkshopLayout.Rect(px, y, pw, Math.max(0, meta.y() - y - 4));
        return new Layout(base.frame(), nav, status, join, tabs, list, visibility, preview, meta, actions, base.footer(), split);
    }
}

