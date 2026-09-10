package art.mapkluss.companion;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.client.input.KeyInput;

abstract class WorkshopTrackerScreen extends Screen {
    protected WorkshopTrackerScreen(Text title) { super(title); }
    protected abstract boolean submitFocusedInput();
    @Override public boolean keyPressed(KeyInput event) {
        int key = event.getKeycode();
        if ((key == 257 || key == 335) && submitFocusedInput()) return true;
        return super.keyPressed(event);
    }
}
