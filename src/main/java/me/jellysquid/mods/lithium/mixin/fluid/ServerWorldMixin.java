package me.jellysquid.mods.lithium.mixin.fluid;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import me.jellysquid.mods.lithium.common.world.FireCachingWorld;
import net.minecraft.block.Block;
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
import java.util.BitSet;
import java.util.Map;

@Mixin(World.class)
public class ServerWorldMixin implements FireCachingWorld {

    @Shadow
    @Final
    protected static Logger LOGGER;

    private final Map<Long, Cache2> burnableBlocksCache = new Long2ObjectOpenHashMap<>();

    @Override
    public boolean isBlockBurnable(BlockPos pos) {
        long chunkKey = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        Cache2 cache = burnableBlocksCache.getOrDefault(chunkKey, new Cache2(512));

        return cache.isPosBurnable(pos);
    }

    @Override
    public void setBlockCannotBurn(BlockPos pos) {
        long chunkKey = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        Cache2 cache = burnableBlocksCache.getOrDefault(chunkKey, new Cache2(512));

        cache.setBlockCannotBurn(pos);

        burnableBlocksCache.put(chunkKey, cache);
    }

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;II)Z", at = @At(value = "RETURN"))
    private void clearCache(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            Cache2 cache = this.burnableBlocksCache.get(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
            if (cache != null) {
                cache.clearSection(pos.getY() >> 4);
            }
        }
    }

    @SuppressWarnings("MixinInnerClass")
    private static class Cache2 {
        private final short[] keys;
        private final BitSet values;

        private final int sectionSize;
        private final int mask;


        Cache2(int sectionSize) {
            this.sectionSize = MathHelper.smallestEncompassingPowerOfTwo(sectionSize);
            this.mask = sectionSize - 1;

            // Initialize default values
            this.keys = new short[16 * sectionSize];
            Arrays.fill(this.keys, Short.MAX_VALUE);

            this.values = new BitSet(16 * sectionSize);
            this.values.set(0, 16 * sectionSize);
        }

        // if true then can (probably) burn; false - guaranteed can't burn
        public boolean isPosBurnable(BlockPos pos) {
            short key = getKey(pos);
            int idx = HashCommon.mix(key) & this.mask;

            int chunkSectionOffset = (pos.getY() >> 4) * sectionSize;

            int offset = chunkSectionOffset + idx;
            if (this.keys[offset] == key) {
                // cache hit
                return this.values.get(offset);
            }

            this.keys[offset] = key;
            this.values.set(offset, true);

            return true;
        }

        public void setBlockCannotBurn(BlockPos pos) {
            short key = getKey(pos);
            int idx = HashCommon.mix(key) & this.mask;

            int chunkSectionOffset = (pos.getY() >> 4) * sectionSize;

            int offset = chunkSectionOffset + idx;

            this.keys[offset] = key;

            this.values.set(offset, false);
        }

        public void clearSection(int sectionIdx) {
            int sectionOffset = sectionIdx * sectionSize;

            Arrays.fill(this.keys, sectionOffset, sectionOffset + sectionSize - 1, Short.MAX_VALUE);
            this.values.set(sectionOffset, sectionOffset + sectionSize - 1);
        }

        // first convert the x,y,z coords to a chunksection pos [0,15]
        // 4 bits for x,y,z each
        // resulting max value is 2^12, so we convert it to short to save up on mem
        private static short getKey(BlockPos pos) {
            int posX = pos.getX();
            int posY = pos.getY();
            int posZ = pos.getZ();

            int sectionX = posX - (posX >> 4) * 16;
            int sectionY = posY - (posY >> 4) * 16;
            int sectionZ = posZ - (posZ >> 4) * 16;

            return (short) (sectionY << 8 | sectionX << 4 | sectionZ);
        }
    }
}
