package com.huziyang520.forbiddenchimera.ai;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import com.huziyang520.forbiddenchimera.entity.ChimeraPhantom;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Phantom;

/**
 * mob2 苦力怕投手幻翼, exactly as specified:
 *
 * <ol>
 *   <li>climb until it hovers {@code throwerHoverHeight} above the player's head;</li>
 *   <li>drop the creeper it is hanging straight at the player;</li>
 *   <li>immediately grab a fresh creeper, so it always looks loaded;</li>
 *   <li>wait out {@code throwerThrowCooldownTicks}, then try again.</li>
 * </ol>
 *
 * <p>It only drops from above - never sideways, and never while still climbing. The replacement cargo
 * is created inside {@code ChimeraPhantom#dropCargo}, i.e. at the exact moment of release.
 */
public class ThrowerDropGoal extends Goal {

    /** How close to the hover height the phantom must be before it may drop (in blocks). */
    private static final double HOVER_TOLERANCE = 2.0D;

    private final Phantom phantom;
    private final ChimeraPhantom chimera;
    private final ChimeraSteering steering;

    public ThrowerDropGoal(Phantom phantom) {
        this.phantom = phantom;
        this.chimera = (ChimeraPhantom) phantom;
        this.steering = (ChimeraSteering) phantom;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // No cargo means no chimera behaviour at all: the phantom falls back to its vanilla goals.
        return this.phantom.isAlive()
                && this.phantom.getTarget() != null
                && this.chimera.forbiddenChimera$cargo() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void tick() {
        LivingEntity target = this.phantom.getTarget();
        if (target == null) {
            return;
        }

        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();
        // The phantom's own look control is a no-op on this backend; facing follows the move control.
        this.steering.flyTowards(target.getX(), target.getY() + config.throwerHoverHeight, target.getZ(),
                config.throwerApproachSpeed);

        boolean aboveThePlayer =
                this.phantom.getY() >= target.getY() + config.throwerHoverHeight - HOVER_TOLERANCE;
        if (aboveThePlayer && this.chimera.forbiddenChimera$canThrow()) {
            this.chimera.forbiddenChimera$dropCargo(target);
        }
    }
}
