package com.huziyang520.forbiddenchimera.entity;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.BlockUtil;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * 末影人幻翼 - the enderman chimera.
 *
 * <p>Same wings and tail as the Creeper Phantom (the geometry keeps every bone name, so the shared
 * animation set plays unchanged) but the creeper torso and head are replaced with the enderman's, and
 * the vanilla enderman skin is composed onto the texture.
 *
 * <p>Behaviour is the plain phantom routine - dive in, no missiles ({@link #canFireSkulls()} is false) -
 * plus the one thing that makes it read as an enderman: it <b>teleports out of a projectile's way</b>.
 * That logic is copied from vanilla {@code Enderman}: a projectile damage source causes up to 64
 * teleport attempts and the hit is voided entirely, so an arrow never lands.
 *
 * <p>It also makes a very rare idle teleport, which is the flying equivalent of an enderman's random
 * relocation.
 */
public class EndermanPhantomEntity extends CreeperPhantomEntity {

    /** Vanilla {@code Enderman#repeatedlyTryToTeleport} attempt count. */
    private static final int TELEPORT_ATTEMPTS = 64;
    /** Fallback attempts when the vanilla wide search finds nowhere valid (a flyer is rarely near ground). */
    private static final int NEARBY_TELEPORT_ATTEMPTS = 16;

    public EndermanPhantomEntity(EntityType<? extends EndermanPhantomEntity> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    /** No creeper part, so nothing to fire: the dive is a plain slam. */
    @Override
    public boolean canFireSkulls() {
        return false;
    }

    /** The spear variant is defined for the creeper line only (mob3 / mob4). */
    @Override
    protected boolean supportsSpearVariant() {
        return false;
    }

    /** No rider either: the enderman variant is defined as the plain-diving projectile dodger. */
    @Override
    protected boolean supportsKnightVariant() {
        return false;
    }

    /**
     * Projectile dodge. Verbatim from vanilla {@code Enderman#hurtServer}: any damage source tagged
     * {@code IS_PROJECTILE} triggers a teleport and does <b>zero</b> damage - the arrow simply misses.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableTo(level, source)) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE) && !this.isPassenger()) {
            this.repeatedlyTryToTeleport();
            return false;
        }
        return super.hurtServer(level, source, damage);
    }

    /** The enderman's rare idle relocation, rolled on the server AI step. */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        double chance = ForbiddenChimeraConfig.get().endermanIdleTeleportChance;
        if (chance > 0.0D && this.random.nextDouble() < chance) {
            this.repeatedlyTryToTeleport();
        }
    }

    /** Vanilla {@code Enderman#repeatedlyTryToTeleport}, plus a short-range fallback for a flying mob. */
    private void repeatedlyTryToTeleport() {
        if (this.isPassenger()) {
            return;
        }
        for (int i = 0; i < TELEPORT_ATTEMPTS; i++) {
            if (this.teleport()) {
                return;
            }
        }
        // Vanilla only ever searches the ground level of a 64 block cube; a phantom is usually high in
        // the air where that finds nothing. Hop a short distance in place instead, so the dodge still
        // happens - same effect, smaller step.
        for (int i = 0; i < NEARBY_TELEPORT_ATTEMPTS; i++) {
            double x = this.getX() + (this.random.nextDouble() - 0.5D) * 16.0D;
            double y = this.getY() + (this.random.nextInt(9) - 4);
            double z = this.getZ() + (this.random.nextDouble() - 0.5D) * 16.0D;
            if (this.teleport(x, y, z)) {
                return;
            }
        }
    }

    /** Vanilla {@code Enderman#teleport()}: random 64 wide, 32 up and down. */
    private boolean teleport() {
        if (this.level().isClientSide() || !this.isAlive()) {
            return false;
        }
        double x = this.getX() + (this.random.nextDouble() - 0.5D) * 64.0D;
        double y = this.getY() + (this.random.nextInt(64) - 32);
        double z = this.getZ() + (this.random.nextDouble() - 0.5D) * 64.0D;
        return this.teleport(x, y, z);
    }

    /** Vanilla {@code Enderman#teleport(double,double,double)}, sound and level event included. */
    private boolean teleport(double x, double y, double z) {
        if (this.isPassenger()) {
            return false;
        }
        Vec3 oldPos = this.position();
        boolean result = this.randomTeleport(x, y, z, true, BlockTags.ENDERMAN_DOES_NOT_TELEPORT_TO);
        if (result) {
            Level level = this.level();
            level.gameEvent(GameEvent.TELEPORT, oldPos, GameEvent.Context.of(this));
            if (!this.isSilent()) {
                BlockPos oldBlockPos = BlockPos.containing(oldPos);
                int packedDiff = BlockUtil.clampedPackDifferenceInPosition(oldBlockPos, this.blockPosition(),
                        127, 127, 127);
                level.levelEvent(2018, oldBlockPos, packedDiff);
                level.playSound(null, oldPos.x, oldPos.y, oldPos.z, SoundEvents.ENDERMAN_TELEPORT,
                        this.getSoundSource(), 1.0F, 1.0F);
                this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
            }
        }
        return result;
    }

    /** Vanilla {@code Enderman#canRandomlyTeleportTo}: never into water. */
    @Override
    public boolean canRandomlyTeleportTo(double x, double y, double z) {
        return !this.level().getBlockState(BlockPos.containing(x, y, z).below()).getFluidState()
                .is(FluidTags.WATER);
    }

    /** Reuses the enderman's own sound set so the chimera reads as enderman, not phantom. */
    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }
}
