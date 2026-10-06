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
    /**
     * 宽限判断最多把 orbit 延长这么多 tick。
     *
     * <p>存在的理由：{@code sideArmAge} 会被下一发侧臂射击清零，所以当
     * {@code bossSideArmDiveGraceTicks} 大于 {@code bossSideArmCooldownTicks}（或者配成了负数以外的
     * 极端值）时，"等年龄涨到宽限值"这件事永远不会发生。没有这个上限，首领就会永远停在 orbit，
     * 火箭/俯冲/音波整条链都进不去。超时后照常放行，宽限只是"尽量等"，不是"必须等到"。
     */
    private static final int GRACE_MAX_DEFER_TICKS = 60;

    private final BossLightningCreeperPhantomKnightEntity mob;
    private int phase = PHASE_ORBIT;
    private int phaseTicks;
    private int special = SPECIAL_SALVO;
    /** How many shots of the current special have already gone out. */
    private int shotsFired;
    /** True once the sonic boom of this phase has gone off. */
    private boolean sonicBoomFired;
    /**
     * 侧臂射击宽限已经连续推迟了多少 tick。
     *
     * <p>用 {@link #GRACE_MAX_DEFER_TICKS} 兜底，保证"等宽限"永远不会变成死循环：只要
     * {@code bossSideArmDiveGraceTicks} 被配成大于 {@code bossSideArmCooldownTicks}，
     * {@code sideArmAge} 就会在到达宽限值之前被下一发侧臂射击清零，单靠年龄判断永远等不到。
     */
    private int graceDeferTicks;
    /**
     * The orbit's zero point, re-anchored every time the boss returns to orbiting (see {@link #enterOrbit}).
     *
     * <p>The angle used to come from {@code mob.tickCount}, which is a plain field that is <b>not</b>
     * persisted - a chunk reload restarted it at 0, so the boss snapped to a different bearing the moment
     * the player came back. Anchored here, the orbit always resumes from where the boss actually is.
     */
    private double orbitAngle;
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
        this.graceDeferTicks = 0;
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
        // Anchor the orbit to where the boss already is, exactly like the nuclear chimera's circling:
        // the angle used to be read off mob.tickCount, which is not persisted, so a chunk reload restarted
        // the clock at 0 and the boss visibly jumped to a different bearing mid-orbit.
        this.orbitAngle = this.bearingToTarget();
        this.mob.setBossAnimation(BossLightningCreeperPhantomKnightEntity.ANIM_FLY);
        this.mob.setBossDiving(false);
    }

    /** The azimuth the boss currently sits at, seen from its target; 0 when it has none. */
    private double bearingToTarget() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return 0.0D;
        }
        return Math.atan2(this.mob.getZ() - target.getZ(), this.mob.getX() - target.getX());
    }

    private void tickOrbit(LivingEntity target, ForbiddenChimeraConfig config) {
        double angle = this.orbitAngle + this.phaseTicks * ORBIT_SPEED;
        double x = target.getX() + Math.cos(angle) * config.bossOrbitRadius;
        double z = target.getZ() + Math.sin(angle) * config.bossOrbitRadius;
        this.mob.flyTowards(x, target.getY() + config.bossOrbitHeight, z, config.bossOrbitSpeed);

        if (this.phaseTicks >= config.bossOrbitTicks) {
            // 宽限：刚打完侧臂那一发就贴脸冲锋，会把自己打出去的头颅炸到自己身上。
            if (this.deferForSideArmGrace(config)) {
                return;
            }
            this.enterSpecial();
        }
    }

    /**
     * 侧臂射击宽限判断：距上一次侧臂射击不足 {@code bossSideArmDiveGraceTicks} 时，是否应该推迟进入
     * 特殊技 / 火箭（也就是推迟冲锋）。
     *
     * <p>放在这里的理由：火箭和俯冲是同一个冲锋动作的两段，{@code enterRocket()} 是它们唯一的入口，
     * 而特殊技是冲锋前的最后一段滞空，所以这两个入口各查一次就覆盖了整条链。
     *
     * <p>不会变成死循环：{@code sideArmAge} 会被下一发侧臂射击清零，当宽限值大于侧臂间隔时永远等不到，
     * 所以推迟次数由 {@link #GRACE_MAX_DEFER_TICKS} 封顶，超限就直接放行。计数只在真正放行时清零，
     * 所以跨阶段（orbit → special → rocket）也是同一个预算，整体有界。
     *
     * @return true 表示这次应该继续留在当前阶段，false 表示宽限已满足（或已等够），可以往下走
     */
    private boolean deferForSideArmGrace(ForbiddenChimeraConfig config) {
        if (this.mob.sideArmAge() >= config.bossSideArmDiveGraceTicks) {
            this.graceDeferTicks = 0;
            return false;
        }
        if (this.graceDeferTicks >= GRACE_MAX_DEFER_TICKS) {
            // 等够了：宽限只是"尽量等"，不是"必须等到"，否则整条链都进不去。
            this.graceDeferTicks = 0;
            return false;
        }
        this.graceDeferTicks++;
        return true;
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
        // 冲锋前的最后一道宽限：侧臂刚射完就加速俯冲，等于往自己头颅的爆炸范围里扎。
        if (this.deferForSideArmGrace(ForbiddenChimeraConfig.get())) {
            // 继续悬停，等下一轮 tickSpecial 再试；阶段与动画都不变，所以看不出卡顿。
            return;
        }
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
