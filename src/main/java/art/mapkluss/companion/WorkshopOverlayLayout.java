package art.mapkluss.companion;

final class WorkshopOverlayLayout {
    private WorkshopOverlayLayout() {}

    static WorkshopLayout.Rect consent(int width,int height,int bodyHeight) {
        int w=Math.max(24,Math.min(420,width-16)),h=bodyHeight+126;
        return new WorkshopLayout.Rect((width-w)/2,Math.max(8,(height-h)/2),w,h);
    }

    static WorkshopLayout.Rect update(int width, int height) {
        int w = Math.max(0, Math.min(420, width - 16));
        int h = Math.max(0, Math.min(148, height - 16));
        return new WorkshopLayout.Rect((width-w)/2, (height-h)/2, w, h);
    }

    static WorkshopLayout.Rect action(WorkshopLayout.Rect frame, int index) {
        int gap = 4, available = Math.max(0, frame.width()-24);
        int w = Math.max(0, (available-gap*2)/3);
        return new WorkshopLayout.Rect(frame.x()+12+index*(w+gap), frame.bottom()-36, w, 24);
    }

    static WorkshopLayout.Rect lens(int width, int measured) {
        return new WorkshopLayout.Rect(5, 5, Math.max(0, Math.min(width-10, measured+16)), 22);
    }
}
