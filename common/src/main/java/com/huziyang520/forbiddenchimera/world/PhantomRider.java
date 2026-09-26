package com.huziyang520.forbiddenchimera.world;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import org.jspecify.annotations.Nullable;

/**
 * The baby zombie a knight variant chimera carries on its back: spawning, ownership, anchoring,
 * releasing, and its spawn equipment.
 *
 * <p>Deliberately <b>not</b> built like {@link PhantomCargo}: the cargo is re-positioned by hand every
 * tick because it needs a negative (hanging) offset, but a rider has to move <b>with</b> its mount. So
 * the rider is a real vanilla {@code Zombie} with {@code setBaby(true)} that is mounted through the
 * <b>vanilla passenger</b> system - see {@link #anchor} for why hand-anchoring was wrong.
 *
 * <p><b>Ownership</b> uses the same persistent tag trick as the cargo:
 * {@code forbidden_chimera_rider:<host uuid>}. Tags are part of the save format, so a reload adopts the
 * existing rider instead of spawning a second one. Vanilla only saves a vehicle's passengers nested in
 * the vehicle, so a reload cannot duplicate the rider either.
 *
 * <p><b>Geometry</b>: the mount point is the same derived model band constant the cargo rider uses,
 * see {@link PhantomCargo#MODEL_BAND_TOP}. It is applied through
 * {@code AbstractChimeraPhantom#getPassengerAttachmentPoint}, a code constant and never a config value,
 * so a model change cannot silently misalign the rider.
 *
 * <p><b>Equipment</b> is rolled here instead of calling {@code Zombie#finalizeSpawn}, because the
 * chances have to be configurable and the rider must always end up with a mace in its main hand. The
 * algorithm is a verbatim copy of {@code Mob#populateDefaultEquipmentSlots} with a configurable base
 * chance, plus the same spawn enchantment providers vanilla zombies use
 * ({@code VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT}).
 */
public final class PhantomRider {

    /** Ownership tag prefix; the suffix is the owning chimera's UUID. Part of the save format. */
    private static final String TAG_PREFIX = "forbidden_chimera_rider:";

    /** How far from the host an owned rider may be and still be recognised after a reload. */
    private static final double SEARCH_RADIUS = 16.0D;

    /** Rider stands on the model's back - the top of the phantom model band (see PhantomCargo). */
    public static final double RIDER_CARRY_HEIGHT = PhantomCargo.MODEL_BAND_TOP;


    /** Vanilla {@code Mob#EQUIPMENT_POPULATION_ORDER}: head first, so a helmet is the likely piece. */
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private PhantomRider() {
    }

    /**
     * Returns the host's own rider, spawning one if it has none.
     *
     * <p>Adoption first: an owned zombie may already exist (chunk reload, server restart, different tick
     * ordering), and spawning unconditionally would duplicate the rider.
     *
     * <p>This overload is the knight variant's: the rider carries a mace. The boss calls the
     * weapon-parameterised overload with a trident instead.
     */
    public static @Nullable Zombie adoptOrSpawn(ServerLevel level, Mob host) {
        return adoptOrSpawn(level, host, Items.MACE);
    }

    /** {@link #adoptOrSpawn(ServerLevel, Mob)} with an explicit main hand weapon. */
    public static @Nullable Zombie adoptOrSpawn(ServerLevel level, Mob host, Item weapon) {
        Zombie owned = findOwned(host);
        if (owned != null) {
            // An adopted rider may have been dismounted (death of an older host, a manual ejection,
            // or a save that lost the passenger link): re-seat it instead of spawning a duplicate.
            anchor(host, owned);
            return owned;
        }
        return spawn(level, host, weapon);
    }

    /**
     * Spawns a fresh baby zombie rider: limp (no AI, no gravity), owned by the host, always carrying
     * {@code weapon} and possibly armour, and never despawning on its own.
     */
    public static @Nullable Zombie spawn(ServerLevel level, Mob host, Item weapon) {
        Zombie rider = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (rider == null) {
            return null;
        }
        rider.setBaby(true);
        rider.setNoAi(true);
        rider.setNoGravity(true);
        rider.setPersistenceRequired();
        rider.addTag(tagFor(host));
        equip(level, host, rider, weapon);
        level.addFreshEntity(rider);
        anchor(host, rider);
        return rider;
    }

    /** Keeps the rider mounted on the host's back. Called every tick; idempotent. */
    public static void anchor(Mob host, Zombie rider) {
        // Vanilla passenger, not a hand-anchored position. Two things go wrong with hand-anchoring:
        //  1. LivingEntity#calculateEntityAnimation calls walkAnimation.stop() for passengers and
        //     updates the walk animation for everyone else, so a snapped-every-tick zombie is read as
        //     "running" and its legs never stop moving;
        //  2. a fast mount leaves the passenger behind, because the two positions are only reconciled
        //     once per tick on the server.
        // Riding goes through Entity#rideTick -> positionRider on both sides, which fixes both.
        if (rider.getVehicle() != host) {
            rider.startRiding(host, true, true);
        }
    }

    /**
     * Hands the rider back to the world: it dismounts, loses its tag and its limbo, and becomes an
     * ordinary baby zombie that walks and attacks on its own. Used when the host dies - "whichever of
     * the two dies, the other carries on with its own logic".
     */
    public static void release(Zombie rider) {
        // Dismount first: the tag is what makes it "owned", and a still-riding zombie must not be
        // adopted by a later host that happens to reuse the slot.
        rider.stopRiding();
        // 26.3 exposes the persistent tag set through entityTags(); copy before removing.
        rider.entityTags().stream().filter(tag -> tag.startsWith(TAG_PREFIX)).toList()
                .forEach(rider::removeTag);
        rider.setNoAi(false);
        rider.setNoGravity(false);
    }

    /** @return the host's live owned rider, or {@code null}. */
    public static @Nullable Zombie findOwned(Mob host) {
        for (Zombie candidate : ownedZombies(host)) {
            if (candidate.isAlive()) {
                return candidate;
            }
        }
        return null;
    }

    private static List<Zombie> ownedZombies(Mob host) {
        String tag = tagFor(host);
        return host.level().getEntitiesOfClass(Zombie.class,
                host.getBoundingBox().inflate(SEARCH_RADIUS),
                candidate -> candidate.entityTags().contains(tag));
    }

    private static String tagFor(Mob host) {
        UUID uuid = host.getUUID();
        return TAG_PREFIX + uuid;
    }

    // --- spawn equipment ----------------------------------------------------

    /** Weapon in the main hand, vanilla-style random armour, vanilla-style spawn enchantments. */
    private static void equip(ServerLevel level, Mob host, Zombie rider, Item weapon) {
        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();
        RandomSource random = rider.getRandom();
        DifficultyInstance difficulty = level.getCurrentDifficultyAt(host.blockPosition());

        // The weapon is not a roll: a rider without its weapon is just a passenger.
        rider.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));

        rollArmor(rider, random, difficulty, config.knightArmorChance);
        enchantSlot(level, rider, EquipmentSlot.MAINHAND, random, difficulty, config.knightWeaponEnchantChance);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            enchantSlot(level, rider, slot, random, difficulty, config.knightArmorEnchantChance);
        }
    }

    /**
     * Verbatim copy of {@code Mob#populateDefaultEquipmentSlots}, with only the base chance made
     * configurable. Picking one material and then filling slots in order is what gives spawned mobs
     * their characteristic matching armour sets.
     */
    private static void rollArmor(Zombie rider, RandomSource random, DifficultyInstance difficulty,
                                  float baseChance) {
        if (random.nextFloat() >= baseChance * difficulty.getSpecialMultiplier()) {
            return;
        }
        int armorType = random.nextInt(3);
        for (int attempt = 1; attempt <= Mob.WEARING_ARMOR_UPGRADE_MATERIAL_ATTEMPTS; attempt++) {
            if (random.nextFloat() < Mob.WEARING_ARMOR_UPGRADE_MATERIAL_CHANCE) {
                armorType++;
            }
        }

        float partialChance = rider.level().getDifficulty() == Difficulty.HARD ? 0.1F : 0.25F;
        boolean first = true;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!first && random.nextFloat() < partialChance) {
                break;
            }
            first = false;
            if (rider.getItemBySlot(slot).isEmpty()) {
                Item equip = Mob.getEquipmentForSlot(slot, armorType);
                if (equip != null) {
                    rider.setItemSlot(slot, new ItemStack(equip));
                }
            }
        }
    }

    /** Same as {@code Mob#enchantSpawnedEquipment}, with the chance supplied by config. */
    private static void enchantSlot(ServerLevel level, Mob rider, EquipmentSlot slot, RandomSource random,
                                    DifficultyInstance difficulty, float chance) {
        ItemStack stack = rider.getItemBySlot(slot);
        if (stack.isEmpty() || random.nextFloat() >= chance * difficulty.getSpecialMultiplier()) {
            return;
        }
        EnchantmentHelper.enchantItemFromProvider(stack, level.registryAccess(),
                VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, difficulty, random);
        rider.setItemSlot(slot, stack);
    }
}
