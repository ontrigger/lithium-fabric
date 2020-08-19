package me.jellysquid.mods.lithium.mixin.fluid;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.jellysquid.mods.lithium.common.world.FireCachingWorld;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(World.class)
public class ServerWorldMixin implements FireCachingWorld {

    @Shadow
    @Final
    protected static Logger LOGGER;

    private final Long2ObjectMap<Cache> burnableBlocksCache = new Long2ObjectOpenHashMap<>();

    @Override
    public boolean isBlockAtOffsetBurnable(BlockPos lavaPos, BlockPos offsetPos) {
        long chunkKey = ChunkPos.toLong(lavaPos.getX() >> 4, lavaPos.getZ() >> 4);
        Cache cache = burnableBlocksCache.getOrDefault(chunkKey, new Cache(512));

        return cache.checkOffsetBurnable(lavaPos, offsetPos);
    }

    @Override
    public void setLavaCannotBurnBlock(BlockPos lavaPos, BlockPos offsetPos) {
        long chunkKey = ChunkPos.toLong(lavaPos.getX() >> 4, lavaPos.getZ() >> 4);
        Cache cache = burnableBlocksCache.getOrDefault(chunkKey, new Cache(512));

        cache.setBlockNotBurnable(lavaPos, offsetPos);

        burnableBlocksCache.put(chunkKey, cache);
    }

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At(value = "RETURN"))
    private void clearCache(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            Cache cache = this.burnableBlocksCache.get(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
            if (cache != null) {
                cache.clear();
            }
        }
    }

    @SuppressWarnings("MixinInnerClass")
    private static class Cache {
        private final int mask;
        private final long[] keys;
        private final long[] values;

        Cache(int capacity) {
            capacity = MathHelper.smallestEncompassingPowerOfTwo(capacity);
            this.mask = capacity - 1;

            // Initialize default values
            this.keys = new long[capacity];
            Arrays.fill(this.keys, Long.MIN_VALUE);
            this.values = new long[capacity];
        }

        // normalizes to x:0-4, y: 0-1, z: 0-4
        private static BlockPos getUnitOffset(BlockPos lavaPos, BlockPos offset) {
            return new BlockPos(
                    (offset.getX() - lavaPos.getX()) + 2,
                    offset.getY() - lavaPos.getY(),
                    (offset.getZ() - lavaPos.getZ()) + 2
            );
        }

        private static int hash(long key) {
            return (int) HashCommon.mix(key);
        }

        private static boolean isBitSet(long bits, int position) {
            return ((bits) & (0x01 << position)) == 1;
        }

        private static int offsetKey(BlockPos unitOffset) {
            return unitOffset.getY() << 4 | unitOffset.getZ() << 2 | unitOffset.getX();
        }

        private static long unsetBitAt(long packedBits, int offsetKey) {
            return packedBits & ~(1 << offsetKey);
        }

        long get(BlockPos lavaPos) {
            long key = lavaPos.asLong();
            int idx = hash(key) & this.mask;

            if (this.keys[idx] == key) {
                // Cache hit, return cached value
                return this.values[idx];
            }

            // Store values in cache
            this.keys[idx] = key;
            this.values[idx] = Long.MAX_VALUE;

            return Long.MAX_VALUE;
        }

        boolean checkOffsetBurnable(BlockPos lavaPos, BlockPos offset) {
            BlockPos unitOffset = getUnitOffset(lavaPos, offset);

            long packedBits = get(lavaPos);
            return isBitSet(packedBits, offsetKey(unitOffset));
        }

        void setBlockNotBurnable(BlockPos lavaPos, BlockPos offset) {
            // TODO: only compute idx once
            long key = lavaPos.asLong();
            int idx = hash(key) & this.mask;

            BlockPos unitOffset = getUnitOffset(lavaPos, offset);

            long packedBits = get(lavaPos);

            this.values[idx] = unsetBitAt(packedBits, offsetKey(unitOffset));
        }

        void clear() {
            Arrays.fill(this.keys, Long.MIN_VALUE);
            Arrays.fill(this.values, Long.MAX_VALUE);
        }
    }
}
