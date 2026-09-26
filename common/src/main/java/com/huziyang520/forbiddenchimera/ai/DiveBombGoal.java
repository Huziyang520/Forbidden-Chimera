package com.huziyang520.forbiddenchimera.ai;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import com.huziyang520.forbiddenchimera.entity.CreeperPhantomEntity;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Two phase attack used by the Creeper Phantom (mob3) and the Lightning Creeper Phantom (mob4):
 * take up position <b>diagonally above and away</b> from the target, then dive in, fire once and pass by.
 *
 * <p>Design notes, all player-facing difficulty decisions:
 * <ul>
 *   <li>the attack position is {@code diverApproachOffset} blocks away horizontally and
 *       {@code diverClimbHeight} above the target, so the dive - and therefore the shot - comes in
 *       diagonally instead of from straight overhead;</li>
 *   <li>skulls are fired <b>only during the dive</b> and <b>only while still at least
 *       {@code diverFireMinDistance} away</b> (horizontal distance). Shots from directly above the
 *       player are almost impossible to dodge, which made the chimera feel unfair;</li>
 *   <li><b>one shot per dive run</b>, then the phantom presses on and passes the player instead of
 *       flying back to its attack position. The old "hold after firing" step made it retreat and come
 *       back, which players read as the mob circling aimlessly;</li>
 *   <li>the approach point is recomputed whenever a dive run starts, so it stays behind wherever the
 *       player is moving to.</li>
 * </ul>
 *
 * <p>The dive cooldown lives on the entity ({@link CreeperPhantomEntity#attackCooldown()}), not in a
 * goal field: goals stop ticking the moment they lose their target, and a frozen cooldown means the
 * mob never attacks again.
 */
public class DiveBombGoal extends Goal {

    private static final int PHASE_CLIMB = 0;
    private static final int PHASE_DIVE = 1;

    /** Horizontal distance past the closest approach that counts as "flown past the player", in blocks. */
    private static final double PASS_BY_OVERSHOOT = 3.0D;

    private final CreeperPhantomEntity mob;
    private int phase = PHASE_CLIMB;
    private int diveTicks;
    /** One shot per dive run, so the pass-by never turns into a second attack turn. */
    private boolean firedThisDive;
    /** Closest horizontal distance reached during the current dive, used to detect the pass-by. */
    private double closestApproach = Double.MAX_VALUE;
    /** Where this dive run starts from: diagonally above and away from the target. */
    private Vec3 approachPoint = Vec3.ZERO;

    public DiveBombGoal(CreeperPhantomEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.attackCooldown() > 0) {
            return false;
        }
        return this.mob.isAlive() && this.mob.getTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        // Deliberately not canUse(): the cooldown must only gate new runs, not interrupt a running one.
        return this.mob.isAlive() && this.mob.getTarget() != null;
    }

    @Override
    public void start() {
        this.phase = PHASE_CLIMB;
        this.mob.setDiving(false);
        this.pickApproachPoint();
    }

    @Override
    public void stop() {
        this.phase = PHASE_CLIMB;
        this.diveTicks = 0;
        this.firedThisDive = false;
        // Never leave the client stuck in the dive pose when the goal is abandoned.
        this.mob.setDiving(false);
    }

    /**
     * Chooses the point the phantom dives in from.
     *
     * <p>Direction is from the target towards the phantom's current position (horizontal only), so it
     * backs off along the line it is already coming from; if it is somehow exactly above the target, a
     * fixed direction is used so the dive is never perfectly vertical.
     */
    private void pickApproachPoint() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();

        Vec3 away = this.mob.position().subtract(target.position());
        double horizontal = Math.sqrt(away.x * away.x + away.z * away.z);
        double dirX;
        double dirZ;
        if (horizontal < 1.0E-3D) {
            dirX = 1.0D;
            dirZ = 0.0D;
        } else {
            dirX = away.x / horizontal;
            dirZ = away.z / horizontal;
        }

        this.approachPoint = new Vec3(
                target.getX() + dirX * config.diverApproachOffset,
                target.getY() + config.diverClimbHeight,
                target.getZ() + dirZ * config.diverApproachOffset);
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();
        // Body and head both track the player, so it is always facing whoever it is about to shoot at.
        this.mob.getLookControl().setLookAt(target, 40.0F, 40.0F);

        if (this.phase == PHASE_CLIMB) {
            this.mob.flyTowards(this.approachPoint.x, this.approachPoint.y, this.approachPoint.z,
                    config.diverClimbSpeed);
            double dx = this.mob.getX() - this.approachPoint.x;
            double dz = this.mob.getZ() - this.approachPoint.z;
            boolean inPosition = dx * dx + dz * dz <= 4.0D
                    && this.mob.getY() >= this.approachPoint.y - 1.0D;
            if (inPosition) {
                this.beginDive();
            }
            return;
        }

        this.tickDive(target, config);
    }

    private void beginDive() {
        this.phase = PHASE_DIVE;
        this.diveTicks = 0;
        this.firedThisDive = false;
        this.closestApproach = Double.MAX_VALUE;
        // Drives the dive pose on the model.
        this.mob.setDiving(true);
    }

    private void tickDive(LivingEntity target, ForbiddenChimeraConfig config) {
        this.diveTicks++;
        this.mob.flyTowards(target.getX(), target.getY(0.5D), target.getZ(), config.diverDiveSpeed);

        if (!this.firedThisDive && this.mob.canFireSkulls() && this.mob.canFire()
                && this.isFarEnoughToFire(target, config)) {
            this.mob.fireCreeperSkull(target);
            this.firedThisDive = true;
        }

        if (this.mob.getBoundingBox().inflate(0.6D).intersects(target.getBoundingBox())) {
            if (this.mob.level() instanceof ServerLevel serverLevel) {
                // The entity decides how the contact hit resolves: plain melee, or the spear's own
                // kinetic weapon damage when this individual rolled the spear variant.
                this.mob.performDiveHit(serverLevel, target);
            }
            this.endRun(config);
            return;
        }

        double dx = this.mob.getX() - target.getX();
        double dz = this.mob.getZ() - target.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        this.closestApproach = Math.min(this.closestApproach, distance);

        // Flew past the player: it is now moving away again after having been closer than before.
        boolean passedBy = this.firedThisDive
                && distance > this.closestApproach + PASS_BY_OVERSHOOT;
        boolean spent = this.diveTicks > config.diverMaxDiveTicks
                || (this.firedThisDive && this.diveTicks > config.diverPassByTicks);
        if (passedBy || spent) {
            this.endRun(config);
        }
    }

    /** Ends the run and hands the cooldown to the entity, where it cannot freeze. */
    private void endRun(ForbiddenChimeraConfig config) {
        this.phase = PHASE_CLIMB;
        this.firedThisDive = false;
        this.mob.setDiving(false);
        this.mob.setAttackCooldown(config.diverDiveCooldownTicks);
        this.pickApproachPoint();
    }

    /** @return whether the phantom is still far enough away that a shot can be dodged. */
    private boolean isFarEnoughToFire(LivingEntity target, ForbiddenChimeraConfig config) {
        double dx = this.mob.getX() - target.getX();
        double dz = this.mob.getZ() - target.getZ();
        double min = config.diverFireMinDistance;
        return dx * dx + dz * dz >= min * min;
    }
}
