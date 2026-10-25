package xyz.daikirai.render;

public final class XYRecord {
    private final float x, y, alpha;
    public XYRecord(float x, float y, float alpha) { this.x = x; this.y = y; this.alpha = alpha; }
    public float x() { return x; }
    public float y() { return y; }
    public float alphaMul() { return alpha; }
}
