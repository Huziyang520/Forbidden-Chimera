package com.huziyang520.forbiddenchimera.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The burning left behind by a chimera blast.
 *
 * <p>{@link ChimeraExplosion} runs a plain block-interaction explosion, which by itself does not set
 * anything on fire. The nuclear chimera is supposed to be a small tactical nuke, so its blast also has to
 * leave the area burning - that is this class.
 *
 * <p>Kept apart from {@code ChimeraExplosion} on purpose: the other blasts (the skull projectiles, the
 * creeper phantom's self destruct) are ordinary explosions and must stay fire-free, so only the caller
 * that wants fire asks for it.
 */
public final class ChimeraFire {

    private ChimeraFire() {
    }

    /**
     * Sets the area alight.
     *
     * @param radius      blast radius, the same value handed to the explosion itself
     * @param fireTicks   how long caught entities burn for; {@code <= 0} leaves entities alone
     * @param groundChance per-block chance of lighting the ground; {@code <= 0} leaves the ground alone
     */
    public static void burnAfterExplosion(ServerLevel level, double x, double y, double z, double radius,
            int fireTicks, double groundChance) {
        if (fireTicks > 0) {
            igniteEntities(level, x, y, z, radius, fireTicks);
        }
        if (groundChance > 0.0D) {
            lightGround(level, x, y, z, radius, groundChance);
        }
    }

    /** Everything living inside the blast that can burn, burns - the blast's own mob included. */
    private static void igniteEntities(ServerLevel level, double x, double y, double z, double radius,
            int fireTicks) {
        double reach = Math.max(radius, 0.0D);
        AABB area = new AABB(x - reach, y - reach, z - reach, x + reach, y + reach, z + reach);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> !entity.fireImmune())) {
            living.igniteForTicks(fireTicks);
        }
    }

    /**
     * Lights patches of fire on the ground the blast reached.
     *
     * <p>Only exposed surface blocks are considered - an air block with something solid underneath - so a
     * blast in mid-air lights nothing instead of hanging fire in the sky. Fire is placed through
     * {@link BaseFireBlock#getState}, the same call vanilla explosions use, so soul soil gets soul fire and
     * a block that cannot hold fire is skipped rather than force-placed.
     */
    private static void lightGround(ServerLevel level, double x, double y, double z, double radius,
            double chance) {
        RandomSource random = level.getRandom();
        int reach = (int) Math.ceil(Math.max(radius, 0.0D));
        BlockPos origin = BlockPos.containing(x, y, z);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int offsetX = -reach; offsetX <= reach; offsetX++) {
            for (int offsetZ = -reach; offsetZ <= reach; offsetZ++) {
                for (int offsetY = -reach; offsetY <= reach; offsetY++) {
                    if (random.nextDouble() >= chance) {
                        continue;
                    }
                    cursor.set(origin.getX() + offsetX, origin.getY() + offsetY, origin.getZ() + offsetZ);
                    if (!level.getBlockState(cursor).isAir()) {
                        continue;
                    }
                    if (!level.getBlockState(cursor.below()).isSolidRender()) {
                        continue;
                    }
                    BlockState fire = BaseFireBlock.getState(level, cursor);
                    if (fire.canSurvive(level, cursor)) {
                        level.setBlockAndUpdate(cursor, fire);
                    }
                }
            }
        }
    }
}