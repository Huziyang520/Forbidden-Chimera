package com.huziyang520.forbiddenchimera.entity;

import com.huziyang520.forbiddenchimera.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 闪电苦力怕幻翼 - the charged sibling of the Creeper Phantom.
 *
 * <p>It reuses everything: same GeckoLib model, same animations, same AI (inherited verbatim from
 * {@link CreeperPhantomEntity}). Only three things differ:
 *
 * <ol>
 *   <li>the creeper part of the model carries the vanilla charged creeper energy swirl (client side,
 *       {@code LightningOverlayGeoLayer}) - wings and tail stay untouched;</li>
 *   <li>it fires a {@link LightningCreeperSkullProjectile}, which hits harder;</li>
 *   <li>its own lightning skulls barely scratch it (98% damage reduction).</li>
 * </ol>
 */
public class LightningCreeperPhantomEntity extends CreeperPhantomEntity {

    public LightningCreeperPhantomEntity(EntityType<? extends LightningCreeperPhantomEntity> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    @Override
    protected CreeperSkullProjectile newSkull(net.minecraft.server.level.ServerLevel level, Vec3 direction) {
        return new LightningCreeperSkullProjectile(ModEntities.lightningCreeperSkull(), this, direction, level);
    }

    /** The knight variant is defined for the plain Creeper Phantom (mob3) only. */
    @Override
    protected boolean supportsKnightVariant() {
        return false;
    }
}
