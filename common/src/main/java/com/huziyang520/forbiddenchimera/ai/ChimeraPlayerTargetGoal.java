package com.huziyang520.forbiddenchimera.ai;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Player targeting that also accepts creative players.
 *
 * <p><b>Why this goal alone was never enough.</b> It writes the target with {@code Mob#setTarget}, which
 * passes the value through {@code Mob#asValidTarget} - and vanilla's version returns {@code null} for any
 * player who is creative or spectator. {@code Mob#getTarget} filters the stored field through the same
 * method again. So the write below was silently discarded and {@code attackCreativePlayers} did nothing in
 * either position: the chimeras simply never acquired a target in creative mode. The fix is the
 * {@code asValidTarget} override that goes with this goal, see
 * {@link ChimeraTargeting} - this class only decides <em>which</em> player, that one decides whether the
 * engine will keep it.
 *
 * <p>The goal never actually runs: it only writes the target and answers {@code false}, so it cannot
 * conflict with the combat goals that read that target. {@code attackCreativePlayers} restores the
 * vanilla behaviour when it is turned off.
 */
public class ChimeraPlayerTargetGoal extends Goal {

    /** How often the scan runs, in ticks. */
    private static final int SCAN_INTERVAL = 10;

    private final Mob mob;
    private int ticksUntilScan;

    public ChimeraPlayerTargetGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        // Drop a target that stopped being valid (left the range, turned creative while disallowed...).
        if (this.mob.getTarget() instanceof Player current && !this.isValidTarget(current)) {
            this.mob.setTarget(null);
        }

        if (this.ticksUntilScan > 0) {
            this.ticksUntilScan--;
            return false;
        }
        this.ticksUntilScan = SCAN_INTERVAL;

        Player player = this.findNearestPlayer();
        if (player != null) {
            this.mob.setTarget(player);
        }
        // Setting the target is the entire job; the goal itself must never run.
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    private @Nullable Player findNearestPlayer() {
        double range = this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        double bestDistance = range * range;
        Player best = null;

        for (Player player : this.mob.level().players()) {
            if (!this.isValidTarget(player)) {
                continue;
            }
            double distance = this.mob.distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private boolean isValidTarget(Player player) {
        if (!player.isAlive() || player.isSpectator()) {
            return false;
        }
        if (player.isCreative() && !ForbiddenChimeraConfig.get().attackCreativePlayers) {
            return false;
        }
        if (player.getAbilities().invulnerable && !ForbiddenChimeraConfig.get().attackCreativePlayers) {
            return false;
        }
        double range = this.mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        return this.mob.distanceToSqr(player) <= range * range;
    }
}
