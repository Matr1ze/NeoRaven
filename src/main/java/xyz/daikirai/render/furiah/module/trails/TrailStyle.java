package xyz.daikirai.render.furiah.module.trails;

import java.util.function.Function;
import xyz.daikirai.render.furiah.module.trails.styles.AfterimageStyle;
import xyz.daikirai.render.furiah.module.trails.styles.BrushstrokeStyle;
import xyz.daikirai.render.furiah.module.trails.styles.CardTrailStyle;
import xyz.daikirai.render.furiah.module.trails.styles.CartoonDustStyle;
import xyz.daikirai.render.furiah.module.trails.styles.CogsStyle;
import xyz.daikirai.render.furiah.module.trails.styles.CometStyle;
import xyz.daikirai.render.furiah.module.trails.styles.DatamoshStyle;
import xyz.daikirai.render.furiah.module.trails.styles.DoodleLineStyle;
import xyz.daikirai.render.furiah.module.trails.styles.EmbersStyle;
import xyz.daikirai.render.furiah.module.trails.styles.FootprintsStyle;
import xyz.daikirai.render.furiah.module.trails.styles.GildedChainStyle;
import xyz.daikirai.render.furiah.module.trails.styles.HelixStyle;
import xyz.daikirai.render.furiah.module.trails.styles.IconsStyle;
import xyz.daikirai.render.furiah.module.trails.styles.LightWallStyle;
import xyz.daikirai.render.furiah.module.trails.styles.LightningStyle;
import xyz.daikirai.render.furiah.module.trails.styles.MercuryStyle;
import xyz.daikirai.render.furiah.module.trails.styles.PaperTrailStyle;
import xyz.daikirai.render.furiah.module.trails.styles.RibbonStyle;
import xyz.daikirai.render.furiah.module.trails.styles.RosePathStyle;
import xyz.daikirai.render.furiah.module.trails.styles.SmokeStyle;
import xyz.daikirai.render.furiah.module.trails.styles.SpeedLinesStyle;
import xyz.daikirai.render.furiah.module.trails.styles.StainedGlassStyle;
import xyz.daikirai.render.furiah.module.trails.styles.StardustStyle;
import xyz.daikirai.render.furiah.module.trails.styles.WraithStyle;
import xyz.daikirai.render.furiah.setting.Displayable;

public enum TrailStyle implements Displayable {
   RIBBON("Ribbon", "A smooth band of light flowing behind you", true, RibbonStyle::new),
   COMET("Comet", "A bright orb with a long tail of light", true, CometStyle::new),
   FOOTPRINTS("Footprints", "Glowing prints where each foot lands", false, FootprintsStyle::new),
   AFTERIMAGE("Afterimage", "Speed ghosts of your body left behind as you run", false, AfterimageStyle::new),
   EMBERS("Embers", "Sparks, embers or flames left behind your feet", false, EmbersStyle::new),
   SMOKE("Smoke", "Soft puffs that rise, drift with the wind and fade", false, SmokeStyle::new),
   LIGHT_WALL("Light Wall", "A light-cycle wall standing on the ground along your path", true, LightWallStyle::new),
   HELIX("Helix", "Orbs circle your waist and twist into glowing strands", true, HelixStyle::new),
   LIGHTNING("Lightning", "Jagged electric arcs crackling along your path", true, LightningStyle::new),
   STARDUST("Stardust", "A cosmic ribbon of drifting nebula and twinkling stars", true, StardustStyle::new),
   SPEED_LINES("Speed Lines", "Anime motion lines streaming back as you sprint", false, SpeedLinesStyle::new),
   ICONS("Icons", "Hearts, stars or notes pop out and float away", false, IconsStyle::new),
   ROSE_PATH(
      "Rose Path", "A thorny vine with roses growing along the ground", true, true, RosePathStyle::new, "roses", "vines", "thorns", "bramble", "gothic", "path"
   ),
   BRUSHSTROKE(
      "Brushstroke", "One long calligraphic stroke of ink", true, true, BrushstrokeStyle::new, "ink", "brush", "stroke", "calligraphy", "sumi-e", "japanese"
   ),
   PAPER_TRAIL(
      "Paper Trail",
      "Paper planes, cranes, stars and confetti flutter off behind you",
      false,
      true,
      PaperTrailStyle::new,
      "paper",
      "origami",
      "planes",
      "cranes",
      "confetti",
      "pastel",
      "cute"
   ),
   STAINED_GLASS(
      "Stained Glass",
      "A low wall of stained glass rising along your path",
      true,
      true,
      StainedGlassStyle::new,
      "stained glass",
      "mosaic",
      "cathedral",
      "glass",
      "wall",
      "gothic"
   ),
   DATAMOSH(
      "Datamosh",
      "A smear of corrupted video: pixel-sorted bars of color",
      true,
      true,
      DatamoshStyle::new,
      "glitch",
      "datamosh",
      "pixel sort",
      "rgb",
      "digital",
      "cyber",
      "vaporwave"
   ),
   MERCURY(
      "Mercury", "Drops of liquid chrome that merge, splash and ripple", false, true, MercuryStyle::new, "chrome", "mercury", "liquid", "metal", "drops", "y2k"
   ),
   WRAITH(
      "Wraith",
      "Smoke tendrils streaming like a tattered cloak",
      true,
      true,
      WraithStyle::new,
      "shadow",
      "smoke",
      "wraith",
      "dark",
      "cloak",
      "tendrils",
      "embers"
   ),
   COGS("Cogs", "Little brass gears spinning out in meshing clusters", false, true, CogsStyle::new, "clockwork", "gears", "cogs", "brass", "steampunk"),
   CARD_TRAIL(
      "Card Trail",
      "Tarot cards dealt behind you, floating and turning",
      false,
      true,
      CardTrailStyle::new,
      "tarot",
      "cards",
      "deck",
      "occult",
      "mystic",
      "arcana"
   ),
   DOODLE_LINE(
      "Doodle Line",
      "A hand-drawn line that wobbles and boils, with doodles along it",
      true,
      true,
      DoodleLineStyle::new,
      "doodle",
      "sketch",
      "pencil",
      "marker",
      "chalk",
      "drawing",
      "line"
   ),
   GILDED_CHAIN("Gilded Chain", "A golden chain strung along your path", true, true, GildedChainStyle::new, "chain", "chains", "gold", "gilded", "links"),
   CARTOON_DUST(
      "Cartoon Dust", "Comic dust clouds puffing out behind your feet", false, true, CartoonDustStyle::new, "comic", "cartoon", "dust", "clouds", "puff", "run"
   );

   private final String displayName;
   private final String description;
   private final boolean path;
   private final Function<FuriahTrails, TrailStyleBase> factory;
   private final boolean isNew;
   private final String[] keywords;

   private TrailStyle(String displayName, String description, boolean path, Function<FuriahTrails, TrailStyleBase> factory) {
      this(displayName, description, path, false, factory);
   }

   private TrailStyle(String displayName, String description, boolean path, boolean isNew, Function<FuriahTrails, TrailStyleBase> factory, String... keywords) {
      this.displayName = displayName;
      this.description = description;
      this.path = path;
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
      return this.path ? "PATH" : "EMITTER";
   }

   public boolean isPath() {
      return this.path;
   }

   TrailStyleBase create(FuriahTrails module) {
      return this.factory.apply(module);
   }
}
