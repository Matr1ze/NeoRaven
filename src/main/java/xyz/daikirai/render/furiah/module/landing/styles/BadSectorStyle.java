package xyz.daikirai.render.furiah.module.landing.styles;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Terrain;
import xyz.daikirai.render.furiah.fx.kit.Glitch;
import xyz.daikirai.render.furiah.fx.look.NearCamera;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.BodyProxy;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import xyz.daikirai.render.furiah.setting.TextSetting;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

public final class BadSectorStyle extends LandingStyleBase {
   private static final float GLITCH = 0.5F;
   private static final float END = 0.9F;
   private static final float RENOISE = 0.05F;
   private static final float JUMP_BURST = 0.25F;
   private static final float JUMP_GONE = 0.36F;
   private static final int MAX_FACES = 260;
   private static final int MAX_TILES = 28;
   private static final int MAX_STREAKS = 22;
   private static final int MAX_GLYPHS = 8;
   private static final int STREAK_SEGS = 6;
   private static final int OUTLINED = 26;
   private static final double TILE_DEPTH = 0.0625;
   private static final double OUTLINE_W = 0.024;
   private static final String[] ERRORS = new String[]{"ERR", "NULL", "404", "BAD", "NaN", "0xDEAD", "I/O"};
   private static final String[] BLOCKS = new String[]{"█▓▒", "▒░▓", "▓█░"};
   private static final String HEX_DIGITS = "0123456789ABCDEF";
   private static final int HOLE = -16316660;
   public final ThemeTint channelB;
   public final ThemeTint glyphColor;
   public final SliderSetting radius;
   public final SliderSetting intensity;
   public final BooleanSetting tiles;
   public final BooleanSetting streaks;
   public final BooleanSetting glyphs;
   public final ModeSetting<GlyphSet> glyphSet;
   public final TextSetting text;
   private final Glitch.Plane plane = new Glitch.Plane();
   private final StringBuilder hexText = new StringBuilder(6);
   private final double[] q = new double[2];
   private final double[] axes = new double[4];
   private final double[] C = new double[8];
   private final float[] edgeU = new float[4];
   private final float[] edgeV = new float[4];

   public BadSectorStyle() {
      super(
         LandingStyle.BAD_SECTOR,
         0.9F,
         1.0F,
         new ThemeTint()
      );
      this.channelB = ThemeTint.detail(-16718337);
      this.glyphColor = ThemeTint.detail(-657931);
      this.radius = this.setting(
         new SliderSetting("badsector.radius", "Radius", "How far round the feet the ground glitches.", 1.0, 5.0, 2.5, 0.1).unit(" blocks")
      );
      this.intensity = this.look(
         this.setting(
            new SliderSetting(
                  "badsector.intensity", "Intensity", "How strong the glitch is: the split, the jumps, how many tiles and streaks.", 0.0, 100.0, 60.0, 5.0
               )
               .unit("%")
         )
      );
      this.tiles = this.setting(new BooleanSetting("badsector.tiles", "Tiles", "Square tiles of the ground jump up and sideways.", true));
      this.streaks = this.setting(new BooleanSetting("badsector.streaks", "Streaks", "Pixel-sort streaks smear outward over the ground.", true));
      this.glyphs = this.setting(new BooleanSetting("badsector.glyphsOn", "Glyphs", "Error glyphs flicker on the ground.", true));
      this.glyphSet = this.setting(
         new ModeSetting<>("badsector.glyphSet", "Glyph Set", "What the glyphs say.", GlyphSet.class, GlyphSet.ERRORS)
            .style(ModeSetting.Style.CHIPS)
            .visibleWhen(new BooleanSupplier() {
               @Override
               public boolean getAsBoolean() {
                  return BadSectorStyle.this.glyphs.get();
               }
            })
      );
      this.text = this.setting(
         new TextSetting("badsector.text", "Custom Text", "The glyphs' text with Custom text.", "BAD SECTOR", 12).visibleWhen(new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
               return BadSectorStyle.this.glyphs.get() && BadSectorStyle.this.glyphSet.get() == GlyphSet.CUSTOM;
            }
         })
      );
   }

   @Override
   public int passes(LandingFx fx) {
      return FxPass.TRANSLUCENT.bit() | FxPass.GLOW.bit();
   }

   @Override
   public void start(LandingFx fx) {
      BadSectorStyle.State s = new BadSectorStyle.State();
      s.jump = fx.land.jump;
      float in = this.intensity.getFloat() / 100.0F;
      s.r = this.radius.getFloat() * Math.max(0.6F, Math.min(1.6F, fx.size)) * (s.jump ? 0.45 : 1.0);
      WorldClient world = fx.preview ? null : Minecraft.getMinecraft().theWorld;
      scan(fx, s, world);
      int n = s.texture = world == null ? Textures.STONE : BodyProxy.texture(TextureMap.locationBlocksTexture);
      if (s.faces == 0) {
         fx.data = s;
      } else {
         double k = Math.sqrt(s.r / 2.5);
         s.tiles = fx.lod ? 0 : Math.min(28, (int)Math.round((5.0F + 14.0F * in) * k * (s.jump ? 0.5 : 1.0)));

         for (int i = 0; i < s.tiles; i++) {
            int face;
            s.tFace[i] = face = (int)(fx.rand(100 + i) * fx.rand(140 + i) * s.faces) % s.faces;
            boolean small = fx.rand(180 + i) < 0.35F;
            float size;
            s.tSize[i] = size = small ? 0.25F : 0.5F;
            s.tX[i] = (int)(fx.rand(220 + i) * (1.0F / size)) * size;
            s.tZ[i] = (int)(fx.rand(260 + i) * (1.0F / size)) * size;
            s.tTint[i] = picture(world, s, face, s.tUv, i * 8);
         }

         s.streaks = Math.min(22, (int)Math.round((6.0F + 12.0F * in) * k * (s.jump ? 0.5 : 1.0)));

         for (int var15 = 0; var15 < s.streaks; var15++) {
            s.sAngle[var15] = fx.rand(300 + var15) * (Math.PI * 2);
            s.sFrom[var15] = s.r * (0.12 + 0.45 * fx.rand(340 + var15));
            s.sLen[var15] = s.r * (0.3 + 0.5 * fx.rand(380 + var15));
            s.sWidth[var15] = 0.07 + 0.12 * fx.rand(420 + var15);
            int face = nearestFace(s, fx.x() + Math.cos(s.sAngle[var15]) * s.sFrom[var15], fx.z() + Math.sin(s.sAngle[var15]) * s.sFrom[var15]);
            s.sColor[var15] = world == null ? -7697782 : Terrain.mapColor(world, s.bx[face] + 0.5, s.by[face], s.bz[face] + 0.5, -7697782);
         }

         s.glyphs = fx.lod ? 0 : Math.min(8, 2 + Math.round(4.0F * in) + (s.r > 3.5 ? 2 : 0));
         if (s.jump) {
            s.glyphs = Math.min(s.glyphs, 2);
         }

         for (int var16 = 0; var16 < s.glyphs; var16++) {
            double a = fx.rand(540 + var16) * (Math.PI * 2);
            double d = s.r * (0.3 + 0.55 * fx.rand(580 + var16));
            s.gX[var16] = fx.x() + Math.cos(a) * d;
            s.gZ[var16] = fx.z() + Math.sin(a) * d;
            s.gH[var16] = (0.22 + 0.12 * fx.rand(620 + var16)) * Math.sqrt(Math.max(0.6F, fx.size));
            s.gPick[var16] = (int)(fx.rand(660 + var16) * 1000.0F);
         }

         fx.data = s;
      }
   }

   private static void scan(LandingFx fx, BadSectorStyle.State s, World world) {
      int cx = (int)Math.floor(fx.x());
      int cz = (int)Math.floor(fx.z());
      int span = (int)Math.ceil(s.r) + 1;
      s.cx = cx;
      s.cz = cz;
      s.span = span;
      int side = span * 2 + 1;
      s.grid = new float[side * side];
      Arrays.fill(s.grid, Float.NaN);
      double reach = (s.r + 0.7) * (s.r + 0.7);

      for (int ring = 0; ring <= span; ring++) {
         for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
                  double mx = cx + dx + 0.5;
                  double mz = cz + dz + 0.5;
                  double ddx = mx - fx.x();
                  double ddz;
                  if (!(ddx * ddx + (ddz = mz - fx.z()) * ddz > reach)) {
                     double h = world == null ? fx.y() : Terrain.surfaceAt(world, mx, mz, fx.y(), 2.0, 2.5);
                     if (!Double.isNaN(h)
                        && (
                           world == null
                              || !world.getBlockState(new BlockPos(cx + dx, (int)Math.floor(h + 0.001), cz + dz))
                                 .getBlock()
                                 .getMaterial()
                                 .isLiquid()
                        )) {
                        s.grid[(dx + span) * side + dz + span] = (float)h;
                        if (s.faces < 260) {
                           int i = s.faces++;
                           s.bx[i] = cx + dx;
                           s.bz[i] = cz + dz;
                           s.by[i] = (float)h;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static double groundAt(BadSectorStyle.State s, double x, double z) {
      int dx = (int)Math.floor(x) - s.cx + s.span;
      int dz = (int)Math.floor(z) - s.cz + s.span;
      int side = s.span * 2 + 1;
      return dx >= 0 && dz >= 0 && dx < side && dz < side ? s.grid[dx * side + dz] : Double.NaN;
   }

   private static int nearestFace(BadSectorStyle.State s, double x, double z) {
      int bx = (int)Math.floor(x);
      int bz = (int)Math.floor(z);

      for (int i = 0; i < s.faces; i++) {
         if (s.bx[i] == bx && s.bz[i] == bz) {
            return i;
         }
      }

      return 0;
   }

   private static int picture(World world, BadSectorStyle.State s, int face, float[] uv, int at) {
      if (world != null) {
         try {
            BlockPos pos = new BlockPos(s.bx[face], (int)Math.floor(s.by[face] - 0.001), s.bz[face]);
            IBlockState state = world.getBlockState(pos);
            Minecraft mc = Minecraft.getMinecraft();
            IBakedModel model = mc.getBlockRendererDispatcher().getModelFromBlockState(state, world, pos);
            BakedQuad top = null;
            if (model != null) {
               List quads = model.getFaceQuads(EnumFacing.UP);
               if (quads != null && !quads.isEmpty()) {
                  top = (BakedQuad)quads.get(0);
               } else if (model.getGeneralQuads() != null) {
                  for (BakedQuad bq : model.getGeneralQuads()) {
                     if (bq.getFace() == EnumFacing.UP) {
                        top = bq;
                        break;
                     }
                  }
               }
            }

            if (top != null && corners(top.getVertexData(), uv, at)) {
               return top.hasTintIndex() ? 0xFF000000 | state.getBlock().colorMultiplier(world, pos, top.getTintIndex()) : -1;
            }

            if (sprite(mc.getBlockRendererDispatcher().getBlockModelShapes().getTexture(state), uv, at)) {
               return -1;
            }
         } catch (RuntimeException var13) {
         }

         if (sprite(Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite("minecraft:blocks/stone"), uv, at)) {
            return -1;
         }
      }

      uv[at] = 0.0F;
      uv[at + 1] = 0.0F;
      uv[at + 2] = 1.0F;
      uv[at + 3] = 0.0F;
      uv[at + 4] = 0.0F;
      uv[at + 5] = 1.0F;
      uv[at + 6] = 1.0F;
      uv[at + 7] = 1.0F;
      return -7697782;
   }

   private static boolean sprite(TextureAtlasSprite sprite, float[] uv, int at) {
      if (sprite == null) {
         return false;
      } else {
         uv[at] = sprite.getMinU();
         uv[at + 1] = sprite.getMinV();
         uv[at + 2] = sprite.getMaxU();
         uv[at + 3] = sprite.getMinV();
         uv[at + 4] = sprite.getMinU();
         uv[at + 5] = sprite.getMaxV();
         uv[at + 6] = sprite.getMaxU();
         uv[at + 7] = sprite.getMaxV();
         return true;
      }
   }

   private static boolean corners(int[] data, float[] uv, int at) {
      if (data != null && data.length >= 24 && data.length % 4 == 0) {
         int stride = data.length / 4;
         int seen = 0;

         for (int v = 0; v < 4; v++) {
            float x = Float.intBitsToFloat(data[v * stride]);
            float z = Float.intBitsToFloat(data[v * stride + 2]);
            float u = Float.intBitsToFloat(data[v * stride + 4]);
            float w = Float.intBitsToFloat(data[v * stride + 5]);
            if (!(u >= 0.0F) || !(u <= 1.0F) || !(w >= 0.0F) || !(w <= 1.0F) || Math.abs(x - Math.round(x)) > 0.01F || Math.abs(z - Math.round(z)) > 0.01F) {
               return false;
            }

            int c = (z > 0.5F ? 2 : 0) + (x > 0.5F ? 1 : 0);
            if ((seen & 1 << c) != 0) {
               return false;
            }

            seen |= 1 << c;
            uv[at + c * 2] = u;
            uv[at + c * 2 + 1] = w;
         }

         return seen == 15;
      } else {
         return false;
      }
   }

   private static float out(LandingFx fx, BadSectorStyle.State s) {
      return s.jump ? 0.0F : Easing.clamp01((fx.styleTime() - 0.5F) / 0.39999998F);
   }

   private static float glitch(LandingFx fx, BadSectorStyle.State s) {
      float t = fx.styleTime();
      return s.jump ? 1.0F - Easing.smoothstep(0.25F, 0.36F, t) : 1.0F - Easing.smoothstep(0.25F, 0.6F, out(fx, s));
   }

   private void axes(Camera cam) {
      double ux = cam.rightX;
      double uz = cam.rightZ;
      double ul = Math.sqrt(ux * ux + uz * uz);
      if (ul < 1.0E-4) {
         ux = 1.0;
         uz = 0.0;
         ul = 1.0;
      }

      this.axes[0] = ux / ul;
      this.axes[1] = uz / ul;
      this.axes[2] = -this.axes[1];
      this.axes[3] = this.axes[0];
      double vx = cam.forwardX;
      double vz = cam.forwardZ;
      if (vx * this.axes[2] + vz * this.axes[3] < 0.0) {
         this.axes[2] = -this.axes[2];
         this.axes[3] = -this.axes[3];
      }
   }

   private void squash(LandingFx fx, double x, double z, double ku, double kv) {
      double dx = x - fx.x();
      double dz = z - fx.z();
      double u = dx * this.axes[0] + dz * this.axes[1];
      double v = dx * this.axes[2] + dz * this.axes[3];
      double var18;
      double var19;
      this.q[0] = fx.x() + this.axes[0] * (var18 = u * ku) + this.axes[2] * (var19 = v * kv);
      this.q[1] = fx.z() + this.axes[1] * var18 + this.axes[3] * var19;
   }

   @Override
   public void renderPass(LandingFx fx, FxFrame f, FxPass pass) {
      BadSectorStyle.State s = (BadSectorStyle.State)fx.data;
      if (s != null && s.faces != 0) {
         float t = fx.styleTime();
         float alpha = fx.alpha();
         if ((!s.jump || !(t >= 0.36F)) && !(alpha <= 0.003F)) {
            Camera cam = Vfx.camera();
            this.axes(cam);
            float o = out(fx, s);
            double kv = Glitch.crtHeight(o);
            double ku = Glitch.crtWidth(o);
            float g = glitch(fx, s);
            boolean calm = Glitch.reduceFlashing();
            int slot = (int)Math.floor(t / 0.05F);
            float in = this.intensity.getFloat() / 100.0F;
            int ca = this.color.getColor(0.5F) | 0xFF000000;
            int cb = this.channelB.getColor(0.5F) | 0xFF000000;
            int cg = this.glyphColor.getColor(0.5F) | 0xFF000000;
            double d = (0.012 + 0.05 * in) * (0.5 + 0.5 * g);
            if (pass == FxPass.GLOW) {
               this.line(fx, f, s, o, ku, calm, ca, cb, cg, alpha);
            } else {
               if (g > 0.003F) {
                  RenderState.decal(true);

                  try {
                     RenderState.translucent(false);
                     if (this.tiles.get() && !calm && !fx.lod) {
                        this.holes(fx, f, s, slot, in, ku, kv, alpha * g, ca, cb);
                     }

                     this.outlines(fx, f, s, slot, calm, ku, kv, d, alpha * g, ca, cb, cg);
                     if (this.streaks.get()) {
                        this.streaks(fx, f, s, t, slot, ku, kv, d, alpha * g, ca, cb);
                     }

                     if (this.glyphs.get() && !fx.lod) {
                        this.glyphs(fx, f, s, slot, calm, ku, kv, d, alpha * g, ca, cb, cg);
                     }
                  } finally {
                     RenderState.decal(false);
                  }

                  if (this.tiles.get() && !calm && !fx.lod) {
                     this.jumped(fx, f, s, cam, slot, in, ku, kv, d, alpha * g, ca, cb);
                  }
               }

               this.lineCore(fx, f, s, o, ku, calm, cg, alpha);
               RenderState.translucent(true);
            }
         }
      }
   }

   private static boolean jumping(BadSectorStyle.State s, int i, int slot, float in) {
      float p = 0.22F + 0.38F * in;
      return Glitch.hash(slot, i * 7 + 1) < p || Glitch.hash(slot - 1, i * 7 + 1) < p * 0.5F;
   }

   private void holes(LandingFx fx, FxFrame f, BadSectorStyle.State s, int slot, float in, double ku, double kv, float a, int ca, int cb) {
      for (int i = 0; i < s.tiles; i++) {
         if (jumping(s, i, slot, in)) {
            int face = s.tFace[i];
            double x0 = s.bx[face] + s.tX[i];
            double z0 = s.bz[face] + s.tZ[i];
            double sz = s.tSize[i];
            double y = f.ry(s.by[face] + 0.004);
            this.corner(fx, f, x0, z0, ku, kv, 0);
            this.corner(fx, f, x0 + sz, z0, ku, kv, 2);
            this.corner(fx, f, x0 + sz, z0 + sz, ku, kv, 4);
            this.corner(fx, f, x0, z0 + sz, ku, kv, 6);
            int c = ColorUtil.mulAlpha(-16316660, 0.92F * a);
            Vfx.quadUV(
               Textures.WHITE,
               this.C[0],
               y,
               this.C[1],
               0.5,
               0.5,
               c,
               this.C[2],
               y,
               this.C[3],
               0.5,
               0.5,
               c,
               this.C[4],
               y,
               this.C[5],
               0.5,
               0.5,
               c,
               this.C[6],
               y,
               this.C[7],
               0.5,
               0.5,
               c
            );
            int ea = Glitch.alphaOf(ca, 0.7F * a);
            int eb = Glitch.alphaOf(cb, 0.7F * a);

            for (int e = 0; e < 4; e++) {
               int p = e * 2;
               int n = (e + 1) % 4 * 2;
               Vfx.flatLine(
                  Textures.WHITE,
                  this.C[p] - this.axes[0] * 0.01,
                  this.C[p + 1] - this.axes[1] * 0.01,
                  this.C[n] - this.axes[0] * 0.01,
                  this.C[n + 1] - this.axes[1] * 0.01,
                  y + 0.001,
                  0.0168,
                  ea,
                  ea
               );
               Vfx.flatLine(
                  Textures.WHITE,
                  this.C[p] + this.axes[0] * 0.01,
                  this.C[p + 1] + this.axes[1] * 0.01,
                  this.C[n] + this.axes[0] * 0.01,
                  this.C[n + 1] + this.axes[1] * 0.01,
                  y + 0.001,
                  0.0168,
                  eb,
                  eb
               );
            }
         }
      }
   }

   private void corner(LandingFx fx, FxFrame f, double x, double z, double ku, double kv, int at) {
      this.squash(fx, x, z, ku, kv);
      this.C[at] = f.rx(this.q[0]);
      this.C[at + 1] = f.rz(this.q[1]);
   }

   private void jumped(LandingFx fx, FxFrame f, BadSectorStyle.State s, Camera cam, int slot, float in, double ku, double kv, double d, float a, int ca, int cb) {
      RenderState.translucent(true);

      for (int i = 0; i < s.tiles; i++) {
         if (jumping(s, i, slot, in)) {
            int face = s.tFace[i];
            int seed = slot * 31 + i;
            double up = (0.05 + 0.25 * Glitch.hash(seed, 3)) * (0.5 + 0.5 * in);
            double sx = (Glitch.hash(seed, 5) - 0.5) * 0.5 * (0.4 + 0.6 * in);
            double sz2 = (Glitch.hash(seed, 7) - 0.5) * 0.5 * (0.4 + 0.6 * in);
            double x0 = s.bx[face] + s.tX[i] + sx;
            double z0 = s.bz[face] + s.tZ[i] + sz2;
            double size = s.tSize[i];
            double top = s.by[face] + up;
            double y = f.ry(top);
            float fade = a * NearCamera.fade(cam, f.rx(x0 + size * 0.5), y, f.rz(z0 + size * 0.5));
            if (!(fade <= 0.003F)) {
               this.corner(fx, f, x0, z0, ku, kv, 0);
               this.corner(fx, f, x0 + size, z0, ku, kv, 2);
               this.corner(fx, f, x0 + size, z0 + size, ku, kv, 4);
               this.corner(fx, f, x0, z0 + size, ku, kv, 6);
               float lx0 = s.tX[i];
               float lz0 = s.tZ[i];
               float lx1 = lx0 + s.tSize[i];
               float lz1 = lz0 + s.tSize[i];
               int b = i * 8;
               float u00 = sub(s.tUv, b, 0, lx0, lz0);
               float v00 = sub(s.tUv, b, 1, lx0, lz0);
               float u10 = sub(s.tUv, b, 0, lx1, lz0);
               float v10 = sub(s.tUv, b, 1, lx1, lz0);
               float u11 = sub(s.tUv, b, 0, lx1, lz1);
               float v11 = sub(s.tUv, b, 1, lx1, lz1);
               float u01 = sub(s.tUv, b, 0, lx0, lz1);
               float v01 = sub(s.tUv, b, 1, lx0, lz1);
               int lit = ColorUtil.shade(s.tTint[i], Math.max(0.25F, fx.light)) | 0xFF000000;
               int ka = Glitch.alphaOf(ColorUtil.multiply(lit, ca), 0.55F * fade);
               int kb = Glitch.alphaOf(ColorUtil.multiply(lit, cb), 0.55F * fade);

               for (int ch = -1; ch <= 1; ch += 2) {
                  double ox = this.axes[0] * d * 1.6 * ch;
                  double oz = this.axes[1] * d * 1.6 * ch;
                  int c = ch < 0 ? ka : kb;
                  Vfx.quadUV(
                     s.texture,
                     this.C[0] + ox,
                     y - 0.002,
                     this.C[1] + oz,
                     u00,
                     v00,
                     c,
                     this.C[2] + ox,
                     y - 0.002,
                     this.C[3] + oz,
                     u10,
                     v10,
                     c,
                     this.C[4] + ox,
                     y - 0.002,
                     this.C[5] + oz,
                     u11,
                     v11,
                     c,
                     this.C[6] + ox,
                     y - 0.002,
                     this.C[7] + oz,
                     u01,
                     v01,
                     c
                  );
               }

               int c = ColorUtil.mulAlpha(lit, fade);
               Vfx.quadUV(
                  s.texture,
                  this.C[0],
                  y,
                  this.C[1],
                  u00,
                  v00,
                  c,
                  this.C[2],
                  y,
                  this.C[3],
                  u10,
                  v10,
                  c,
                  this.C[4],
                  y,
                  this.C[5],
                  u11,
                  v11,
                  c,
                  this.C[6],
                  y,
                  this.C[7],
                  u01,
                  v01,
                  c
               );
               int side = ColorUtil.mulAlpha(ColorUtil.shade(lit, 0.62F) | 0xFF000000, fade);
               double yb = y - 0.0625 * Math.min(1.0, kv + 0.2);
               float[] us = this.edgeU;
               float[] vs = this.edgeV;
               us[0] = u00;
               us[1] = u10;
               us[2] = u11;
               us[3] = u01;
               vs[0] = v00;
               vs[1] = v10;
               vs[2] = v11;
               vs[3] = v01;

               for (int e = 0; e < 4; e++) {
                  int p = e * 2;
                  int n = (e + 1) % 4 * 2;
                  Vfx.quadUV(
                     s.texture,
                     this.C[p],
                     y,
                     this.C[p + 1],
                     us[e],
                     vs[e],
                     side,
                     this.C[n],
                     y,
                     this.C[n + 1],
                     us[(e + 1) % 4],
                     vs[(e + 1) % 4],
                     side,
                     this.C[n],
                     yb,
                     this.C[n + 1],
                     us[(e + 1) % 4],
                     vs[(e + 1) % 4],
                     side,
                     this.C[p],
                     yb,
                     this.C[p + 1],
                     us[e],
                     vs[e],
                     side
                  );
               }
            }
         }
      }

      RenderState.translucent(false);
   }

   private static float sub(float[] uv, int b, int k, float x, float z) {
      float a = uv[b + k] + (uv[b + 2 + k] - uv[b + k]) * x;
      float c = uv[b + 4 + k] + (uv[b + 6 + k] - uv[b + 4 + k]) * x;
      return a + (c - a) * z;
   }

   private void outlines(
      LandingFx fx, FxFrame f, BadSectorStyle.State s, int slot, boolean calm, double ku, double kv, double d, float a, int ca, int cb, int cg
   ) {
      int n = Math.min(s.faces, fx.lod ? 13 : 26);
      double reach = Math.max(1.3, s.r * 0.75);
      int ea = Glitch.alphaOf(ca, 0.8F * a);
      int eb = Glitch.alphaOf(cb, 0.8F * a);
      int core = Glitch.alphaOf(cg, 0.45F * a);

      for (int i = 0; i < n; i++) {
         double mx = s.bx[i] + 0.5;
         double mz = s.bz[i] + 0.5;
         if (!(Math.hypot(mx - fx.x(), mz - fx.z()) > reach) && (calm || !(Glitch.hash(slot, i * 3 + 11) > 0.62F))) {
            double y = f.ry(s.by[i] + 0.012);
            double jx = calm ? 0.0 : (Glitch.hash(slot, i * 3 + 19) - 0.5) * 0.08;
            double jz = calm ? 0.0 : (Glitch.hash(slot, i * 3 + 23) - 0.5) * 0.08;
            this.corner(fx, f, s.bx[i] + jx, s.bz[i] + jz, ku, kv, 0);
            this.corner(fx, f, s.bx[i] + 1 + jx, s.bz[i] + jz, ku, kv, 2);
            this.corner(fx, f, s.bx[i] + 1 + jx, s.bz[i] + 1 + jz, ku, kv, 4);
            this.corner(fx, f, s.bx[i] + jx, s.bz[i] + 1 + jz, ku, kv, 6);
            double dd = d * (calm ? 1.0 : 0.6 + 0.8 * Glitch.hash(slot, i * 3 + 13));

            for (int e = 0; e < 4; e++) {
               if (calm || !(Glitch.hash(slot * 4 + e, i * 3 + 17) < 0.25F)) {
                  int p = e * 2;
                  int m = (e + 1) % 4 * 2;
                  Vfx.flatLine(
                     Textures.WHITE,
                     this.C[p] - this.axes[0] * dd,
                     this.C[p + 1] - this.axes[1] * dd,
                     this.C[m] - this.axes[0] * dd,
                     this.C[m + 1] - this.axes[1] * dd,
                     y,
                     0.024,
                     ea,
                     ea
                  );
                  Vfx.flatLine(
                     Textures.WHITE,
                     this.C[p] + this.axes[0] * dd,
                     this.C[p + 1] + this.axes[1] * dd,
                     this.C[m] + this.axes[0] * dd,
                     this.C[m + 1] + this.axes[1] * dd,
                     y,
                     0.024,
                     eb,
                     eb
                  );
                  Vfx.flatLine(Textures.WHITE, this.C[p], this.C[p + 1], this.C[m], this.C[m + 1], y + 5.0E-4, 0.012, core, core);
               }
            }
         }
      }
   }

   private void streaks(LandingFx fx, FxFrame f, BadSectorStyle.State s, float t, int slot, double ku, double kv, double d, float a, int ca, int cb) {
      double grow = Easing.outCubic(Easing.clamp01(t / (s.jump ? 0.15F : 0.3F)));
      float light = Math.max(0.35F, fx.light);

      for (int i = 0; i < s.streaks; i++) {
         double ang = s.sAngle[i];
         double cx = Math.cos(ang);
         double cz = Math.sin(ang);
         double px = -cz;
         double pz = cx;
         double len = s.sLen[i] * grow;
         double w = s.sWidth[i];

         for (int j = 0; j < 6; j++) {
            float sorted = j / 5.0F;
            int base = s.sColor[i] | 0xFF000000;
            int lit = ColorUtil.shade(
                  sorted < 0.5F ? ColorUtil.brighten(base, 0.5F * (1.0F - 2.0F * sorted)) : ColorUtil.shade(base, 1.0F - 0.5F * (2.0F * sorted - 1.0F)), light
               )
               | 0xFF000000;
            double r0 = s.sFrom[i] + len * j / 6.0;
            double r1 = s.sFrom[i] + len * (j + 1) / 6.0;
            double shift = (Glitch.hash(slot * 13 + i, j) - 0.5) * w * 0.9;
            double mx = fx.x() + cx * (r0 + r1) * 0.5 + px * shift;
            double gy = groundAt(s, mx, fx.z() + cz * (r0 + r1) * 0.5 + pz * shift);
            if (Double.isNaN(gy)) {
               break;
            }

            double y = f.ry(gy + 0.014 + 4.0E-4 * j);
            float k = a * (1.0F - j / 6.0F) * 0.9F;
            double hw = w * 0.5 * (1.0 - 0.25 * j / 6.0);
            this.corner(fx, f, fx.x() + cx * r0 + px * (shift - hw), fx.z() + cz * r0 + pz * (shift - hw), ku, kv, 0);
            this.corner(fx, f, fx.x() + cx * r1 + px * (shift - hw), fx.z() + cz * r1 + pz * (shift - hw), ku, kv, 2);
            this.corner(fx, f, fx.x() + cx * r1 + px * (shift + hw), fx.z() + cz * r1 + pz * (shift + hw), ku, kv, 4);
            this.corner(fx, f, fx.x() + cx * r0 + px * (shift + hw), fx.z() + cz * r0 + pz * (shift + hw), ku, kv, 6);
            int c = ColorUtil.mulAlpha(lit, k);
            Vfx.quadUV(
               Textures.WHITE,
               this.C[0],
               y,
               this.C[1],
               0.5,
               0.5,
               c,
               this.C[2],
               y,
               this.C[3],
               0.5,
               0.5,
               c,
               this.C[4],
               y,
               this.C[5],
               0.5,
               0.5,
               c,
               this.C[6],
               y,
               this.C[7],
               0.5,
               0.5,
               c
            );
            int ea = Glitch.alphaOf(ca, 0.6F * k);
            int eb = Glitch.alphaOf(cb, 0.6F * k);
            Vfx.flatLine(
               Textures.WHITE,
               this.C[0] - this.axes[0] * d,
               this.C[1] - this.axes[1] * d,
               this.C[2] - this.axes[0] * d,
               this.C[3] - this.axes[1] * d,
               y + 5.0E-4,
               0.0144,
               ea,
               ea
            );
            Vfx.flatLine(
               Textures.WHITE,
               this.C[6] + this.axes[0] * d,
               this.C[7] + this.axes[1] * d,
               this.C[4] + this.axes[0] * d,
               this.C[5] + this.axes[1] * d,
               y + 5.0E-4,
               0.0144,
               eb,
               eb
            );
         }
      }
   }

   private void glyphs(LandingFx fx, FxFrame f, BadSectorStyle.State s, int slot, boolean calm, double ku, double kv, double d, float a, int ca, int cb, int cg) {
      int tex = Glitch.atlas();

      for (int i = 0; i < s.glyphs; i++) {
         double gz;
         double gx;
         double gy;
         CharSequence str;
         if ((calm || !(Glitch.hash(slot, i * 5 + 71) > 0.68F))
            && (str = this.glyphText(s.gPick[i])) != null
            && str.length() != 0
            && !Double.isNaN(
               gy = groundAt(
                  s,
                  gx = s.gX[i] + (calm ? 0.0 : (Glitch.hash(slot, i * 5 + 73) - 0.5) * 0.08),
                  gz = s.gZ[i] + (calm ? 0.0 : (Glitch.hash(slot, i * 5 + 75) - 0.5) * 0.08)
               )
            )) {
            this.squash(fx, gx, gz, ku, kv);
            double gh = s.gH[i];
            double width = Glitch.textWidth(str) * gh;
            this.plane
               .set(f.rx(this.q[0]), f.ry(gy + 0.016), f.rz(this.q[1]), this.axes[0] * ku, 0.0, this.axes[1] * ku, this.axes[2] * kv, 0.0, this.axes[3] * kv);
            this.plane
               .splitText(
                  tex, str, -width * 0.5, -gh * 0.3, gh, d * 1.2, Glitch.alphaOf(ca, 0.7F * a), Glitch.alphaOf(cb, 0.7F * a), Glitch.alphaOf(cg, 0.95F * a)
               );
         }
      }
   }

   private CharSequence glyphText(int pick) {
      switch ((GlyphSet)this.glyphSet.get()) {
         case HEX:
            this.hexText.setLength(0);
            this.hexText.append('0').append('x').append("0123456789ABCDEF".charAt(pick % 16)).append("0123456789ABCDEF".charAt(pick / 16 % 16));
            return this.hexText;
         case BLOCKS:
            return BLOCKS[pick % BLOCKS.length];
         case CUSTOM:
            return this.text.get();
         default:
            return ERRORS[pick % ERRORS.length];
      }
   }

   private void lineCore(LandingFx fx, FxFrame f, BadSectorStyle.State s, float o, double ku, boolean calm, int cg, float alpha) {
      float k = lineStrength(o) * (calm ? 0.45F : 1.0F) * alpha;
      if (!(k <= 0.003F)) {
         RenderState.decal(true);
         RenderState.translucent(false);
         this.drawLine(fx, f, s, Textures.LINE_CORE, s.r * ku, 0.05, Glitch.alphaOf(cg, k));
         RenderState.decal(false);
      }
   }

   private void line(LandingFx fx, FxFrame f, BadSectorStyle.State s, float o, double ku, boolean calm, int ca, int cb, int cg, float alpha) {
      if (!s.jump && !calm) {
         float k = lineStrength(o) * alpha;
         if (k > 0.003F) {
            RenderState.decal(true);
            this.drawLine(fx, f, s, Textures.LINE_GLOW, s.r * ku, 0.34, Glitch.alphaOf(ColorUtil.lerp(ca, cb, 0.5F), 0.55F * k));
            this.drawLine(fx, f, s, Textures.LINE_GLOW, s.r * ku, 0.14, Glitch.alphaOf(cg, 0.8F * k));
            RenderState.decal(false);
         }

         float dot;
         if ((dot = Glitch.crtDot(o) * alpha) > 0.003F) {
            double gy = groundAt(s, fx.x(), fx.z());
            double y = Double.isNaN(gy) ? fx.y() : gy;
            Vfx.sprite(Textures.GLOW, f.rx(fx.x()), f.ry(y + 0.06), f.rz(fx.z()), 0.5 * Math.sqrt(s.r / 2.5), 0.0F, Glitch.alphaOf(cg, 0.9F * dot));
         }
      }
   }

   private static float lineStrength(float o) {
      return Easing.smoothstep(0.2F, 0.5F, o) * (1.0F - Easing.smoothstep(0.8F, 0.92F, o));
   }

   private void drawLine(LandingFx fx, FxFrame f, BadSectorStyle.State s, int tex, double half, double width, int col) {
      if (!(half <= 0.01)) {
         int parts = Math.max(2, (int)Math.ceil(half * 4.0));

         for (int p = 0; p < parts; p++) {
            double a0 = -half + 2.0 * half * p / parts;
            double a1 = -half + 2.0 * half * (p + 1) / parts;
            double am = (a0 + a1) * 0.5;
            double gy = groundAt(s, fx.x() + this.axes[0] * am, fx.z() + this.axes[1] * am);
            if (!Double.isNaN(gy)) {
               Vfx.flatLine(
                  tex,
                  f.rx(fx.x() + this.axes[0] * a0),
                  f.rz(fx.z() + this.axes[1] * a0),
                  f.rx(fx.x() + this.axes[0] * a1),
                  f.rz(fx.z() + this.axes[1] * a1),
                  f.ry(gy + 0.018),
                  width,
                  col,
                  col
               );
            }
         }
      }
   }

   private static final class State {
      boolean jump;
      double r;
      int faces;
      final int[] bx = new int[260];
      final int[] bz = new int[260];
      final float[] by = new float[260];
      int span;
      int cx;
      int cz;
      float[] grid;
      int texture;
      int tiles;
      int streaks;
      int glyphs;
      final int[] tFace = new int[28];
      final int[] tTint = new int[28];
      final float[] tX = new float[28];
      final float[] tZ = new float[28];
      final float[] tSize = new float[28];
      final float[] tUv = new float[224];
      final double[] sAngle = new double[22];
      final double[] sFrom = new double[22];
      final double[] sLen = new double[22];
      final double[] sWidth = new double[22];
      final int[] sColor = new int[22];
      final double[] gX = new double[8];
      final double[] gZ = new double[8];
      final double[] gH = new double[8];
      final int[] gPick = new int[8];

      private State() {
      }
   }
   public enum GlyphSet implements Displayable {
      ERRORS("Errors", "ERR, NULL, 404..."),
      HEX("Hex", "0x and a byte"),
      BLOCKS("Blocks", "Shade blocks"),
      CUSTOM("Custom text", "Your own text, up to 12 characters");

      private final String displayName;
      private final String description;

      private GlyphSet(String displayName, String description) {
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
