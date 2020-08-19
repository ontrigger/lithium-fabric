package me.jellysquid.mods.lithium.common.world;

import net.minecraft.util.math.BlockPos;

public interface FireCachingWorld {
    boolean isBlockAtOffsetBurnable(BlockPos lavaPos, BlockPos offsetPos);

    void setLavaCannotBurnBlock(BlockPos lavaPos, BlockPos offsetPos);
}
