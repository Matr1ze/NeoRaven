package xyz.daikirai.util;

public enum RotationSource {
    SCAFFOLD(true),
    SCAFFOLD2(true),
    LONGJUMP(true),
    WATERBUCKET(false),
    NOSLOW(false),
    AIMASSIST(true),
    FREECAM(false),
    SCRIPT(true),
    BEDAURA(true),
    KILLAURA(true),
    KILLAURA2(true),
    AUTOLADDER(true),
    ANTIFIREBALL(true),
    AUTOBLOCKIN(true),
    QUAKECRAFT(true),
    RODAIMBOT(true),
    WOOLWARS(true),
    INVMANAGER(true),
    AUTOROD(true),
    /** AutoRodNew: predictive rod aiming, movement fix on like the other combat rotations. */
    AUTORODNEW(true),
    /** Amunix Velocity JumpReset: the module writes its own forward/strafe, so no movement fix. */
    VELOCITY2(false),
    DISPLACE(true),
    /** raven-bS BridgeAssist pre-place aim: leans the view down at the face being placed against. */
    BRIDGEASSIST(true),
    JUMP45(true);

    public final boolean movementFix;

    RotationSource(boolean movementFix) {
        this.movementFix = movementFix;
    }
}
