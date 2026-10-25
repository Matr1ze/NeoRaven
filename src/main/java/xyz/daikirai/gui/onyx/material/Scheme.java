package xyz.daikirai.gui.onyx.material;

/**
 * Material 3 colour roles, generated from a single accent seed exactly like the Onyx
 * ClickGUI did ({@code theme.PrimaryOnPrimaryRecord}).
 *
 * <p>The scheme is always the dark variant: surfaces sit between tone 4 and 24, the accent
 * roles between tone 20 and 90. {@code tint} controls how much accent colour bleeds into the
 * neutral (surface) palettes, {@code neutralCut}/{@code surfaceCut} desaturate very bright or
 * very dim seeds so the seed stays readable.</p>
 */
public final class Scheme {

    public final int primary;
    public final int onPrimary;
    public final int primaryContainer;
    public final int onPrimaryContainer;
    public final int secondary;
    public final int onSecondary;
    public final int secondaryContainer;
    public final int onSecondaryContainer;
    public final int tertiary;
    public final int onTertiary;
    public final int tertiaryContainer;
    public final int onTertiaryContainer;
    public final int error;
    public final int onError;
    public final int errorContainer;
    public final int onErrorContainer;
    public final int surface;
    public final int onSurface;
    public final int surfaceVariant;
    public final int onSurfaceVariant;
    public final int surfaceDim;
    public final int surfaceBright;
    public final int surfaceContainerLowest;
    public final int surfaceContainerLow;
    public final int surfaceContainer;
    public final int surfaceContainerHigh;
    public final int surfaceContainerHighest;
    public final int outline;
    public final int outlineVariant;
    public final int inverseSurface;
    public final int inverseOnSurface;
    public final int inversePrimary;
    public final int scrim;
    public final int shadow;

    private Scheme(int primary, int onPrimary, int primaryContainer, int onPrimaryContainer,
                   int secondary, int onSecondary, int secondaryContainer, int onSecondaryContainer,
                   int tertiary, int onTertiary, int tertiaryContainer, int onTertiaryContainer,
                   int error, int onError, int errorContainer, int onErrorContainer,
                   int surface, int onSurface, int surfaceVariant, int onSurfaceVariant,
                   int surfaceDim, int surfaceBright, int surfaceContainerLowest,
                   int surfaceContainerLow, int surfaceContainer, int surfaceContainerHigh,
                   int surfaceContainerHighest, int outline, int outlineVariant,
                   int inverseSurface, int inverseOnSurface, int inversePrimary,
                   int scrim, int shadow) {
        this.primary = primary;
        this.onPrimary = onPrimary;
        this.primaryContainer = primaryContainer;
        this.onPrimaryContainer = onPrimaryContainer;
        this.secondary = secondary;
        this.onSecondary = onSecondary;
        this.secondaryContainer = secondaryContainer;
        this.onSecondaryContainer = onSecondaryContainer;
        this.tertiary = tertiary;
        this.onTertiary = onTertiary;
        this.tertiaryContainer = tertiaryContainer;
        this.onTertiaryContainer = onTertiaryContainer;
        this.error = error;
        this.onError = onError;
        this.errorContainer = errorContainer;
        this.onErrorContainer = onErrorContainer;
        this.surface = surface;
        this.onSurface = onSurface;
        this.surfaceVariant = surfaceVariant;
        this.onSurfaceVariant = onSurfaceVariant;
        this.surfaceDim = surfaceDim;
        this.surfaceBright = surfaceBright;
        this.surfaceContainerLowest = surfaceContainerLowest;
        this.surfaceContainerLow = surfaceContainerLow;
        this.surfaceContainer = surfaceContainer;
        this.surfaceContainerHigh = surfaceContainerHigh;
        this.surfaceContainerHighest = surfaceContainerHighest;
        this.outline = outline;
        this.outlineVariant = outlineVariant;
        this.inverseSurface = inverseSurface;
        this.inverseOnSurface = inverseOnSurface;
        this.inversePrimary = inversePrimary;
        this.scrim = scrim;
        this.shadow = shadow;
    }

    /**
     * @param accent seed colour (alpha ignored)
     * @param tint   0..1, how much accent the neutral surfaces keep
     */
    public static Scheme of(int accent, float tint) {
        float[] hsv = Material.hsv(accent);
        Material.Hct base = Material.Hct.fromInt(Material.fromHsv(hsv[0], hsv[1], 1.0F, 255));

        double hue = base.hue;
        double chroma = base.chroma;
        double accentStrength = Material.clamp(tint, 0.0F, 1.0F) * Material.clamp(hsv[1], 0.0F, 1.0F);
        double neutralCut = 30.0 * (1.0 - Material.clamp(hsv[2], 0.0F, 1.0F));
        double surfaceCut = (1.0 - Material.clamp(hsv[2], 0.0F, 1.0F)) * 50.0;

        Material.TonalPalette primary = Material.TonalPalette.of(hue, chroma);
        Material.TonalPalette secondary = Material.TonalPalette.of(hue, chroma / 3.0);
        Material.TonalPalette tertiary = Material.TonalPalette.of(hue + 60.0, chroma / 2.0);
        Material.TonalPalette neutral = Material.TonalPalette.of(hue, 36.0 * accentStrength);
        Material.TonalPalette neutralVariant = Material.TonalPalette.of(hue, 56.0 * accentStrength);
        Material.TonalPalette neutralBright = Material.TonalPalette.of(hue, 64.0 * accentStrength);
        Material.TonalPalette error = Material.TonalPalette.of(25.0, 84.0);

        return new Scheme(
                primary.tone(80.0, neutralCut), primary.tone(20.0), primary.tone(30.0, neutralCut), primary.tone(90.0),
                secondary.tone(80.0, neutralCut), secondary.tone(20.0), secondary.tone(30.0, neutralCut), secondary.tone(90.0),
                tertiary.tone(80.0, neutralCut), tertiary.tone(20.0), tertiary.tone(30.0, neutralCut), tertiary.tone(90.0),
                error.tone(80.0), error.tone(20.0), error.tone(30.0), error.tone(90.0),
                neutralVariant.tone(6.0, surfaceCut), neutral.tone(90.0), neutralBright.tone(30.0, surfaceCut), neutralBright.tone(80.0),
                neutralVariant.tone(6.0, surfaceCut), neutralVariant.tone(24.0, surfaceCut),
                neutralVariant.tone(4.0, surfaceCut), neutralVariant.tone(10.0, surfaceCut),
                neutralVariant.tone(12.0, surfaceCut), neutralVariant.tone(17.0, surfaceCut),
                neutralVariant.tone(22.0, surfaceCut),
                neutralBright.tone(60.0, surfaceCut), neutralBright.tone(30.0, surfaceCut),
                neutral.tone(90.0), neutral.tone(20.0), primary.tone(40.0, neutralCut),
                neutral.tone(0.0), neutral.tone(0.0));
    }

    /** Port of {@code theme.Util5.getIntForInt}: Material elevation levels 0..5. */
    public int surfaceLevel(int level) {
        switch (Material.clamp(level, 0, 5)) {
            case 1:
                return this.surfaceContainerLow;
            case 2:
                return this.surfaceContainer;
            case 3:
                return this.surfaceContainerHigh;
            case 4:
            case 5:
                return this.surfaceContainerHighest;
            default:
                return this.surface;
        }
    }

    /** Port of {@code theme.Util5.getFloatForInt}: shadow spread per elevation level. */
    public static float shadowForLevel(int level) {
        float[] levels = {0.0F, 1.0F, 3.0F, 6.0F, 8.0F, 12.0F};
        return levels[Material.clamp(level, 0, 5)];
    }
}
