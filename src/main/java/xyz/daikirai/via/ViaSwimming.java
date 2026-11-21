package xyz.daikirai.via;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public final class ViaSwimming {
    private double waterDepth;
    private int lastFluidTick = Integer.MIN_VALUE;
    private boolean sprintingBeforeInput;
    private boolean swimming;

    public static ViaSwimming forPlayer(Entity entity) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player != null && entity == player && ViaCompatibility.usesMovement1206(player.sendQueue.getNetworkManager())) {
            return ((ViaSwimmingPlayer) player).onyx$getSwimming();
        }
        return null;
    }

    public double waterDepth() {
        return waterDepth;
    }

    public boolean isSwimming() {
        return swimming;
    }

    public boolean isCrawlingOnLand(EntityPlayerSP player) {
        return player.height == 0.6F && !player.isInWater() && !player.capabilities.isFlying
                && !player.isRiding() && !player.isPlayerSleeping();
    }

    public boolean updateWater(EntityPlayerSP player) {
        if (lastFluidTick == player.ticksExisted) {
            return waterDepth > 0.0;
        }
        lastFluidTick = player.ticksExisted;
        waterDepth = 0.0;
        AxisAlignedBB box = player.getEntityBoundingBox().contract(0.001, 0.001, 0.001);
        BlockPos min = new BlockPos(box.minX, box.minY, box.minZ);
        BlockPos max = new BlockPos(MathHelper.ceiling_double_int(box.maxX), MathHelper.ceiling_double_int(box.maxY),
                MathHelper.ceiling_double_int(box.maxZ));
        World world = player.worldObj;
        if (!world.isAreaLoaded(min, max)) {
            return false;
        }
        Vec3 current = new Vec3(0, 0, 0);
        int samples = 0;
        for (BlockPos pos : BlockPos.getAllInBox(min, max.add(-1, -1, -1))) {
            float height = waterHeight(world, pos);
            double depth = pos.getY() + height - box.minY;
            if (height <= 0.0F || depth <= 0.0) {
                continue;
            }
            waterDepth = Math.max(waterDepth, depth);
            if (player.isPushedByWater()) {
                Vec3 flow = waterFlow(world, pos);
                double scale = waterDepth < 0.4 ? waterDepth : 1.0;
                current = current.addVector(flow.xCoord * scale, flow.yCoord * scale, flow.zCoord * scale);
                samples++;
            }
        }
        if (samples > 0 && current.lengthVector() > 0.0) {
            double push = 0.014 / samples;
            Vec3 velocity = new Vec3(current.xCoord * push, current.yCoord * push, current.zCoord * push);
            if (Math.abs(player.motionX) < 0.003 && Math.abs(player.motionZ) < 0.003 && velocity.lengthVector() < 0.0045) {
                Vec3 direction = velocity.normalize();
                velocity = new Vec3(direction.xCoord * 0.0045, direction.yCoord * 0.0045, direction.zCoord * 0.0045);
            }
            player.motionX += velocity.xCoord;
            player.motionY += velocity.yCoord;
            player.motionZ += velocity.zCoord;
        }
        return waterDepth > 0.0;
    }

    public void beforeInput(EntityPlayerSP player) {
        sprintingBeforeInput = player.isSprinting();
    }

    public void applyInput(EntityPlayerSP player) {
        boolean touching = player.isInWater() && !player.capabilities.isFlying && !player.isRiding();
        boolean submerged = touching && eyesInWater(player);
        if (touching) {
            if (swimming && sprintingBeforeInput && (player.onGround || player.movementInput.sneak
                    || player.movementInput.moveForward > 1.0E-5F
                    && (player.getFoodStats().getFoodLevel() > 6 || player.capabilities.allowFlying))) {
                player.setSprinting(true);
            } else if (!swimming && !submerged) {
                player.setSprinting(false);
            }
            if (player.movementInput.sneak) {
                player.motionY -= 0.04F;
            }
        }
        BlockPos feet = new BlockPos(player.posX, player.posY, player.posZ);
        swimming = touching && player.isSprinting() && (swimming || submerged && waterHeight(player.worldObj, feet) > 0.0F);
        if (!player.isPlayerSleeping() && !player.isRiding() && !isCrawlingOnLand(player)) {
            resize(player, swimming ? 0.6F : uprightHeight(player));
        }
    }

    public void afterTravel(EntityPlayerSP player) {
        // The modern client resolves the dry pose after travel, using the smaller box for this tick.
        if (isCrawlingOnLand(player) && !resize(player, uprightHeight(player))) {
            resize(player, 1.5F);
        }
    }

    private static float uprightHeight(EntityPlayerSP player) {
        return player.isSneaking() && !player.capabilities.isFlying ? 1.5F : 1.8F;
    }

    private static boolean resize(EntityPlayerSP player, float height) {
        if (height == player.height) {
            return true;
        }
        AxisAlignedBB box = player.getEntityBoundingBox();
        AxisAlignedBB requested = new AxisAlignedBB(box.minX, box.minY, box.minZ, box.maxX, box.minY + (double) height, box.maxZ);
        if (height > player.height && !player.worldObj.getCollidingBoundingBoxes(player, requested).isEmpty()) {
            return false;
        }
        player.height = height;
        player.setEntityBoundingBox(requested);
        return true;
    }

    public boolean eyesInWater(EntityPlayerSP player) {
        double eyeY = player.posY + player.getEyeHeight() - 0.1111111119389534;
        BlockPos atEye = new BlockPos(player.posX, eyeY, player.posZ);
        return atEye.getY() + waterHeight(player.worldObj, atEye) > eyeY;
    }

    public void travel(EntityPlayerSP player, float strafe, float forward) {
        if (swimming) {
            double lookY = player.getLookVec().yCoord;
            BlockPos ahead = new BlockPos(player.posX, player.posY + 0.9, player.posZ);
            if (lookY <= 0.0 || player.movementInput.jump || waterHeight(player.worldObj, ahead) > 0.0F) {
                player.motionY += (lookY - player.motionY) * (lookY < -0.2 ? 0.085 : 0.06);
            }
        }
        boolean falling = player.motionY <= 0.0;
        double startY = player.posY;
        float drag = player.isSprinting() ? 0.9F : 0.8F;
        float speed = 0.02F;
        float depthStrider = Math.min(3, EnchantmentHelper.getDepthStriderModifier(player));
        if (!player.onGround) {
            depthStrider *= 0.5F;
        }
        if (depthStrider > 0.0F) {
            drag += (0.54600006F - drag) * depthStrider / 3.0F;
            speed += (player.getAIMoveSpeed() - speed) * depthStrider / 3.0F;
        }
        player.moveFlying(strafe, forward, speed);
        player.moveEntity(player.motionX, player.motionY, player.motionZ);
        if (player.isCollidedHorizontally && player.isOnLadder()) {
            player.motionY = 0.2;
        }
        player.motionX *= drag;
        player.motionY *= 0.8F;
        player.motionZ *= drag;
        if (!player.isSprinting()) {
            double gravity = player.motionY - 0.08 / 16.0;
            player.motionY = falling && Math.abs(player.motionY - 0.005) >= 0.003 && Math.abs(gravity) < 0.003
                    ? -0.003 : gravity;
        }
        if (player.isCollidedHorizontally && player.isOffsetPositionInLiquid(player.motionX,
                player.motionY + (double) 0.6F - player.posY + startY, player.motionZ)) {
            player.motionY = 0.3F;
        }
        player.prevLimbSwingAmount = player.limbSwingAmount;
        double dx = player.posX - player.prevPosX;
        double dz = player.posZ - player.prevPosZ;
        float swing = Math.min(1.0F, MathHelper.sqrt_double(dx * dx + dz * dz) * 4.0F);
        player.limbSwingAmount += (swing - player.limbSwingAmount) * 0.4F;
        player.limbSwing += player.limbSwingAmount;
    }

    private static float waterHeight(World world, BlockPos pos) {
        float height = ownHeight(world.getBlockState(pos));
        return height > 0.0F && ownHeight(world.getBlockState(pos.up())) > 0.0F ? 1.0F : height;
    }

    private static float ownHeight(IBlockState state) {
        Block block = state.getBlock();
        if (block.getMaterial() != Material.water) {
            return 0.0F;
        }
        int level = block instanceof BlockLiquid ? state.getValue(BlockLiquid.LEVEL) : 0;
        return (8 - (level >= 8 ? 0 : level)) / 9.0F;
    }

    private static Vec3 waterFlow(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        float own = ownHeight(state);
        double x = 0.0;
        double z = 0.0;
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.offset(side);
            IBlockState adjacent = world.getBlockState(neighbor);
            Material material = adjacent.getBlock().getMaterial();
            if (material.isLiquid() && material != Material.water) {
                continue;
            }
            float neighborHeight = ownHeight(adjacent);
            if (neighborHeight > 0.0F) {
                float delta = own - neighborHeight;
                x += delta * side.getFrontOffsetX();
                z += delta * side.getFrontOffsetZ();
            } else if (!material.blocksMovement()) {
                float below = ownHeight(world.getBlockState(neighbor.down()));
                if (below > 0.0F) {
                    float delta = own - below + 8.0F / 9.0F;
                    x += delta * side.getFrontOffsetX();
                    z += delta * side.getFrontOffsetZ();
                }
            }
        }
        Vec3 flow = new Vec3(x, 0, z).normalize();
        if (state.getBlock() instanceof BlockLiquid && state.getValue(BlockLiquid.LEVEL) >= 8) {
            for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) {
                BlockPos neighbor = pos.offset(side);
                if (world.getBlockState(neighbor).getBlock().isSideSolid(world, neighbor, side)
                        || world.getBlockState(neighbor.up()).getBlock().isSideSolid(world, neighbor.up(), side)) {
                    return flow.addVector(0, -6, 0).normalize();
                }
            }
        }
        return flow;
    }
}
