package com.huziyang520.forbiddenchimera.entity;

import com.huziyang520.forbiddenchimera.ai.ChimeraSteering;
import com.huziyang520.forbiddenchimera.world.PhantomRider;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shared base for the three chimera monsters.
 *
 * <p>Deliberately NOT extending {@code Phantom}: every vanilla phantom goal is a private inner class
 * driving a private {@code moveTargetPoint}, so a subclass cannot replace the AI without breaking the
 * movement control. Instead this base rebuilds the flying behaviour from public/vanilla parts:
 * {@link FlyingMoveControl} for steering, {@code LivingEntity#travelFlying} for motion. That gives a
 * mob that looks and flies like a phantom while keeping its goals fully under our control.
 */
public abstract class AbstractChimeraPhantom extends Mob implements Enemy, ChimeraSteering {

    protected AbstractChimeraPhantom(EntityType<? extends AbstractChimeraPhantom> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl<>(this, 20, true);
        this.setNoGravity(true);
        this.xpReward = 5;
    }

    @Override
    protected void registerGoals() {
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** All three chimeras hover and steer like the vanilla phantom. */
    @Override
    public void travel(Vec3 input) {
        this.travelFlying(input, 0.2F);
    }

    /** Phantoms are fliers: they neither take fall damage nor climb. */
    @Override
    protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    /** Phantoms are meant to be seen from far away. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    /** Steers the flying move control towards a point. Public because the goals live in another package. */
    public void flyTowards(double x, double y, double z, double speed) {
        this.getMoveControl().setWantedPosition(x, y, z, speed);
    }

    /**
     * Where a passenger (the knight variant's baby zombie, the boss's trident rider) sits.
     *
     * <p>Vanilla would fall back to the entity's own height ({@code AT_HEIGHT}), i.e. the top of the
     * <b>hitbox</b> - 0.5 for every chimera. These models are flat phantoms, so the rider belongs on the
     * modelled back instead, and the value is the derived model band constant the cargo uses
     * ({@link PhantomRider#RIDER_CARRY_HEIGHT}). It is deliberately a code constant, not a config value.
     *
     * <p>The offset is measured from the mount's own position (its feet), because vanilla subtracts the
     * passenger's {@code VEHICLE} attachment point, which is {@code (0, 0, 0)} for a zombie.
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0D, PhantomRider.RIDER_CARRY_HEIGHT, 0.0D);
    }

    /**
     * The carried rider must <b>never</b> take control of its mount.
     *
     * <p>{@code Mob#getControllingPassenger} hands the mount over to its first passenger whenever that
     * passenger is a {@code Mob} with {@code canControlVehicle()} - which is true for any entity that is
     * not in the {@code minecraft:non_controlling_rider} tag, the baby zombie included. The mount then
     * calls {@code updateControlFlags}, which switches <b>off</b> the {@code MOVE}, {@code LOOK} and
     * {@code JUMP} control flags for the whole goal selector: every goal of ours becomes a no-op and the
     * chimera just hangs in the air. ("It freezes until the baby zombie dies" - the rider dying empties
     * the passenger list, so the flags come back.)
     *
     * <p>Returning {@code null} keeps the mount fully in charge of its own AI, which is the only thing
     * that makes sense here: the rider is a passenger we spawned, not a player sitting on a horse.
     *
     * <p>Note on the <b>seated</b> look, which needs no code here: the humanoid renderer feeds
     * {@code state.isPassenger = entity.isPassenger()} straight from the entity in 26.3 (verified in the
     * compiled {@code HumanoidMobRenderer}), and {@code HumanoidModel} bends the arms and legs into the
     * riding pose whenever that flag is on. Being a real passenger is therefore what makes the rider sit.
     */
    @Override
    public @Nullable LivingEntity getControllingPassenger() {
        return null;
    }
}
