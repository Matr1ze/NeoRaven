package xyz.daikirai.render.furiah.module.trails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.Breeze;
import xyz.daikirai.render.furiah.fx.FxParticles;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.style.StyleMaterials;
import xyz.daikirai.render.furiah.fx.trail.PathRecorder;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.fx.trail.TrailShape;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.Setting;
import xyz.daikirai.render.furiah.setting.SettingStyle;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public abstract class TrailStyleBase implements SettingStyle {
   public static final float WALK = 4.3F;
   public static final float SPRINT = 5.6F;
   protected final FuriahTrails module;
   protected final TrailStyle type;
   public final ThemeTint color;
   protected final ColorAlong colorAlong;
   public final SliderSetting size;
   public final SliderSetting length;
   public final SliderSetting opacity;
   public final SliderSetting glow;
   public final SliderSetting speedScaling;
   public final SliderSetting maxLength;
   public final SliderSetting taper;
   public final SliderSetting smoothing;
   public final ModeSetting<TrailStyleBase.WhenStop> whenStop;
   public final ModeSetting<TrailStyleBase.FadeCurve> fadeCurve;
   public final SliderSetting density;
   public final SliderSetting followWind;
   public final SliderSetting animSpeed;
   public final ModeSetting<TrailAnchor> anchor;
   public final BooleanSetting on;
   protected int tint;
   protected float light = 1.0F;
   protected float matLight = 1.0F;
   protected float night;
   boolean passFailed;
   private final TrailAnchor fixedAnchor;
   private final List<Setting> settings = new ArrayList<>();
   private static final BooleanSupplier NEVER = new BooleanSupplier() {
      @Override
      public boolean getAsBoolean() {
         return false;
      }
   };
   private List<SliderSetting> lookTargets;
   private static final float[] WIND = new float[3];
   private static final float[] BREEZE = new float[3];
   protected static final float[] POINT_ALPHA = new float[1024];

   protected static TrailStyleBase.Spec spec() {
      return new TrailStyleBase.Spec();
   }

   protected TrailStyleBase(FuriahTrails module, TrailStyle type, TrailStyleBase.Spec spec) {
      this.module = module;
      this.type = type;
      String id = type.name().toLowerCase(Locale.ROOT);
      String name = type.displayName();
      boolean path = type.isPath();
      this.color = new ThemeTint();
      this.colorAlong = spec.along != null ? spec.along : path ? ColorAlong.HEAD_TO_TAIL : ColorAlong.BY_AGE;
      double[] sz = spec.size;
      this.size = this.own(
         new SliderSetting(id + ".size", "Size", path ? "How thick the trail is." : "How big its pieces are.", sz[0], sz[1], sz[2], sz[3]).unit(spec.sizeUnit),
         "Size & strength"
      );
      this.length = this.own(
         new SliderSetting(id + ".length", "Length", path ? "How long the trail lasts behind you." : "How long each piece lasts.", 0.2, 5.0, spec.length, 0.1)
            .unit("s"),
         "Size & strength"
      );
      this.maxLength = path
         ? this.own(
            new SliderSetting(id + ".maxLength", "Max Length", "Caps the trail's length at high speed (speed potions, flying, horses).", 1.0, 30.0, 8.0, 0.5)
               .unit(" blocks"),
            "Size & strength"
         )
         : null;
      this.opacity = this.own(new SliderSetting(id + ".opacity", "Opacity", "How solid the trail looks.", 0.0, 100.0, 85.0, 1.0).unit("%"), "Size & strength");
      this.glow = this.own(new SliderSetting(id + ".glow", "Glow", "The soft glow around it.", 0.0, 100.0, 50.0, 1.0).unit("%"), "Size & strength");
      this.density = path
         ? null
         : this.own(
            new SliderSetting(id + ".density", "Density", "How many pieces it drops per block traveled.", 0.0, 200.0, 100.0, 5.0).unit("%"), "Size & strength"
         );
      this.taper = path
         ? this.own(
            new SliderSetting(id + ".taper", "Taper", "How much the trail narrows towards its tail.", 0.0, 100.0, spec.taper, 1.0).unit("%"), "Size & strength"
         )
         : null;
      this.whenStop = path
         ? this.own(
            new ModeSetting<>(
               id + ".whenStop", "When I Stop", "What the trail does when you stop moving.", TrailStyleBase.WhenStop.class, TrailStyleBase.WhenStop.FADE
            ),
            "Motion"
         )
         : null;
      this.speedScaling = this.own(
         new SliderSetting(
               id + ".speedScaling",
               "Speed Scaling",
               "Faster movement makes the trail longer, thicker and brighter (walking is 4.3 blocks/s, sprinting 5.6).",
               0.0,
               100.0,
               50.0,
               1.0
            )
            .unit("%"),
         "Motion"
      );
      this.smoothing = path
         ? this.own(
            new SliderSetting(id + ".smoothing", "Smoothing", "How smoothly the trail curves; 0% follows every step.", 0.0, 100.0, 60.0, 1.0).unit("%"),
            "Motion"
         )
         : null;
      this.fadeCurve = this.own(
         new ModeSetting<>(id + ".fadeCurve", "Fade Curve", "How the trail fades with age.", TrailStyleBase.FadeCurve.class, spec.fade), "Motion"
      );
      this.animSpeed = spec.animated
         ? this.own(
            new SliderSetting(id + ".animSpeed", "Animation Speed", "Twinkle, flicker, spin and pulse speed.", 25.0, 300.0, 100.0, 5.0).unit("%"), "Motion"
         )
         : null;
      this.followWind = spec.windy
         ? this.own(
            new SliderSetting(
                  id + ".followWind",
                  "Follow Wind",
                  "How much its floating pieces drift with Ambience's wind (with the snow and the leaves).",
                  0.0,
                  100.0,
                  30.0,
                  1.0
               )
               .unit("%"),
            "Motion"
         )
         : null;
      this.fixedAnchor = spec.anchorFixed ? spec.anchor : null;
      this.anchor = spec.anchorFixed
         ? null
         : this.own(
            new ModeSetting<>(
                  id + ".anchor", "Anchor", "Where the trail comes from. Both feet and Hands make two trails, one each side.", TrailAnchor.class, spec.anchor
               )
               .style(ModeSetting.Style.CHIPS),
            "Style options"
         );
      this.on = new BooleanSetting(id + ".on", name, name + " runs with the other styles switched on (Combine Styles).", type.ordinal() == 0)
         .<BooleanSetting>group("Style")
         .<BooleanSetting>nested()
         .ownedBy(this);
      this.settings.add(this.on);
   }

   protected final ThemeTint secondColor(String key, String name, String description, int argb, boolean staticWhite) {
      return staticWhite ? ThemeTint.highlight() : ThemeTint.detail(argb);
   }

   protected final ThemeTint signatureColor(String key, String name, String description, int argb, int gradientTo) {
      return ThemeTint.detail(argb);
   }

   private <S extends Setting> S own(S setting, String group) {
      setting.group(group);
      setting.ownedBy(this);
      this.settings.add(setting);
      return setting;
   }

   protected final void doesNotApply(Setting... settings) {
      for (Setting s : settings) {
         if (s != null) {
            s.visibleWhen(NEVER);
            s.notSearched();
         }
      }
   }

   protected final <S extends Setting> S setting(S setting) {
      return this.own(setting, "Style options");
   }

   protected final ModeSetting<Material> materialSetting(Material made, String description) {
      ModeSetting<Material> m = this.own(StyleMaterials.material(this.type.name().toLowerCase(Locale.ROOT), made, description), "Colors");
      return m;
   }

   protected final ModeSetting<Finish> finishSetting(Finish finish, ModeSetting<Material> material) {
      return this.own(StyleMaterials.finish(this.type.name().toLowerCase(Locale.ROOT), finish, material), "Colors");
   }

   @Override
   public final String styleName() {
      return this.type.displayName();
   }

   @Override
   public final boolean isShown() {
      return true;
   }

   @Override
   public final boolean isActive() {
      return this.isShown();
   }

   @Override
   public final boolean isMultiple() {
      return false;
   }

   @Override
   public final void show() {
   }

   @Override
   public final String searchGroup(String group) {
      return group != null && !group.equals(this.type.displayName()) ? this.type.displayName() + " › " + group : this.type.displayName();
   }

   @Override
   public final boolean foundElsewhereByNameOnly() {
      return true;
   }

   public final List<Setting> getSettings() {
      return Collections.unmodifiableList(this.settings);
   }

   public final TrailStyle getType() {
      return this.type;
   }

   public final TrailAnchor anchor() {
      return this.fixedAnchor != null ? this.fixedAnchor : this.anchor.get();
   }

   public final List<SliderSetting> lookTargets() {
      if (this.lookTargets == null) {
         ArrayList<SliderSetting> out = new ArrayList<>();
         out.add(this.size);
         out.add(this.length);
         out.add(this.opacity);
         out.add(this.glow);
         out.add(this.speedScaling);
         if (this.animSpeed != null) {
            out.add(this.animSpeed);
         }

         this.addLookTargets(out);
         this.lookTargets = Collections.unmodifiableList(out);
      }

      return this.lookTargets;
   }

   protected void addLookTargets(List<SliderSetting> out) {
   }

   public boolean sprintOnly() {
      return false;
   }

   public void render(PlayerTrail p, TrailFrame f) {
   }

   public int passes() {
      return 0;
   }

   public void renderPass(PlayerTrail p, TrailFrame f, FxPass pass) {
   }

   public void renderPiecesPass(TrailFrame f, FxPass pass) {
   }

   final void piecesState() {
      this.tint = 0;
      this.light = 1.0F;
      this.matLight = 1.0F;
      this.night = 0.0F;
   }

   protected final boolean spawnPass(FxPass pass) {
      return pass == null || (this.passes() & pass.bit() - 1) == 0;
   }

   protected final Material materialFor(Material chosen) {
      return this.tint != 0 ? Material.CUSTOM : chosen;
   }

   public String stateForTest() {
      return null;
   }

   protected TrailStyleBase.Store newStore() {
      return null;
   }

   protected final <T extends TrailStyleBase.Store> T store(TrailFrame f) {
      TrailStyleBase.Store s = f.store(this.type);
      if (s == null) {
         s = this.newStore();
         f.setStore(this.type, s);
      }

      return (T)s;
   }

   protected void drawStore(TrailFrame f, TrailStyleBase.Store store) {
   }

   public void update(TrailFrame f) {
      FxParticles p = f.piecesIfAny(this.type);
      if (p != null) {
         p.update(f.dt);
      }

      TrailStyleBase.Store s;
      if ((s = f.store(this.type)) != null) {
         s.update(f.dt);
      }
   }

   public void renderPieces(TrailFrame f) {
      this.tint = 0;
      this.light = 1.0F;
      TrailStyleBase.Store s = f.store(this.type);
      if (s != null && s.size() > 0) {
         this.drawStore(f, s);
      }

      FxParticles p;
      if ((p = f.piecesIfAny(this.type)) != null) {
         p.render(f.viewX, f.viewY, f.viewZ);
      }
   }

   protected final int piecesDue(PlayerTrail p, double perBlock, int max) {
      if (p.path != null && p.attached && !p.ghost) {
         double rate = perBlock * (this.density != null ? this.density.getFloat() / 100.0 : 1.0) * (p.reduced ? 0.5 : 1.0);
         if (rate <= 0.0) {
            return 0;
         } else {
            PlayerTrail.StyleState st = p.style;
            double traveled = p.path.traveled() + p.path.sinceLast(p.headX, p.headY, p.headZ);
            double step = 1.0 / rate;
            int side = p.side;
            if (st.next[side] < traveled - 2.0 || st.next[side] > traveled + step * 2.0) {
               st.next[side] = traveled + step * 0.5;
            }

            int n;
            for (n = 0; st.next[side] <= traveled && n < max; n++) {
               st.next[side] = st.next[side] + step;
            }

            if (st.next[side] <= traveled) {
               st.next[side] = traveled + step * 0.5;
            }

            return n;
         }
      } else {
         return 0;
      }
   }

   protected final float[] wind(TrailFrame f, double x, double y, double z) {
      WIND[2] = 0.0F;
      WIND[1] = 0.0F;
      WIND[0] = 0.0F;
      float k = this.followWind == null ? 0.0F : this.followWind.getFloat() / 100.0F;
      if (k <= 0.0F) {
         return WIND;
      } else if (f.preview) {
         WIND[0] = 0.7F * k;
         WIND[2] = 0.3F * k;
         return WIND;
      } else {
         xyz.daikirai.render.furiah.module.ambience.FuriahAmbienceModule.sampleWind(x, y, z, k, WIND);
         return WIND;
      }
   }

   protected final float[] breeze(TrailFrame f, double x, double y, double z, float follow) {
      float k = this.followWind != null ? this.followWind.getFloat() / 100.0F : follow;
      Breeze.at(x, y, z, f.preview, BREEZE);
      BREEZE[0] = BREEZE[0] * k;
      BREEZE[1] = BREEZE[1] * k;
      BREEZE[2] = BREEZE[2] * k;
      return BREEZE;
   }

   protected static double traveledAtHead(PlayerTrail p) {
      PathRecorder r = p.path;
      if (r == null || r.count() == 0) {
         return 0.0;
      } else if (p.ghost && p.style != null) {
         int i = r.count() - 1;

         while (i > 0 && r.time(i) > p.style.ghostUntil) {
            i--;
         }

         return r.distance(i);
      } else {
         return r.traveled() + (p.attached ? r.sinceLast(p.headX, p.headY, p.headZ) : 0.0);
      }
   }

   public int pieceCount(TrailFrame f) {
      FxParticles p = f.piecesIfAny(this.type);
      TrailStyleBase.Store s = f.store(this.type);
      return (p == null ? 0 : p.size()) + (s == null ? 0 : s.size());
   }

   public final float lengthNow(float speed) {
      return this.length.getFloat() * (1.0F + this.speedBoost(speed));
   }

   public final float speedBoost(float speed) {
      float s = Math.max(-0.5F, Math.min(1.5F, (speed - 4.3F) / 4.3F));
      return s * this.speedScaling.getFloat() / 100.0F;
   }

   public final float fade(float a) {
      float x = Math.max(0.0F, Math.min(1.0F, a));
      switch ((TrailStyleBase.FadeCurve)this.fadeCurve.get()) {
         case LINEAR:
            return 1.0F - x;
         case LATE:
            return 1.0F - x * x * x;
         default:
            return (1.0F - x) * (1.0F - x);
      }
   }

   public final int colorAt(float headToTail, float age, float speed, double time, float shift) {
      int c = this.gradientAt(headToTail, age, speed, time, shift);
      return this.light < 0.999F ? ColorUtil.shade(c, this.light) : c;
   }

   private int gradientAt(float headToTail, float age, float speed, double time, float shift) {
      return this.color.getColor(headToTail, time);
   }

   protected final int slotColor(ThemeTint slot, float pos, double time) {
      int c = this.tint != 0 ? this.tint : slot.getColor(pos, time);
      return this.light < 0.999F ? ColorUtil.shade(c, this.light) : c;
   }

   protected static int lit(int color, TrailFrame f, double x, double y, double z) {
      return f.lit ? ColorUtil.shade(color, f.lightAt(x, y, z)) : color;
   }

   private static float speedPos(float speed) {
      return Math.max(0.0F, Math.min(1.0F, (speed - 4.3F) / 6.8999996F));
   }

   protected final FxParticles colorPiece(FxParticles pool, float spot, float ageFrom, float speed) {
      if (this.tint == 0 && !(this.light < 0.999F)) {
         switch ((TrailStyleBase.ColorAlong)this.colorAlong) {
            case BY_AGE:
               return pool.color(this.color, ageFrom).colorAge(1.0F);
            case BY_SPEED:
               return pool.color(this.color, speedPos(speed));
            default:
               return pool.color(this.color, spot);
         }
      } else {
         return pool.color(this.colorAt(spot, ageFrom, speed, ThemeTint.clock(), 0.0F));
      }
   }

   protected final int fill(PlayerTrail p, TrailFrame f, int k, float baseWidth, float taperAmount, float shift) {
      TrailShape s = p.shape;
      int from = s.runStart[k];
      int to = s.runEnd[k];
      int n = 0;
      float maxAge = Math.max(0.05F, p.maxAge);
      float tp = taperAmount >= 0.0F ? taperAmount : (this.taper == null ? 0.0F : this.taper.getFloat() / 100.0F);
      float total = Math.max(0.001F, s.length);
      float cap = this.maxLength == null ? Float.MAX_VALUE : this.maxLength.getFloat();
      float op = this.opacity.getFloat() / 100.0F * p.alpha;

      for (int i = from; i < to && n < 1024; i++) {
         double wx = s.x[i];
         double wy = s.y[i];
         double wz = s.z[i];
         float u = Math.min(1.0F, s.along[i] / total);
         float age = s.age[i] / maxAge;
         float boost = this.speedBoost(s.speed[i]);
         float w = baseWidth * (1.0F - tp * u) * (float)Math.sqrt(Math.max(0.25F, 1.0F + boost)) * p.widthScale;
         float alpha = op
            * this.fade(age)
            * Math.min(1.3F, 1.0F + boost)
            * smooth(0.0F, 0.3F, s.along[i])
            * (1.0F - smooth(cap * 0.85F, cap, s.along[i]))
            * f.nearFade(wx, wy, wz)
            * f.fog(wx, wy, wz);
         float px = f.pixels(w, wx, wy, wz);
         if (px < 1.5F && px > 0.0F) {
            alpha *= px / 1.5F;
            w *= 1.5F / px;
         }

         Vfx.X[n] = wx - f.viewX;
         Vfx.Y[n] = wy - f.viewY;
         Vfx.Z[n] = wz - f.viewZ;
         Vfx.W[n] = w;
         POINT_ALPHA[n] = Math.min(1.0F, alpha);
         Vfx.C[n] = ColorUtil.mulAlpha(this.colorAt(u, age, s.speed[i], f.colorTime, shift), POINT_ALPHA[n]);
         n++;
      }

      return n;
   }

   protected final void recolor(PlayerTrail p, TrailFrame f, int k, int n, float shift) {
      TrailShape s = p.shape;
      int from = s.runStart[k];
      float maxAge = Math.max(0.05F, p.maxAge);
      float total = Math.max(0.001F, s.length);

      for (int j = 0; j < n; j++) {
         int i = from + j;
         Vfx.C[j] = ColorUtil.mulAlpha(this.colorAt(Math.min(1.0F, s.along[i] / total), s.age[i] / maxAge, s.speed[i], f.colorTime, shift), POINT_ALPHA[j]);
      }
   }

   protected static float smooth(float a, float b, float x) {
      float t = Math.max(0.0F, Math.min(1.0F, (x - a) / (b - a)));
      return t * t * (3.0F - 2.0F * t);
   }

   protected static int core(int color, float alpha) {
      return ColorUtil.mulAlpha(ColorUtil.core(color), alpha);
   }

   public static enum ColorAlong implements Displayable {
      HEAD_TO_TAIL("Head to tail", "The gradient from the head to the tail (pieces: along each line, up each ghost, or one spot each)"),
      BY_AGE("By age", "New parts take the start of the gradient, old ones its end"),
      BY_SPEED("By speed", "From walking speed to twice sprinting speed: speeding up shifts the color");

      private final String name;
      private final String description;

      private ColorAlong(String name, String description) {
         this.name = name;
         this.description = description;
      }

      @Override
      public String displayName() {
         return this.name;
      }

      @Override
      public String description() {
         return this.description;
      }
   }

   public static enum FadeCurve implements Displayable {
      LINEAR("Linear", "Fades evenly"),
      EASE_OUT("Ease out", "Fades quickly at first, then slowly"),
      LATE("Late fade", "Stays bright, then fades quickly");

      private final String name;
      private final String description;

      private FadeCurve(String name, String description) {
         this.name = name;
         this.description = description;
      }

      @Override
      public String displayName() {
         return this.name;
      }

      @Override
      public String description() {
         return this.description;
      }
   }

   protected static final class Spec {
      double[] size = new double[]{0.05, 1.0, 0.25, 0.01};
      String sizeUnit = " blocks";
      double length = 1.0;
      double taper = 70.0;
      TrailAnchor anchor = TrailAnchor.WAIST;
      boolean anchorFixed;
      boolean animated;
      boolean windy;
      TrailStyleBase.ColorAlong along;
      TrailStyleBase.FadeCurve fade = TrailStyleBase.FadeCurve.EASE_OUT;

      public TrailStyleBase.Spec size(double min, double max, double def, double step) {
         this.size = new double[]{min, max, def, step};
         return this;
      }

      public TrailStyleBase.Spec sizeUnit(String unit) {
         this.sizeUnit = unit;
         return this;
      }

      public TrailStyleBase.Spec length(double seconds) {
         this.length = seconds;
         return this;
      }

      public TrailStyleBase.Spec taper(double percent) {
         this.taper = percent;
         return this;
      }

      public TrailStyleBase.Spec anchor(TrailAnchor a, boolean fixed) {
         this.anchor = a;
         this.anchorFixed = fixed;
         return this;
      }

      public TrailStyleBase.Spec animated() {
         this.animated = true;
         return this;
      }

      public TrailStyleBase.Spec windy() {
         this.windy = true;
         return this;
      }

      public TrailStyleBase.Spec along(TrailStyleBase.ColorAlong a) {
         this.along = a;
         return this;
      }

      public TrailStyleBase.Spec fade(TrailStyleBase.FadeCurve curve) {
         this.fade = curve;
         return this;
      }
   }

   public interface Store {
      void update(float var1);

      int size();

      void clear();
   }

   public static enum WhenStop implements Displayable {
      FADE("Fade out", "The whole trail fades out"),
      RETRACT("Retract", "The tail pulls in towards you"),
      FREEZE("Freeze", "The trail stays where it is and fades over its Length");

      private final String name;
      private final String description;

      private WhenStop(String name, String description) {
         this.name = name;
         this.description = description;
      }

      @Override
      public String displayName() {
         return this.name;
      }

      @Override
      public String description() {
         return this.description;
      }
   }
}
