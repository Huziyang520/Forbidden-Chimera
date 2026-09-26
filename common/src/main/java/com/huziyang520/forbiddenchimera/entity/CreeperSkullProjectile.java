package com.huziyang520.forbiddenchimera.entity;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import com.huziyang520.forbiddenchimera.world.ChimeraExplosion;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Explosive creeper skull fired by the Creeper Phantom.
 *
 * <p>Homing is intentionally weak: every tick the current velocity is blended a little towards the
 * shooter's target, so the skull corrects but can still be dodged.
 */
public class CreeperSkullProjectile extends AbstractHurtingProjectile implements ItemSupplier {

    public CreeperSkullProjectile(EntityType<? extends CreeperSkullProjectile> type, Level level) {
        super(type, level);
    }

    public CreeperSkullProjectile(EntityType<? extends CreeperSkullProjectile> type, LivingEntity owner,
                                  Vec3 direction, Level level) {
        super(type, owner, direction, level);
        this.accelerationPower = 0.05D;
        // The parent constructor copies the SHOOTER's rotation onto the missile, which leaves the head
        // pointing wherever the phantom happened to be facing. Face the flight direction instead, so the
        // head is aimed at the player from the very first frame - and start the previous-rotation fields
        // at the same value, otherwise the client interpolates the head round on spawn.
        this.faceDirection(direction);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    /**
     * Points the missile along {@code direction}, using the vanilla convention for projectile rotation
     * ({@code Projectile#shoot}): yaw measured from +X minus 90, pitch from the vertical component.
     */
    private void faceDirection(Vec3 direction) {
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontal < 1.0E-4D && Math.abs(direction.y) < 1.0E-4D) {
            return;
        }
        this.setYRot((float) (Math.atan2(direction.z, direction.x) * (180.0D / Math.PI)) - 90.0F);
        this.setXRot((float) (-(Math.atan2(direction.y, horizontal) * (180.0D / Math.PI))));
    }

    @Override
    protected ClipContext.Block getClipType() {
        return ClipContext.Block.COLLIDER;
    }

    /** A creeper skull should not catch fire mid flight. */
    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    protected @Nullable ParticleOptions getTrailParticle() {
        return ParticleTypes.SMOKE;
    }

    /**
     * Blast radius of this skull, read live from config so tuning applies to the next detonation.
     *
     * <p>Overridden by the lightning variant, which hits harder.
     */
    protected float explosionRadius() {
        return (float) ForbiddenChimeraConfig.get().skullExplosionRadius;
    }

    /**
     * Fraction of the blast the shooter takes. Kept tiny for the base skull so a diving Creeper
     * Phantom cannot kill itself, and reduced to almost nothing for the lightning variant.
     */
    protected float ownerDamageFactor() {
        return (float) ForbiddenChimeraConfig.get().skullOwnerDamageFactor;
    }

    /** Rendered as a creeper head through {@code ThrownItemRenderer}. */
    @Override
    public ItemStack getItem() {
        return new ItemStack(Items.CREEPER_HEAD);
    }

    @Override
    public void tick() {
        super.tick();

        // Vanilla turns projectiles towards their movement by only 20% per tick, so a head launched
        // sideways visibly lags before it lines up; and homing changes the velocity anyway. Re-align
        // exactly, every tick, on both sides.
        this.faceDirection(this.getDeltaMovement());

        if (this.level().isClientSide()) {
            return;
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            this.spawnFlightEffects(serverLevel);
        }

        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();
        if (this.tickCount > config.skullMaxLifetimeTicks) {
            this.detonate();
            return;
        }

        this.applyWeakHoming(config.skullHomingStrength);
    }

    /**
     * Extra per-tick effects while flying. Base skull does nothing beyond its trail particle;
     * the lightning variant sprays electric sparks.
     */
    protected void spawnFlightEffects(ServerLevel level) {
    }

    /**
     * Extra effects when the skull goes off. Base skull does nothing; the lightning variant adds the
     * flash, sparks and thunder that make the charged payload read as "lightning".
     */
    protected void spawnDetonationEffects(ServerLevel level, double x, double y, double z) {
    }

    private void applyWeakHoming(double strength) {
        if (strength <= 0.0D) {
            return;
        }

        Entity owner = this.getOwner();
        if (!(owner instanceof Mob mob) || mob.getTarget() == null) {
            return;
        }

        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0E-4D) {
            return;
        }

        Vec3 desired = mob.getTarget().getEyePosition().subtract(this.position());
        if (desired.lengthSqr() < 1.0E-4D) {
            return;
        }

        Vec3 blended = velocity.normalize().scale(1.0D - strength)
                .add(desired.normalize().scale(strength));
        if (blended.lengthSqr() < 1.0E-4D) {
            return;
        }

        this.setDeltaMovement(blended.normalize().scale(speed));
    }

    /**
     * {@code Projectile#hitTargetOrDeflectSelf} routes both entity and block impacts through
     * {@code onHit}, so overriding this single hook covers every way the skull can land.
     */
    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        this.detonate();
    }

    private void detonate() {
        if (this.level() instanceof ServerLevel serverLevel) {
            // The shooter is credited as the blast source and is the one entity the explosion barely
            // hurts, so a diving Creeper Phantom no longer kills itself with its own missile.
            ChimeraExplosion.explode(serverLevel, this, this.getOwner(), this.getX(), this.getY(), this.getZ(),
                    this.explosionRadius(), this.ownerDamageFactor());
            this.spawnDetonationEffects(serverLevel, this.getX(), this.getY(), this.getZ());
        }
        this.discard();
    }
}
