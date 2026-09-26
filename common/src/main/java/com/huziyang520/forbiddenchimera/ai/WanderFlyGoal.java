package com.huziyang520.forbiddenchimera.ai;

import java.util.EnumSet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Ambient flight for a flying mob that has nothing to attack.
 *
 * <p>Without this the chimera simply hovers where it was spawned, which reads as "the AI is not
 * implemented" even though its combat goals work. The goal keeps re-picking a point inside a loose box
 * around the spot it took up, so the mob drifts around instead of standing still.
 *
 * <p>Steering goes through {@link ChimeraSteering} - never through the move control directly, see the
 * interface for why.
 */
public class WanderFlyGoal extends Goal {

    /** Horizontal radius of the drift box, in blocks. */
    private static final double RADIUS_XZ = 8.0D;
    /** Vertical span of the drift box, in blocks. */
    private static final double RADIUS_Y = 3.0D;
    private static final double SPEED = 0.7D;
    private static final int MIN_TICKS = 60;
    private static final int MAX_TICKS = 140;

    private final Mob mob;
    private final ChimeraSteering steering;
    private Vec3 anchor = Vec3.ZERO;
    private Vec3 destination = Vec3.ZERO;
    private int ticksUntilNextPick;

    public <T extends Mob & ChimeraSteering> WanderFlyGoal(T mob) {
        this.mob = mob;
        this.steering = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // Combat goals always win; this only fills the idle time.
        return this.mob.isAlive() && this.mob.getTarget() == null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void start() {
        this.anchor = this.mob.position();
        this.pickDestination();
    }

    @Override
    public void tick() {
        if (--this.ticksUntilNextPick <= 0) {
            this.pickDestination();
        }
        this.steering.flyTowards(this.destination.x, this.destination.y, this.destination.z, SPEED);
    }

    private void pickDestination() {
        RandomSource random = this.mob.getRandom();
        this.ticksUntilNextPick = MIN_TICKS + random.nextInt(MAX_TICKS - MIN_TICKS);
        this.destination = this.anchor.add(
                (random.nextDouble() * 2.0D - 1.0D) * RADIUS_XZ,
                (random.nextDouble() * 2.0D - 1.0D) * RADIUS_Y,
                (random.nextDouble() * 2.0D - 1.0D) * RADIUS_XZ);
    }
}
