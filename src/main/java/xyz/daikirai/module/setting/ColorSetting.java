package xyz.daikirai.module.setting;

public final class ColorSetting extends ButtonSetting {
    private int color;
    private boolean accent = true;
    private final float hueShift;
    private final ColorSetting inherited;
    private Integer preview;

    public ColorSetting(String name, int color) {
        this(name, color, 0);
    }

    public ColorSetting(String name, int color, float hueShift) {
        this(name, color, hueShift, null);
    }

    /** While following, use another palette's effective color as the default. */
    public ColorSetting(String name, int color, ColorSetting inherited) {
        this(name, color, 0, inherited);
    }

    private ColorSetting(String name, int color, float hueShift, ColorSetting inherited) {
        super(name);
        this.color = color | 0xFF000000;
        this.hueShift = hueShift;
        this.inherited = inherited;
        this.saved = true;
    }

    public int color() { return preview != null ? preview : accent ? themeColor() : color; }

    public void drawPreview(int rgb, Runnable render) {
        Integer before = preview;
        preview = rgb;
        try {
            render.run();
        } finally {
            preview = before;
        }
    }
    public int customColor() { return color; }
    public boolean followsTheme() { return accent; }
    public String followLabel() { return inherited == null ? "Follow theme" : "Module color"; }

    public int themeColor() {
        if (inherited != null) return inherited.color();
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
