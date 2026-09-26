package com.huziyang520.forbiddenchimera.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The single place where "low power, destroys terrain, 100% of blocks drop" is implemented.
 *
 * <p>Probed directly against the Minecraft 26.3 decompiled sources before writing this:
 * <ul>
 *   <li>{@code Explosion} is now an <b>interface</b> ({@code net.minecraft.world.level.Explosion}),
 *       implemented by {@code ServerExplosion}. The old {@code BlockInteraction} constants live inside
 *       that interface.</li>
 *   <li>{@code ServerExplosion#interactWithBlocks} calls
 *       {@code BlockState#onExplosionHit(...)}, whose default implementation drops loot whenever
 *       {@code Block#dropFromExplosion} returns true.</li>
 *   <li>{@code Block#dropFromExplosion} <b>defaults to {@code true}</b>, and the old
 *       {@code survives_explosion} loot condition no longer exists in 26.3.</li>
 *   <li>{@code ServerLevel} maps {@code ExplosionInteraction.BLOCK} to
 *       {@code Explosion.BlockInteraction.DESTROY} (only downgraded to {@code DESTROY_WITH_DECAY},
 *       which merely attaches an otherwise unused loot parameter, when the
 *       {@code block_explosion_drop_decay} game rule is on).</li>
 * </ul>
 *
 * <p>Conclusion: a plain {@link Level.ExplosionInteraction#BLOCK} explosion already satisfies all three
 * requirements. Writing our own block iteration would duplicate vanilla work and risk double drops, so
 * this class deliberately stays a thin, documented wrapper.
 *
 * <p>Note that {@code ExplosionInteraction.BLOCK} is used instead of {@code MOB} on purpose:
 * {@code MOB} explosions are disabled when the {@code mobGriefing} game rule is off, but a thrown
 * explosive skull should always scar the terrain.
 */
public final class ChimeraExplosion {

    private ChimeraExplosion() {
    }

    /**
     * Detonates a chimera explosion.
     *
     * @param level  server level to detonate in.
     * @param source the entity credited with the explosion (may be null).
     * @param x      center x.
     * @param y      center y.
     * @param z      center z.
     * @param radius blast radius; keep this below the vanilla creeper's 3.0F.
     */
    public static void explode(ServerLevel level, @Nullable Entity source, double x, double y, double z, float radius) {
        explode(level, source, null, x, y, z, radius, 1.0F);
    }

    /**
     * Detonates an explosion that hurts one specific entity far less than everybody else.
     *
     * <p>This is how the Creeper Phantom survives its own missiles: the skull is a child of the mob, so
     * without this the blast would hit the shooter with full force.
     *
     * @param protectedEntity     entity that takes reduced damage, usually the shooter.
     * @param protectedEntityFactor damage multiplier for that entity, 0.1 means a 90% reduction.
     */
    public static void explode(ServerLevel level, @Nullable Entity source, @Nullable Entity protectedEntity,
                               double x, double y, double z, float radius, float protectedEntityFactor) {
        if (radius <= 0.0F) {
            return;
        }

        ExplosionDamageCalculator calculator = protectedEntity == null
                ? null
                : new ReducedEntityDamageCalculator(protectedEntity, protectedEntityFactor);

        // A null damage source makes vanilla build the usual explosion damage source from `source`.
        level.explode(source, null, calculator, x, y, z, radius, false, Level.ExplosionInteraction.BLOCK);
    }

    /** Scales the blast damage taken by exactly one entity, leaving the vanilla maths untouched. */
    private static final class ReducedEntityDamageCalculator extends ExplosionDamageCalculator {

        private final Entity protectedEntity;
        private final float factor;

        private ReducedEntityDamageCalculator(Entity protectedEntity, float factor) {
            this.protectedEntity = protectedEntity;
            this.factor = factor;
        }

        @Override
        public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
            float damage = super.getEntityDamageAmount(explosion, entity, exposure);
            return entity == this.protectedEntity ? damage * this.factor : damage;
        }
    }
}
