package com.huziyang520.forbiddenchimera.registry;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;

/**
 * Attribute suppliers for the chimera mobs.
 *
 * <p>None of these entity types exist in vanilla {@code DefaultAttributes}, therefore they MUST be
 * registered by each loader (Fabric: {@code FabricDefaultAttributeRegistry}, NeoForge:
 * {@code EntityAttributeCreationEvent}) or the game crashes the first time one spawns.
 *
 * <p>{@code ATTACK_DAMAGE} in particular is mandatory: {@code LivingEntity#getAttribute} of our own
 * mobs will be null without it.
 */
public final class ModAttributes {

    private ModAttributes() {
    }

    /** Creeper Phantom: the original creature, a fast diver. 36 health = 18 hearts. */
    public static AttributeSupplier creeperPhantom() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 36.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .build();
    }

    /** Enderman Phantom: 32 health = 16 hearts, squishier than the creeper line but it dodges arrows. */
    public static AttributeSupplier endermanPhantom() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.45D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .build();
    }

    /**
     * Nuclear Creeper Phantom: 16 health = 8 hearts, same as the other two. The threat is the blast, not
     * the health bar - and a short health bar means the player can shoot the bomb down mid hover.
     */
    public static AttributeSupplier nuclearCreeperPhantom() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.ATTACK_DAMAGE, 0.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .build();
    }

    /** Lightning Creeper Phantom: same AI and body, slightly more health for the harder hitting variant. */
    public static AttributeSupplier lightningCreeperPhantom() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.4D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .build();
    }

    /**
     * The boss: 150 health = 75 hearts, as specified.
     *
     * <p>{@code ATTACK_DAMAGE} stays close to the other divers on purpose - the boss's threat is the head
     * cluster, not its body slam, and its dive damage is dealt by the trident rider anyway. It is also
     * immune to nothing here: the 90% explosion reduction is implemented in the entity ({@code
     * hurtServer}), because it has to cover <b>vanilla</b> explosions too, which an attribute cannot do.
     */
    public static AttributeSupplier bossLightningCreeperPhantomKnight() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 150.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 96.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .build();
    }
}
