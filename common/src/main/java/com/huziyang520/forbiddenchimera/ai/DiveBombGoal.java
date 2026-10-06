package com.huziyang520.forbiddenchimera.ai;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import com.huziyang520.forbiddenchimera.entity.CreeperPhantomEntity;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Three phase attack used by the Creeper Phantom (mob3) and the Lightning Creeper Phantom (mob4):
 * take up position <b>diagonally above and away</b> from the target, fire a short salvo from there,
 * then dive in and pass by.
 *
 * <p>Design notes, all player-facing difficulty decisions:
 * <ul>
 *   <li>the attack position is {@code diverApproachOffset} blocks away horizontally and
 *       {@code diverClimbHeight} above the target, so the salvo - and the dive that follows it - comes
 *       in diagonally instead of from straight overhead;</li>
 *   <li>skulls are fired <b>only during the salvo phase</b>, from that same attack position, and only
 *       while still at least {@code diverFireMinDistance} away (horizontal distance). Shots from
 *       directly above the player are almost impossible to dodge, which made the chimera feel unfair;</li>
 *   <li>the dive itself <b>never fires</b>. It used to shoot on the way in, so the phantom flew
 *       straight into the blast of the skull it had just launched and blew itself up ("每次发射头颅之后
 *       马上冲向玩家，结果把自己炸死"). Firing first and charging afterwards keeps both halves of the
 *       attack readable, and the salvo distance is the existing approach distance, not a new one;</li>
 *   <li>the salvo is bounded by {@code diverSalvoCount} shots and by a timeout, so a target that stays
 *       too close to shoot at (or a mob that cannot line up) still gets charged instead of hovering
 *       forever;</li>
 *   <li>the approach point is recomputed whenever a run starts, so it stays behind wherever the
 *       player is moving to.</li>
 * </ul>
 *
 * <p>The dive cooldown lives on the entity ({@link CreeperPhantomEntity#attackCooldown()}), not in a
 * goal field: goals stop ticking the moment they lose their target, and a frozen cooldown means the
 * mob never attacks again.
 */
public class DiveBombGoal extends Goal {

    private static final int PHASE_CLIMB = 0;
    private static final int PHASE_DIVE = 1;
    /** 俯冲之前的前置开火阶段：在就位点悬停，按间隔打出若干颗头颅，打完才冲锋。 */
    private static final int PHASE_SALVO = 2;

    /** Horizontal distance past the closest approach that counts as "flown past the player", in blocks. */
    private static final double PASS_BY_OVERSHOOT = 3.0D;
    /** 开火阶段在预期耗时之外额外允许的等待 tick 数，避免距离/冷却不满足时卡死。 */
    private static final int SALVO_TIMEOUT_SLACK = 40;

    private final CreeperPhantomEntity mob;
    private int phase = PHASE_CLIMB;
    private int diveTicks;
    /** 开火阶段已经打出去的头颅数。 */
    private int salvoFired;
    /** 开火阶段已经过去的 tick 数，用于发射间隔与超时兜底。 */
    private int salvoTicks;
    /**
     * 本轮是否真的打出过头颅。
     *
     * <p>原来叫 {@code firedThisDive}，语义没变，只是开火从俯冲挪到了开火阶段：它仍然负责把"打完了
     * 才收手"和"纯粹扑空"两种情况分开——{@code diverPassByTicks} 与越肩判定只对前者生效，所以不会
     * 发头颅的末影幻翼（它整轮都不会置位）依旧只受 {@code diverMaxDiveTicks} 约束。
     */
    private boolean firedThisRun;
    /** Closest horizontal distance reached during the current dive, used to detect the pass-by. */
    private double closestApproach = Double.MAX_VALUE;
    /** Where this dive run starts from: diagonally above and away from the target. */
    private Vec3 approachPoint = Vec3.ZERO;

    public DiveBombGoal(CreeperPhantomEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.attackCooldown() > 0) {
            return false;
        }
        return this.mob.isAlive() && this.mob.getTarget() != null;
    }

    @Override
    public boolean canContinueToUse() {
        // Deliberately not canUse(): the cooldown must only gate new runs, not interrupt a running one.
        return this.mob.isAlive() && this.mob.getTarget() != null;
    }

    @Override
    public void start() {
        this.phase = PHASE_CLIMB;
        this.salvoFired = 0;
        this.salvoTicks = 0;
        this.firedThisRun = false;
        this.mob.setDiving(false);
        this.pickApproachPoint();
    }

    @Override
    public void stop() {
        this.phase = PHASE_CLIMB;
        this.diveTicks = 0;
        this.salvoFired = 0;
        this.salvoTicks = 0;
        this.firedThisRun = false;
        // Never leave the client stuck in the dive pose when the goal is abandoned.
        this.mob.setDiving(false);
    }

    /**
     * Chooses the point the phantom dives in from.
     *
     * <p>Direction is from the target towards the phantom's current position (horizontal only), so it
     * backs off along the line it is already coming from; if it is somehow exactly above the target, a
     * fixed direction is used so the dive is never perfectly vertical.
     */
    private void pickApproachPoint() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }
        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();

        Vec3 away = this.mob.position().subtract(target.position());
        double horizontal = Math.sqrt(away.x * away.x + away.z * away.z);
        double dirX;
        double dirZ;
        if (horizontal < 1.0E-3D) {
            dirX = 1.0D;
            dirZ = 0.0D;
        } else {
            dirX = away.x / horizontal;
            dirZ = away.z / horizontal;
        }

        this.approachPoint = new Vec3(
                target.getX() + dirX * config.diverApproachOffset,
                target.getY() + config.diverClimbHeight,
                target.getZ() + dirZ * config.diverApproachOffset);
    }

    @Override
    public void tick() {
        LivingEntity target = this.mob.getTarget();
        if (target == null) {
            return;
        }

        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();
        // Body and head both track the player, so it is always facing whoever it is about to shoot at.
        this.mob.getLookControl().setLookAt(target, 40.0F, 40.0F);

        if (this.phase == PHASE_CLIMB) {
            this.mob.flyTowards(this.approachPoint.x, this.approachPoint.y, this.approachPoint.z,
                    config.diverClimbSpeed);
            double dx = this.mob.getX() - this.approachPoint.x;
            double dz = this.mob.getZ() - this.approachPoint.z;
            boolean inPosition = dx * dx + dz * dz <= 4.0D
                    && this.mob.getY() >= this.approachPoint.y - 1.0D;
            if (inPosition) {
                this.beginSalvo();
            }
            return;
        }

        if (this.phase == PHASE_SALVO) {
            this.tickSalvo(target, config);
            return;
        }

        this.tickDive(target, config);
    }

    /**
     * 进入前置开火阶段。
     *
     * <p>不能发头颅的变体（末影幻翼，{@link CreeperPhantomEntity#canFireSkulls()} 为 false）直接跳过
     * 整个阶段去俯冲，否则它会在就位点悬停到超时，白白多出一段什么都不做的滞空。
     */
    private void beginSalvo() {
        if (!this.mob.canFireSkulls()) {
            this.beginDive();
            return;
        }
        this.phase = PHASE_SALVO;
        this.salvoFired = 0;
        this.salvoTicks = 0;
        this.firedThisRun = false;
        // 开火阶段不摆俯冲姿势：模型保持普通飞行，冲锋时才切过去。
        this.mob.setDiving(false);
    }

    /**
     * 在就位点悬停并按 {@code diverSalvoIntervalTicks} 的间隔打出 {@code diverSalvoCount} 颗头颅，
     * 打完（或超时）再进入俯冲。
     *
     * <p>站位就停在爬升阶段的 {@code approachPoint} 上，也就是水平 {@code diverApproachOffset} 格、
     * 高 {@code diverClimbHeight} 格处，没有新增硬编码距离。原地悬停（速度 0）是为了让每一发都从
     * 同一个斜上方角度出去，也让玩家有时间反应。
     *
     * <p>每一发都要过一遍原来的可用性判断（{@code canFireSkulls} / {@code canFire} / 距离），任何一条
     * 不满足就只是等；{@code salvoTicks} 的超时保证不会因为玩家贴脸站着而永久卡在开火阶段。
     */
    private void tickSalvo(LivingEntity target, ForbiddenChimeraConfig config) {
        this.salvoTicks++;
        // Hover in place at the attack position while the salvo plays out.
        this.mob.flyTowards(this.approachPoint.x, this.approachPoint.y, this.approachPoint.z, 0.0D);

        if (this.salvoFired >= config.diverSalvoCount) {
            this.beginDive();
            return;
        }

        int due = this.salvoFired * config.diverSalvoIntervalTicks;
        if (this.salvoTicks >= due && this.mob.canFire() && this.isFarEnoughToFire(target, config)) {
            this.mob.fireCreeperSkull(target);
            this.salvoFired++;
            this.firedThisRun = true;
        }

        // 超时兜底：两发之间的真实间隔是 max(开火阶段间隔, 原有的 diverFireCooldownTicks)——开火冷却
        // 一直有效，所以只要它比 salvo 间隔长，节奏就由它决定（默认 60 > 12）。按较慢的那个算预算，
        // 否则默认配置下会在第 2、3 发之前就被超时打断，"先打几轮"根本打不满。
        int perShot = Math.max(config.diverSalvoIntervalTicks, config.diverFireCooldownTicks);
        if (this.salvoTicks > config.diverSalvoCount * perShot + SALVO_TIMEOUT_SLACK) {
            this.beginDive();
        }
    }

    private void beginDive() {
        this.phase = PHASE_DIVE;
        this.diveTicks = 0;
        this.closestApproach = Double.MAX_VALUE;
        // Drives the dive pose on the model.
        this.mob.setDiving(true);
    }

    private void tickDive(LivingEntity target, ForbiddenChimeraConfig config) {
        this.diveTicks++;
        this.mob.flyTowards(target.getX(), target.getY(0.5D), target.getZ(), config.diverDiveSpeed);

        // 俯冲阶段不再发射：头颅已经在开火阶段打完，冲锋不会冲进自己刚打出去的爆炸里。

        if (this.mob.getBoundingBox().inflate(0.6D).intersects(target.getBoundingBox())) {
            if (this.mob.level() instanceof ServerLevel serverLevel) {
                // The entity decides how the contact hit resolves: plain melee, or the spear's own
                // kinetic weapon damage when this individual rolled the spear variant.
                this.mob.performDiveHit(serverLevel, target);
            }
            this.endRun(config);
            return;
        }

        double dx = this.mob.getX() - target.getX();
        double dz = this.mob.getZ() - target.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        this.closestApproach = Math.min(this.closestApproach, distance);

        // Flew past the player: it is now moving away again after having been closer than before.
        boolean passedBy = this.firedThisRun && distance > this.closestApproach + PASS_BY_OVERSHOOT;
        boolean spent = this.diveTicks > config.diverMaxDiveTicks
                || (this.firedThisRun && this.diveTicks > config.diverPassByTicks);
        if (passedBy || spent) {
            this.endRun(config);
        }
    }

    /** Ends the run and hands the cooldown to the entity, where it cannot freeze. */
    private void endRun(ForbiddenChimeraConfig config) {
        this.phase = PHASE_CLIMB;
        this.salvoFired = 0;
        this.salvoTicks = 0;
        this.firedThisRun = false;
        this.mob.setDiving(false);
        this.mob.setAttackCooldown(config.diverDiveCooldownTicks);
        this.pickApproachPoint();
    }

    /** @return whether the phantom is still far enough away that a shot can be dodged. */
    private boolean isFarEnoughToFire(LivingEntity target, ForbiddenChimeraConfig config) {
        double dx = this.mob.getX() - target.getX();
        double dz = this.mob.getZ() - target.getZ();
        double min = config.diverFireMinDistance;
        return dx * dx + dz * dz >= min * min;
    }
}
