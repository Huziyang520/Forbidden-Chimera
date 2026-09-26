package com.huziyang520.forbiddenchimera.ai;

/**
 * How a chimera steers itself towards a point.
 *
 * <p>The three chimeras use different movement backends: the vanilla phantom based ones only react to
 * their private {@code moveTargetPoint} (see {@code PhantomAccessor}), while the Creeper Phantom uses a
 * plain flying move control. Goals must go through this interface instead of touching the move control
 * directly, otherwise steering silently does nothing on one of the backends.
 */
public interface ChimeraSteering {

    /**
     * Steers the mob towards a point.
     *
     * @param speed advisory speed; the phantom backend ramps its own speed and ignores it.
     */
    void flyTowards(double x, double y, double z, double speed);
}
