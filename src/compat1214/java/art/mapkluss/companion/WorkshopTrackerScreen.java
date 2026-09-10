package art.mapkluss.companion;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

abstract class WorkshopTrackerScreen extends Screen {
    protected WorkshopTrackerScreen(Text title) { super(title); }
    protected abstract boolean submitFocusedInput();
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode;
        if ((key == 257 || key == 335) && submitFocusedInput()) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
