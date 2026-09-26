package com.huziyang520.forbiddenchimera.ai;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import com.huziyang520.forbiddenchimera.entity.ChimeraPhantom;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Phantom;

/**
 * mob1 幻翼骑士苦力怕: fly straight at the target and detonate the carried creeper on contact.
 *
 * <p>Runs at priority 0 on a real vanilla phantom, preempting the vanilla circling goals. Steering
 * goes through {@link ChimeraSteering} (the phantom backend) and the cargo is re-anchored by
 * {@code PhantomMixin} every tick, so there is nothing to manage here.
 */
public class RiderChargeGoal extends Goal {

    private final Phantom phantom;
    private final ChimeraPhantom chimera;
    private final ChimeraSteering steering;

    public RiderChargeGoal(Phantom phantom) {
        this.phantom = phantom;
        this.chimera = (ChimeraPhantom) phantom;
        this.steering = (ChimeraSteering) phantom;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.isCharging();
    }

    @Override
    public boolean canContinueToUse() {
        return this.isCharging();
    }

    private boolean isCharging() {
        return this.phantom.isAlive()
                && this.phantom.getTarget() != null
                && this.chimera.forbiddenChimera$cargo() != null;
    }

    @Override
    public void tick() {
        LivingEntity target = this.phantom.getTarget();
        if (target == null) {
            return;
        }

        this.phantom.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.steering.flyTowards(target.getX(), target.getY(0.5D), target.getZ(),
                ForbiddenChimeraConfig.get().riderChargeSpeed);

        if (this.phantom.distanceToSqr(target)
                <= ForbiddenChimeraConfig.get().riderDetonateDistanceSqr) {
            this.chimera.forbiddenChimera$detonateCargo();
        }
    }
}
