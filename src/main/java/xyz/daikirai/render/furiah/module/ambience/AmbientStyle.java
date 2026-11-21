package xyz.daikirai.render.furiah.module.ambience;

import java.util.function.Function;
import xyz.daikirai.render.furiah.setting.Displayable;

public enum AmbientStyle implements Displayable {
   WIND("Wind", "Glowing streaks ride the wind; some curl into loops", "wind", WindStyle::new),
   COMET_SPARKS("Comet Sparks", "Sparks dart on curved paths with comet tails", "comets", CometSparksStyle::new),
   SNOWFALL("Snowfall", "Flakes drift down, land and melt away", "snow", SnowfallStyle::new),
   WIRE_CRYSTALS("Wire Crystals", "Glowing wireframe crystals tumble in the air", "crystals", WireCrystalsStyle::new),
   CIRCUIT_LINES("Circuit Lines", "Circuit traces rise with running data pulses", "circuits", CircuitLinesStyle::new),
   FIREFLIES("Fireflies", "Glowing orbs wander and blink softly", "fireflies", FirefliesStyle::new),
   EMBERS("Embers", "Hot specks rise, flicker and cool", "embers", EmbersStyle::new),
   BOKEH("Bokeh Orbs", "Soft out-of-focus discs of light", "bokeh", BokehStyle::new),
   PETALS("Petals & Leaves", "Petals or leaves flutter down and settle", "petals", PetalsStyle::new),
   PLEXUS("Plexus", "A shifting web of glowing points and lines", "plexus", PlexusStyle::new),
   GROUND_MIST("Ground Mist", "Soft mist rolls over the ground", "mist", GroundMistStyle::new),
   AURORA("Aurora", "Curtains of light wave in the night sky", "aurora", AuroraStyle::new);

   private final String displayName;
   private final String description;
   final String key;
   private final Function<FuriahAmbienceModule, AmbientStyleBase> factory;

   private AmbientStyle(String displayName, String description, String key, Function<FuriahAmbienceModule, AmbientStyleBase> factory) {
      this.displayName = displayName;
      this.description = description;
      this.key = key;
      this.factory = factory;
   }

   @Override
   public String displayName() {
      return this.displayName;
   }

   @Override
   public String description() {
      return this.description;
   }

   public String key() {
      return this.key;
   }

   AmbientStyleBase create(FuriahAmbienceModule module) {
      return this.factory.apply(module);
   }
}
