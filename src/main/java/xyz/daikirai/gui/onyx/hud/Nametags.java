package xyz.daikirai.gui.onyx.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import xyz.daikirai.client.Chat;
import xyz.daikirai.client.RenderDispatcher;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.render.NametagsModule;
import xyz.daikirai.util.Render3D;
import xyz.daikirai.util.RenderUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Material 3 name plate, ported from OpenOnyx's {@code NamesModule} ("ESP").
 *
 * <p>Onyx drew a rounded plate above each player with the name plus optional rank, health and ping
 * readouts, and a strip of equipment tiles next to it. This port keeps the readouts, the equipment
 * strip and the distance scaling, and adds what the rest of this client's HUD has: the shared
 * frosted-glass backdrop, a scale setting and colour settings for both the plate and its text
 * (both open the Material colour picker).</p>
 *
 * <p>Everything is drawn in GUI space from the render tick (like the target card and the potion
 * list) instead of inside the entity renderer: each player's head is projected once per frame and
 * the plate is laid out around that point, so the card always faces the camera and its blur
 * samples the same backdrop as the other HUD elements.</p>
 */
public final class Nametags {

    private static final float PAD = 5.0F;
    private static final float GAP = 4.0F;
    private static final float PLATE_H = 18.0F;
    private static final float RADIUS = 9.0F;
    private static final float TILE = 20.0F;
    private static final float TILE_GAP = 2.0F;
    private static final float HEAD_GAP = 6.0F;
    private static final float MAX_NAME = 120.0F;
    private static final int HEALTHY = 0xFF5FD35F;
    private static final int WARN = 0xFFE8C547;
    private static final int BAD = 0xFFE05B5B;

    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer SCREEN = BufferUtils.createFloatBuffer(3);

    /** Camera position for the frame, taken from the modelview matrix by {@link #beginCamera}. */
    private static final double[] CAMERA = new double[3];

    private final UI ui = new UI();
    private final Anim visible = new Anim(0.0F);
    private long lastFrame;
    /** How many plates were drawn in the last frame (used by the dev harness). */
    private int drawn;
    private int frames;

    /** Draws every visible player's plate for this frame. */
    public void render(NametagsModule module, float partialTicks) {
        if (module == null) {
            return;
        }
        float delta = tick();
        drawn = 0;
        frames++;
        Minecraft mc = Minecraft.getMinecraft();
        boolean active = module.isEnabled() && mc.theWorld != null && mc.thePlayer != null;
        visible.to(active ? 1.0F : 0.0F, active ? 200.0F : 160.0F,
                active ? Ease.EMPHASIZED : Ease.DECELERATE);
        visible.update(delta);
        float fade = visible.value();
        if (fade <= 0.002F || !active) {
            return;
        }

        Scheme scheme = Theme.scheme();
        ScaledResolution resolution = new ScaledResolution(mc);
        List<EntityPlayer> players = new ArrayList<EntityPlayer>(mc.theWorld.playerEntities);
        players.sort(Comparator.comparingDouble(p -> -mc.thePlayer.getDistanceSqToEntity(p)));

        ui.begin();
        try {
            GlStateManager.pushMatrix();
            mc.entityRenderer.setupOverlayRendering();
            if (module.frosted() && fade > 0.01F) {
                Blur.prepare();
            }
            ui.alphaPush(fade);
            // One camera snapshot per frame: every plate has to project against the same matrices
            // the world was drawn with.
            if (beginCamera(partialTicks)) {
                for (EntityPlayer player : players) {
                    if (!shows(module, player)) {
                        continue;
                    }
                    float[] head = project(player, partialTicks, resolution);
                    if (head == null) {
                        continue;
                    }
                    float distance = mc.thePlayer.getDistanceToEntity(player);
                    float scale = module.scale() * (module.autoScale() ? autoScale(distance) : 1.0F);
                    drawPlate(module, scheme, player, head[0], head[1], scale);
                    drawn++;
                }
            }
            ui.alphaPop();
        } finally {
            GlStateManager.popMatrix();
            ui.end();
            restoreHudState();
        }
    }

    // ------------------------------------------------------------------ //
    // Which players get a plate
    // ------------------------------------------------------------------ //

    private static boolean shows(NametagsModule module, EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (player == null) {
            return false;
        }
        // The local player only gets a plate in third person, and only when asked for: in first
        // person it would sit on the crosshair.
        if (player == mc.thePlayer && (!module.renderSelf() || mc.gameSettings.thirdPersonView == 0)) {
            return false;
        }
        if (player.isDead || player.deathTime != 0 || player.isSpectator() || player.isPlayerSleeping()) {
            return false;
        }
        if (mc.thePlayer.getDistanceSqToEntity(player) > module.distance() * module.distance()) {
            return false;
        }
        return RenderDispatcher.visible(player, module.showInvis());
    }

    /** Closer players get a slightly larger plate, like Onyx's distance scaling. */
    private static float autoScale(float distance) {
        return Material.clamp(7.0F / Math.max(2.0F, distance), 0.55F, 1.1F);
    }

    // ------------------------------------------------------------------ //
    // Drawing
    // ------------------------------------------------------------------ //

    private void drawPlate(NametagsModule module, Scheme scheme, EntityPlayer player,
                           float headX, float headY, float scale) {
        List<Piece> pieces = pieces(module, scheme, player);
        float textWidth = 0.0F;
        for (int i = 0; i < pieces.size(); i++) {
            textWidth += pieces.get(i).width();
            if (i > 0) {
                textWidth += GAP;
            }
        }
        float plateWidth = textWidth + PAD * 2.0F;
        float opacity = Material.clamp(module.backgroundOpacity() / 100.0F, 0.0F, 1.0F);
        boolean background = module.background() && opacity > 0.01F;
        int backgroundTint = Material.withAlpha(module.backgroundColor().color(), 1.0F);
        int backgroundFill = Material.withAlpha(module.backgroundColor().color(), opacity);

        List<ItemStack> equipment = module.showItems() ? equipment(player) : new ArrayList<ItemStack>();
        float strip = equipment.isEmpty()
                ? 0.0F
                : equipment.size() * TILE + (equipment.size() - 1) * TILE_GAP;
        float tileTop = -PLATE_H - TILE_GAP - TILE;

        ui.push();
        ui.translate(headX, headY - HEAD_GAP);
        ui.scale(scale, 0.0F, 0.0F);

        if (background) {
            float left = -plateWidth / 2.0F;
            if (module.frosted() && Blur.ready()) {
                // The blurred backdrop is laid down first and the plate tint is mixed over it
                // afterwards. Feeds the opacity into the shader as well (as the first port did)
                // and the plate is attenuated twice, which on anything but a bright sky reads as
                // no background at all.
                ui.frost(left, -PLATE_H, plateWidth, PLATE_H, RADIUS, backgroundTint, 0.0F);
            }
            ui.rounded(left, -PLATE_H, plateWidth, PLATE_H, RADIUS, backgroundFill);
        }

        float cursor = -textWidth / 2.0F;
        for (int i = 0; i < pieces.size(); i++) {
            Piece piece = pieces.get(i);
            ui.text(piece.style, piece.text, cursor, -PLATE_H / 2.0F, piece.color, UI.ALIGN_LEFT);
            cursor += piece.width() + GAP;
        }

        if (!equipment.isEmpty()) {
            float tileCursor = -strip / 2.0F;
            for (ItemStack stack : equipment) {
                if (background) {
                    ui.rounded(tileCursor, tileTop, TILE, TILE, 5.0F, backgroundFill);
                }
                // RenderUtils.drawItem binds the item atlas but never enables texturing, which the
                // UI leaves off after every shape; without this the item renders as a white quad.
                GlStateManager.enableTexture2D();
                RenderUtils.drawItem(stack, tileCursor + 2.0F, tileTop + 2.0F);
                // Item rendering leaves blending off: the next tile, plate or piece of text needs
                // the UI's own blend state back.
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
                GlStateManager.enableTexture2D();
                tileCursor += TILE + TILE_GAP;
            }
        }

        if (background && module.outline()) {
            ui.outline(-plateWidth / 2.0F, -PLATE_H, plateWidth, PLATE_H, RADIUS, 1.0F,
                    Material.withAlpha(module.backgroundColor().color(), opacity * 0.6F));
        }

        ui.pop();
    }

    /** The readouts on the plate: rank, name, health, ping and distance, in that order. */
    private List<Piece> pieces(NametagsModule module, Scheme scheme, EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        List<Piece> pieces = new ArrayList<Piece>(5);
        int textColor = Material.withAlpha(module.textColor().color(), 1.0F);
        if (module.showRank()) {
            String rank = rank(player);
            if (!rank.isEmpty()) {
                pieces.add(new Piece(Typeface.CAPTION, rank, Material.withAlpha(textColor, 0.85F)));
            }
        }
        String name = player.getName() == null ? "" : player.getName();
        pieces.add(new Piece(Typeface.BODY_MEDIUM, ui.truncate(Typeface.BODY_MEDIUM, name, MAX_NAME),
                textColor));
        if (module.showHealth()) {
            float shown = player.getHealth() + player.getAbsorptionAmount();
            float max = Math.max(1.0F, player.getMaxHealth());
            float fraction = Material.clamp(player.getHealth() / max, 0.0F, 1.0F);
            pieces.add(new Piece(Typeface.CAPTION, Math.round(shown) + "hp",
                    mix(scheme.error, HEALTHY, fraction)));
        }
        if (module.showPing()) {
            int ping = ping(player);
            if (ping > 0) {
                pieces.add(new Piece(Typeface.CAPTION, ping + "ms",
                        ping < 120 ? HEALTHY : ping < 250 ? WARN : BAD));
            }
        }
        if (module.showDistance() && player != mc.thePlayer) {
            pieces.add(new Piece(Typeface.CAPTION,
                    Math.round(mc.thePlayer.getDistanceToEntity(player)) + "m",
                    Material.withAlpha(textColor, 0.65F)));
        }
        return pieces;
    }

    /** The team prefix, which is where most servers put a player's rank. */
    private static String rank(EntityPlayer player) {
        Team team = player.getTeam();
        if (team instanceof ScorePlayerTeam) {
            String prefix = Chat.strip(((ScorePlayerTeam) team).getColorPrefix()).trim();
            if (!prefix.isEmpty()) {
                return prefix;
            }
        }
        return "";
    }

    private static int ping(EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getNetHandler() == null) {
            return 0;
        }
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
        return info == null ? 0 : Math.max(0, info.getResponseTime());
    }

    private static List<ItemStack> equipment(EntityPlayer player) {
        List<ItemStack> items = new ArrayList<ItemStack>(5);
        // Helmet, chestplate, leggings, boots, main hand: the order Onyx's slot list used.
        for (int slot : new int[]{4, 3, 2, 1}) {
            ItemStack stack = player.getEquipmentInSlot(slot);
            if (stack != null) {
                items.add(stack);
            }
        }
        ItemStack held = player.getHeldItem();
        if (held != null) {
            items.add(held);
        }
        return items;
    }

    // ------------------------------------------------------------------ //
    // Projection
    // ------------------------------------------------------------------ //

    /**
     * Snapshots the projection for this frame: the world matrices and the origin they expect.
     * Returns {@code false} when the camera transform could not be rebuilt, in which case the
     * plates are skipped rather than drawn against the 2D overlay matrices.
     */
    private static boolean beginCamera(float partialTicks) {
        if (!Render3D.setupCamera(partialTicks)) {
            return false;
        }
        MODELVIEW.clear();
        PROJECTION.clear();
        VIEWPORT.clear();
        GL11.glGetFloat(2982, MODELVIEW);
        GL11.glGetFloat(2983, PROJECTION);
        GL11.glGetInteger(2978, VIEWPORT);
        MODELVIEW.rewind();
        PROJECTION.rewind();
        VIEWPORT.rewind();

        // The world matrix is built for viewer-relative coordinates: the entity and chunk renderers
        // subtract RenderManager.viewerPos themselves, which is also why the matrix carries the
        // -eyeHeight offset the first person camera needs. Anything projected against it therefore
        // has to be handed over as "world position minus viewerPos" and nothing else - the eye
        // height and the third person camera distance are already inside the matrix. Adding them
        // here (as the first port did, with viewerPos + eyeHeight) is what left every plate a block
        // and a half below the head.
        RenderManager manager = Minecraft.getMinecraft().getRenderManager();
        CAMERA[0] = manager.viewerPosX;
        CAMERA[1] = manager.viewerPosY;
        CAMERA[2] = manager.viewerPosZ;

        Render3D.setupOverlay();
        return true;
    }

    /**
     * Projects the player's bounding box and returns the top centre of it in GUI coordinates, or
     * {@code null} when the box is behind the camera or off screen.
     *
     * <p>The plate hangs off the whole projected box rather than off a single point: seen from
     * above, the box's top centre projects to the middle of the head, and hanging the plate there
     * puts it over the face. The topmost projected corner is where the plate belongs, which is what
     * Onyx's {@code Util11.getLeftTopRecordForEntity} handed its name module too.</p>
     */
    private static float[] project(EntityLivingBase target, float partialTicks, ScaledResolution resolution) {
        AxisAlignedBB box = target.getEntityBoundingBox();
        double px = target.lastTickPosX + (target.posX - target.lastTickPosX) * partialTicks;
        double py = target.lastTickPosY + (target.posY - target.lastTickPosY) * partialTicks;
        double pz = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partialTicks;
        double minX = px + (box.minX - target.posX);
        double maxX = px + (box.maxX - target.posX);
        double minY = py + (box.minY - target.posY);
        double maxY = py + (box.maxY - target.posY);
        double minZ = pz + (box.minZ - target.posZ);
        double maxZ = pz + (box.maxZ - target.posZ);

        // One in-front test for the whole box replaces the old look-vector dot product, which
        // rejected players the camera looks down on even though they were perfectly visible.
        if (eyeDepth((minX + maxX) / 2.0D, (minY + maxY) / 2.0D, (minZ + maxZ) / 2.0D) > -0.05D) {
            return null;
        }

        float factor = resolution.getScaleFactor();
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        boolean any = false;
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? minX : maxX;
            double y = (corner & 2) == 0 ? minY : maxY;
            double z = (corner & 4) == 0 ? minZ : maxZ;
            SCREEN.clear();
            if (!GLU.gluProject((float) (x - CAMERA[0]), (float) (y - CAMERA[1]), (float) (z - CAMERA[2]),
                    MODELVIEW, PROJECTION, VIEWPORT, SCREEN)) {
                continue;
            }
            float depth = SCREEN.get(2);
            if (depth < 0.0F || depth > 1.0F) {
                continue;
            }
            any = true;
            float sx = SCREEN.get(0) / factor;
            float sy = (VIEWPORT.get(3) - SCREEN.get(1)) / factor;
            left = Math.min(left, sx);
            right = Math.max(right, sx);
            top = Math.min(top, sy);
            bottom = Math.max(bottom, sy);
        }
        if (!any) {
            return null;
        }
        if (right < -40.0F || left > resolution.getScaledWidth() + 40.0F
                || bottom < -40.0F || top > resolution.getScaledHeight() + 60.0F) {
            return null;
        }
        return new float[]{(left + right) / 2.0F, top};
    }

    /** Camera-space depth of a world point: negative is in front of the camera. */
    private static double eyeDepth(double x, double y, double z) {
        double rx = x - CAMERA[0];
        double ry = y - CAMERA[1];
        double rz = z - CAMERA[2];
        return MODELVIEW.get(2) * rx + MODELVIEW.get(6) * ry + MODELVIEW.get(10) * rz + MODELVIEW.get(14);
    }

    // ------------------------------------------------------------------ //
    // Misc
    // ------------------------------------------------------------------ //

    private float tick() {
        long now = System.nanoTime();
        float delta = lastFrame == 0L ? 16.0F : Math.min((now - lastFrame) / 1000000.0F, 100.0F);
        lastFrame = now;
        return delta;
    }

    public void reset() {
        visible.set(0.0F);
        lastFrame = 0L;
        drawn = 0;
    }

    /** Plates drawn during the last frame. */
    public int drawn() {
        return drawn;
    }

    /** Frames the renderer actually ran in (used by the dev harness). */
    public int frames() {
        return frames;
    }

    private static int mix(int from, int to, float t) {
        t = Material.clamp(t, 0.0F, 1.0F);
        return Material.argb(255,
                Math.round(Material.red(from) + (Material.red(to) - Material.red(from)) * t),
                Math.round(Material.green(from) + (Material.green(to) - Material.green(from)) * t),
                Math.round(Material.blue(from) + (Material.blue(to) - Material.blue(from)) * t));
    }

    private static void restoreHudState() {
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** One coloured run of text on the plate. */
    private static final class Piece {
        private final Typeface.Style style;
        private final String text;
        private final int color;

        private Piece(Typeface.Style style, String text, int color) {
            this.style = style;
            this.text = text;
            this.color = color;
        }

        private float width() {
            return style.width(text);
        }
    }
}
