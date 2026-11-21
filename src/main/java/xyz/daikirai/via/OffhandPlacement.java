package xyz.daikirai.via;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public final class OffhandPlacement {
    private static final Map<WorldClient, Map<BlockPos, OffhandPlacement>> PENDING = new WeakHashMap<>();
    private final BlockPos position;
    private final IBlockState previous;
    private final IBlockState predicted;

    private OffhandPlacement(BlockPos position, IBlockState previous, IBlockState predicted) {
        this.position = position;
        this.previous = previous;
        this.predicted = predicted;
    }

    static OffhandPlacement predict(WorldClient world, EntityPlayerSP player, ItemStack stack, BlockPos clicked,
                                    EnumFacing face, float x, float y, float z) {
        if (!(stack.getItem() instanceof ItemBlock)) {
            return null;
        }
        BlockPos position = world.getBlockState(clicked).getBlock().isReplaceable(world, clicked)
                ? clicked : clicked.offset(face);
        IBlockState previous = world.getBlockState(position);
        // The vanilla use path updates collision and only consumes this copy.
        if (!stack.copy().onItemUse(player, world, clicked, face, x, y, z)) {
            return null;
        }
        IBlockState predicted = world.getBlockState(position);
        if (previous.equals(predicted)) {
            return null;
        }
        OffhandPlacement placement = new OffhandPlacement(position, previous, predicted);
        PENDING.computeIfAbsent(world, key -> new HashMap<>()).put(position, placement);
        return placement;
    }

    public static void authoritativeUpdate(WorldClient world, BlockPos position) {
        Map<BlockPos, OffhandPlacement> pending = PENDING.get(world);
        if (pending != null) {
            pending.remove(position);
        }
    }

    void acknowledged(WorldClient world) {
        Map<BlockPos, OffhandPlacement> pending = PENDING.get(world);
        if (pending != null && pending.remove(position, this)
                && world == Minecraft.getMinecraft().theWorld && world.getBlockState(position).equals(predicted)) {
            world.setBlockState(position, previous, 3);
        }
    }
}
