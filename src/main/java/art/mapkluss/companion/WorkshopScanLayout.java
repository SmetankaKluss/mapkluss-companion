package art.mapkluss.companion;

final class WorkshopScanLayout {
    record Layout(WorkshopLayout.Rect frame, WorkshopLayout.Rect navigation,
        WorkshopLayout.Rect title, WorkshopLayout.Rect modes, WorkshopLayout.Rect tabs,
        WorkshopLayout.Rect details, WorkshopLayout.Rect preview, WorkshopLayout.Rect actions,
        WorkshopLayout.Rect footer, boolean split) { }

    static WorkshopLayout.Rect part(WorkshopLayout.Rect row, int index, int count) {
        if (count < 1 || index < 0 || index >= count) throw new IllegalArgumentException("Invalid cell");
        int w = (row.width() - (count - 1) * 4) / count;
        return new WorkshopLayout.Rect(row.x() + index * (w + 4), row.y(),
            index == count - 1 ? row.width() - index * (w + 4) : w, row.height());
    }

    static Layout at(int width, int height) {
        var base = WorkshopLayout.library(width, height);
        var nav = base.navigation();
        var title = new WorkshopLayout.Rect(nav.x(), nav.bottom() + 4, nav.width(), 20);
        var modes = new WorkshopLayout.Rect(nav.x(), title.bottom() + 4, nav.width(), 20);
        var tabs = new WorkshopLayout.Rect(nav.x(), modes.bottom() + 4, nav.width(), 20);
        var actions = new WorkshopLayout.Rect(nav.x(), base.footer().y() - 24, nav.width(), 20);
        int y = tabs.bottom() + 4;
        int h = Math.max(0, actions.y() - y - 4);
        boolean split = nav.width() >= 548 && h >= 90;
        int dw = split ? Math.max(160, nav.width() * 32 / 100) : nav.width();
        var details = new WorkshopLayout.Rect(nav.x(), y, dw, h);
        var preview = new WorkshopLayout.Rect(split ? details.right() + 6 : nav.x(), y,
            split ? nav.width() - dw - 6 : nav.width(), h);
        return new Layout(base.frame(), nav, title, modes, tabs, details, preview, actions, base.footer(), split);
    }
}
