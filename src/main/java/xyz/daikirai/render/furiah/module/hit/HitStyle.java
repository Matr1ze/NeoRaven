package xyz.daikirai.render.furiah.module.hit;

import java.util.function.Supplier;
import xyz.daikirai.render.furiah.fx.Lifecycle;
import xyz.daikirai.render.furiah.module.hit.styles.ArcLightningStyle;
import xyz.daikirai.render.furiah.module.hit.styles.BlockBurstStyle;
import xyz.daikirai.render.furiah.module.hit.styles.CardFlurryStyle;
import xyz.daikirai.render.furiah.module.hit.styles.CorruptStyle;
import xyz.daikirai.render.furiah.module.hit.styles.CrescentSlashStyle;
import xyz.daikirai.render.furiah.module.hit.styles.GearBurstStyle;
import xyz.daikirai.render.furiah.module.hit.styles.GlassShatterStyle;
import xyz.daikirai.render.furiah.module.hit.styles.HexShieldStyle;
import xyz.daikirai.render.furiah.module.hit.styles.IconBurstStyle;
import xyz.daikirai.render.furiah.module.hit.styles.InkSlashStyle;
import xyz.daikirai.render.furiah.module.hit.styles.MosaicStyle;
import xyz.daikirai.render.furiah.module.hit.styles.PaperBurstStyle;
import xyz.daikirai.render.furiah.module.hit.styles.PierceStyle;
import xyz.daikirai.render.furiah.module.hit.styles.PowStyle;
import xyz.daikirai.render.furiah.module.hit.styles.QuicksilverStyle;
import xyz.daikirai.render.furiah.module.hit.styles.ScribbleStyle;
import xyz.daikirai.render.furiah.module.hit.styles.ShackleStyle;
import xyz.daikirai.render.furiah.module.hit.styles.ShadowBurstStyle;
import xyz.daikirai.render.furiah.module.hit.styles.ShockwaveConeStyle;
import xyz.daikirai.render.furiah.module.hit.styles.SoulKnockStyle;
import xyz.daikirai.render.furiah.module.hit.styles.SparkSprayStyle;
import xyz.daikirai.render.furiah.module.hit.styles.ThornLashStyle;
import xyz.daikirai.render.furiah.module.hit.styles.VortexStyle;
import xyz.daikirai.render.furiah.module.hit.styles.WarpBubbleStyle;
import xyz.daikirai.render.furiah.setting.Displayable;

public enum HitStyle implements Displayable {
   VORTEX("Vortex", "A spinning whirlpool of light", Lifecycle.ANIMATION, VortexStyle::new),
   BLOCK_BURST("Block Burst", "Glowing cubes burst and tumble", Lifecycle.FADE, BlockBurstStyle::new),
   WARP_BUBBLE("Warp Bubble", "A jelly bubble wobbles and pops", Lifecycle.ANIMATION, WarpBubbleStyle::new),
   CRESCENT_SLASH("Crescent Slash", "A blade arc follows your swipe", Lifecycle.FADE, CrescentSlashStyle::new),
   SHOCKWAVE_CONE("Shockwave Cone", "Sonic rings punch through", Lifecycle.FADE, ShockwaveConeStyle::new),
   SPARK_SPRAY("Spark Spray", "Grinder sparks bounce off the floor", Lifecycle.FADE, SparkSprayStyle::new),
   GLASS_SHATTER("Glass Shatter", "A pane cracks, then shatters", Lifecycle.ANIMATION, GlassShatterStyle::new),
   HEX_SHIELD("Hex Shield", "A honeycomb shield ripples", Lifecycle.ANIMATION, HexShieldStyle::new),
   ARC_LIGHTNING("Arc Lightning", "Crackling arcs crawl over the body", Lifecycle.FADE, ArcLightningStyle::new),
   ICON_BURST("Icon Burst", "Hearts, stars or skulls pop out", Lifecycle.FADE, IconBurstStyle::new),
   SOUL_KNOCK("Soul Knock", "A ghost is knocked out of the body", Lifecycle.ANIMATION, SoulKnockStyle::new),
   PIERCE("Pierce", "A lance of light stabs through", Lifecycle.FADE, PierceStyle::new),
   THORN_LASH(
      "Thorn Lash",
      "Thorny vines coil, snap and shed petals",
      Lifecycle.ANIMATION,
      true,
      ThornLashStyle::new,
      "thorns",
      "vines",
      "roses",
      "petals",
      "bramble",
      "gothic"
   ),
   INK_SLASH(
      "Ink Slash",
      "A bold brush stroke slashes across",
      Lifecycle.FADE,
      true,
      InkSlashStyle::new,
      "ink",
      "brush",
      "slash",
      "sumi-e",
      "calligraphy",
      "japanese",
      "seal"
   ),
   PAPER_BURST(
      "Paper Burst",
      "Paper squares fold into cranes as they fly",
      Lifecycle.ANIMATION,
      true,
      PaperBurstStyle::new,
      "paper",
      "origami",
      "cranes",
      "folded",
      "confetti",
      "pastel",
      "cute"
   ),
   MOSAIC(
      "Mosaic",
      "A stained-glass medallion drops its cells",
      Lifecycle.ANIMATION,
      true,
      MosaicStyle::new,
      "stained glass",
      "mosaic",
      "medallion",
      "cathedral",
      "church",
      "gothic"
   ),
   CORRUPT(
      "Corrupt",
      "The target glitches, then snaps back",
      Lifecycle.FADE,
      true,
      CorruptStyle::new,
      "glitch",
      "corrupt",
      "error",
      "rgb",
      "datamosh",
      "digital",
      "cyber"
   ),
   QUICKSILVER(
      "Quicksilver",
      "Liquid chrome splashes, then scatters",
      Lifecycle.ANIMATION,
      true,
      QuicksilverStyle::new,
      "chrome",
      "liquid",
      "metal",
      "mercury",
      "quicksilver",
      "splash",
      "y2k"
   ),
   SHADOW_BURST(
      "Shadow Burst",
      "Black smoke bursts out of their back",
      Lifecycle.FADE,
      true,
      ShadowBurstStyle::new,
      "shadow",
      "smoke",
      "dark",
      "burst",
      "embers",
      "demon"
   ),
   GEAR_BURST(
      "Gear Burst",
      "A clock ticks round, then bursts into gears",
      Lifecycle.ANIMATION,
      true,
      GearBurstStyle::new,
      "clockwork",
      "gears",
      "cogs",
      "brass",
      "clock",
      "steampunk"
   ),
   CARD_FLURRY(
      "Card Flurry",
      "Tarot cards fan out, flip and burn",
      Lifecycle.ANIMATION,
      true,
      CardFlurryStyle::new,
      "tarot",
      "cards",
      "occult",
      "mystic",
      "arcana",
      "fortune"
   ),
   SCRIBBLE(
      "Scribble",
      "A pencil scribble and doodles, then an eraser",
      Lifecycle.FADE,
      true,
      ScribbleStyle::new,
      "doodle",
      "scribble",
      "sketch",
      "pencil",
      "marker",
      "chalk",
      "cartoon"
   ),
   SHACKLE(
      "Shackle", "A golden chain locks, then bursts", Lifecycle.ANIMATION, true, ShackleStyle::new, "chain", "chains", "gold", "shackle", "padlock", "bind"
   ),
   POW("Pow", "A comic-book POW! pops out", Lifecycle.ANIMATION, true, PowStyle::new, "comic", "pow", "bam", "pop art", "halftone", "cartoon", "onomatopoeia");

   private final String displayName;
   private final String description;
   private final Lifecycle lifecycle;
   private final Supplier<HitStyleBase> factory;
   private final boolean isNew;
   private final String[] keywords;

   private HitStyle(String displayName, String description, Lifecycle lifecycle, Supplier<HitStyleBase> factory) {
      this(displayName, description, lifecycle, false, factory);
   }

   private HitStyle(
      String displayName, String description, Lifecycle lifecycle, boolean isNew, Supplier<HitStyleBase> factory, String... keywords
   ) {
      this.displayName = displayName;
      this.description = description;
      this.lifecycle = lifecycle;
      this.isNew = isNew;
      this.factory = factory;
      this.keywords = keywords;
   }

   @Override
   public String[] keywords() {
      return this.keywords.length == 0 ? null : (String[])this.keywords.clone();
   }

   @Override
   public boolean isNew() {
      return this.isNew;
   }

   @Override
   public String displayName() {
      return this.displayName;
   }

   @Override
   public String description() {
      return this.description;
   }

   @Override
   public String tag() {
      return this.lifecycle.tag();
   }

   public Lifecycle lifecycle() {
      return this.lifecycle;
   }

   HitStyleBase create() {
      return this.factory.get();
   }
}
