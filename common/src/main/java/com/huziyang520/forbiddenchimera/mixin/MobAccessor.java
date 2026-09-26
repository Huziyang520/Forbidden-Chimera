package com.huziyang520.forbiddenchimera.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link Mob}'s protected goal selectors.
 *
 * <p>A mixin into {@code Phantom} cannot shadow fields it inherits from {@code Mob}, and the vanilla
 * phantom's own goals are all private inner classes - so injecting the chimera goals at priority 0
 * through these accessors is the only way to attach behaviour to a real vanilla phantom.
 */
@Mixin(Mob.class)
public interface MobAccessor {

    @Accessor("goalSelector")
    GoalSelector forbiddenChimera$getGoalSelector();

    @Accessor("targetSelector")
    GoalSelector forbiddenChimera$getTargetSelector();
}
