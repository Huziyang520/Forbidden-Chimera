package com.huziyang520.forbiddenchimera.entity;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 闪电苦力怕头颅 - the charged missile of the Lightning Creeper Phantom.
 *
 * <p>Same flight and homing as {@link CreeperSkullProjectile}; it differs in payload (higher blast
 * radius, config {@code lightningSkullExplosionRadius}) and in how much of that blast its own shooter
 * takes (config {@code lightningSkullOwnerDamageFactor}, default 0.02 = 98% reduction).
 *
 * <p>The lightning look is three layered effects, all on top of the vanilla recipe:
 * <ul>
 *   <li>the vanilla charged creeper armour texture is not just on the mob - this skull <b>is</b> the
 *       charged thing, so it is drawn as a creeper head (vanilla item renderer) wrapped in electric
 *       sparks;</li>
 *   <li>a dense {@code ELECTRIC_SPARK} trail while flying (the base skull trails smoke instead);</li>
 *   <li>on detonation: a flash, a spark burst and thunder, so the impact reads as lightning rather
 *       than as a plain creeper blast.</li>
 * </ul>
 */
public class LightningCreeperSkullProjectile extends CreeperSkullProjectile {

    /** Sparks spawned per tick along the flight path. */
    private static final int TRAIL_SPARKS = 3;
    /** Sparks spawned by the detonation. */
    private static final int DETONATION_SPARKS = 80;

    public LightningCreeperSkullProjectile(EntityType<? extends LightningCreeperSkullProjectile> type, Level level) {
        super(type, level);
    }

    public LightningCreeperSkullProjectile(EntityType<? extends LightningCreeperSkullProjectile> type,
                                           LivingEntity owner, Vec3 direction, Level level) {
        super(type, owner, direction, level);
    }

    @Override
    protected float explosionRadius() {
        return (float) ForbiddenChimeraConfig.get().lightningSkullExplosionRadius;
    }

    @Override
    protected float ownerDamageFactor() {
        return (float) ForbiddenChimeraConfig.get().lightningSkullOwnerDamageFactor;
    }

    @Override
    protected @Nullable ParticleOptions getTrailParticle() {
        return ParticleTypes.ELECTRIC_SPARK;
    }

    @Override
    protected void spawnFlightEffects(ServerLevel level) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                this.getX(), this.getY(), this.getZ(), TRAIL_SPARKS, 0.15D, 0.15D, 0.15D, 0.02D);
    }

    @Override
    protected void spawnDetonationEffects(ServerLevel level, double x, double y, double z) {
        // ParticleTypes.FLASH is a ParticleType<ColorParticleOption>, not a ParticleOptions itself, so it
        // has to be wrapped before it can be sent.
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF), x, y, z, 1,
                0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, DETONATION_SPARKS, 1.4D, 1.0D, 1.4D, 0.45D);
        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 6, 1.0D, 0.6D, 1.0D, 0.1D);
        level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0F, 1.3F);
    }
}
