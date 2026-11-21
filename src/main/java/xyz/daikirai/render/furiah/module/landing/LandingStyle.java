package xyz.daikirai.render.furiah.module.landing;

import java.util.function.Supplier;
import xyz.daikirai.render.furiah.fx.Lifecycle;
import xyz.daikirai.render.furiah.module.landing.styles.BadSectorStyle;
import xyz.daikirai.render.furiah.module.landing.styles.BrambleBloomStyle;
import xyz.daikirai.render.furiah.module.landing.styles.CardSpreadStyle;
import xyz.daikirai.render.furiah.module.landing.styles.CathedralLightStyle;
import xyz.daikirai.render.furiah.module.landing.styles.ChainSlamStyle;
import xyz.daikirai.render.furiah.module.landing.styles.ChromeSplashStyle;
import xyz.daikirai.render.furiah.module.landing.styles.ClockfaceStyle;
import xyz.daikirai.render.furiah.module.landing.styles.DustRingStyle;
import xyz.daikirai.render.furiah.module.landing.styles.EnergyFissuresStyle;
import xyz.daikirai.render.furiah.module.landing.styles.FeatherFallStyle;
import xyz.daikirai.render.furiah.module.landing.styles.HexPulseStyle;
import xyz.daikirai.render.furiah.module.landing.styles.InkBlotStyle;
import xyz.daikirai.render.furiah.module.landing.styles.JumpCirclesStyle;
import xyz.daikirai.render.furiah.module.landing.styles.LightPillarStyle;
import xyz.daikirai.render.furiah.module.landing.styles.LiquidRippleStyle;
import xyz.daikirai.render.furiah.module.landing.styles.NeuralNetStyle;
import xyz.daikirai.render.furiah.module.landing.styles.PopUpStyle;
import xyz.daikirai.render.furiah.module.landing.styles.SealStampStyle;
import xyz.daikirai.render.furiah.module.landing.styles.ShadowPoolStyle;
import xyz.daikirai.render.furiah.module.landing.styles.SketchRingsStyle;
import xyz.daikirai.render.furiah.module.landing.styles.SparkleBurstStyle;
import xyz.daikirai.render.furiah.module.landing.styles.ThudStyle;
import xyz.daikirai.render.furiah.module.landing.styles.WarpDomeStyle;
import xyz.daikirai.render.furiah.module.landing.styles.XRayScanStyle;

public enum LandingStyle {
   JUMP_CIRCLES("Furiah Jump Circles", Lifecycle.FADE, JumpCirclesStyle::new),
   NEURAL_NET("Neural Net", Lifecycle.ANIMATION, NeuralNetStyle::new),
   XRAY_SCAN("X-Ray Scan", Lifecycle.ANIMATION, XRayScanStyle::new),
   HEX_PULSE("Hex Pulse", Lifecycle.FADE, HexPulseStyle::new),
   WARP_DOME("Warp Dome", Lifecycle.ANIMATION, WarpDomeStyle::new),
   DUST_RING("Dust Ring", Lifecycle.FADE, DustRingStyle::new),
   ENERGY_FISSURES("Energy Fissures", Lifecycle.ANIMATION, EnergyFissuresStyle::new),
   SEAL_STAMP("Seal Stamp", Lifecycle.ANIMATION, SealStampStyle::new),
   LIGHT_PILLAR("Light Pillar", Lifecycle.ANIMATION, LightPillarStyle::new),
   FEATHER_FALL("Feather Fall", Lifecycle.FADE, FeatherFallStyle::new),
   LIQUID_RIPPLE("Liquid Ripple", Lifecycle.ANIMATION, LiquidRippleStyle::new),
   SPARKLE_BURST("Sparkle Burst", Lifecycle.FADE, SparkleBurstStyle::new),
   BRAMBLE_BLOOM("Bramble Bloom", Lifecycle.ANIMATION, BrambleBloomStyle::new),
   INK_BLOT("Ink Blot", Lifecycle.ANIMATION, InkBlotStyle::new),
   POP_UP("Pop-Up", Lifecycle.ANIMATION, PopUpStyle::new),
   CATHEDRAL_LIGHT("Cathedral Light", Lifecycle.FADE, CathedralLightStyle::new),
   BAD_SECTOR("Bad Sector", Lifecycle.FADE, BadSectorStyle::new),
   CHROME_SPLASH("Chrome Splash", Lifecycle.ANIMATION, ChromeSplashStyle::new),
   SHADOW_POOL("Shadow Pool", Lifecycle.FADE, ShadowPoolStyle::new),
   CLOCKFACE("Clockface", Lifecycle.ANIMATION, ClockfaceStyle::new),
   CARD_SPREAD("Card Spread", Lifecycle.ANIMATION, CardSpreadStyle::new),
   SKETCH_RINGS("Sketch Rings", Lifecycle.FADE, SketchRingsStyle::new),
   CHAIN_SLAM("Chain Slam", Lifecycle.ANIMATION, ChainSlamStyle::new),
   THUD("Thud", Lifecycle.ANIMATION, ThudStyle::new);

   private final String displayName;
   private final Lifecycle lifecycle;
   private final Supplier<LandingStyleBase> factory;

   LandingStyle(String displayName, Lifecycle lifecycle, Supplier<LandingStyleBase> factory) {
      this.displayName = displayName;
      this.lifecycle = lifecycle;
      this.factory = factory;
   }

   public String displayName() { return displayName; }
   public Lifecycle lifecycle() { return lifecycle; }
   LandingStyleBase create() { return factory.get(); }
}
