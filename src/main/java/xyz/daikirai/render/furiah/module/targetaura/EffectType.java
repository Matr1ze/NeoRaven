package xyz.daikirai.render.furiah.module.targetaura;

import java.util.function.Supplier;
import xyz.daikirai.render.furiah.module.targetaura.effects.ArmillaryEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.AscensionEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.ChainBindEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.CrystalHaloEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.DoodleEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.GlitchFrameEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.GroundSigilEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.InkEnsoEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.LiquidChromeEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.LockOnEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.NeonIconEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.OrbitRingsEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.PaperCranesEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.PulseWaveEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.RoseWindowEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.ShadowVeilEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.SoulOrbsEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.SpiritSpiralEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.TarotEffect;
import xyz.daikirai.render.furiah.module.targetaura.effects.ThornCrownEffect;
import xyz.daikirai.render.furiah.setting.Displayable;

public enum EffectType implements Displayable {
   CRYSTAL_HALO("Crystal Halo", "Floating crystal, orbiting shards", false, CrystalHaloEffect::new, "crystal", "gem", "diamond", "halo", "shards"),
   ORBIT_RINGS("Orbit Rings", "Bright rings on tilted axes", false, OrbitRingsEffect::new, "rings", "orbit", "atom", "gyroscope"),
   SPIRIT_SPIRAL("Spirit Spiral", "Comet trails spiral around", false, SpiritSpiralEffect::new, "spiral", "comet", "swirl", "spirit"),
   SOUL_ORBS("Soul Orbs", "Glowing orbs with trails", false, SoulOrbsEffect::new, "orbs", "souls", "wisps", "spheres"),
   CHAIN_BIND("Chain Bind", "Chains wrap the body", false, ChainBindEffect::new, "chains", "metal", "bound", "gold", "silver", "iron"),
   NEON_ICON("Neon Icon", "Flickering neon sign", false, NeonIconEffect::new, "neon", "sign", "icon", "dollar", "heart", "skull"),
   GROUND_SIGIL("Ground Sigil", "Rotating magic circle", false, GroundSigilEffect::new, "sigil", "magic circle", "runes", "summoning"),
   LOCK_ON("Lock-On", "HUD corner brackets", false, LockOnEffect::new, "lock on", "target", "brackets", "hud"),
   ASCENSION("Ascension", "Light column and embers", false, AscensionEffect::new, "column", "beam", "pillar", "holy", "embers"),
   PULSE_WAVE("Pulse Wave", "Sonar ping on the ground", false, PulseWaveEffect::new, "pulse", "wave", "sonar", "shockwave", "ripple"),
   THORN_CROWN("Thorn Crown", "Thorny vines and a crown of roses", true, ThornCrownEffect::new, "thorns", "vines", "roses", "brambles", "gothic", "crown"),
   INK_ENSO("Ink Enso", "One brush stroke paints a circle", true, InkEnsoEffect::new, "ink", "brush", "enso", "sumi-e", "calligraphy", "japanese", "seal"),
   PAPER_CRANES("Paper Cranes", "Origami cranes circling round", true, PaperCranesEffect::new, "paper", "origami", "cranes", "birds", "pastel", "cute"),
   ROSE_WINDOW(
      "Rose Window", "Stained glass casting colored light", true, RoseWindowEffect::new, "stained glass", "window", "cathedral", "church", "gothic", "halo"
   ),
   GLITCH_FRAME(
      "Glitch Frame",
      "Digital corruption round the target",
      true,
      GlitchFrameEffect::new,
      "glitch",
      "error",
      "rgb",
      "datamosh",
      "digital",
      "vaporwave",
      "cyber"
   ),
   LIQUID_CHROME("Liquid Chrome", "Mercury drops that merge and split", true, LiquidChromeEffect::new, "chrome", "liquid", "metal", "mercury", "y2k", "blobs"),
   SHADOW_VEIL("Shadow Veil", "Dark smoke curling round the body", true, ShadowVeilEffect::new, "shadow", "smoke", "dark", "umbra", "void", "demon"),
   ARMILLARY(
      "Armillary",
      "Brass rings ticking like clockwork",
      true,
      ArmillaryEffect::new,
      "armillary",
      "brass",
      "clockwork",
      "steampunk",
      "astronomy",
      "rings",
      "gears"
   ),
   TAROT("Tarot", "Ornate cards that flip and float", true, TarotEffect::new, "tarot", "cards", "occult", "mystic", "fortune", "arcana"),
   DOODLE("Doodle", "Hand-drawn circle, arrows and sketches", true, DoodleEffect::new, "doodle", "sketch", "pencil", "marker", "chalk", "drawing", "cartoon");

   private final String displayName;
   private final String description;
   private final boolean isNew;
   private final Supplier<AuraEffect> factory;
   private final String[] keywords;

   private EffectType(String displayName, String description, boolean isNew, Supplier<AuraEffect> factory, String... keywords) {
      this.displayName = displayName;
      this.description = description;
      this.isNew = isNew;
      this.factory = factory;
      this.keywords = keywords;
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
   public String[] keywords() {
      return (String[])this.keywords.clone();
   }

   @Override
   public boolean isNew() {
      return this.isNew;
   }

   AuraEffect create() {
      return this.factory.get();
   }
}
