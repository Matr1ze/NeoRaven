package xyz.daikirai.gui;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import xyz.daikirai.client.ClientConfig;
import xyz.daikirai.client.Profiles;
import xyz.daikirai.gui.onyx.hud.Island;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.ClickGuiModule;
import xyz.daikirai.module.render.WatermarkModule;

/** Shared single-line editor presented as an input field on the island. */
public final class IslandTextEditor extends GuiScreen {
    private final GuiScreen parent;
    private final WatermarkModule module;
    private final Island island;
    private final String initialText;
    private final Consumer<String> save;
    private final UI ui = new UI();
    private final boolean previousRepeat = Keyboard.areRepeatEventsEnabled();
    private GuiTextField field;
    private float[] bounds;

    public IslandTextEditor(GuiScreen parent, WatermarkModule module) {
        this(parent, module.islandText(), value -> {
            String old = module.islandText();
            module.setIslandText(value);
            if (!old.equals(module.islandText())) {
                WatermarkModule.island().refreshIdleLabel();
                // The label belongs to the active config now, so it saves with the profile; a fresh
                // install without a config keeps it in the single-file fallback.
                Profiles.markModified();
                if (Profiles.current != null) {
                    Profiles.current.save();
                } else {
                    ClientConfig.save();
                }
            }
        });
    }

    public IslandTextEditor(GuiScreen parent, String initialText, Consumer<String> save) {
        this.parent = parent;
        this.module = WatermarkModule.instance;
        this.island = WatermarkModule.island();
        this.initialText = initialText;
        this.save = save;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        String value = field == null ? initialText : field.getText();
        bounds = island.textEditorBounds();
        float inset = island.textEditorInset(bounds[3], module.logo());
        int fieldX = Math.round(bounds[0] + inset);
        int fieldY = Math.round(bounds[1] + (bounds[3] - fontRendererObj.FONT_HEIGHT) / 2.0F);
        field = new GuiTextField(0, fontRendererObj, fieldX, fieldY,
                Math.max(1, Math.round(bounds[2] - inset - 8)), fontRendererObj.FONT_HEIGHT);
        field.setMaxStringLength(WatermarkModule.MAX_ISLAND_TEXT);
        field.setEnableBackgroundDrawing(false);
        field.setCanLoseFocus(false);
        field.setText(value);
        field.setFocused(true);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ClickGuiModule.sync();
        if (module.frosted()) Blur.prepare();
        if (mc.theWorld == null) drawBackground(0);
        Scheme scheme = Theme.scheme();
        ui.begin();
        try {
            island.drawTextEditor(ui, scheme, module, bounds);
        } finally {
            ui.end();
        }
        field.setTextColor(scheme.onSurface);
        field.drawTextBox();
    }

    @Override
    public void updateScreen() {
        field.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typed, int key) {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER || key == Keyboard.KEY_ESCAPE) {
            confirm();
        } else {
            field.textboxKeyTyped(typed, key);
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        if (x < bounds[0] || x >= bounds[0] + bounds[2] || y < bounds[1] || y >= bounds[1] + bounds[3]) {
            confirm();
        } else {
            field.mouseClicked(x, y, button);
        }
    }

    private void confirm() {
        save.accept(field.getText());
        if (parent == ClickGui.instance) ClickGuiModule.applyScale();
        mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(previousRepeat);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
