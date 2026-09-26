package com.huziyang520.forbiddenchimera.ai;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import com.huziyang520.forbiddenchimera.entity.BossLightningCreeperPhantomKnightEntity;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The boss's entire attack cycle, in one goal.
 *
 * <p>Why one goal: the boss's moves are a <b>sequence</b>, not competing options. Footballing them into
 * separate goals would need priority games to keep them from overlapping, and every transition would be
 * at the mercy of goal re-evaluation. A single state machine makes the order explicit and gives each
 * animation a defined window.
 *
 * <p>The cycle:
 * <ol>
 *   <li>{@code ORBIT} - cruise around the target at <code>bossOrbitRadius</code> /
 *       <code>bossOrbitHeight</code> for <code>bossOrbitTicks</code>;</li>
 *   <li>{@code SPECIAL} - one of three ranged moves, picked at random: a <b>凋灵之手连发</b>
 *       (wither skull salvo), a <b>dragon breath</b>, or a <b>darkness</b> pulse;</li>
 *   <li>{@code ROCKET} - burn the firework rockets and close in;</li>
 *   <li>{@code DIVE} - straight at the target; the trident rider delivers the hit on contact;</li>
 *   <li>{@code PASS_BY} - keep flying so a miss never becomes a hover loop;</li>
 *   <li>{@code SONIC_BOOM} - the parting scream, if the target is still inside the warden's reach;</li>
 *   <li>back to {@code ORBIT}.</li>
 * </ol>
 *
 * <p>The <b>lightning</b> skulls are <b>not</b> here: they are the boss's continuous side arm on their
 * own cooldown, fired from the entity tick (see {@code Boss...Entity#tick}), suppressed while diving so
 * the charge stays readable. What this goal runs is the <b>burst</b>: the 凋灵之手 salvo.
 *
 * <p>Timings are in server ticks and drive the effects; the client animation only mirrors the synced
 * state. The {@code dragon_breath} window in particular is anchored to the <b>asset</b>: the clip holds
 * the jaws open from 0.35 s to 1.6 s, so shots start at <code>bossDragonBreathFirstTick</code> (7) and
 * are spaced by <code>bossDragonBreathIntervalTicks</code>.
 */
public class BossAttackGoal extends Goal {

    private static final int PHASE_ORBIT = 0;
    private static final int PHASE_SPECIAL = 1;
    private static final int PHASE_ROCKET = 2;
    private static final int PHASE_DIVE = 3;
    private static final int PHASE_PASS_BY = 4;
    private static final int PHASE_SONIC_BOOM = 5;

    private static final int SPECIAL_SALVO = 0;
    private static final int SPECIAL_DRAGON_BREATH = 1;
    private static final int SPECIAL_DARKNESS = 2;
    private static final int SPECIAL_COUNT = 3;

    /** Contact test inflation, same as the other chimeras' attacks. */
    private static final double HIT_INFLATION = 0.6D;
    /** Horizontal radius of the orbit. */
    private static final double ORBIT_RADIUS = 16.0D;
    /** Radians of orbit per tick. */
    private static final double ORBIT_SPEED = 0.05D;
    /** Ticks the boss keeps flying after a dive. */
    private static final int PASS_BY_TICKS = 14;
    /** Blocks past the target the pass-by steers for. */
    private static final double PASS_BY_DISTANCE = 16.0D;
    /** Tick inside the salvo clip the first skull leaves (the wind up is about 0.3 s). */
    private static final int SALVO_FIRST_TICK = 6;
    /**
     * Phase lengths, in ticks. These are the animation-driven floors and stay put (the clips are 2 s
     * long); the cycle was shortened by {@code bossOrbitTicks} and the two "filler" windows below, on
     * 2026-09-26, because the player reported every move except the lightning side arm as too rare.
     */
    private static final int SALVO_TICKS = 20;
    private static final int DRAGON_BREATH_TICKS = 40;
    private static final int SONIC_BOOM_TICKS = 24;
    private static final int DARKNESS_TICKS = 40;
    /** Tick inside the darkness clip the pulse lands on. */
    private static final int DARKNESS_PULSE_TICK = 8;

    private final BossLightningCreeperPhantomKnightEntity mob;
    private int phase = PHASE_ORBIT;
    private int phaseTicks;
    private int special = SPECIAL_SALVO;
    /** How many shots of the current special have already gone out. */
    private int shotsFired;
    /** True once the sonic boom of this phase has gone off. */
    private boolean sonicBoomFired;
    /** Where the boss was on the previous dive tick, for the swept hit test. */
    private Vec3 lastDivePosition;

    public BossAttackGoal(BossLightningCreeperPhantomKnightEntity mob) {
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
        this.enterOrbit();
    }

    @Override
    public void stop() {
        // Never leave the client stuck in a one shot pose.
        this.mob.setBossAnimation(BossLightningCreeperPhantomKnightEntity.ANIM_FLY);
        this.mob.setBossDiving(false);
        this.phase = PHASE_ORBIT;
        this.phaseTicks = 0;
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
            case PHASE_ORBIT -> this.tickOrbit(target, config);
            case PHASE_SPECIAL -> this.tickSpecial(target, config);
            case PHASE_ROCKET -> this.tickRocket(target, config);
            case PHASE_DIVE -> this.tickDive(target, config);
            case PHASE_PASS_BY -> this.tickPassBy(target, config);
            default -> this.tickSonicBoom(target, config);
        }
    }

    // --- phases -------------------------------------------------------------

    private void enterOrbit() {
        this.phase = PHASE_ORBIT;
        this.phaseTicks = 0;
        this.mob.setBossAnimation(BossLightningCreeperPhantomKnightEntity.ANIM_FLY);
        this.mob.setBossDiving(false);
    }

    private void tickOrbit(LivingEntity target, ForbiddenChimeraConfig config) {
        double angle = this.mob.tickCount * ORBIT_SPEED;
        double x = target.getX() + Math.cos(angle) * config.bossOrbitRadius;
        double z = target.getZ() + Math.sin(angle) * config.bossOrbitRadius;
        this.mob.flyTowards(x, target.getY() + config.bossOrbitHeight, z, config.bossOrbitSpeed);

        if (this.phaseTicks >= config.bossOrbitTicks) {
            this.enterSpecial();
        }
    }

    private void enterSpecial() {
        this.phase = PHASE_SPECIAL;
        this.phaseTicks = 0;
        this.shotsFired = 0;
        this.special = this.mob.getRandom().nextInt(SPECIAL_COUNT);
        switch (this.special) {
            case SPECIAL_DRAGON_BREATH -> {
                this.mob.setBossAnimation(BossLightningCreeperPhantomKnightEntity.ANIM_DRAGON_BREATH);
                this.mob.playSound(SoundEvents.ENDER_DRAGON_SHOOT, 4.0F, 0.8F);
            }
            case SPECIAL_DARKNESS -> {
                this.mob.setBossAnimation(BossLightningCreeperPhantomKnightEntity.ANIM_DARKNESS);
                this.mob.playSound(SoundEvents.WARDEN_ROAR, 4.0F, 0.8F);
            }
            default -> this.mob.setBossAnimation(BossLightningCreeperPhantomKnightEntity.ANIM_SALVO);
        }
    }

    private void tickSpecial(LivingEntity target, ForbiddenChimeraConfig config) {
        // Hover in place while the attack plays out.
        this.mob.flyTowards(this.mob.getX(), this.mob.getY(), this.mob.getZ(), 0.0D);

        switch (this.special) {
            case SPECIAL_DRAGON_BREATH -> this.tickDragonBreath(target, config);
            case SPECIAL_DARKNESS -> this.tickDarkness(config);
            default -> this.tickSalvo(target, config);
        }
    }

    /** 连发: one 凋灵之手 per interval, starting after the wind up. */
    private void tickSalvo(LivingEntity target, ForbiddenChimeraConfig config) {
        if (this.shotsFired >= config.bossSalvoCount) {
            this.enterRocket();
            return;
        }
        int due = SALVO_FIRST_TICK + this.shotsFired * config.bossSalvoIntervalTicks;
        if (this.phaseTicks >= due) {
            // The alternating left/right head pattern lives on the entity, so a burst that is spread over
            // several phases still walks the six heads in order instead of restarting.
            this.mob.fireWitherVolleyShot(target);
            this.shotsFired++;
        }
        if (this.phaseTicks > SALVO_TICKS) {
            this.enterRocket();
        }
    }

    /** Dragon fireballs only while the modelled jaws are open. */
    private void tickDragonBreath(LivingEntity target, ForbiddenChimeraConfig config) {
        if (this.shotsFired < config.bossDragonBreathCount) {
            int due = config.bossDragonBreathFirstTick
                    + this.shotsFired * config.bossDragonBreathIntervalTicks;
            if (this.phaseTicks >= due) {
                this.mob.fireDragonBreath(target);
                this.shotsFired++;
            }
        }
        if (this.phaseTicks > DRAGON_BREATH_TICKS) {
            this.enterRocket();
        }
    }

    private void tickDarkness(ForbiddenChimeraConfig config) {
        if (this.phaseTicks == DARKNESS_PULSE_TICK) {
            this.mob.pulseDarkness();
        }
        if (this.phaseTicks > DARKNESS_TICKS) {
            this.enterRocket();
        }
    }

    private void enterRocket() {
        this.phase = PHASE_ROCKET;
        this.phaseTicks = 0;
        this.mob.setBossAnimation(BossLightningCreeperPhantomKnightEntity.ANIM_ROCKET_BOOST);
        this.mob.setBossDiving(true);
        this.mob.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 3.0F, 0.7F);
    }

    private void tickRocket(LivingEntity target, ForbiddenChimeraConfig config) {
        this.mob.rocketBurnTick();
        this.mob.flyTowards(target.getX(), target.getY(0.5D), target.getZ(),
                config.bossOrbitSpeed * config.bossRocketBoostMultiplier);
        if (this.phaseTicks >= config.bossRocketBoostTicks) {
            this.phase = PHASE_DIVE;
            this.phaseTicks = 0;
            this.lastDivePosition = null;
            this.mob.setBossAnimation(BossLightningCreeperPhantomKnightEntity.ANIM_DIVE);
        }
    }

    private void tickDive(LivingEntity target, ForbiddenChimeraConfig config) {
        this.mob.flyTowards(target.getX(), target.getY(0.5D), target.getZ(), config.bossDiveSpeed);
        if (!(this.mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Swept hit test: the boss closes at bossDiveSpeed (2.0 blocks/tick by default) while the target
        // is only ~0.6 wide, so a plain "do the two boxes overlap right now" test can be stepped straight
        // over between two ticks - which is exactly the "the dive goes past me, then it has to turn
        // around and come back to connect" report. Testing the segment travelled since the previous tick
        // instead of a single point cannot be tunnelled through.
        Vec3 from = this.lastDivePosition != null ? this.lastDivePosition : this.mob.position();
        Vec3 to = this.mob.position();
        this.lastDivePosition = to;

        if (new AABB(from, to).inflate(HIT_INFLATION).intersects(target.getBoundingBox())) {
            // The rider delivers the hit through the vanilla weapon pipeline (trident included); if it
            // is already dead the boss still slams.
            if (!this.mob.riderStrike(serverLevel, target)) {
                this.mob.doHurtTarget(serverLevel, target);
            }
            this.mob.playSound(SoundEvents.PHANTOM_SWOOP, 2.0F, 0.6F);
            this.enterPassBy();
            return;
        }
        if (this.phaseTicks > config.bossMaxDiveTicks) {
            this.enterPassBy();
        }
    }

    private void enterPassBy() {
        this.phase = PHASE_PASS_BY;
        this.phaseTicks = 0;
        this.mob.setBossDiving(false);
    }

    private void tickPassBy(LivingEntity target, ForbiddenChimeraConfig config) {
        Vec3 away = this.mob.position().subtract(target.position());
        Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
        if (horizontal.lengthSqr() < 1.0E-4D) {
            horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        }
        Vec3 destination = this.mob.position().add(horizontal.normalize().scale(PASS_BY_DISTANCE));
        this.mob.flyTowards(destination.x, destination.y, destination.z, config.bossDiveSpeed);
        if (this.phaseTicks > PASS_BY_TICKS) {
            this.enterSonicBoom(target);
        }
    }

    private void enterSonicBoom(LivingEntity target) {
        this.phase = PHASE_SONIC_BOOM;
        this.phaseTicks = 0;
        this.sonicBoomFired = false;
        if (this.mob.inSonicBoomRange(target)) {
            this.mob.setBossAnimation(BossLightningCreeperPhantomKnightEntity.ANIM_SONIC_BOOM);
            this.mob.sonicBoomCharge();
        } else {
            // Out of the warden's reach: skip the clip rather than scream into the void.
            this.enterOrbit();
        }
    }

    private void tickSonicBoom(LivingEntity target, ForbiddenChimeraConfig config) {
        this.mob.flyTowards(this.mob.getX(), this.mob.getY(), this.mob.getZ(), 0.0D);
        if (!this.sonicBoomFired && this.phaseTicks >= config.bossSonicBoomTick) {
            this.sonicBoomFired = true;
            this.mob.sonicBoom(target);
        }
        if (this.phaseTicks > SONIC_BOOM_TICKS) {
            this.enterOrbit();
        }
    }
}
