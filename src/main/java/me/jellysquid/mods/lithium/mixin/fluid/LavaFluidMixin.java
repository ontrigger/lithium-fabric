package me.jellysquid.mods.lithium.mixin.fluid;

import me.jellysquid.mods.lithium.common.world.FireCachingWorld;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.LavaFluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Random;

@Mixin(LavaFluid.class)
public class LavaFluidMixin {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * @author ontrigger
     */
    @Overwrite
    public void onRandomTick(World world, BlockPos lavaPos, FluidState state, Random random) {
        if (world.getGameRules().getBoolean(GameRules.DO_FIRE_TICK)) {
            int i = random.nextInt(3);

            int myChunkX = lavaPos.getX() >> 4;
            int myChunkZ = lavaPos.getZ() >> 4;

            BlockPos.Mutable offsetPos = lavaPos.mutableCopy();
            if (i > 0) {
                for (int j = 0; j < i; ++j) {
                    offsetPos.move(random.nextInt(3) - 1, 1, random.nextInt(3) - 1);

                    if (!world.canSetBlock(offsetPos)) {
                        return;
                    }

                    boolean canBurnBlock = ((FireCachingWorld) world).isBlockBurnable(offsetPos);

                    // either the block is burnable or it isn't cached
                    if (canBurnBlock) {

                        int otherChunkX = offsetPos.getX() >> 4;
                        int otherChunkZ = offsetPos.getZ() >> 4;

                        boolean sameChunk = myChunkX == otherChunkX && myChunkZ == otherChunkZ;

                        BlockState blockState = world.getBlockState(offsetPos);

                        if (this.canLightFireLithium(world, offsetPos, blockState, sameChunk)) {
                            world.setBlockState(offsetPos, AbstractFireBlock.getState(world, offsetPos));
                            return;
                        } else if (blockState.getMaterial().blocksMovement()) {
                            return;
                        }
                    }

                }
            } else {
                for (int k = 0; k < 3; ++k) {
                    offsetPos.move(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                    if (!world.canSetBlock(offsetPos)) {
                        return;
                    }

                    boolean canBurnBlock = ((FireCachingWorld) world).isBlockBurnable(offsetPos);
                    if (canBurnBlock) {
                        BlockPos up = offsetPos.up();
                        if (world.isAir(up) && this.hasBurnableBlock(world, offsetPos)) {
                            world.setBlockState(up, AbstractFireBlock.getState(world, offsetPos));
                        } else {
                            ((FireCachingWorld) world).setBlockCannotBurn(offsetPos);
                        }
                    }
                }
            }

        }
    }

    private boolean canLightFireLithium(World world, BlockPos offsetPos, BlockState checkedState, boolean sameChunk) {
        if (checkedState.isAir()) {
            Direction[] allDirections = Direction.values();

            for (Direction direction : allDirections) {
                if (this.hasBurnableBlock(world, offsetPos.offset(direction))) {
                    return true;
                }
            }

            // only cache if the block is in the same chunk as the lava block
            if (sameChunk) {
                // no directions have burnable blocks
                ((FireCachingWorld) world).setBlockCannotBurn(offsetPos);
            }
        }

        return false;
    }

    @Shadow
    private boolean hasBurnableBlock(WorldView world, BlockPos pos) {
        throw new UnsupportedOperationException();
    }
}
