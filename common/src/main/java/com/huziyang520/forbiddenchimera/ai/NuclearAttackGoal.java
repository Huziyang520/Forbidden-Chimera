package com.huziyang520.forbiddenchimera.ai;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import com.huziyang520.forbiddenchimera.entity.NuclearCreeperPhantomEntity;
import com.huziyang520.forbiddenchimera.world.ChimeraExplosion;
import com.huziyang520.forbiddenchimera.world.ChimeraFire;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
 *   <li><b>charge</b> - dive at the target at {@code nuclearChargeSpeed}, nose and head locked onto the
 *       player. Because the hover point is offset, this is a visible diagonal dive, not a drop from
 *       straight overhead;</li>
 *   <li><b>detonate</b> - self destruct on contact, or after {@code nuclearChargeMaxTicks}, with a
 *       {@code nuclearExplosionRadius} blast that also leaves the area on fire.</li>
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
    /**
     * Horizontal stand-off kept between the mob and its steering point during the dive.
     *
     * <p>See {@link #tickCharge}: the flight move control derives the body yaw from
     * {@code atan2(dz, dx)} of the point it is steering at, so that point must never sit directly above or
     * below the mob or the bearing degenerates and the nose swings to an arbitrary direction. Keeping a
     * small horizontal offset keeps the bearing - and therefore the facing - on the player.
     */
    private static final double CHARGE_MIN_HORIZONTAL = 0.5D;

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
    /**
     * The orbit's own zero point: the tick the circling began, and the bearing the mob already had at
     * that moment.
     *
     * <p>The angle used to be read straight off {@code mob.tickCount}, i.e. off a clock that had been
     * running since the mob spawned. Entering the circling phase therefore teleported the goal post to
     * whatever azimuth that absolute clock happened to be at, somewhere else on the ring, and the mob
     * spent the whole phase sprinting sideways across the sky to catch a point that was itself moving -
     * which is what read as "endlessly spinning in circles". Anchored to its own entry bearing, the mob
     * starts the orbit exactly where it already is and simply follows the ring.
     */
    private int circleStartTick;
    private double circleStartAngle;
    /** Unit horizontal bearing onto the player, carried across ticks so the dive never loses its aim. */
    private double chargeDirX;
    private double chargeDirZ;

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
        LivingEntity target = this.mob.getTarget();
        this.phase = PHASE_CIRCLE;
        this.phaseTicks = 0;
        this.chargeTicks = 0;
        this.circleStartTick = this.mob.tickCount;
        this.circleStartAngle = target == null ? 0.0D : this.bearingTo(target);
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

    /** The azimuth the mob currently sits at, seen from the target. */
    private double bearingTo(LivingEntity target) {
        return Math.atan2(this.mob.getZ() - target.getZ(), this.mob.getX() - target.getX());
    }

    private void tickCircle(LivingEntity target, ForbiddenChimeraConfig config) {
        double angle = this.circleStartAngle + (this.mob.tickCount - this.circleStartTick) * CIRCLE_SPEED;
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
            this.parkAndFace(target);
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
     * Stops dead and points the whole body at the target.
     *
     * <p>The order matters and is the reason this is a method rather than two lines at the call site.
     * The flight move control runs <b>after</b> the goals each tick and rewrites the body yaw from its own
     * steering target, so a goal that merely calls {@code setYRot} is silently overruled. Aiming the move
     * control at the mob's own position is what disarms it: the wanted point is then zero blocks away, the
     * move control takes its "no distance to travel" branch and returns without touching the yaw, and the
     * facing written here survives. Only then does {@link #faceTowards} mean anything.
     */
    private void parkAndFace(LivingEntity target) {
        this.mob.flyTowards(this.mob.getX(), this.mob.getY(), this.mob.getZ(), 0.0D);
        this.faceTowards(target);
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

        // The move control works out the body yaw from the bearing of the point it is steering at, and
        // LookControl then clamps the head to +-40 degrees of that body yaw - so a body facing the wrong
        // way drags the head off the player no matter what the look control asks for. The bearing is kept
        // from the last tick where it was well defined, which is what stops the nose from swinging away
        // once the mob is close enough to be almost directly above the player.
        double dx = target.getX() - this.mob.getX();
        double dz = target.getZ() - this.mob.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal >= CHARGE_MIN_HORIZONTAL) {
            this.chargeDirX = dx / horizontal;
            this.chargeDirZ = dz / horizontal;
        }
        double steerX = this.mob.getX() + this.chargeDirX * CHARGE_MIN_HORIZONTAL;
        double steerZ = this.mob.getZ() + this.chargeDirZ * CHARGE_MIN_HORIZONTAL;
        this.mob.flyTowards(steerX, target.getY(0.5D), steerZ, config.nuclearChargeSpeed);

        boolean contact = this.mob.getBoundingBox().inflate(0.6D).intersects(target.getBoundingBox());
        if (contact || this.chargeTicks >= config.nuclearChargeMaxTicks) {
            this.detonate();
        }
    }

    /**
     * Writes the body, head and pitch needed to look at the target, all three in one go.
     *
     * <p>Only useful once the move control has been parked (see {@link #parkAndFace}); while it is
     * actively steering it rewrites the yaw again later in the same tick and this would be discarded.
     * {@code yBodyRot} and {@code yHeadRot} are set alongside {@code yRot} because the body turn is what
     * the renderer reads and the head turn otherwise lags a tick behind it.
     */
    private void faceTowards(LivingEntity target) {
        double dx = target.getX() - this.mob.getX();
        double dy = target.getEyeY() - this.mob.getEyeY();
        double dz = target.getZ() - this.mob.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, horizontal) * (180.0D / Math.PI)));
        this.mob.setYRot(yaw);
        this.mob.setYBodyRot(yaw);
        this.mob.setYHeadRot(yaw);
        this.mob.setXRot(pitch);
    }

    /** Goes off where it stands: the blast is the whole point of this chimera, fire included. */
    private void detonate() {
        if (!(this.mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();
        this.mob.setNuclearPhase(NuclearCreeperPhantomEntity.PHASE_DETONATE);
        double x = this.mob.getX();
        double y = this.mob.getY();
        double z = this.mob.getZ();
        ChimeraExplosion.explode(serverLevel, this.mob, x, y, z, (float) config.nuclearExplosionRadius);
        // A plain explosion does not set anything on fire, and this one is meant to be a small nuke.
        ChimeraFire.burnAfterExplosion(serverLevel, x, y, z, config.nuclearExplosionRadius,
                config.nuclearExplosionFireTicks, config.nuclearExplosionFireChance);
        this.mob.kill(serverLevel);
    }
}