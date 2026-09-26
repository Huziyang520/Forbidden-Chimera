package com.huziyang520.forbiddenchimera.ai;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import com.huziyang520.forbiddenchimera.entity.NuclearCreeperPhantomEntity;
import com.huziyang520.forbiddenchimera.world.ChimeraExplosion;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * 核弹苦力怕幻翼 behaviour, exactly as specified:
 *
 * <ol>
 *   <li><b>circle</b> - stay at altitude and circle the target for {@code nuclearCircleTicks};</li>
 *   <li><b>hover</b> - take position above <b>and beside</b> the target ({@code nuclearHoverOffset}
 *       blocks out) and hover for {@code nuclearHoverTicks}, the wing flap accelerating the whole time
 *       (driven by the animation controller off the synced hover counter);</li>
 *   <li><b>charge</b> - dive at the target at {@code nuclearChargeSpeed}. Because the hover point is
 *       offset, this is a visible diagonal dive, not a drop from straight overhead;</li>
 *   <li><b>detonate</b> - self destruct on contact, or after {@code nuclearChargeMaxTicks}, with a
 *       {@code nuclearExplosionRadius} blast.</li>
 * </ol>
 *
 * <p>Unlike the divers this one has no ranged attack at all: it is a single-use missile.
 */
public class NuclearAttackGoal extends Goal {

    private static final int PHASE_CIRCLE = 0;
    private static final int PHASE_HOVER = 1;
    private static final int PHASE_CHARGE = 2;

    /** Horizontal radius of the circling orbit around the target. */
    private static final double CIRCLE_RADIUS = 12.0D;
    /** Radians of orbit travelled per tick; keeps the circling slow and readable. */
    private static final double CIRCLE_SPEED = 0.05D;
    /**
     * Ticks at the end of the hover spent standing still and turning to face the player.
     *
     * <p>Without it the mob never actually aims: the flight move control overwrites the body yaw with
     * the direction it is travelling every tick, so as long as the hover keeps steering, the look
     * control can never win. Stopping the movement first is what lets the body line up with the player
     * before the charge.
     */
    private static final int AIM_TICKS = 12;

    private final NuclearCreeperPhantomEntity mob;
    private int phase = PHASE_CIRCLE;
    private int phaseTicks;
    private int chargeTicks;
    /**
     * The hover point, frozen when the hover begins.
     *
     * <p>It used to be recomputed every tick from the mob's own bearing to the target, which made the
     * goal post move as the mob flew towards it: the thing chased its own offset and read as circling
     * instead of lining up. Computed once, the mob settles into position.
     */
    private double hoverX;
    private double hoverZ;

    public NuclearAttackGoal(NuclearCreeperPhantomEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.mob.isAlive() && this.mob.getTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.phase = PHASE_CIRCLE;
        this.phaseTicks = 0;
        this.chargeTicks = 0;
        this.mob.setNuclearPhase(NuclearCreeperPhantomEntity.PHASE_CIRCLE);
    }

    @Override
    public void stop() {
        this.mob.setNuclearPhase(NuclearCreeperPhantomEntity.PHASE_FLY);
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();
        this.mob.getLookControl().setLookAt(target, 40.0F, 40.0F);
        this.phaseTicks++;

        switch (this.phase) {
            case PHASE_CIRCLE -> this.tickCircle(target, config);
            case PHASE_HOVER -> this.tickHover(target, config);
            default -> this.tickCharge(target, config);
        }
    }

    private void tickCircle(LivingEntity target, ForbiddenChimeraConfig config) {
        double angle = this.mob.tickCount * CIRCLE_SPEED;
        double x = target.getX() + Math.cos(angle) * CIRCLE_RADIUS;
        double z = target.getZ() + Math.sin(angle) * CIRCLE_RADIUS;
        this.mob.flyTowards(x, target.getY() + config.nuclearHoverHeight, z, config.nuclearApproachSpeed);

        if (this.phaseTicks >= config.nuclearCircleTicks) {
            this.phase = PHASE_HOVER;
            this.phaseTicks = 0;
            this.mob.setNuclearPhase(NuclearCreeperPhantomEntity.PHASE_HOVER);
        }
    }

    private void tickHover(LivingEntity target, ForbiddenChimeraConfig config) {
        // Drives the accelerating wing flap on the client.
        this.mob.setHoverTicks(this.phaseTicks);

        if (this.phaseTicks >= config.nuclearHoverTicks) {
            this.phase = PHASE_CHARGE;
            this.chargeTicks = 0;
            this.mob.setNuclearPhase(NuclearCreeperPhantomEntity.PHASE_CHARGE);
            return;
        }

        // The last stretch is spent parked and facing the player, so the dive reads as "locked on".
        int aimFrom = Math.max(0, config.nuclearHoverTicks - AIM_TICKS);
        if (this.phaseTicks > aimFrom) {
            this.mob.flyTowards(this.mob.getX(), this.mob.getY(), this.mob.getZ(), 0.0D);
            return;
        }

        if (this.phaseTicks == 1) {
            double[] hover = this.hoverPoint(target, config);
            this.hoverX = hover[0];
            this.hoverZ = hover[1];
        }
        this.mob.flyTowards(this.hoverX, target.getY() + config.nuclearHoverHeight, this.hoverZ,
                config.nuclearApproachSpeed);
    }

    /**
     * Hover position as {@code {x, z}}: directly over the target, pulled out to
     * {@code nuclearHoverOffset} blocks horizontally.
     *
     * <p>The offset is what makes the dive visible. Hovering straight above the target turned the charge
     * into a vertical drop from out of view; from a horizontal offset the charge is a diagonal dive.
     * The azimuth is kept from where the circle left the mob, so the approach angle differs run to run.
     *
     * <p>If it already sits directly above the target the azimuth is undefined, so the fallback is the
     * target's own facing direction - that puts it in front of the player's eyes rather than blind
     * overhead.
     */
    private double[] hoverPoint(LivingEntity target, ForbiddenChimeraConfig config) {
        double offset = config.nuclearHoverOffset;
        double dx = this.mob.getX() - target.getX();
        double dz = this.mob.getZ() - target.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        if (horizontal < 1.0E-3D) {
            Vec3 look = target.getLookAngle();
            double lookHorizontal = Math.sqrt(look.x * look.x + look.z * look.z);
            if (lookHorizontal < 1.0E-3D) {
                return new double[] {target.getX() + offset, target.getZ()};
            }
            return new double[] {target.getX() + look.x / lookHorizontal * offset,
                    target.getZ() + look.z / lookHorizontal * offset};
        }

        double scale = offset / horizontal;
        return new double[] {target.getX() + dx * scale, target.getZ() + dz * scale};
    }

    private void tickCharge(LivingEntity target, ForbiddenChimeraConfig config) {
        this.chargeTicks++;
        this.mob.flyTowards(target.getX(), target.getY(0.5D), target.getZ(), config.nuclearChargeSpeed);

        boolean contact = this.mob.getBoundingBox().inflate(0.6D).intersects(target.getBoundingBox());
        if (contact || this.chargeTicks >= config.nuclearChargeMaxTicks) {
            this.detonate();
        }
    }

    /** Goes off where it stands: the blast is the whole point of this chimera. */
    private void detonate() {
        if (!(this.mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();
        this.mob.setNuclearPhase(NuclearCreeperPhantomEntity.PHASE_DETONATE);
        ChimeraExplosion.explode(serverLevel, this.mob, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                (float) config.nuclearExplosionRadius);
        this.mob.kill(serverLevel);
    }
}
