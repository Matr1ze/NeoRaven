package xyz.daikirai.module.setting;

public final class ColorSetting extends ButtonSetting {
    private int color;
    private boolean accent = true;
    private final float hueShift;

    public ColorSetting(String name, int color) {
        this(name, color, 0);
    }

    public ColorSetting(String name, int color, float hueShift) {
        super(name);
        this.color = color | 0xFF000000;
        this.hueShift = hueShift;
        this.saved = true;
    }

    public int color() { return accent ? themeColor() : color; }
    public int customColor() { return color; }
    public boolean followsTheme() { return accent; }
    public int themeColor() {
        int primary = xyz.daikirai.gui.onyx.material.Theme.scheme().primary;
        if (hueShift == 0) return primary;
        float[] hsv = java.awt.Color.RGBtoHSB(primary >> 16 & 255, primary >> 8 & 255, primary & 255, null);
        return java.awt.Color.HSBtoRGB((hsv[0] + hueShift / 360) % 1, hsv[1], hsv[2]);
    }

    public void setColor(int color) {
        setAppearance(color, false);
    }

    public void setAppearance(int color, boolean accent) {
        this.color = color | 0xFF000000;
        this.accent = accent;
        xyz.daikirai.client.Profiles.markModified();
    }
}
