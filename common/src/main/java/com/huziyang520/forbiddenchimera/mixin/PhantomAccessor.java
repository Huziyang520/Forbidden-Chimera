package com.huziyang520.forbiddenchimera.mixin;

import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the phantom's private steering target.
 *
 * <p>{@code PhantomMoveControl} ignores {@code MoveControl#wantedPosition} completely - it only steers
 * towards this field. Any goal that wants a phantom to fly somewhere on purpose has to write here,
 * which is what makes the rider and thrower chimeras actually charge and hover.
 */
@Mixin(Phantom.class)
public interface PhantomAccessor {

    @Accessor("moveTargetPoint")
    Vec3 forbiddenChimera$getMoveTargetPoint();

    @Accessor("moveTargetPoint")
    void forbiddenChimera$setMoveTargetPoint(Vec3 moveTargetPoint);
}
