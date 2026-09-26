package com.huziyang520.forbiddenchimera.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.huziyang520.forbiddenchimera.ai.BossAttackGoal;
import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import com.huziyang520.forbiddenchimera.world.PhantomRider;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 首领 · 闪电苦力怕幻翼骑士 - the boss.
 *
 * <p>Everything the smaller chimeras are, plus a head cluster: three wither heads per wing, a warden
 * head between two ender dragon heads, and firework rockets on its back. It is <b>never</b> part of
 * the natural spawn table - it exists only where the 禁忌板材 item is used.
 *
 * <p>Behaviour lives in {@link BossAttackGoal}; this class owns the parts that are not movement:
 * <ul>
 *   <li><b>animation state</b> - a synced id drives which of the seven clips plays, so both sides always
 *       agree (same approach as the nuclear variant);</li>
 *   <li><b>闪电苦力怕头颅 side arm</b> - the ordinary ranged attack: one lightning creeper skull every
 *       {@code bossSideArmCooldownTicks}, fired from the entity tick while it is not diving. This is what
 *       the player sees most of the time;</li>
 *   <li><b>凋灵之手 salvo</b> - the burst, driven by {@link BossAttackGoal}'s special phase, alternating
 *       left/right wing and then the three heads of that side;</li>
 *   <li><b>rider</b> - a vanilla baby zombie with a trident, maintained through {@link PhantomRider}
 *       exactly like the knight variant's mace rider;</li>
 *   <li><b>explosion resistance</b> - 90% off every explosion damage source, vanilla ones included.</li>
 * </ul>
 *
 * <p>Getting here at all takes a dragon head, a nether star's worth of rarity and a warden's worth of
 * noise; it is not meant to be a fair fight.
 */
public class BossLightningCreeperPhantomKnightEntity extends LightningCreeperPhantomEntity {

    // --- animation states, matching boss_lightning_creeper_phantom_knight.animation.json ---
    public static final int ANIM_FLY = 0;
    public static final int ANIM_DIVE = 1;
    public static final int ANIM_SALVO = 2;
    public static final int ANIM_DRAGON_BREATH = 3;
    public static final int ANIM_SONIC_BOOM = 4;
    public static final int ANIM_DARKNESS = 5;
    public static final int ANIM_ROCKET_BOOST = 6;

    private static final String PREFIX = "animation.boss_lightning_creeper_phantom_knight.";
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop(PREFIX + "fly");
    private static final RawAnimation DIVE = RawAnimation.begin().thenLoop(PREFIX + "dive");
    private static final RawAnimation SALVO = RawAnimation.begin().thenPlayAndHold(PREFIX + "salvo");
    private static final RawAnimation DRAGON_BREATH = RawAnimation.begin().thenPlayAndHold(PREFIX + "dragon_breath");
    private static final RawAnimation SONIC_BOOM = RawAnimation.begin().thenPlayAndHold(PREFIX + "sonic_boom");
    private static final RawAnimation DARKNESS = RawAnimation.begin().thenPlayAndHold(PREFIX + "darkness");
    private static final RawAnimation ROCKET_BOOST = RawAnimation.begin().thenPlayAndHold(PREFIX + "rocket_boost");

    private static final EntityDataAccessor<Integer> DATA_BOSS_ANIMATION =
            SynchedEntityData.defineId(BossLightningCreeperPhantomKnightEntity.class,
                    EntityDataSerializers.INT);

    /** Vanilla warden sonic boom numbers, from {@code SonicBoom}: 10 damage, 0.5/2.5 knockback. */
    private static final float SONIC_BOOM_DAMAGE = 10.0F;
    private static final double SONIC_BOOM_KNOCKBACK_VERTICAL = 0.5D;
    private static final double SONIC_BOOM_KNOCKBACK_HORIZONTAL = 2.5D;
    /**
     * How far the parting scream reaches. Wider than the warden's own {@code SonicBoom#DISTANCE_XZ}
     * (15) / {@code DISTANCE_Y} (20) on 2026-09-26 by user request: at the warden's numbers the boss
     * usually drifted out of range while lining up the pass-by, so the move was skipped and the whole
     * sonic boom clip never played. The damage/knockback numbers themselves are still the warden's.
     */
    private static final double SONIC_BOOM_RANGE = 24.0D;
    private static final double SONIC_BOOM_RANGE_Y = 32.0D;

    private final AnimationController<BossLightningCreeperPhantomKnightEntity> mainController;

    /** Ticks left before the next lightning skull of the side arm. Lives on the entity, never freezes. */
    private int sideArmCooldown;
    /** Which wing the next volley comes from: 0 = left, 1 = right. Alternates. */
    private int witherSide;
    /** Which of that wing's three heads fires next. */
    private int witherHead;
    /** True while the dive is running; the volley is suppressed so the charge is readable. */
    private boolean bossDiving;
    /** Whether the one time rider hand out has already happened. See the first tick in {@link #tick}. */
    private boolean riderInitialised;

    // --- 高低差脱困 (stuck on a ledge) --------------------------------------
    /** 观察窗口起点的位置；与 {@link #stuckTicks} 一起判断"有没有真的挪动"。 */
    private Vec3 stuckAnchor = Vec3.ZERO;
    /** 当前窗口里"几乎没挪动"的 tick 数。 */
    private int stuckTicks;
    /** 连续这么多 tick 没有实质位移，才认为是被高低差卡住。 */
    private static final int STUCK_TICKS = 20;
    /** 窗口内位移小于这个平方距离（0.3 格）就算"没挪动"。 */
    private static final double STUCK_PROGRESS_SQR = 0.09D;
    /** 脱困时朝上补的速度，够抬过一格台阶即可。 */
    private static final double STUCK_LIFT = 0.45D;

    public BossLightningCreeperPhantomKnightEntity(
            EntityType<? extends BossLightningCreeperPhantomKnightEntity> type, Level level) {
        super(type, level);
        this.xpReward = 100;
        this.mainController = new AnimationController<>("main", 4, this::selectAnimation);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BOSS_ANIMATION, ANIM_FLY);
    }

    // --- animation ----------------------------------------------------------

    /** Replaces the inherited controller: the boss has its own seven clip set. */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(this.mainController);
    }

    private PlayState selectAnimation(AnimationTest<BossLightningCreeperPhantomKnightEntity> state) {
        BossLightningCreeperPhantomKnightEntity entity = state.animatable();
        return switch (entity.bossAnimation()) {
            case ANIM_DIVE -> state.setAndContinue(DIVE);
            case ANIM_SALVO -> state.setAndContinue(SALVO);
            case ANIM_DRAGON_BREATH -> state.setAndContinue(DRAGON_BREATH);
            case ANIM_SONIC_BOOM -> state.setAndContinue(SONIC_BOOM);
            case ANIM_DARKNESS -> state.setAndContinue(DARKNESS);
            case ANIM_ROCKET_BOOST -> state.setAndContinue(ROCKET_BOOST);
            default -> state.setAndContinue(FLY);
        };
    }

    /** Synced animation id, see the {@code ANIM_*} constants. */
    public int bossAnimation() {
        return this.getEntityData().get(DATA_BOSS_ANIMATION);
    }

    public void setBossAnimation(int animation) {
        this.getEntityData().set(DATA_BOSS_ANIMATION, animation);
    }

    // --- attack goal hookup -------------------------------------------------

    /** The boss replaces the plain dive entirely: {@link BossAttackGoal} runs the whole cycle. */
    @Override
    protected Goal createAttackGoal() {
        return new BossAttackGoal(this);
    }

    /** The spear variant is one of the small divers' quirks; the boss never carries one. */
    @Override
    protected boolean supportsSpearVariant() {
        return false;
    }

    /**
     * Explosion resistance: every explosion hurts the boss at {@code bossExplosionDamageFactor} of its
     * normal damage. Scaling the incoming damage here - rather than making the entity invulnerable -
     * is what makes <b>vanilla</b> explosions (TNT, creepers, beds) obey the same 90% reduction as this
     * mod's own blasts.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            damage *= (float) ForbiddenChimeraConfig.get().bossExplosionDamageFactor;
        }
        return super.hurtServer(level, source, damage);
    }

    // --- rider --------------------------------------------------------------

    /** Set by the attack goal so the wither volley does not fire during the charge. */
    public boolean isBossDiving() {
        return this.bossDiving;
    }

    public void setBossDiving(boolean diving) {
        this.bossDiving = diving;
    }

    /**
     * Keeps the boss's trident rider alive and anchored. Same lifecycle rules as the knight variant: the
     * rider is adopted if it already exists (never duplicated across a reload) and is <b>not</b> replaced
     * once lost, because "whichever of the two dies, the other carries on".
     */
    private void tickRider(ServerLevel level) {
        Zombie current = this.knightRider();
        if (current == null) {
            this.setRider(null);
            // A reload loses the field, not the entity: the rider comes back as our passenger, so
            // re-adopt it. Without this the restored zombie would ride along as a stranger we could no
            // longer release when we die.
            if (this.riderInitialised) {
                this.setRider(PhantomRider.findOwned(this));
            }
            return;
        }
        PhantomRider.anchor(this, current);
    }

    /**
     * Creates the rider if this boss does not have one yet.
     *
     * <p>Runs on the first server tick rather than at summon time, so a {@code /summon}ed boss (which
     * never goes through the item) gets one too - and a boss loaded from a save adopts the rider that is
     * already tagged to it instead of spawning a second.
     */
    private void ensureRider(ServerLevel level) {
        if (this.knightRider() == null) {
            this.setRider(PhantomRider.adoptOrSpawn(level, this, Items.TRIDENT));
        }
    }

    /**
     * NBT key marking that this individual has already handed out its one rider.
     *
     * <p>Why it has to be persisted: {@code riderInitialised} is a plain field, so any reload resets it.
     * A player dying and respawning unloads and reloads the chunks around the boss, and if the rider had
     * died in the meantime (the boss's own skulls are perfectly capable of killing it) the next load
     * handed out a <b>brand new</b> rider - the "the baby zombie regenerates, seems tied to respawning"
     * report. One boss, one rider, for its whole life; see D22's "whichever of the two dies, the other
     * carries on".
     */
    private static final String RIDER_GIVEN_KEY = "ForbiddenChimeraRiderGiven";

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean(RIDER_GIVEN_KEY, this.riderInitialised);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.riderInitialised = input.getBooleanOr(RIDER_GIVEN_KEY, false);
    }

    /**
     * The rider delivers a dive hit, with the vanilla weapon pipeline doing the maths: the trident's
     * own damage source and attribute bonus apply, exactly as if a mob had swung it.
     */
    public boolean riderStrike(ServerLevel level, LivingEntity target) {
        Zombie current = this.knightRider();
        if (current == null) {
            return false;
        }
        return current.doHurtTarget(level, target);
    }

    // --- wither skull volley ------------------------------------------------

    /**
     * Fires one 凋灵之手 (wither skull) from the next head of the alternating pattern.
     *
     * <p>This is the <b>burst</b> attack: the cadence between two shots belongs to
     * {@link BossAttackGoal}'s special phase, so no cooldown is touched here. The alternating side/head
     * counters live on the entity because they have to survive a phase change.
     *
     * <p>Muzzle position: the per-head bones exist in the model ({@code boss_wither_skull_left_1..3} and
     * the right trio), but a bone pivot is model space - turning it into a world position needs the full
     * render transform, and this project's rule is to measure that in game rather than copy the modelled
     * draft offsets. Until that calibration is done the skulls leave from the body centre; the side and
     * head counters below already encode the alternating pattern, so only this method has to change.
     */
    public void fireWitherVolleyShot(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 from = new Vec3(this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ());
        Vec3 direction = target.getEyePosition().subtract(from);
        if (direction.lengthSqr() < 1.0E-4D) {
            return;
        }

        // A plain skull: `WitherSkull`'s dangerous flag defaults to false, so this is the blue,
        // non terrain destroying variant, which is what a volley should be.
        WitherSkull skull = new WitherSkull(serverLevel, this, direction.normalize());
        serverLevel.addFreshEntity(skull);

        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WITHER_SHOOT,
                SoundSource.HOSTILE, 3.0F, 0.8F);

        this.witherHead++;
        if (this.witherHead >= WITHER_HEADS_PER_WING) {
            this.witherHead = 0;
            this.witherSide = 1 - this.witherSide;
        }
    }

    /** Three wither heads per wing, matching the model. */
    private static final int WITHER_HEADS_PER_WING = 3;

    // --- ranged attacks -----------------------------------------------------

    /** Aim jitter, in blocks, of an ordinary side arm shot. */
    private static final double SIDE_ARM_SPREAD = 0.16D;

    /**
     * 平时（非俯冲）的常驻射击：一发闪电苦力怕头颅。
     *
     * <p>用继承来的 {@link #newSkull} 工厂，因此它发射的就是 mob4 那只
     * {@code LightningCreeperSkullProjectile}（含带电叠加层），不是新的弹射物类型。
     */
    public void fireSideArmSkull(LivingEntity target) {
        this.fireLightningSkull(target, SIDE_ARM_SPREAD);
        this.sideArmCooldown = ForbiddenChimeraConfig.get().bossSideArmCooldownTicks;
    }

    /**
     * One lightning creeper skull.
     *
     * <p>Uses the inherited {@link #newSkull} factory, so these are exactly the same
     * {@code LightningCreeperSkullProjectile} mob4 fires - including the charged overlay - rather than a
     * new projectile type.
     *
     * @param spread aim jitter in blocks; 0 fires dead centre.
     */
    public void fireLightningSkull(LivingEntity target, double spread) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 from = new Vec3(this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ());
        Vec3 aim = target.getEyePosition().subtract(from);
        if (aim.lengthSqr() < 1.0E-4D) {
            return;
        }
        Vec3 direction = aim.normalize();
        if (spread > 0.0D) {
            direction = direction.add(
                    (this.random.nextDouble() - 0.5D) * spread,
                    (this.random.nextDouble() - 0.5D) * spread,
                    (this.random.nextDouble() - 0.5D) * spread).normalize();
        }
        serverLevel.addFreshEntity(this.newSkull(serverLevel, direction));
    }

    /**
     * One dragon fireball, straight from the head cluster.
     *
     * <p>Vanilla projectiles again: {@code DragonFireball} carries its own breath cloud, so nothing has
     * to be reimplemented. The timeline that gates these shots to the open-jaw window lives in
     * {@link BossAttackGoal}.
     */
    public void fireDragonBreath(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 from = this.getEyePosition();
        Vec3 direction = target.getEyePosition().subtract(from);
        if (direction.lengthSqr() < 1.0E-4D) {
            return;
        }
        serverLevel.addFreshEntity(new DragonFireball(serverLevel, this, direction.normalize()));
    }

    // --- sonic boom ---------------------------------------------------------

    /**
     * The warden's sonic boom, reimplemented from {@code SonicBoom} with its numbers copied verbatim:
     * a line of {@code SONIC_BOOM} particles from the chest to the target, the warden's boom sound,
     * 10 damage through {@code DamageSources#sonicBoom}, and the warden's knockback on top.
     *
     * <p>Only the source point differs: the warden reads a chest attachment that this entity type does
     * not have, so the boss fires from its head height instead.
     */
    public void sonicBoom(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 source = this.getEyePosition();
        Vec3 delta = target.getEyePosition().subtract(source);
        if (delta.lengthSqr() < 1.0E-4D) {
            return;
        }
        Vec3 direction = delta.normalize();

        int steps = (int) Math.floor(delta.length()) + 7;
        for (int i = 1; i < steps; i++) {
            Vec3 particlePos = source.add(direction.scale(i));
            serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, particlePos.x, particlePos.y,
                    particlePos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.HOSTILE, 3.0F, 1.0F);
        if (target.hurtServer(serverLevel, serverLevel.damageSources().sonicBoom(this), SONIC_BOOM_DAMAGE)) {
            double resistance = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            double vertical = SONIC_BOOM_KNOCKBACK_VERTICAL * (1.0D - resistance);
            double horizontal = SONIC_BOOM_KNOCKBACK_HORIZONTAL * (1.0D - resistance);
            target.push(direction.x() * horizontal, direction.y() * vertical, direction.z() * horizontal);
        }
    }

    /** Charge-up sound and pose, played at the start of the sonic boom clip. */
    public void sonicBoomCharge() {
        this.playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3.0F, 1.0F);
    }

    /** Whether the target is inside the warden's own sonic boom reach. */
    public boolean inSonicBoomRange(LivingEntity target) {
        return this.closerThan(target, SONIC_BOOM_RANGE, SONIC_BOOM_RANGE_Y);
    }

    // --- darkness -----------------------------------------------------------

    /** Applies darkness to every player around the boss; the boss's only non damaging attack. */
    public void pulseDarkness() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();
        double radius = config.bossDarknessRadius;
        List<ServerPlayer> players = serverLevel.getEntitiesOfClass(ServerPlayer.class,
                new AABB(this.getX() - radius, this.getY() - radius, this.getZ() - radius,
                        this.getX() + radius, this.getY() + radius, this.getZ() + radius));
        for (ServerPlayer player : players) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,
                    config.bossDarknessDurationTicks, 0), this);
        }
    }

    // --- firework rockets ---------------------------------------------------

    /**
     * The rocket burn: vanilla firework particles out of the tail plus the vanilla launch sound. Purely
     * a look and an acceleration cue - it changes no damage numbers.
     */
    public void rocketBurnTick() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 back = this.getLookAngle().scale(-1.0D);
        for (int i = 0; i < 2; i++) {
            serverLevel.sendParticles(ParticleTypes.FIREWORK,
                    this.getX() + back.x * 1.5D, this.getY() + 0.4D, this.getZ() + back.z * 1.5D,
                    1, 0.05D, 0.05D, 0.05D, 0.02D);
        }
        if (this.tickCount % 8 == 0) {
            this.playSound(SoundEvents.FIREWORK_ROCKET_LAUNCH, 2.0F, 1.0F);
        }
    }

    // --- tick ---------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!this.riderInitialised) {
            this.riderInitialised = true;
            this.ensureRider(serverLevel);
        }
        this.tickRider(serverLevel);

        if (this.sideArmCooldown > 0) {
            this.sideArmCooldown--;
        }
        LivingEntity target = this.getTarget();
        if (target != null && !this.bossDiving && this.sideArmCooldown <= 0) {
            // 平时（俯冲之外）就是这一发：闪电苦力怕头颅。凋灵之手是连发，见 BossAttackGoal。
            this.fireSideArmSkull(target);
        }
    }

    /**
     * 首领飞行时偶尔会顶在高低差的方块边上（"卡住"）。
     *
     * <p>原版的飞行移动控制只管"想去哪"，撞到地形不会自己绕开，所以这里做一个很小的兜底：只要它
     * <b>确实在赶路</b>（目标点够远且速度不为 0）却连续 {@link #STUCK_TICKS} 个 tick 几乎没有位移，
     * 就朝上补一次速度，让它抬一档越过台阶。原地悬停的相位（目标点就是自己）不会触发。
     */
    @Override
    public void flyTowards(double x, double y, double z, double speed) {
        super.flyTowards(x, y, z, speed);

        double dx = x - this.getX();
        double dz = z - this.getZ();
        boolean travelling = speed > 0.0D && dx * dx + dz * dz > 4.0D;
        if (!travelling) {
            this.stuckTicks = 0;
            this.stuckAnchor = this.position();
            return;
        }

        if (++this.stuckTicks >= STUCK_TICKS) {
            if (this.position().distanceToSqr(this.stuckAnchor) < STUCK_PROGRESS_SQR) {
                this.push(0.0D, STUCK_LIFT, 0.0D);
            }
            this.stuckTicks = 0;
            this.stuckAnchor = this.position();
        }
    }

    /** Reuses the boss's own voice set instead of the phantom's squeak. */
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    /**
     * Releases the rider when the boss dies, exactly like the knight variant: the baby zombie drops off
     * as an ordinary mob rather than vanishing with its mount.
     */
    @Override
    public void die(DamageSource source) {
        if (!this.level().isClientSide()) {
            Zombie current = this.rider;
            if (current != null && current.isAlive()) {
                PhantomRider.release(current);
            }
            this.rider = null;
        }
        super.die(source);
    }

    /** The boss never rolls the small knight variant: it always carries its own trident rider. */
    @Override
    protected boolean supportsKnightVariant() {
        return false;
    }
}
