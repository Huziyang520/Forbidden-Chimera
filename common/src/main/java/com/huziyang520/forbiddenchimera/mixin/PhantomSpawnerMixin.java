package com.huziyang520.forbiddenchimera.mixin;

import com.huziyang520.forbiddenchimera.world.ChimeraSpawner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla phantom spawning with the chimeras.
 *
 * <p>{@code PhantomSpawner} is the only source of phantoms in the game - they are not part of any
 * biome spawn list - so cancelling its tick at HEAD and running {@link ChimeraSpawner} is a complete
 * and exact swap: the chimeras inherit the sky darkness check, the altitude and sky visibility
 * requirement, the difficulty roll, the {@code TIME_SINCE_REST} insomnia roll and the
 * {@code spawn_phantoms} game rule.
 *
 * <p>The countdown lives here as a {@code @Unique} field so that every {@code ServerLevel} keeps its
 * own schedule, exactly like the vanilla spawner instance it replaces.
 */
@Mixin(PhantomSpawner.class)
public class PhantomSpawnerMixin {

    @Unique
    private int forbiddenChimera$nextTick;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void forbiddenChimera$spawnChimerasInstead(ServerLevel level, boolean spawnEnemies,
                                                       CallbackInfo callback) {
        callback.cancel();
        this.forbiddenChimera$nextTick =
                ChimeraSpawner.tick(level, spawnEnemies, this.forbiddenChimera$nextTick);
    }
}
