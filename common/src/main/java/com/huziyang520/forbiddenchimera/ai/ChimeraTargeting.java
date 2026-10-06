package com.huziyang520.forbiddenchimera.ai;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Target filtering for the chimeras, mirroring {@code Mob#asValidTarget} with one deliberate exception.
 *
 * <p><b>Why this exists.</b> {@code Mob#setTarget} passes every value through {@code asValidTarget}, and
 * {@code Mob#getTarget} filters the stored field through it again. Vanilla's version returns {@code null}
 * for any player who is creative or spectator, so {@link ChimeraPlayerTargetGoal}'s target write was
 * silently thrown away and {@code attackCreativePlayers} did nothing in either position - the mob simply
 * never acquired a target in creative mode.
 *
 * <p>It is a shared helper rather than two copies of the same logic because it has to be reached from two
 * unrelated places: {@code AbstractChimeraPhantom} for the registered entities, and {@code PhantomMixin}
 * for mob1/mob2, which are real vanilla phantoms. The mixin cannot call {@code super.asValidTarget(...)}
 * - its own class does not extend {@code Mob} - so both go through here instead.
 *
 * <p>The rest of the method reproduces the vanilla rule exactly, including the {@code canAttack} check, so
 * turning {@code attackCreativePlayers} off is indistinguishable from vanilla behaviour.
 */
public final class ChimeraTargeting {

    private ChimeraTargeting() {
    }

    /**
     * The target a chimera is allowed to hold, or {@code null}.
     *
     * <p>Two ways a player is exempted, matching {@link ChimeraPlayerTargetGoal#isValidTarget}: creative
     * mode, and the {@code invulnerable} ability (which is what creative sets, but which can also be
     * granted on its own). Spectators are never targeted - {@code canBeSeenAsEnemy} is false for them and
     * they are not playing.
     */
    public static @Nullable LivingEntity filter(Mob mob, @Nullable LivingEntity target) {
        if (target instanceof Player player
                && ForbiddenChimeraConfig.get().attackCreativePlayers
                && !player.isSpectator()
                && (player.isCreative() || player.getAbilities().invulnerable)) {
            return target;
        }

        // --- vanilla Mob#asValidTarget, reproduced ---
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return null;
        }
        return target != null && !mob.canAttack(target) ? null : target;
    }
}