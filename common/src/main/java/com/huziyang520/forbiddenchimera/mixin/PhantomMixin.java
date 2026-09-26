package com.huziyang520.forbiddenchimera.mixin;

import com.huziyang520.forbiddenchimera.Constants;
import com.huziyang520.forbiddenchimera.ai.ChimeraPlayerTargetGoal;
import com.huziyang520.forbiddenchimera.ai.ChimeraSteering;
import com.huziyang520.forbiddenchimera.ai.RiderChargeGoal;
import com.huziyang520.forbiddenchimera.ai.ThrowerDropGoal;
import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import com.huziyang520.forbiddenchimera.entity.ChimeraPhantom;
import com.huziyang520.forbiddenchimera.entity.ChimeraVariant;
import com.huziyang520.forbiddenchimera.world.ChimeraExplosion;
import com.huziyang520.forbiddenchimera.world.PhantomCargo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns a plain vanilla phantom into mob1 (幻翼骑士苦力怕) or mob2 (苦力怕投手幻翼).
 *
 * <p>Nothing is registered for these two: the entity is a genuine vanilla phantom, the cargo is a
 * genuine vanilla creeper, and this mixin is the entire custom surface. Lifecycle, in order:
 *
 * <ol>
 *   <li><b>first server tick</b> - resolve the variant (spawner/egg may have assigned it already);</li>
 *   <li><b>first tick with a carrier variant</b> - bolt the goal set onto the vanilla selectors at
 *       priority 0;</li>
 *   <li><b>every tick</b> - keep exactly one cargo anchored and tick the throw cooldown;</li>
 *   <li><b>detonation or death</b> - stop handing out cargo and dispose of the carried one.</li>
 * </ol>
 *
 * <p>Design notes worth keeping:
 * <ul>
 *   <li>goals are injected through {@link MobAccessor}, because every vanilla phantom goal is a
 *       private inner class and a mixin into {@code Phantom} cannot see {@code Mob}'s fields;</li>
 *   <li>steering goes through {@link PhantomAccessor} ({@code moveTargetPoint}); the phantom move
 *       control ignores {@code MoveControl#wantedPosition} entirely;</li>
 *   <li>the "no replacement cargo after detonation" guard is what stops the charge from leaving a
 *       brain dead creeper standing next to the player;</li>
 *   <li>a one-shot geometry report is logged per variant, so a render/cargo mismatch can be measured
 *       from the log instead of being eyeballed.</li>
 * </ul>
 */
@Mixin(Phantom.class)
public abstract class PhantomMixin implements ChimeraPhantom, ChimeraSteering {

    /** Save key for the variant id; part of the save format, never rename it. */
    @Unique
    private static final String VARIANT_KEY = "forbidden_chimera_variant";

    // --- lifecycle ----------------------------------------------------------

    @Unique
    private ChimeraVariant forbiddenChimera$variant = ChimeraVariant.UNDECIDED;

    @Unique
    private @Nullable Creeper forbiddenChimera$cargo;

    @Unique
    private int forbiddenChimera$throwCooldown;

    @Unique
    private boolean forbiddenChimera$goalsInjected;

    @Unique
    private boolean forbiddenChimera$detonated;

    @Unique
    private boolean forbiddenChimera$geometryReported;

    /** Rider only: true once its single creeper has ever been attached, so it is never replenished. */
    @Unique
    private boolean forbiddenChimera$cargoEverAttached;

    // --- ChimeraPhantom -----------------------------------------------------

    @Override
    public ChimeraVariant forbiddenChimera$variant() {
        return this.forbiddenChimera$variant;
    }

    @Override
    public void forbiddenChimera$setVariant(ChimeraVariant variant) {
        this.forbiddenChimera$variant = variant;
    }

    @Override
    public @Nullable Creeper forbiddenChimera$cargo() {
        Creeper cargo = this.forbiddenChimera$cargo;
        return cargo != null && cargo.isAlive() ? cargo : null;
    }

    @Override
    public boolean forbiddenChimera$canThrow() {
        return this.forbiddenChimera$throwCooldown <= 0;
    }

    @Override
    public void forbiddenChimera$dropCargo(LivingEntity target) {
        Phantom self = (Phantom) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Creeper payload = this.forbiddenChimera$cargo();
        this.forbiddenChimera$cargo = null;
        if (payload == null) {
            return;
        }

        // Released, it is an ordinary creeper again: it walks, primes and chases on its own.
        PhantomCargo.release(payload);
        Vec3 direction = target.position()
                .add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                .subtract(payload.position());
        if (direction.lengthSqr() < 1.0E-4D) {
            direction = new Vec3(0.0D, -1.0D, 0.0D);
        }
        payload.setDeltaMovement(direction.normalize()
                .scale(ForbiddenChimeraConfig.get().throwerThrowSpeed));

        self.playSound(SoundEvents.PHANTOM_SWOOP, 1.5F, 0.8F);
        this.forbiddenChimera$throwCooldown = ForbiddenChimeraConfig.get().throwerThrowCooldownTicks;

        // Loaded again immediately, so the phantom never looks empty-handed.
        this.forbiddenChimera$cargo = PhantomCargo.spawn(serverLevel, self);
    }

    @Override
    public void forbiddenChimera$detonateCargo() {
        Phantom self = (Phantom) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel) || this.forbiddenChimera$detonated) {
            return;
        }
        this.forbiddenChimera$detonated = true;

        Creeper cargo = this.forbiddenChimera$cargo();
        this.forbiddenChimera$cargo = null;
        if (cargo != null) {
            cargo.ignite();
            ChimeraExplosion.explode(serverLevel, cargo, cargo.getX(), cargo.getY(), cargo.getZ(),
                    PhantomCargo.CARGO_EXPLOSION_RADIUS);
            cargo.discard();
        } else {
            ChimeraExplosion.explode(serverLevel, self, self.getX(), self.getY(), self.getZ(),
                    PhantomCargo.CHARGE_EXPLOSION_RADIUS);
        }

        // The carrier does not survive its own payload.
        self.kill(serverLevel);
    }

    // --- ChimeraSteering ----------------------------------------------------

    @Override
    public void flyTowards(double x, double y, double z, double speed) {
        // PhantomMoveControl only reacts to this private field; its speed is ramped by the control.
        ((PhantomAccessor) this).forbiddenChimera$setMoveTargetPoint(new Vec3(x, y, z));
    }

    // --- lifecycle ----------------------------------------------------------

    @Inject(method = "tick", at = @At("TAIL"))
    private void forbiddenChimera$tickLifecycle(CallbackInfo callback) {
        Phantom self = (Phantom) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }

        if (this.forbiddenChimera$variant == ChimeraVariant.UNDECIDED) {
            this.forbiddenChimera$variant =
                    ChimeraVariant.roll(self.getRandom(), ForbiddenChimeraConfig.get());
        }
        if (!this.forbiddenChimera$variant.carriesCargo()) {
            return;
        }

        if (!this.forbiddenChimera$goalsInjected) {
            this.forbiddenChimera$goalsInjected = true;
            MobAccessor accessors = (MobAccessor) self;
            // Priority 0 preempts every vanilla phantom goal (they sit at 1..3); the target goal can
            // also see creative players, which vanilla targeting cannot.
            accessors.forbiddenChimera$getTargetSelector().addGoal(0, new ChimeraPlayerTargetGoal(self));
            accessors.forbiddenChimera$getGoalSelector().addGoal(0,
                    this.forbiddenChimera$variant == ChimeraVariant.RIDER
                            ? new RiderChargeGoal(self)
                            : new ThrowerDropGoal(self));
        }

        if (this.forbiddenChimera$throwCooldown > 0) {
            this.forbiddenChimera$throwCooldown--;
        }

        // Blown up or dead: never hand out another creeper, and let the survivor carry on with the
        // vanilla AI. The cargo is released (not removed), so a phantom killed by anything other than
        // its own charge leaves a perfectly normal creeper behind - same as the thrower variant.
        if (this.forbiddenChimera$detonated || !self.isAlive()) {
            PhantomCargo.releaseAllOwned(self);
            this.forbiddenChimera$cargo = null;
            return;
        }

        if (!(self.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Creeper cargo = this.forbiddenChimera$cargo();
        if (cargo == null) {
            // Rules differ per variant:
            //  - thrower: always exactly one cargo; a new one is grabbed once the previous is thrown
            //    or dead, so it can keep bombarding;
            //  - rider: carries one creeper for its whole life and never replenishes. Once the cargo
            //    is gone (killed, or lost over a reload) the phantom simply stays a vanilla phantom.
            if (this.forbiddenChimera$variant == ChimeraVariant.THROWER) {
                cargo = PhantomCargo.adoptOrSpawn(serverLevel, self);
                this.forbiddenChimera$cargo = cargo;
            } else if (!this.forbiddenChimera$cargoEverAttached) {
                this.forbiddenChimera$cargoEverAttached = true;
                cargo = PhantomCargo.adoptOrSpawn(serverLevel, self);
                this.forbiddenChimera$cargo = cargo;
            }
        }
        if (cargo != null) {
            PhantomCargo.anchor(self, cargo, this.forbiddenChimera$variant);
            this.forbiddenChimera$reportGeometryOnce(self, cargo);
        }
    }

    /**
     * Logs the exact geometry once per phantom, so any future render/cargo mismatch can be measured
     * from the log instead of being eyeballed.
     *
     * <p>Vanilla pipeline constants (26.3, {@code LivingEntityRenderer#submit}): the model transform
     * ends with {@code translate(0, -1.501, 0)} and the phantom renderer contributes +1.3125 before it,
     * so the model band is {@code [1.501 - 1.3125 - 0.125, 1.501 - 1.3125 + 0.125]} = [0.0635, 0.3135]
     * above the phantom's feet, inside its 0.5 tall hitbox.
     */
    @Unique
    private void forbiddenChimera$reportGeometryOnce(Phantom phantom, Creeper cargo) {
        if (this.forbiddenChimera$geometryReported) {
            return;
        }
        this.forbiddenChimera$geometryReported = true;
        double feet = phantom.getY();
        double modelBottom = feet + PhantomCargo.MODEL_BAND_BOTTOM;
        double modelTop = feet + PhantomCargo.MODEL_BAND_TOP;
        Constants.LOG.info(
                "Forbidden Chimera geometry [{}]: feet={} box=[{} .. {}] model=[{} .. {}] "
                        + "cargoFeet={} cargoTop={}",
                this.forbiddenChimera$variant, feet, feet, phantom.getBoundingBox().maxY,
                modelBottom, modelTop, cargo.getY(), cargo.getBoundingBox().maxY);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void forbiddenChimera$saveVariant(ValueOutput output, CallbackInfo callback) {
        output.putInt(VARIANT_KEY, this.forbiddenChimera$variant.id());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void forbiddenChimera$loadVariant(ValueInput input, CallbackInfo callback) {
        this.forbiddenChimera$variant = ChimeraVariant.byId(
                input.getIntOr(VARIANT_KEY, ChimeraVariant.UNDECIDED.id()), ChimeraVariant.UNDECIDED);
    }
}
