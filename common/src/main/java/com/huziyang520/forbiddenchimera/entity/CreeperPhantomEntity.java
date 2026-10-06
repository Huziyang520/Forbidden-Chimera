package com.huziyang520.forbiddenchimera.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.instance.SingletonAnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.huziyang520.forbiddenchimera.ai.ChimeraPlayerTargetGoal;
import com.huziyang520.forbiddenchimera.ai.DiveBombGoal;
import com.huziyang520.forbiddenchimera.ai.RiderChargeAttackGoal;
import com.huziyang520.forbiddenchimera.ai.WanderFlyGoal;
import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import com.huziyang520.forbiddenchimera.registry.ModEntities;
import com.huziyang520.forbiddenchimera.world.PhantomRider;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Creeper Phantom - the original chimera. It climbs above its target, dives, and fires explosive
 * creeper skulls <b>only while diving</b>.
 *
 * <p>It is the only chimera with a hand made GeckoLib model, so it is also the only one implementing
 * {@link GeoEntity}. The played pose is chosen from synced flags rather than raw client state, so
 * both sides always agree on what the model is doing.
 */
public class CreeperPhantomEntity extends AbstractChimeraPhantom implements GeoEntity {

    /** Animation names, exactly as declared in creeper_phantom.animation.json. */
    private static final String ANIM_FLY = "animation.creeper_phantom.fly";
    private static final String ANIM_DIVE = "animation.creeper_phantom.dive";
    private static final String ANIM_FIRE = "animation.creeper_phantom.fire";

    private static final RawAnimation FLY = RawAnimation.begin().thenLoop(ANIM_FLY);
    private static final RawAnimation DIVE = RawAnimation.begin().thenLoop(ANIM_DIVE);
    private static final RawAnimation FIRE = RawAnimation.begin().thenPlayAndHold(ANIM_FIRE);

    /** How long a freshly fired skull keeps the recoil animation alive (0.35 s + slack). */
    private static final int FIRE_ANIMATION_TICKS = 8;

    private static final EntityDataAccessor<Boolean> DATA_DIVING =
            SynchedEntityData.defineId(CreeperPhantomEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_FIRING =
            SynchedEntityData.defineId(CreeperPhantomEntity.class, EntityDataSerializers.BOOLEAN);
    /**
     * The spear carried by the spear variant, or {@link ItemStack#EMPTY}. Synced because the client has
     * to render the actual stack (and therefore support resource packs that restyle it).
     */
    private static final EntityDataAccessor<ItemStack> DATA_SPEAR =
            SynchedEntityData.defineId(CreeperPhantomEntity.class, EntityDataSerializers.ITEM_STACK);

    /** The seven vanilla spears, weakest to strongest; the variant picks one of them at random. */
    private static final List<Item> SPEARS = List.of(
            Items.WOODEN_SPEAR, Items.STONE_SPEAR, Items.COPPER_SPEAR, Items.IRON_SPEAR,
            Items.GOLDEN_SPEAR, Items.DIAMOND_SPEAR, Items.NETHERITE_SPEAR);
    /** NBT key of the carried spear. Written and read as a plain {@code ItemStack} compound. */
    private static final String SPEAR_KEY = "ForbiddenChimeraSpear";
    /** NBT key of the knight variant flag. */
    private static final String KNIGHT_KEY = "ForbiddenChimeraKnight";
    /**
     * NBT key marking that this individual's variant has already been decided.
     *
     * <p>Why it is needed at all: {@code /summon} and {@code /data merge entity} both route through
     * {@code Entity#load} → {@code readAdditionalSaveData}, exactly like loading from a save, so "we are
     * being loaded" cannot be used to tell a fresh command summon from a reloaded mob. This explicit
     * marker is written by {@link #addAdditionalSaveData} on every save, so a reloaded chimera keeps its
     * variant while a freshly summoned one still gets its one random roll on the first tick.
     *
     * <p>It is also the documented way to summon a guaranteed <b>plain</b> chimera:
     * {@code /summon forbidden_chimera:creeper_phantom ~ ~1 ~ {ForbiddenChimeraVariantRolled:1b}}.
     */
    private static final String VARIANT_ROLLED_KEY = "ForbiddenChimeraVariantRolled";
    /** NBT key marking that this individual already handed out its knight rider once. */
    private static final String RIDER_ATTACHED_KEY = "ForbiddenChimeraRiderAttached";
    /**
     * NBT keys for the three cooldowns and the armed flag.
     *
     * <p>These are part of the save format: never rename them, or every loaded chimera forgets its
     * cooldowns (which is the bug they exist to fix) and dives the instant the chunk comes back.
     */
    private static final String FIRE_COOLDOWN_KEY = "ForbiddenChimeraFireCooldown";
    private static final String ATTACK_COOLDOWN_KEY = "ForbiddenChimeraAttackCooldown";
    private static final String CHARGE_COOLDOWN_KEY = "ForbiddenChimeraChargeCooldown";
    private static final String CHARGE_ARMED_KEY = "ForbiddenChimeraChargeArmed";
    /**
     * {@code KineticWeapon#damageEntities} scales every speed threshold by the attacker's "action
     * factor": 1.0 for players, 0.2 for everything else. A diving chimera is a mob, so it uses 0.2 -
     * the same value vanilla would apply, not a hand tuned number.
     */
    private static final double MOB_ACTION_FACTOR = 0.2D;

    private final AnimatableInstanceCache geoCache = new SingletonAnimatableInstanceCache(this);

    private int fireCooldown;
    /** Ticks left before the next dive run may start; owned by the entity so it never freezes. */
    private int attackCooldown;
    private int fireAnimationTicks;
    /** Whether the one time variant roll (spear / knight) has already happened. */
    private boolean variantRolled;

    // --- knight variant -----------------------------------------------------
    /** True for the knight variant: a baby zombie rides on the back and swings a mace on the dive. */
    private boolean knight;
    /**
     * The live rider, refreshed every tick; only meaningful while {@link #knight} is true.
     *
     * <p>Protected rather than private because the boss also carries a rider (a trident baby zombie)
     * through its own, separately gated path - see {@code BossLightningCreeperPhantomKnightEntity}.
     */
    protected @Nullable Zombie rider;
    /** Whether a rider was ever attached, so a lost one is only replaced when the config asks for it. */
    private boolean riderEverAttached;
    /**
     * Ticks before the next charge <b>attempt</b> may be rolled. Lives on the entity, not in the goal:
     * a goal stops ticking the moment it loses its target, and a frozen cooldown means the knight never
     * charges again.
     */
    private int chargeCooldown;
    /** Set when a charge attempt roll succeeded, cleared by the charge goal when it starts. */
    private boolean chargeArmed;

    public CreeperPhantomEntity(EntityType<? extends CreeperPhantomEntity> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DIVING, false);
        builder.define(DATA_FIRING, false);
        builder.define(DATA_SPEAR, ItemStack.EMPTY);
    }

    // --- spear variant ------------------------------------------------------

    /** The carried spear, or {@link ItemStack#EMPTY} for the ordinary chimera. */
    public ItemStack spearStack() {
        return this.getEntityData().get(DATA_SPEAR);
    }

    /** Derived from the stack itself, never stored separately, so the two can never disagree. */
    public boolean hasSpear() {
        return !this.spearStack().isEmpty();
    }

    public void setSpearStack(ItemStack stack) {
        this.getEntityData().set(DATA_SPEAR, stack);
    }

    /**
     * Whether this variant may roll a spear at all. The creeper line does; the enderman has no creeper
     * part and the nuclear variant is a single use bomb, so both opt out.
     */
    protected boolean supportsSpearVariant() {
        return true;
    }

    // --- knight variant -----------------------------------------------------

    /** True for the knight variant: a baby zombie rides this chimera and swings a mace on its dives. */
    public boolean isKnight() {
        return this.knight;
    }

    /** The live rider, or {@code null} when this is not a knight / the rider is gone. */
    public @Nullable Zombie knightRider() {
        Zombie current = this.rider;
        return current != null && current.isAlive() ? current : null;
    }

    /** Lets a subclass (the boss) own the same rider slot without going through the knight variant. */
    protected void setRider(@Nullable Zombie rider) {
        this.rider = rider;
    }

    /** Whether a charge attempt has been rolled successfully and is waiting for the goal to run. */
    public boolean chargeArmed() {
        return this.chargeArmed;
    }

    /** Called by the charge goal when it takes over, so one roll buys exactly one charge. */
    public void consumeCharge() {
        this.chargeArmed = false;
    }

    /** Called by the charge goal when a run ends, whether it connected or was broken off. */
    public void setChargeCooldown(int ticks) {
        this.chargeCooldown = ticks;
        this.chargeArmed = false;
    }

    /**
     * Whether this variant may roll a rider at all. Only the plain Creeper Phantom is a knight - the
     * enderman has no place for a rider, the nuclear variant is a bomb, and the design calls for the
     * knight on mob3 only.
     */
    protected boolean supportsKnightVariant() {
        return true;
    }

    /**
     * Keeps the knight's rider alive, anchored and armed. Runs on the server every tick.
     *
     * <p>Lifecycle, in order: refresh the rider (adopt an existing one, spawn if there is none), weld it
     * to the back, then roll the next charge attempt once the cooldown has run out. A rider that dies is
     * <b>not</b> replaced unless {@code knightRespawnRider} is on - "whichever of the two dies, the other
     * carries on".
     */
    private void tickKnight(ServerLevel level) {
        if (this.chargeCooldown > 0) {
            this.chargeCooldown--;
        }
        if (!this.knight) {
            return;
        }

        Zombie current = this.knightRider();
        if (current == null) {
            this.rider = null;
            if (this.riderEverAttached && !ForbiddenChimeraConfig.get().knightRespawnRider) {
                // One-off encounter: the mount simply stops being a knight and carries on.
                this.knight = false;
                return;
            }
            this.rider = PhantomRider.adoptOrSpawn(level, this);
            this.riderEverAttached = true;
            return;
        }

        PhantomRider.anchor(this, current);

        // One attempt per cooldown period, hence the low trigger rate the design asks for.
        if (this.chargeCooldown <= 0 && !this.chargeArmed) {
            ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();
            if (this.random.nextDouble() < config.knightChargeChance) {
                this.chargeArmed = true;
            } else {
                this.chargeCooldown = config.knightChargeCooldownTicks;
            }
        }
    }

    /**
     * Host death releases the rider instead of deleting it - it drops off as an ordinary baby zombie.
     * Self destruction is not a case here: the knight does not explode, only mob1/mob2 do.
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

    /**
     * One time variant roll: knight first, spear second.
     *
     * <p>The two are mutually exclusive by design - a knight has both hands full with its mace, so it
     * never carries a spear as well.
     *
     * <p>Runs on the first server tick rather than in {@code finalizeSpawn} because {@code /summon}
     * skips {@code finalizeSpawn} entirely (see the project's AI forced rules), so a spawn-egg or
     * command summoned chimera would otherwise never get its roll.
     */
    private void rollVariant() {
        // A variant that is already set - supplied by summon NBT, `/data merge`, or loaded from a save -
        // is respected instead of being re-rolled. This is also what keeps the two variants mutually
        // exclusive when a command asks for one of them explicitly.
        if (this.knight || this.hasSpear()) {
            return;
        }

        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();

        if (this.supportsKnightVariant()
                && config.enableKnightVariant && config.knightVariantChance > 0.0D
                && this.random.nextDouble() < config.knightVariantChance) {
            this.knight = true;
            return;
        }

        if (!this.supportsSpearVariant()
                || !config.enableSpearVariant || config.spearVariantChance <= 0.0D) {
            return;
        }
        if (this.random.nextDouble() >= config.spearVariantChance) {
            return;
        }
        this.setSpearStack(new ItemStack(SPEARS.get(this.random.nextInt(SPEARS.size()))));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        ItemStack spear = this.spearStack();
        if (!spear.isEmpty()) {
            output.store(SPEAR_KEY, ItemStack.CODEC, spear);
        }
        output.putBoolean(KNIGHT_KEY, this.knight);
        // Always written, so a reloaded chimera is never re-rolled.
        output.putBoolean(VARIANT_ROLLED_KEY, true);
        output.putBoolean(RIDER_ATTACHED_KEY, this.riderEverAttached);
        // Cooldowns live on the entity, so they have to be persisted with it: without this a chunk
        // reload zeroed them, and an unloaded-and-reloaded chimera came back with its dive and its
        // charge both immediately available - which is exactly the "it charges again the instant I
        // come back" behaviour. chargeArmed matters most: it is a decision already rolled, and losing
        // it silently drops a charge the player was owed.
        output.putInt(FIRE_COOLDOWN_KEY, this.fireCooldown);
        output.putInt(ATTACK_COOLDOWN_KEY, this.attackCooldown);
        output.putInt(CHARGE_COOLDOWN_KEY, this.chargeCooldown);
        output.putBoolean(CHARGE_ARMED_KEY, this.chargeArmed);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.read(SPEAR_KEY, ItemStack.CODEC).ifPresent(this::setSpearStack);
        this.knight = input.getBooleanOr(KNIGHT_KEY, false);
        // Only a save (or an explicit command) carries this marker; a bare `/summon` does not, and that
        // is exactly what lets a summoned chimera still roll its variant on the first tick.
        this.variantRolled = input.getBooleanOr(VARIANT_ROLLED_KEY, false);
        // Persisted for the same reason the boss's is: otherwise a chunk reload resets it and
        // `knightRespawnRider = false` ("whichever dies, the other carries on") stops holding.
        this.riderEverAttached = input.getBooleanOr(RIDER_ATTACHED_KEY, false);
        this.fireCooldown = input.getIntOr(FIRE_COOLDOWN_KEY, 0);
        this.attackCooldown = input.getIntOr(ATTACK_COOLDOWN_KEY, 0);
        this.chargeCooldown = input.getIntOr(CHARGE_COOLDOWN_KEY, 0);
        this.chargeArmed = input.getBooleanOr(CHARGE_ARMED_KEY, false);
    }

    // --- GeckoLib animation -------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // setAndContinue only (re)starts an animation when the state actually changes, which is what
        // lets the one shot fire animation play to its end instead of restarting every tick.
        // The type argument is explicit: inside ControllerRegistrar.add(AnimationController<?>...) a
        // diamond would infer GeoAnimatable and state.animatable() would lose our entity type.
        controllers.add(new AnimationController<CreeperPhantomEntity>("main", 4, state -> {
            CreeperPhantomEntity entity = state.animatable();
            if (entity.isFiring()) {
                return state.setAndContinue(FIRE);
            }
            return state.setAndContinue(entity.isDiving() ? DIVE : FLY);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    /** True while {@link DiveBombGoal} is in its dive phase; synced so the model can follow. */
    public boolean isDiving() {
        return this.getEntityData().get(DATA_DIVING);
    }

    public void setDiving(boolean diving) {
        this.getEntityData().set(DATA_DIVING, diving);
    }

    /** True for the first few ticks after a skull was launched. */
    public boolean isFiring() {
        return this.getEntityData().get(DATA_FIRING);
    }

    private void setFiring(boolean firing) {
        this.getEntityData().set(DATA_FIRING, firing);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // The knight's charge outranks the dive: while it runs, the mount is a mount, not a bomber.
        this.goalSelector.addGoal(0, new RiderChargeAttackGoal(this));
        this.goalSelector.addGoal(1, this.createAttackGoal());
        // Idle flight, so the mob never just hangs in the air when there is nothing to dive at.
        this.goalSelector.addGoal(3, new WanderFlyGoal(this));
        // Vanilla targeting cannot see creative players; this one can, see ChimeraPlayerTargetGoal.
        this.targetSelector.addGoal(0, new ChimeraPlayerTargetGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            this.tickWingTrail();
        } else {
            if (!this.variantRolled) {
                this.variantRolled = true;
                this.rollVariant();
            }
            if (this.fireCooldown > 0) {
                this.fireCooldown--;
            }
            if (this.attackCooldown > 0) {
                this.attackCooldown--;
            }
            if (this.fireAnimationTicks > 0 && --this.fireAnimationTicks == 0) {
                this.setFiring(false);
            }
            if (this.level() instanceof ServerLevel serverLevel) {
                this.tickKnight(serverLevel);
            }
        }
    }

    /**
     * Ticks left before the next dive run may start.
     *
     * <p>Lives on the entity, not in the goal: a goal stops ticking the moment it loses its target, so
     * a cooldown field inside it freezes and the attack never resumes ("it only attacks after I kill
     * the thing it carries" was exactly this bug in an earlier version).
     */
    public int attackCooldown() {
        return this.attackCooldown;
    }

    public void setAttackCooldown(int ticks) {
        this.attackCooldown = ticks;
    }

    /**
     * Vanilla phantom wing trail, reused verbatim from {@code Phantom#tick}: one {@code MYCELIUM} mote
     * per wing tip every tick, plus the flap sound when the flap animation passes its peak.
     *
     * <p>Same formulas as vanilla, so the effect sits on the wing line exactly like the real phantom:
     * lateral offset {@code bbWidth * 1.48}, height {@code (0.3 + anim * 0.45) * bbHeight * 2.5}, flap
     * phase from {@code id * 3 + tickCount}. No new particle type is registered - this is the vanilla
     * effect, not a look alike.
     *
     * <p>Inherited by every model based chimera ({@code creeper_phantom}, {@code lightning_creeper_phantom},
     * {@code nuclear_creeper_phantom}), which is why all three finally trail particles.
     */
    private void tickWingTrail() {
        float radiansPerDegree = (float) (Math.PI / 180.0);
        float anim = Mth.cos((this.uniqueFlapTickOffset() + this.tickCount) * 7.448451F * radiansPerDegree
                + (float) Math.PI);
        float nextAnim = Mth.cos((this.uniqueFlapTickOffset() + this.tickCount + 1) * 7.448451F
                * radiansPerDegree + (float) Math.PI);
        if (anim > 0.0F && nextAnim <= 0.0F) {
            this.level().playLocalSound(this, SoundEvents.PHANTOM_FLAP, this.getSoundSource(),
                    0.95F + this.random.nextFloat() * 0.05F, 0.95F + this.random.nextFloat() * 0.05F);
        }

        float width = this.getBbWidth() * 1.48F;
        float cos = Mth.cos(this.getYRot() * radiansPerDegree) * width;
        float sin = Mth.sin(this.getYRot() * radiansPerDegree) * width;
        float height = (0.3F + anim * 0.45F) * this.getBbHeight() * 2.5F;
        this.level().addParticle(ParticleTypes.MYCELIUM, this.getX() + cos, this.getY() + height,
                this.getZ() + sin, 0.0D, 0.0D, 0.0D);
        this.level().addParticle(ParticleTypes.MYCELIUM, this.getX() - cos, this.getY() + height,
                this.getZ() - sin, 0.0D, 0.0D, 0.0D);
    }

    /** Vanilla phantom flap phase source: {@code Phantom#getUniqueFlapTickOffset}. */
    public int uniqueFlapTickOffset() {
        return this.getId() * 3;
    }

    public boolean canFire() {
        return this.fireCooldown <= 0;
    }

    /**
     * Whether a dive run may launch missiles. The enderman variant only swoops: it has no creeper part
     * to fire, so its dive is a plain body slam.
     */
    public boolean canFireSkulls() {
        return true;
    }

    /**
     * Hook for the attack goal, so a subclass can replace the whole behaviour instead of copying the
     * goal list. The nuclear variant overrides this: it is a single-use missile, not a diver.
     */
    protected Goal createAttackGoal() {
        return new DiveBombGoal(this);
    }

    /**
     * Creates the missile for one shot.
     *
     * <p>Overridden by the lightning variant, which fires a lightning creeper skull instead; the whole
     * spawn logic stays identical, only the projectile differs.
     */
    protected CreeperSkullProjectile newSkull(ServerLevel level, Vec3 direction) {
        return new CreeperSkullProjectile(ModEntities.creeperSkull(), this, direction, level);
    }

    /**
     * Resolves the contact hit at the end of a dive run.
     *
     * <p>Without a spear this is the ordinary melee attack. With one, the damage comes from the spear
     * itself and follows the vanilla kinetic weapon rules instead: the spear's own
     * {@code kinetic_weapon} component decides whether the strike lands at all, its
     * {@code ATTACK_DAMAGE} bonus plus a relative approach speed term decides how hard it hits, and the
     * damage is dealt with the component's own damage type ({@code minecraft:spear}).
     *
     * <p>Numbers are read live off the {@link ItemStack} rather than copied into config, so a resource
     * pack or data pack that restat a spear changes the chimera's dive damage with it. The only config
     * knob is {@code spearDiveDamageScale}, a pure tuning multiplier.
     *
     * <p>{@code ticksUsed} is passed as 0: the dive has no "charge up" phase, the whole point of the
     * variant is that the damage scales with <b>dive speed</b>, not with how long the spear was held.
     */
    public void performDiveHit(ServerLevel level, LivingEntity target) {
        ItemStack spear = this.spearStack();
        KineticWeapon kinetic = spear.isEmpty() ? null : spear.get(DataComponents.KINETIC_WEAPON);
        if (kinetic == null) {
            this.doHurtTarget(level, target);
            return;
        }

        Vec3 look = this.getLookAngle();
        // Same motion source as vanilla KineticWeapon#getMotion: the entity's own per tick movement,
        // expressed per second.
        double attackerSpeed = look.dot(this.getKnownSpeed().scale(20.0D));
        double targetSpeed = look.dot(target.getKnownSpeed().scale(20.0D));
        double relativeSpeed = Math.max(0.0D, attackerSpeed - targetSpeed);

        boolean lands = kinetic.damageConditions()
                .map(condition -> condition.test(0, attackerSpeed, relativeSpeed, MOB_ACTION_FACTOR))
                .orElse(false);
        if (!lands) {
            return;
        }

        double base = this.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        float damage = (float) (base + Mth.floor(relativeSpeed * kinetic.damageMultiplier()));
        damage *= (float) ForbiddenChimeraConfig.get().spearDiveDamageScale;
        if (damage <= 0.0F) {
            return;
        }

        Holder<DamageType> damageType = spear.get(DataComponents.DAMAGE_TYPE);
        DamageSource source = damageType != null
                ? new DamageSource(damageType, this, this)
                : this.damageSources().mobAttack(this);
        target.hurtServer(level, source, damage);
        // Same hit feedback vanilla's kinetic weapons use (LivingEntity#onKineticHit).
        kinetic.makeLocalHitSound(this);
    }

    /** Launches an explosive creeper skull at the target. */
    public void fireCreeperSkull(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 from = new Vec3(this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ());
        Vec3 direction = target.getEyePosition().subtract(from);
        if (direction.lengthSqr() < 1.0E-4D) {
            return;
        }

        CreeperSkullProjectile skull = this.newSkull(serverLevel, direction.normalize());
        serverLevel.addFreshEntity(skull);

        this.fireCooldown = ForbiddenChimeraConfig.get().diverFireCooldownTicks;
        // The client derives the recoil pose from the synced flag, so no extra packet is needed.
        this.fireAnimationTicks = FIRE_ANIMATION_TICKS;
        this.setFiring(true);
        this.playSound(SoundEvents.PHANTOM_SWOOP, 1.0F, 0.6F);
    }
}
