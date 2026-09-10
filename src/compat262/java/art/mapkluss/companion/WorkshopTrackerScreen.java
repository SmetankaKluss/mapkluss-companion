package art.mapkluss.companion;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.KeyEvent;

abstract class WorkshopTrackerScreen extends Screen {
    protected WorkshopTrackerScreen(Component title) { super(title); }
    protected abstract boolean submitFocusedInput();
    @Override public boolean keyPressed(KeyEvent event) {
        int key = event.input();
        if ((key == 257 || key == 335) && submitFocusedInput()) return true;
        return super.keyPressed(event);
    }
}
