package xyz.daikirai.module.minigames;

import xyz.daikirai.client.Hypixel;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.SliderSetting;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.IChatComponent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of OpenExpo's {@code Expo.module.impl.configuration.Teams}.
 *
 * <p>One configurable "is this entity on my team?" rule, so the target-based modules stop swinging
 * at their own teammates in Bed Wars (and anywhere else the scoreboard colour marks the sides).
 * Every module that already filters targets - KillAura, AimAssist, AutoBlock, Backtrack, Lag Range,
 * AntiCheat - keeps calling {@code Hypixel#sameTeam}, which now routes through this module while it
 * is enabled, so nothing else had to change.</p>
 *
 * <p>Modes, in the order the original offered them:</p>
 *
 * <ul>
 *   <li>{@code Name color} (default) - the scoreboard team colour, i.e. the tab-list colour. On
 *       Hypixel this also keeps the original extras (team-coloured silverfish/iron golems, the PIT
 *       exclusion); on other servers it is the plain colour-prefix comparison.</li>
 *   <li>{@code Armor color} - Bed Wars gives each team dyed leather armour, so two players wearing a
 *       leather helmet of the same colour are teammates even when the scoreboard hides teams.</li>
 *   <li>{@code Pattern} - a coloured {@code [A]} tag in the display name (the original's default
 *       regex {@code \[[A-Z]\]}); both names have to carry the same tag.</li>
 *   <li>{@code Vanilla} - {@code Entity#isOnSameTeam}.</li>
 *   <li>{@code None} - teaming off: nobody counts as a teammate, every other filter still applies.</li>
 * </ul>
 *
 * <p>The friend/enemy lists the original also owned already exist here as {@code .friend} /
 * {@code .enemy} ({@code Friends}), and the target modules consult them separately, so this port
 * only covers the team rule itself. The original's configurable regex setting is not exposed yet -
 * {@code Pattern} uses its shipped default, since this client's settings are toggle/mode/slider
 * only (no text field).</p>
 */
public class TeamsModule extends Module {

    /** {@code Mode}: scoreboard/tab-list team colour. */
    public static final int MODE_NAME_COLOR = 0;
    /** {@code Mode}: dyed leather armour colour. */
    public static final int MODE_ARMOR_COLOR = 1;
    /** {@code Mode}: {@code [A]}-style tag in the display name. */
    public static final int MODE_PATTERN = 2;
    /** {@code Mode}: {@code Entity#isOnSameTeam}. */
    public static final int MODE_VANILLA = 3;
    /** {@code Mode}: no teaming. */
    public static final int MODE_NONE = 4;

    /** The original's default pattern; {@code [A]} .. {@code [Z]} anywhere in the name. */
    private static final Pattern DEFAULT_PATTERN = Pattern.compile("\\[[A-Z]\\]");

    public static TeamsModule instance;

    private static SliderSetting mode;

    public TeamsModule(int key) {
        super("Teams", Category.minigames, key);
        instance = this;
    }

    @Override
    public void link() {
        mode = (SliderSetting) ModuleManager.setting(this, "Mode");
    }

    /** Selected mode index, defaulting to the scoreboard colour before {@link #link()} ran. */
    public static int mode() {
        return mode == null ? MODE_NAME_COLOR : (int) Math.round(mode.getInput());
    }

    /** Whether {@code entity} is on the player's team and must be left alone. */
    public static boolean isTeammate(EntityLivingBase entity) {
        if (entity == null || mc.thePlayer == null) {
            return false;
        }
        if (entity == mc.thePlayer) {
            return true;
        }
        switch (mode()) {
            case MODE_ARMOR_COLOR:
                return armorColorMatches(entity);
            case MODE_PATTERN:
                return patternMatches(entity);
            case MODE_VANILLA:
                return entity.isOnSameTeam(mc.thePlayer);
            case MODE_NONE:
                return false;
            default:
                // Hypixel's own rule first (it knows about the team-coloured mobs), then the plain
                // colour comparison so non-Hypixel scoreboards work too.
                return Hypixel.hypixelTeam(entity) || nameColorMatches(entity);
        }
    }

    // ------------------------------------------------------------------ //
    // Modes
    // ------------------------------------------------------------------ //

    /** Same scoreboard team colour as the player (the tab-list colour). */
    static boolean nameColorMatches(EntityLivingBase entity) {
        ScorePlayerTeam own = teamOf(mc.thePlayer);
        if (own == null) {
            return false;
        }
        ScorePlayerTeam other = teamOf(entity);
        return other != null && own.getColorPrefix().equals(other.getColorPrefix());
    }

    /** Same dyed leather helmet colour as the player. */
    static boolean armorColorMatches(EntityLivingBase entity) {
        ItemStack own = helmet(mc.thePlayer);
        ItemStack other = helmet(entity);
        if (!isDyedLeatherHelmet(own) || !isDyedLeatherHelmet(other)) {
            return false;
        }
        return ((ItemArmor) own.getItem()).getColor(own) == ((ItemArmor) other.getItem()).getColor(other);
    }

    /** The same {@code [A]}-style tag in both display names. */
    static boolean patternMatches(EntityLivingBase entity) {
        IChatComponent other = entity.getDisplayName();
        IChatComponent own = mc.thePlayer.getDisplayName();
        if (other == null || own == null) {
            return false;
        }
        Matcher theirs = DEFAULT_PATTERN.matcher(other.getFormattedText());
        if (!theirs.find()) {
            return false;
        }
        Matcher ours = DEFAULT_PATTERN.matcher(own.getFormattedText());
        return ours.find() && ours.group().equalsIgnoreCase(theirs.group());
    }

    // ------------------------------------------------------------------ //
    // Helpers
    // ------------------------------------------------------------------ //

    /**
     * Team of an entity: the scoreboard lookup by name first (that is what a client-side scoreboard
     * has), then the tab-list entry, which carries the team the server assigned even for players
     * whose name the sidebar does not list.
     */
    private static ScorePlayerTeam teamOf(EntityLivingBase entity) {
        if (entity == null) {
            return null;
        }
        try {
            ScorePlayerTeam team = (ScorePlayerTeam) entity.getTeam();
            if (team != null) {
                return team;
            }
        } catch (RuntimeException ignored) {
            // No scoreboard (menu, world change): fall through to the tab list.
        }
        if (mc.getNetHandler() != null) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(entity.getUniqueID());
            if (info != null) {
                return info.getPlayerTeam();
            }
        }
        return null;
    }

    private static ItemStack helmet(EntityLivingBase entity) {
        return entity == null ? null : entity.getEquipmentInSlot(4);
    }

    private static boolean isDyedLeatherHelmet(ItemStack stack) {
        if (stack == null || stack.getItem() != Items.leather_helmet) {
            return false;
        }
        return Items.leather_helmet.hasColor(stack);
    }
}
