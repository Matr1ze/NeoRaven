package xyz.daikirai.render.furiah.module.ambience;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.render.furiah.render.FuriahRender;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import xyz.daikirai.render.furiah.core.GameClock;
import xyz.daikirai.module.Module;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.ViewFrustum;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.Setting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

public final class FuriahAmbienceModule extends Module {
   public final SliderSetting renderDistance;
   public final ModeSetting<FuriahAmbienceModule.WindMode> windMode;
   public final SliderSetting windDirection;
   public final SliderSetting windStrength;
   public final SliderSetting gustFrequency;
   public final SliderSetting gustStrength;
   public final SliderSetting turbulence;
   public final BooleanSetting overworld;
   public final BooleanSetting nether;
   public final BooleanSetting end;
   public final ModeSetting<FuriahAmbienceModule.Location> location;
   public final BooleanSetting hideUnderwater;
   public final BooleanSetting calmInCombat;
   public final SliderSetting calmOpacity;
   public final BooleanSetting atmosphere;
   public final ModeSetting<FuriahAmbienceModule.Dimension> atmosphereEditing;
   final ColorSetting palette = new ColorSetting("Color", -4204033);
   final ColorSetting[] tintColor = new ColorSetting[3];
   final SliderSetting[] tintStrength = new SliderSetting[3];
   final SliderSetting[] fogDistance = new SliderSetting[3];
   public final BooleanSetting wake;
   public final BooleanSetting wakeOthers;
   public final SliderSetting wakeStrength;
   public final SliderSetting totalCap;
   public final BooleanSetting adaptive;
   public final SliderSetting adaptiveFps;
   public final ModeSetting<AmbientStyle> editing;
   private final AmbientStyleBase[] styles;
   private final AmbientStyleBase[] drawOrder;
   final AmbientEnv env = new AmbientEnv();
   private final Atmosphere air = new Atmosphere();
   private final Camera camera = new Camera();
   private final FuriahRender renderer = new FuriahRender();
   private final ArrayList<Binding> bindings = new ArrayList<>();
   private final ArrayList<xyz.daikirai.module.setting.Setting> visible = new ArrayList<>();
   private final xyz.daikirai.module.setting.SliderSetting section;
   private static FuriahAmbienceModule instance;
   private long lastFight = Long.MIN_VALUE / 2;
   private int lastHurt;
   private final ViewFrustum frustum = new ViewFrustum();
   private double lastCamX = Double.NaN;
   private double lastCamY;
   private double lastCamZ;
   private boolean reseed;
   private int refillTicks;
   private float quality = 1.0F;
   private long lastFrameNanos;
   private int lastDimension = Integer.MIN_VALUE;
   private static final int MAX_WAKE = 16;
   private final double[] wakeX = new double[16];
   private final double[] wakeY = new double[16];
   private final double[] wakeZ = new double[16];
   private final float[] wakeVX = new float[16];
   private final float[] wakeVZ = new float[16];
   private int wakeCount;
   private final float[] fogTint = new float[3];

   public FuriahAmbienceModule(int key) {
      super("Ambience", Category.render, key);
      instance = this;
      AmbientStyle[] types = AmbientStyle.values();
      String[] pages = new String[types.length + 1];
      pages[0] = "General";
      for (AmbientStyle type : types) pages[type.ordinal() + 1] = type.displayName();
      section = new xyz.daikirai.module.setting.SliderSetting("Ambience settings", "", "", 0, 0,
            pages.length - 1, 1, pages, null);
      registerSetting(section);
      bindColor(palette, null, () -> true);
      BooleanSupplier fixed = new BooleanSupplier() {
         @Override
         public boolean getAsBoolean() {
            return FuriahAmbienceModule.this.windMode.get() == FuriahAmbienceModule.WindMode.FIXED;
         }
      };
      this.renderDistance = this.add(
         new SliderSetting(
               "distance",
               "Render Distance",
               "Ambience only exists this far around you (never beyond Minecraft's own render distance). It fades out over the last fifth and into the fog, so there is never an edge.",
               8.0,
               96.0,
               32.0,
               1.0
            )
            .unit(" blocks")
            .<SliderSetting>group("Render Distance")
            .keywords(new String[]{"radius", "view distance"})
      );
      this.renderDistance.info(new Supplier<String>() {
         public String get() {
            int n = FuriahAmbienceModule.this.totalEstimate();
            return n == 0 ? "no style is on" : "≈ " + FuriahAmbienceModule.this.formatCount(n) + " particles";
         }
      }, new BooleanSupplier() {
         @Override
         public boolean getAsBoolean() {
            return FuriahAmbienceModule.this.totalEstimate() >= Math.min(4000, FuriahAmbienceModule.this.totalCap.getInt());
         }
      });
      this.windMode = this.add(
         new ModeSetting<>("windMode", "Direction", "Where the wind blows.", FuriahAmbienceModule.WindMode.class, FuriahAmbienceModule.WindMode.AUTO).group("Wind")
      );
      this.windDirection = this.add(
         new SliderSetting(
               "windDirection",
               "Blows Toward",
               "The compass direction the wind blows toward: 0° north, 90° east, 180° south, 270° west.",
               0.0,
               355.0,
               90.0,
               5.0
            )
            .unit("°")
            .<SliderSetting>group("Wind")
            .visibleWhen(fixed)
      );
      this.windStrength = this.add(new SliderSetting("windStrength", "Strength", "How hard the wind blows.", 0.0, 100.0, 40.0, 5.0).unit("%").group("Wind"));
      this.gustFrequency = this.add(
         new SliderSetting("gustFrequency", "Gust Frequency", "How often gusts come.", 0.0, 100.0, 35.0, 5.0).unit("%").group("Wind")
      );
      this.gustStrength = this.add(
         new SliderSetting(
               "gustStrength",
               "Gust Strength",
               "How much a gust speeds everything up (the Wind style sends a bunch of streaks with it).",
               0.0,
               100.0,
               50.0,
               5.0
            )
            .unit("%")
            .group("Wind")
      );
      this.turbulence = this.add(
         new SliderSetting("turbulence", "Turbulence", "Small swirls, so nothing moves in straight lines.", 0.0, 100.0, 30.0, 5.0).unit("%").group("Wind")
      );
      this.overworld = this.add(new BooleanSetting("overworld", "Overworld", "Shows in the overworld.", true).group("Where & When"));
      this.nether = this.add(
         new BooleanSetting("nether", "Nether", "Shows in the Nether (it has no sky: Outdoors Only is ignored there).", true).group("Where & When")
      );
      this.end = this.add(new BooleanSetting("end", "End", "Shows in the End.", true).group("Where & When"));
      this.location = this.add(
         new ModeSetting<>("location", "Location", "Where it may show.", FuriahAmbienceModule.Location.class, FuriahAmbienceModule.Location.OUTDOORS).group("Where & When")
      );
      this.hideUnderwater = this.add(
         new BooleanSetting("hideUnderwater", "Hide Underwater", "Fades away while your head is under water.", true).group("Where & When")
      );
      this.calmInCombat = this.add(
         new BooleanSetting(
               "calm",
               "Calm in Combat",
               "While you fight (Target Aura has a target, or you hit someone or were hit in the last 5 s), Ambience fades down so it never gets in the way.",
               false
            )
            .group("Where & When")
      );
      this.calmOpacity = this.add(
         new SliderSetting("calmOpacity", "Opacity in Combat", "How much of it stays while you fight.", 0.0, 100.0, 25.0, 5.0)
            .unit("%")
            .<SliderSetting>group("Where & When")
            .visibleWhen(new BooleanSupplier() {
               @Override
               public boolean getAsBoolean() {
                  return FuriahAmbienceModule.this.calmInCombat.get();
               }
            })
      );
      BooleanSupplier atmosphereOn = new BooleanSupplier() {
         @Override
         public boolean getAsBoolean() {
            return FuriahAmbienceModule.this.atmosphere.get();
         }
      };
      this.atmosphere = this.add(
         new BooleanSetting(
               "atmosphere",
               "Atmosphere",
               "Tints the fog, the sky and the clouds (like a red or a cyan world) and changes how far you see. Off: vanilla.",
               false
            )
            .group("Atmosphere")
      );
      this.atmosphereEditing = this.add(
         new ModeSetting<>(
               "atmosphereDimension",
               "Dimension",
               "Each dimension has its own tint and fog; pick the one to change.",
               FuriahAmbienceModule.Dimension.class,
               FuriahAmbienceModule.Dimension.OVERWORLD
            )
            .<ModeSetting>group("Atmosphere")
            .visibleWhen(atmosphereOn)
      );
      String[] dimKeys = new String[]{"overworld", "nether", "end"};
      float[] defaultTint = new float[]{45.0F, 40.0F, 40.0F};
      int[] defaultColors = new int[]{-11544321, -50373, -5219073};

      for (int d = 0; d < 3; d++) {
         final FuriahAmbienceModule.Dimension dim = FuriahAmbienceModule.Dimension.values()[d];
         BooleanSupplier shown = new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
               return FuriahAmbienceModule.this.atmosphere.get() && FuriahAmbienceModule.this.atmosphereEditing.get() == dim;
            }
         };
         this.tintColor[d] = new ColorSetting(dim.displayName() + " — Tint", defaultColors[d], palette);
         bindColor(this.tintColor[d], null, shown);
         this.tintStrength[d] = this.add(
            new SliderSetting(
                  "atmo." + dimKeys[d] + ".strength",
                  dim.displayName() + " — Tint Strength",
                  "How far fog, sky and clouds shift toward the tint (their brightness stays).",
                  0.0,
                  100.0,
                  defaultTint[d],
                  5.0
               )
               .unit("%")
               .<SliderSetting>group("Atmosphere")
               .visibleWhen(shown)
         );
         this.fogDistance[d] = this.add(
            new SliderSetting(
                  "atmo." + dimKeys[d] + ".fog",
                  dim.displayName() + " — Fog Distance",
                  "Below 1× the air is thicker (the fog comes closer); above, it is clearer.",
                  0.25,
                  3.0,
                  1.0,
                  0.05
               )
               .unit("×")
               .<SliderSetting>group("Atmosphere")
               .visibleWhen(shown)
         );
      }

      this.wake = this.add(
         new BooleanSetting(
               "wake",
               "Player Wake",
               "Light particles (snow, fireflies, petals, sparks, mist) are pushed aside and swirl behind you when you walk or sprint through them.",
               true
            )
            .group("Player Wake")
      );
      BooleanSupplier wakeOn = new BooleanSupplier() {
         @Override
         public boolean getAsBoolean() {
            return FuriahAmbienceModule.this.wake.get();
         }
      };
      this.wakeStrength = this.add(
         new SliderSetting("wakeStrength", "Wake Strength", "How hard they are pushed.", 0.0, 100.0, 60.0, 5.0)
            .unit("%")
            .<SliderSetting>group("Player Wake")
            .visibleWhen(wakeOn)
      );
      this.wakeOthers = this.add(
         new BooleanSetting("wakeOthers", "Other Players Too", "Other players leave a wake as well.", false)
            .<BooleanSetting>group("Player Wake")
            .visibleWhen(wakeOn)
      );
      this.totalCap = this.add(
         new SliderSetting("totalCap", "Total Cap", "The most particles all styles together ever show.", 1000.0, 12000.0, 6000.0, 250.0)
            .<SliderSetting>group("Performance")
            .keywords(new String[]{"limit", "fps", "lag"})
      );
      this.adaptive = this.add(
         new BooleanSetting(
               "adaptive", "Adaptive Quality", "When your FPS drops below the value below, density goes down smoothly, and comes back later.", true
            )
            .group("Performance")
      );
      this.adaptiveFps = this.add(
         new SliderSetting("adaptiveFps", "Keep FPS Above", "The frame rate Adaptive Quality protects.", 20.0, 144.0, 50.0, 1.0)
            .<SliderSetting>group("Performance")
            .visibleWhen(new BooleanSupplier() {
               @Override
               public boolean getAsBoolean() {
                  return FuriahAmbienceModule.this.adaptive.get();
               }
            })
      );
      this.editing = new ModeSetting<>("editing", "Open Style", "", AmbientStyle.class, AmbientStyle.WIND);
      this.styles = new AmbientStyleBase[types.length];
      for (AmbientStyle type : types) {
         AmbientStyleBase style = type.create(this);
         this.styles[type.ordinal()] = style;
         bind(style.on, type, type.displayName() + " — Enabled");
         bindColor(style.palette, type, () -> true);
         for (Setting setting : style.getSettings()) {
            bind(setting, type, type.displayName() + " — " + setting.getName());
         }
      }

      AmbientStyle[] order = new AmbientStyle[]{
         AmbientStyle.GROUND_MIST,
         AmbientStyle.BOKEH,
         AmbientStyle.PETALS,
         AmbientStyle.SNOWFALL,
         AmbientStyle.WIRE_CRYSTALS,
         AmbientStyle.PLEXUS,
         AmbientStyle.CIRCUIT_LINES,
         AmbientStyle.FIREFLIES,
         AmbientStyle.EMBERS,
         AmbientStyle.COMET_SPARKS,
         AmbientStyle.WIND
      };
      ArrayList<AmbientStyleBase> list = new ArrayList<>();

      for (AmbientStyle t : order) {
         list.add(this.style(t));
      }

      for (AmbientStyleBase s : this.styles) {
         if (!list.contains(s) && s.spec.volume) {
            list.add(s);
         }
      }

      this.drawOrder = list.toArray(new AmbientStyleBase[0]);
   }

   public AmbientStyleBase style(AmbientStyle type) {
      return this.styles[type.ordinal()];
   }

   AmbientStyleBase[] styles() {
      return this.styles;
   }

   float minecraftDistance() {
      return Math.max(32, Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16);
   }

   int totalEstimate() {
      int sum = 0;

      for (AmbientStyleBase s : this.styles) {
         if (s.isOn() && s.spec.volume) {
            sum += s.estimate(s.radius(this.env));
         }
      }

      return Math.min(sum, this.totalCap.getInt());
   }

   String formatCount(int n) {
      return n >= 1000 ? String.format(Locale.ROOT, "%.1fk", n / 1000.0F) : Integer.toString(n);
   }

   @SubscribeEvent
   public void onTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) return;
      syncSettings();
      Minecraft mc = Minecraft.getMinecraft();
      Entity view = (Entity)(mc.getRenderViewEntity() != null ? mc.getRenderViewEntity() : mc.thePlayer);
      if (mc.theWorld != null && view != null) {
         if (this.env.world != mc.theWorld) onWorldChange();
         this.env.world = mc.theWorld;
         if (mc.thePlayer != null) {
            if (mc.thePlayer.hurtTime > lastHurt) lastFight = GameClock.millis();
            lastHurt = mc.thePlayer.hurtTime;
         }
         this.env.paused = mc.isGamePaused();
         this.env.tickCount++;
         this.env.camX = view.posX;
         this.env.camY = view.posY + view.getEyeHeight();
         this.env.camZ = view.posZ;
         int dim = mc.theWorld.provider.getDimensionId();
         if (dim != this.lastDimension) {
            this.lastDimension = dim;
            this.reseed = true;
         }

         double dz;
         double dy;
         double dx;
         if (!Double.isNaN(this.lastCamX)
            && (dx = this.env.camX - this.lastCamX) * dx + (dy = this.env.camY - this.lastCamY) * dy + (dz = this.env.camZ - this.lastCamZ) * dz
               > Math.pow(Math.max(12.0, this.renderDistance.get() * 0.5), 2.0)) {
            this.reseed = true;
            this.env.forgetGround();
         }

         this.lastCamX = this.env.camX;
         this.lastCamY = this.env.camY;
         this.lastCamZ = this.env.camZ;
         this.env.radius = Math.min(this.renderDistance.getFloat(), this.minecraftDistance());
         boolean sky = !mc.theWorld.provider.getHasNoSky();
         this.env.dayCycle = dim == 0 || sky && dim != 1 && dim != -1;
         this.env.outdoorsOnly = this.location.get() == FuriahAmbienceModule.Location.OUTDOORS && sky && dim != -1;
         float sun = MathHelper.cos(mc.theWorld.getCelestialAngle(1.0F) * (float) Math.PI * 2.0F);
         this.env.daylight = smooth(-0.18F, 0.18F, sun);
         this.env.light = this.env.dayCycle
            ? (0.36F + 0.64F * this.env.daylight) * (1.0F - 0.3F * mc.theWorld.getRainStrength(1.0F))
            : (dim == -1 ? 0.75F : 0.6F);
         this.env.tickCache();
         boolean allowed = this.dimensionAllowed(dim);
         boolean underwater = this.hideUnderwater.get() && view.isInsideOfMaterial(Material.water);
         float target = allowed && !underwater ? 1.0F : 0.0F;
         if (this.calmInCombat.get() && this.fighting()) {
            target = Math.min(target, this.calmOpacity.getFloat() / 100.0F);
         }

         this.env.globalAlpha = this.env.globalAlpha + (target - this.env.globalAlpha) * (target < this.env.globalAlpha ? 0.14F : 0.07F);
         if (Math.abs(target - this.env.globalAlpha) < 0.003F) {
            this.env.globalAlpha = target;
         }

         if (!this.env.paused) {
            this.env
               .wind
               .tick(
                  0.05F,
                  this.windMode.get() == FuriahAmbienceModule.WindMode.FIXED ? this.windDirection.getFloat() : Float.NaN,
                  this.windStrength.getFloat() / 100.0F,
                  this.gustFrequency.getFloat() / 100.0F,
                  this.gustStrength.getFloat() / 100.0F,
                  this.turbulence.getFloat() / 100.0F
               );
            this.collectWake(mc);
            this.updateQuality();
            float scale = this.quality * this.capScale();
            boolean again = this.reseed;
            this.reseed = false;
            this.refillTicks = again ? 10 : Math.max(0, this.refillTicks - 1);

            for (AmbientStyleBase s : this.styles) {
               s.tick(this.env, scale, again, this.refillTicks > 0);
            }
         }

      }
   }

   private static float smooth(float e0, float e1, float x) {
      float t = Math.max(0.0F, Math.min(1.0F, (x - e0) / (e1 - e0)));
      return t * t * (3.0F - 2.0F * t);
   }

   private boolean dimensionAllowed(int dim) {
      return dim == -1 ? this.nether.get() : (dim == 1 ? this.end.get() : this.overworld.get());
   }

   private float capScale() {
      int sum = 0;

      for (AmbientStyleBase s : this.styles) {
         if (s.isOn() && s.spec.volume) {
            sum += s.estimate(s.radius(this.env));
         }
      }

      return sum <= this.totalCap.getInt() ? 1.0F : (float)this.totalCap.getInt() / sum;
   }

   private void updateQuality() {
      if (!this.adaptive.get()) {
         this.quality = 1.0F;
      } else {
         int fps = Minecraft.getDebugFPS();
         float want = this.adaptiveFps.getFloat();
         if (fps > 0 && fps < want) {
            this.quality = Math.max(0.3F, this.quality - 0.015F);
         } else if (fps > want * 1.15F) {
            this.quality = Math.min(1.0F, this.quality + 0.004F);
         }
      }
   }

   @SubscribeEvent
   public void onAttack(AttackEntityEvent event) {
      if (event.entityPlayer == mc.thePlayer) lastFight = GameClock.millis();
   }

   private boolean fighting() {
      return GameClock.millis() - lastFight < 5000L;
   }

   public static boolean sampleWind(double x, double y, double z, float follow, float[] out) {
      FuriahAmbienceModule module = instance;
      if (module == null || !module.isEnabled() || module.env.world == null || module.env.world != mc.theWorld) {
         return false;
      }
      module.env.wind.velocity(x, y, z, follow, out);
      return true;
   }

   private void collectWake(Minecraft mc) {
      this.wakeCount = 0;
      if (this.wake.get() && mc.theWorld != null) {
         List players = mc.theWorld.playerEntities;
         float r = this.env.radius + 2.0F;

         for (int i = 0; i < players.size() && this.wakeCount < 16; i++) {
            EntityPlayer p = (EntityPlayer)players.get(i);
            double dz;
            double dx;
            double vz;
            double vx;
            if ((p == mc.thePlayer || this.wakeOthers.get())
               && !p.isSpectator()
               && !p.isInvisible()
               && !((vx = (p.posX - p.prevPosX) * 20.0) * vx + (vz = (p.posZ - p.prevPosZ) * 20.0) * vz < 1.2)
               && !((dx = p.posX - this.env.camX) * dx + (dz = p.posZ - this.env.camZ) * dz > r * r)) {
               this.wakeX[this.wakeCount] = p.posX;
               this.wakeY[this.wakeCount] = p.posY + 0.9;
               this.wakeZ[this.wakeCount] = p.posZ;
               this.wakeVX[this.wakeCount] = (float)vx;
               this.wakeVZ[this.wakeCount] = (float)vz;
               this.wakeCount++;
            }
         }
      }
   }

   boolean wakeActive() {
      return this.wakeCount > 0;
   }

   void wake(AmbientPool p, int i, float dt) {
      float strength = this.wakeStrength.getFloat() / 100.0F;

      for (int s = 0; s < this.wakeCount; s++) {
         double dy = p.y[i] - this.wakeY[s];
         double dz;
         double dx;
         double d2;
         if (!(dy < -1.4) && !(dy > 1.4) && !((d2 = (dx = p.x[i] - this.wakeX[s]) * dx + (dz = p.z[i] - this.wakeZ[s]) * dz) > 3.2) && !(d2 < 1.0E-6)) {
            double d = Math.sqrt(d2);
            float speed = (float)Math.sqrt(this.wakeVX[s] * this.wakeVX[s] + this.wakeVZ[s] * this.wakeVZ[s]);
            float fall = (float)(1.0 - d / 1.8);
            float push = strength * fall * Math.min(1.0F, speed / 5.6F) * 9.0F;
            double nx = dx / d;
            double nz = dz / d;
            double side = nx * this.wakeVZ[s] - nz * this.wakeVX[s] >= 0.0 ? 1.0 : -1.0;
            float mx = this.wakeVX[s] / Math.max(0.1F, speed);
            float mz = this.wakeVZ[s] / Math.max(0.1F, speed);
            p.kx[i] = p.kx[i] + (float)((nx * 0.8 + mx * 0.5 - nz * side * 0.5) * push * dt * 4.0);
            p.kz[i] = p.kz[i] + (float)((nz * 0.8 + mz * 0.5 + nx * side * 0.5) * push * dt * 4.0);
            p.ky[i] = p.ky[i] + (float)(push * dt * (0.8 - dy * 0.3));
         }
      }
   }

   private boolean anyParticles() {
      for (AmbientStyleBase s : this.styles) {
         if (s.count() > 0 || s.presence > 0.0F && !s.spec.volume) {
            return true;
         }
      }

      return false;
   }

   @SubscribeEvent
   public void onRenderWorld(RenderWorldLastEvent event) {
      if (mc.theWorld == null || this.env.world != mc.theWorld || mc.getRenderViewEntity() == null) return;
      long now = GameClock.nanos();
      float dt = this.lastFrameNanos == 0L ? 0.05F : Math.min(0.1F, (now - this.lastFrameNanos) / 1.0E9F);
      this.lastFrameNanos = now;
      this.updateAtmosphere(dt, mc);
      if (!this.anyParticles()) return;
      renderer.draw(camera, -1, false, () -> {
         Textures.ensureAmbienceLoaded();
         frame(mc, event.partialTicks, dt);
         env.skyPass = false;
         for (AmbientStyleBase style : styles) {
            if (!style.spec.volume && style.presence > 0.0F) drawStyle(style, true);
         }
         for (AmbientStyleBase style : drawOrder) {
            if (style.count() > 0) drawStyle(style, false);
         }
      });
   }

   private void drawStyle(AmbientStyleBase style, boolean sky) {
      try (ThemeTint.Scope ignored = ThemeTint.use(style.palette.color())) {
         if (sky) style.drawSky(env);
         else style.draw(env);
         Vfx.flush();
         RenderState.additive();
      }
   }

   private void frame(Minecraft mc, float partialTicks, float dt) {
      this.camera.captureModelView();
      Vfx.setCamera(this.camera);
      this.frustum.capture();
      this.env.cam = this.camera;
      this.env.frustum = this.frustum;
      this.env.partialTicks = partialTicks;
      this.env.viewX = mc.getRenderManager().viewerPosX;
      this.env.viewY = mc.getRenderManager().viewerPosY;
      this.env.viewZ = mc.getRenderManager().viewerPosZ;
      Entity v = (Entity)(mc.getRenderViewEntity() != null ? mc.getRenderViewEntity() : mc.thePlayer);
      if (v != null) {
         this.env.eyeX = v.lastTickPosX + (v.posX - v.lastTickPosX) * partialTicks;
         this.env.eyeY = v.lastTickPosY + (v.posY - v.lastTickPosY) * partialTicks + v.getEyeHeight();
         this.env.eyeZ = v.lastTickPosZ + (v.posZ - v.lastTickPosZ) * partialTicks;
      }

      this.env.frameDt = this.env.paused ? 0.0F : dt;
      this.env.time = this.env.time + this.env.frameDt;
      FogCapture.apply(this.env, this.minecraftDistance());
   }

   private int dimensionIndex(int dim) {
      return dim == -1 ? 1 : (dim == 1 ? 2 : 0);
   }

   private void updateAtmosphere(float dt, Minecraft mc) {
      if (mc.theWorld != null) {
         int d = this.dimensionIndex(mc.theWorld.provider.getDimensionId());
         boolean on = this.atmosphere.get();
         int tint = this.tintColor[d].color();
         this.air
            .update(
               dt,
               tint,
               tint,
               tint,
               on ? this.tintStrength[d].getFloat() / 100.0F : 0.0F,
               on ? this.fogDistance[d].getFloat() : 1.0F
            );
      }
   }

   public static Vec3 tintSky(Vec3 sky) {
      return instance != null && instance.isEnabled() ? instance.air.tint(2, sky, 1.0F) : sky;
   }

   public static Vec3 tintClouds(Vec3 clouds) {
      return instance != null && instance.isEnabled() ? instance.air.tint(1, clouds, 0.85F) : clouds;
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public void onFogColors(EntityViewRenderEvent.FogColors event) {
      Material material = event.block == null ? null : event.block.getMaterial();
      if (this.air.active() && material != Material.water && material != Material.lava) {
         this.air.tint(0, event.red, event.green, event.blue, this.fogTint, 1.0F);
         event.red = this.fogTint[0];
         event.green = this.fogTint[1];
         event.blue = this.fogTint[2];
      }
      FogCapture.colors(event);
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public void onRenderFog(EntityViewRenderEvent.RenderFogEvent event) {
      float factor = this.air.fogChanged() ? this.air.fogFactor() : 1.0F;
      FogCapture.fog(event, factor);
      if (factor != 1.0F) FogCapture.scaleFog(event, factor);
   }

   @SubscribeEvent
   public void unload(WorldEvent.Unload event) {
      if (event.world == this.env.world) onWorldChange();
   }

   private void onWorldChange() {
      for (AmbientStyleBase style : this.styles) style.clear();
      this.lastCamX = Double.NaN;
      this.env.world = null;
      this.env.forgetGround();
      this.wakeCount = 0;
      this.reseed = true;
      this.lastDimension = Integer.MIN_VALUE;
      this.env.globalAlpha = 0;
      this.env.time = 0;
      this.lastFrameNanos = 0;
      this.lastFight = Long.MIN_VALUE / 2;
      this.lastHurt = 0;
      this.air.reset();
   }

   @Override public void onDisable() {
      onWorldChange();
   }

   @Override public String getInfo() {
      int active = 0;
      for (AmbientStyleBase style : styles) {
         if (style.isOn()) active++;
      }
      return Integer.toString(active);
   }

   private <S extends Setting> S add(S setting) {
      bind(setting, null, setting.getName());
      return setting;
   }

   private void bind(Setting setting, AmbientStyle style, String name) {
      xyz.daikirai.module.setting.Setting control;
      Runnable sync;
      if (setting instanceof SliderSetting) {
         SliderSetting source = (SliderSetting) setting;
         xyz.daikirai.module.setting.SliderSetting slider = new xyz.daikirai.module.setting.SliderSetting(
               name, "", source.getUnit(), source.getDefault(), source.getMin(), source.getMax(),
               source.getStep(), null, null);
         control = slider;
         sync = () -> source.set(slider.getInput());
      } else if (setting instanceof ModeSetting) {
         ModeSetting<?> source = (ModeSetting<?>) setting;
         Enum<?>[] values = source.getValues();
         String[] options = new String[values.length];
         for (int i = 0; i < options.length; i++) options[i] = ModeSetting.displayName(values[i]);
         xyz.daikirai.module.setting.SliderSetting mode = new xyz.daikirai.module.setting.SliderSetting(
               name, "", "", source.getIndex(), 0, options.length - 1, 1, options, null);
         control = mode;
         sync = () -> source.setIndex((int)mode.getInput());
      } else {
         BooleanSetting source = (BooleanSetting) setting;
         ButtonSetting button = new ButtonSetting(name, source.get());
         control = button;
         sync = () -> source.set(button.isToggled());
      }
      registerSetting(control);
      bindings.add(new Binding(control, style, setting::isVisible, sync));
   }

   private void bindColor(ColorSetting control, AmbientStyle style, BooleanSupplier shown) {
      registerSetting(control);
      bindings.add(new Binding(control, style, shown, () -> {}));
   }

   private void syncSettings() {
      int page = (int)section.getInput();
      if (page > 0) editing.setIndex(page - 1);
      for (Binding binding : bindings) binding.sync.run();
   }

   @Override public ArrayList<xyz.daikirai.module.setting.Setting> getSettings() {
      syncSettings();
      AmbientStyle page = section.getInput() == 0 ? null : editing.get();
      visible.clear();
      visible.add(section);
      for (Binding binding : bindings) {
         if (binding.style == page && binding.shown.getAsBoolean()) visible.add(binding.control);
      }
      return visible;
   }

   @Override public ArrayList<xyz.daikirai.module.setting.Setting> persistedSettings() {
      return super.getSettings();
   }

   private static final class Binding {
      final xyz.daikirai.module.setting.Setting control;
      final AmbientStyle style;
      final BooleanSupplier shown;
      final Runnable sync;

      Binding(xyz.daikirai.module.setting.Setting control, AmbientStyle style, BooleanSupplier shown, Runnable sync) {
         this.control = control;
         this.style = style;
         this.shown = shown;
         this.sync = sync;
      }
   }

   public static enum Dimension implements Displayable {
      OVERWORLD("Overworld", "The overworld's air"),
      NETHER("Nether", "The Nether's air"),
      END("End", "The End's air");

      private final String displayName;
      private final String description;

      private Dimension(String displayName, String description) {
         this.displayName = displayName;
         this.description = description;
      }

      @Override
      public String displayName() {
         return this.displayName;
      }

      @Override
      public String description() {
         return this.description;
      }
   }

   public static enum Location implements Displayable {
      OUTDOORS("Outdoors Only", "Only where the sky is visible: nothing under roofs or in caves"),
      EVERYWHERE("Everywhere", "Indoors and in caves too");

      private final String displayName;
      private final String description;

      private Location(String displayName, String description) {
         this.displayName = displayName;
         this.description = description;
      }

      @Override
      public String displayName() {
         return this.displayName;
      }

      @Override
      public String description() {
         return this.description;
      }
   }

   public static enum WindMode implements Displayable {
      AUTO("Auto", "Turns slowly by itself"),
      FIXED("Fixed", "Always blows the same way");

      private final String displayName;
      private final String description;

      private WindMode(String displayName, String description) {
         this.displayName = displayName;
         this.description = description;
      }

      @Override
      public String displayName() {
         return this.displayName;
      }

      @Override
      public String description() {
         return this.description;
      }
   }
}
