package art.mapkluss.companion;

/** Logical-pixel geometry, independent of Minecraft GUI scale. */
final class WorkshopLayout {
    static final int GAP = 4;
    static final int CONTROL = 24;
    static final int ICON = 16;
    record Rect(int x, int y, int width, int height) {
        Rect { if (width < 0 || height < 0) throw new IllegalArgumentException("Negative bounds"); }
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean contains(Rect other) { return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom(); }
        boolean intersects(Rect other) { return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y; }
    }
    record Shell(Rect frame, Rect navigation, Rect tabs, Rect list, Rect preview, Rect metadata, Rect actions, Rect footer, boolean split) {}
    record Appearance(Rect frame, java.util.List<Rect> themes, Rect language, Rect back) {}
    record Form(Rect frame, Rect navigation, Rect tabs, Rect preview, Rect footer) {}

    static Form account(int viewportWidth, int viewportHeight) {
        int width=Math.min(600,viewportWidth-16), height=Math.min(264,viewportHeight-16);
        var frame=new Rect((viewportWidth-width)/2,(viewportHeight-height)/2,width,height);
        var nav=new Rect(frame.x()+6,frame.y()+6,width-12,28);
        var tabs=new Rect(nav.x(),nav.bottom()+4,nav.width(),24);
        var footer=new Rect(nav.x(),frame.bottom()-26,nav.width(),20);
        return new Form(frame,nav,tabs,new Rect(nav.x(),tabs.bottom()+4,nav.width(),footer.y()-tabs.bottom()-8),footer);
    }

    static Appearance appearance(int viewportWidth, int viewportHeight) {
        int width = Math.min(420, viewportWidth - 16), height = Math.min(224, viewportHeight - 16);
        var frame = new Rect((viewportWidth - width) / 2, (viewportHeight - height) / 2, width, height);
        int cellWidth = (width - 28) / 2;
        var choices = new java.util.ArrayList<Rect>();
        for (int i = 0; i < WorkshopTheme.IDS.size(); i++) choices.add(new Rect(frame.x() + 12 + (i % 2) * (cellWidth + 4), frame.y() + 38 + (i / 2) * 30, cellWidth, 26));
        return new Appearance(frame, java.util.List.copyOf(choices),
            new Rect(frame.x() + 12, frame.bottom() - 38, width - 64, 24),
            new Rect(frame.right() - 40, frame.bottom() - 38, 28, 24));
    }

    static Shell library(int viewportWidth, int viewportHeight) {
        if (viewportWidth < 240 || viewportHeight < 180) throw new IllegalArgumentException("Viewport below supported minimum");
        int width = viewportWidth >= 960 ? Math.min(900, Math.round(viewportWidth * .635f)) : viewportWidth - 16;
        int height = viewportHeight >= 540 ? Math.min(620, Math.round(viewportHeight * .8f)) : viewportHeight - 16;
        int x = (viewportWidth - width) / 2, y = (viewportHeight - height) / 2;
        Rect frame = new Rect(x, y, width, height);
        x += 6; y += 6; width -= 12; height -= 12;
        Rect nav = new Rect(x, y, width, 28);
        Rect tabs = new Rect(x, nav.bottom() + GAP, width, 24);
        Rect footer = new Rect(x, frame.bottom() - 26, width, 20);
        int bodyY = tabs.bottom() + GAP;
        int bodyHeight = footer.y() - GAP - bodyY;
        boolean split = width >= 548 && bodyHeight >= 240;
        int listWidth = split ? Math.round(width * .308f) : 0;
        Rect list = new Rect(x, bodyY, listWidth, bodyHeight);
        int detailX = split ? list.right() + GAP : x;
        int detailWidth = split ? width - listWidth - GAP : width;
        Rect actions = new Rect(detailX, footer.y() - GAP - 28, detailWidth, 28);
        Rect metadata = new Rect(detailX, actions.y() - GAP - 32, detailWidth, 32);
        Rect preview = new Rect(detailX, bodyY, detailWidth, Math.max(0, metadata.y() - GAP - bodyY));
        return new Shell(frame, nav, tabs, list, preview, metadata, actions, footer, split);
    }

    static Rect contain(Rect area, int imageWidth, int imageHeight) {
        if (imageWidth <= 0 || imageHeight <= 0) return new Rect(area.x, area.y, 0, 0);
        double scale = Math.min((double) area.width / imageWidth, (double) area.height / imageHeight);
        int width = (int) Math.floor(imageWidth * scale), height = (int) Math.floor(imageHeight * scale);
        return new Rect(area.x + (area.width - width) / 2, area.y + (area.height - height) / 2, width, height);
    }
}
