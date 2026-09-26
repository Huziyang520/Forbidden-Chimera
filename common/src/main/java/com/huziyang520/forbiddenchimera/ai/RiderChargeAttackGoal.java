package com.huziyang520.forbiddenchimera.ai;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import com.huziyang520.forbiddenchimera.entity.CreeperPhantomEntity;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * The knight variant's signature move: the chimera climbs above its target, drops on it, and the rider
 * does the damage with its mace.
 *
 * <p>Three phases, mirroring the plain dive so it reads as the same creature doing something worse:
 * {@code CLIMB} to {@code knightClimbHeight} above the target, {@code CHARGE} straight down, then
 * {@code PASS_BY} for a moment so it never loops on the spot.
 *
 * <p>Key differences from {@link DiveBombGoal}:
 * <ul>
 *   <li>it only runs for the knight variant, and only when the entity has <b>armed</b> a charge
 *       ({@link CreeperPhantomEntity#chargeArmed()}) - the low trigger rate and the cooldown both live
 *       on the entity, so a lost target can never freeze them;</li>
 *   <li>it sits at priority 0 while the dive sits at 1, so the two are mutually exclusive by
 *       construction: during a charge the mount does not fire skulls;</li>
 *   <li>the hit is dealt by the <b>rider</b>, not the mount. {@code Mob#doHurtTarget} then runs the
 *       whole vanilla weapon pipeline on the zombie: the mace supplies its own damage source and its
 *       own fall bonus. See {@link #strike} for how the fall distance is produced.</li>
 * </ul>
 */
public class RiderChargeAttackGoal extends Goal {

    private static final int PHASE_IDLE = 0;
    private static final int PHASE_CLIMB = 1;
    private static final int PHASE_CHARGE = 2;
    private static final int PHASE_PASS_BY = 3;

    /** Ticks the knight keeps flying after the strike, so a miss cannot turn into a hover loop. */
    private static final int PASS_BY_TICKS = 20;
    /** Contact test inflation, identical to the dive so both attacks connect at the same range. */
    private static final double HIT_INFLATION = 0.6D;
    /** How far past the target the pass-by steers, in blocks. */
    private static final double PASS_BY_DISTANCE = 12.0D;

    private final CreeperPhantomEntity mob;
    private int phase = PHASE_IDLE;
    private int phaseTicks;
    /** Height the charge started from; the difference to the current height is the real descent. */
    private double chargeTopY;
    private Vec3 approachPoint = Vec3.ZERO;

    public RiderChargeAttackGoal(CreeperPhantomEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.mob.isAlive()
                && this.mob.isKnight()
                && this.mob.chargeArmed()
                && this.mob.knightRider() != null
                && this.mob.getTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        // Deliberately not canUse(): the armed flag is consumed on start, and a running charge must not
        // be cut short by it. A finished run (PHASE_IDLE) ends the goal here instead of looping in tick.
        return this.phase != PHASE_IDLE
                && this.mob.isAlive()
                && this.mob.isKnight()
                && this.mob.knightRider() != null
                && this.mob.getTarget() != null;
    }

    @Override
    public void start() {
        this.mob.consumeCharge();
        this.phase = PHASE_CLIMB;
        this.phaseTicks = 0;
        this.pickApproachPoint();
    }

    @Override
    public void stop() {
        // Never leave the cooldown in a half consumed state if the goal is abandoned.
        if (this.phase != PHASE_IDLE) {
            this.endRun();
        }
    }

    /**
     * Attack position: straight above the target at {@code knightClimbHeight}.
     *
     * <p>The plain dive deliberately comes in diagonally (see {@code DiveBombGoal}) because its shots
     * have to be dodgeable. The knight fires nothing, and the mace bonus scales with <b>vertical</b>
     * descent, so a straight drop is both the fair and the effective choice here.
     */
    private void pickApproachPoint() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.approachPoint = new Vec3(target.getX(),
                target.getY() + ForbiddenChimeraConfig.get().knightClimbHeight, target.getZ());
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        this.mob.getLookControl().setLookAt(target, 40.0F, 40.0F);
        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();

        switch (this.phase) {
            case PHASE_CLIMB -> this.tickClimb(config);
            case PHASE_CHARGE -> this.tickCharge(target, config);
            case PHASE_PASS_BY -> this.tickPassBy(target, config);
            default -> {
                // PHASE_IDLE: nothing left to do, canContinueToUse is already false.
            }
        }
    }

    private void tickClimb(ForbiddenChimeraConfig config) {
        this.mob.flyTowards(this.approachPoint.x, this.approachPoint.y, this.approachPoint.z,
                config.knightClimbSpeed);
        double dx = this.mob.getX() - this.approachPoint.x;
        double dz = this.mob.getZ() - this.approachPoint.z;
        boolean inPosition = dx * dx + dz * dz <= 4.0D
                && this.mob.getY() >= this.approachPoint.y - 1.0D;
        if (inPosition) {
            this.phase = PHASE_CHARGE;
            this.phaseTicks = 0;
            this.chargeTopY = this.mob.getY();
        }
    }

    private void tickCharge(LivingEntity target, ForbiddenChimeraConfig config) {
        this.phaseTicks++;
        this.mob.flyTowards(target.getX(), target.getY(0.5D), target.getZ(), config.knightChargeSpeed);

        if (this.mob.getBoundingBox().inflate(HIT_INFLATION).intersects(target.getBoundingBox())) {
            this.strike(target);
            this.phase = PHASE_PASS_BY;
            this.phaseTicks = 0;
            return;
        }
        if (this.phaseTicks > config.knightMaxChargeTicks) {
            this.endRun();
        }
    }

    private void tickPassBy(LivingEntity target, ForbiddenChimeraConfig config) {
        this.phaseTicks++;
        Vec3 away = this.mob.position().subtract(target.position());
        Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
        if (horizontal.lengthSqr() < 1.0E-4D) {
            horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 destination = this.mob.position().add(horizontal.normalize().scale(PASS_BY_DISTANCE));
        this.mob.flyTowards(destination.x, destination.y, destination.z, config.knightChargeSpeed);
        if (this.phaseTicks > PASS_BY_TICKS) {
            this.endRun();
        }
    }

    /**
     * The rider delivers the blow.
     *
     * <p><b>Making the mace smash work.</b> {@code MaceItem} only adds its fall bonus when
     * {@code MaceItem.canSmashAttack(attacker)} is true, which needs {@code attacker.fallDistance > 1.5}.
     * The rider is a tick-anchored rider, not a passenger, and an AI-less mob with no gravity never
     * builds its own fall distance - it is carried, not falling. So the rider is handed the descent the
     * <b>mount</b> just performed ({@code chargeTopY} minus the current height), which is exactly the
     * drop the pair actually travelled. Below {@code knightSmashMinFallDistance} the strike is treated
     * as an ordinary mace hit instead.
     *
     * <p>Everything after that is vanilla: {@code Mob#doHurtTarget} picks the mace's damage source, adds
     * {@code MaceItem#getAttackDamageBonus} scaled by that fall distance, and plays the smash sound and
     * knockback through {@code MaceItem#hurtEnemy}.
     */
    private void strike(LivingEntity target) {
        if (!(this.mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Zombie rider = this.mob.knightRider();
        if (rider == null) {
            return;
        }

        double descent = this.chargeTopY - this.mob.getY();
        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();
        rider.fallDistance = descent >= config.knightSmashMinFallDistance ? descent : 0.0D;
        this.mob.playSound(SoundEvents.PHANTOM_SWOOP, 1.5F, 0.7F);
        rider.doHurtTarget(serverLevel, target);
    }

    private void endRun() {
        this.phase = PHASE_IDLE;
        this.phaseTicks = 0;
        this.mob.setChargeCooldown(ForbiddenChimeraConfig.get().knightChargeCooldownTicks);
        this.pickApproachPoint();
    }
}
