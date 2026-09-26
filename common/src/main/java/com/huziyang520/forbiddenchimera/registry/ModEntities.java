package com.huziyang520.forbiddenchimera.registry;

import com.huziyang520.forbiddenchimera.entity.BossLightningCreeperPhantomKnightEntity;
import com.huziyang520.forbiddenchimera.entity.CreeperPhantomEntity;
import com.huziyang520.forbiddenchimera.entity.CreeperSkullProjectile;
import com.huziyang520.forbiddenchimera.entity.LightningCreeperPhantomEntity;
import com.huziyang520.forbiddenchimera.entity.LightningCreeperSkullProjectile;
import com.huziyang520.forbiddenchimera.entity.EndermanPhantomEntity;
import com.huziyang520.forbiddenchimera.entity.NuclearCreeperPhantomEntity;
import com.huziyang520.forbiddenchimera.platform.services.EntityRegistrar;
import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Entity type definitions.
 *
 * <p>Registered chimera entity types are only the ones with a hand made model: the Creeper Phantom
 * (mob3) and its charged sibling 闪电苦力怕幻翼, plus their two skull projectiles.
 *
 * <p>mob1 (幻翼骑士苦力怕) and mob2 (苦力怕投手幻翼) are deliberately <b>not</b> registered here: they
 * are vanilla phantom + vanilla creeper combinations injected into the vanilla {@code Phantom} by
 * {@code PhantomMixin}, so no entity types, no loot tables and no attributes exist for them.
 *
 * <p>Registration itself is loader specific, so the actual {@link EntityType} instances are only
 * available through the {@link Supplier}s filled in by {@link #register(EntityRegistrar)}. Always read
 * them through the accessor methods, never cache the result in a static field of another class.
 */
public final class ModEntities {

    private static Supplier<EntityType<CreeperPhantomEntity>> creeperPhantom;
    private static Supplier<EntityType<LightningCreeperPhantomEntity>> lightningCreeperPhantom;
    private static Supplier<EntityType<NuclearCreeperPhantomEntity>> nuclearCreeperPhantom;
    private static Supplier<EntityType<EndermanPhantomEntity>> endermanPhantom;
    private static Supplier<EntityType<BossLightningCreeperPhantomKnightEntity>> bossLightningCreeperPhantomKnight;
    private static Supplier<EntityType<CreeperSkullProjectile>> creeperSkull;
    private static Supplier<EntityType<LightningCreeperSkullProjectile>> lightningCreeperSkull;

    private ModEntities() {
    }

    public static void register(EntityRegistrar registrar) {
        creeperPhantom = registrar.register("creeper_phantom",
                () -> EntityType.Builder.of(CreeperPhantomEntity::new, MobCategory.MONSTER)
                        .sized(0.9F, 0.5F)
                        .eyeHeight(0.175F)
                        .clientTrackingRange(8)
                        .notInPeaceful());

        lightningCreeperPhantom = registrar.register("lightning_creeper_phantom",
                () -> EntityType.Builder.of(LightningCreeperPhantomEntity::new, MobCategory.MONSTER)
                        .sized(0.9F, 0.5F)
                        .eyeHeight(0.175F)
                        .clientTrackingRange(8)
                        .notInPeaceful());

        nuclearCreeperPhantom = registrar.register("nuclear_creeper_phantom",
                () -> EntityType.Builder.of(NuclearCreeperPhantomEntity::new, MobCategory.MONSTER)
                        .sized(0.9F, 0.5F)
                        .eyeHeight(0.175F)
                        .clientTrackingRange(8)
                        .notInPeaceful());

        endermanPhantom = registrar.register("enderman_phantom",
                () -> EntityType.Builder.of(EndermanPhantomEntity::new, MobCategory.MONSTER)
                        .sized(0.9F, 0.5F)
                        .eyeHeight(0.175F)
                        .clientTrackingRange(8)
                        .notInPeaceful());

        // The boss is deliberately absent from every spawn table: it can only be produced by the
        // 禁忌板材 item, so it has no spawn weight and no enable toggle. Its dimensions are the same as
        // the other chimeras on purpose - the geometry is built on the same phantom model band, so the
        // hitbox alignment proven for mob3/mob4 carries over verbatim.
        bossLightningCreeperPhantomKnight = registrar.register("boss_lightning_creeper_phantom_knight",
                () -> EntityType.Builder.of(BossLightningCreeperPhantomKnightEntity::new, MobCategory.MONSTER)
                        .sized(0.9F, 0.5F)
                        .eyeHeight(0.175F)
                        .clientTrackingRange(10)
                        .notInPeaceful());

        creeperSkull = registrar.register("creeper_skull",
                () -> EntityType.Builder.<CreeperSkullProjectile>of(CreeperSkullProjectile::new, MobCategory.MISC)
                        .sized(0.5F, 0.5F)
                        .clientTrackingRange(6));

        lightningCreeperSkull = registrar.register("lightning_creeper_skull",
                () -> EntityType.Builder.<LightningCreeperSkullProjectile>of(
                                LightningCreeperSkullProjectile::new, MobCategory.MISC)
                        .sized(0.5F, 0.5F)
                        .clientTrackingRange(6));
    }

    public static EntityType<CreeperPhantomEntity> creeperPhantom() {
        return creeperPhantom.get();
    }

    public static EntityType<LightningCreeperPhantomEntity> lightningCreeperPhantom() {
        return lightningCreeperPhantom.get();
    }

    public static EntityType<NuclearCreeperPhantomEntity> nuclearCreeperPhantom() {
        return nuclearCreeperPhantom.get();
    }

    public static EntityType<EndermanPhantomEntity> endermanPhantom() {
        return endermanPhantom.get();
    }

    public static EntityType<BossLightningCreeperPhantomKnightEntity> bossLightningCreeperPhantomKnight() {
        return bossLightningCreeperPhantomKnight.get();
    }

    public static EntityType<CreeperSkullProjectile> creeperSkull() {
        return creeperSkull.get();
    }

    public static EntityType<LightningCreeperSkullProjectile> lightningCreeperSkull() {
        return lightningCreeperSkull.get();
    }
}
