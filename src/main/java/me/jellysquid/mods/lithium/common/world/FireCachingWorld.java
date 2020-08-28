package me.jellysquid.mods.lithium.common.world;

import net.minecraft.util.math.BlockPos;

public interface FireCachingWorld {
    boolean isBlockBurnable(BlockPos pos);

    void setBlockCannotBurn(BlockPos pos);
}
