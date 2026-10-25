package xyz.daikirai.gui;

import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import xyz.daikirai.client.Profiles;
import xyz.daikirai.module.render.JumpCirclesModule;

public final class JumpCircleEditor extends GuiScreen {
    private final GuiScreen parent;
    private final JumpCirclesModule module;
    private GuiTextField text;

    public JumpCircleEditor(GuiScreen parent, JumpCirclesModule module, boolean focusText) {
        this.parent = parent;
        this.module = module;
    }

    @Override public void initGui() {
        String pending = text == null ? module.text() : text.getText();
        Keyboard.enableRepeatEvents(true);
        text = new GuiTextField(0, fontRendererObj, width / 2 - 100, height / 2 - 10, 200, 20);
        text.setMaxStringLength(64);
        text.setText(pending);
        text.setFocused(true);
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 100, height / 2 + 30, 96, 20, "Save"));
        buttonList.add(new GuiButton(1, width / 2 + 4, height / 2 + 30, 96, 20, "Cancel"));
    }

    @Override public void drawScreen(int x, int y, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "JumpCircles - Text", width / 2, height / 2 - 36, 0xFFFFFF);
        text.drawTextBox();
        super.drawScreen(x, y, partialTicks);
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id == 0) { module.setText(text.getText()); Profiles.markModified(); }
        mc.displayGuiScreen(parent);
    }
    @Override protected void keyTyped(char typed, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) actionPerformed(buttonList.get(0));
        else text.textboxKeyTyped(typed, key);
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        super.mouseClicked(x, y, button); text.mouseClicked(x, y, button);
    }
    @Override public void updateScreen() { text.updateCursorCounter(); }
    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); }
    @Override public boolean doesGuiPauseGame() { return false; }
}
