package xyz.daikirai.module.player;

/** Keeps existing profiles, binds and the HUD counter wired to the full Myau controller. */
public final class Scaffold2Module extends MyauTelly {
    private static final net.minecraft.client.Minecraft MC = net.minecraft.client.Minecraft.getMinecraft();
    public static Scaffold2Module instance;
    public Scaffold2Module(int key) { super(key); instance = this; }
    public static int blockCount() {
        if (MC.thePlayer == null) return 0;
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            net.minecraft.item.ItemStack stack = MC.thePlayer.inventory.getStackInSlot(slot);
            if (xyz.daikirai.util.ItemUtils.isScaffoldBlock(stack)) count += stack.stackSize;
        }
        return count;
    }
    public static boolean islandOwnsCount() {
        xyz.daikirai.module.render.WatermarkModule watermark = xyz.daikirai.module.render.WatermarkModule.instance;
        return watermark != null && watermark.isEnabled() && watermark.scaffoldGauge();
    }
    public static void renderCount() { /* The island supplies the shared block counter. */ }
}
