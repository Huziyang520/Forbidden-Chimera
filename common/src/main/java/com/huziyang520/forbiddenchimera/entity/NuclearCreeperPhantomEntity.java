package com.huziyang520.forbiddenchimera.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.huziyang520.forbiddenchimera.ai.NuclearAttackGoal;
import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

/**
 * 核弹苦力怕幻翼 - the third model based chimera.
 *
 * <p>Same model as the Creeper Phantom with a red filtered texture, but its own animations and its own
 * behaviour: it circles, hovers with an accelerating wing flap, then charges in and self destructs with
 * a huge blast ({@link NuclearAttackGoal}).
 *
 * <p>Animation state is synced rather than derived from raw client state, so both sides always agree:
 * the phase drives which clip plays, and the hover counter drives the flap speed ramp. That is also why
 * the controller is kept in a field - the handler needs it to raise the playback speed.
 */
public class NuclearCreeperPhantomEntity extends CreeperPhantomEntity {

    // --- synced phase, matching NuclearAttackGoal's phases plus the idle ones ---
    public static final int PHASE_FLY = 0;
    public static final int PHASE_CIRCLE = 1;
    public static final int PHASE_HOVER = 2;
    public static final int PHASE_CHARGE = 3;
    public static final int PHASE_DETONATE = 4;

    // Animation names, exactly as declared in nuclear_creeper_phantom.animation.json.
    private static final String ANIM_FLY = "animation.nuclear_creeper_phantom.fly";
    private static final String ANIM_HOVER = "animation.nuclear_creeper_phantom.hover";
    private static final String ANIM_CHARGE = "animation.nuclear_creeper_phantom.charge";
    private static final String ANIM_DETONATE = "animation.nuclear_creeper_phantom.detonate";

    private static final RawAnimation FLY = RawAnimation.begin().thenLoop(ANIM_FLY);
    private static final RawAnimation HOVER = RawAnimation.begin().thenLoop(ANIM_HOVER);
    private static final RawAnimation CHARGE = RawAnimation.begin().thenPlayAndHold(ANIM_CHARGE);
    private static final RawAnimation DETONATE = RawAnimation.begin().thenPlayAndHold(ANIM_DETONATE);

    private static final EntityDataAccessor<Integer> DATA_NUCLEAR_PHASE =
            SynchedEntityData.defineId(NuclearCreeperPhantomEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HOVER_TICKS =
            SynchedEntityData.defineId(NuclearCreeperPhantomEntity.class, EntityDataSerializers.INT);

    private final AnimationController<NuclearCreeperPhantomEntity> mainController;

    public NuclearCreeperPhantomEntity(EntityType<? extends NuclearCreeperPhantomEntity> type, Level level) {
        super(type, level);
        this.xpReward = 15;
        this.mainController = new AnimationController<>("main", 4, this::selectAnimation);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_NUCLEAR_PHASE, PHASE_FLY);
        builder.define(DATA_HOVER_TICKS, 0);
    }

    /** Replaces the parent's controller entirely: this variant has its own animations. */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(this.mainController);
    }

    private PlayState selectAnimation(AnimationTest<NuclearCreeperPhantomEntity> state) {
        NuclearCreeperPhantomEntity entity = state.animatable();
        switch (entity.nuclearPhase()) {
            case PHASE_DETONATE -> {
                this.mainController.setAnimationSpeed(1.0D);
                return state.setAndContinue(DETONATE);
            }
            case PHASE_CHARGE -> {
                this.mainController.setAnimationSpeed(1.0D);
                return state.setAndContinue(CHARGE);
            }
            case PHASE_HOVER, PHASE_CIRCLE -> {
                // Wing flap accelerates across the hover: 1.0 at the start, nuclearFlapSpeedMax at the end.
                this.mainController.setAnimationSpeed(this.flapSpeed(entity));
                return state.setAndContinue(HOVER);
            }
            default -> {
                this.mainController.setAnimationSpeed(1.0D);
                return state.setAndContinue(FLY);
            }
        }
    }

    private double flapSpeed(NuclearCreeperPhantomEntity entity) {
        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();
        int rampTicks = Math.max(1, config.nuclearHoverTicks);
        double progress = Math.min(1.0D, entity.hoverTicks() / (double) rampTicks);
        return 1.0D + (config.nuclearFlapSpeedMax - 1.0D) * progress;
    }

    @Override
    protected Goal createAttackGoal() {
        return new NuclearAttackGoal(this);
    }

    /** The spear variant is defined for the diving creeper chimeras (mob3 / mob4), not the bomb. */
    @Override
    protected boolean supportsSpearVariant() {
        return false;
    }

    /** Neither variant applies to the bomb: a rider would be incinerated, and it never dives. */
    @Override
    protected boolean supportsKnightVariant() {
        return false;
    }

    public int nuclearPhase() {
        return this.getEntityData().get(DATA_NUCLEAR_PHASE);
    }

    public void setNuclearPhase(int phase) {
        this.getEntityData().set(DATA_NUCLEAR_PHASE, phase);
    }

    /** Ticks spent hovering so far; drives the wing flap ramp on the client. */
    public int hoverTicks() {
        return this.getEntityData().get(DATA_HOVER_TICKS);
    }

    public void setHoverTicks(int ticks) {
        this.getEntityData().set(DATA_HOVER_TICKS, ticks);
    }
}
