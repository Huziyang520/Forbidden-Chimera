package com.huziyang520.forbiddenchimera.world;

import com.huziyang520.forbiddenchimera.entity.ChimeraPhantom;
import com.huziyang520.forbiddenchimera.entity.ChimeraVariant;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import org.jspecify.annotations.Nullable;

/**
 * The creeper a phantom chimera carries: spawning, ownership, anchoring, releasing.
 *
 * <p>The cargo is deliberately <b>not</b> a passenger. The vanilla phantom's passenger attachment is a
 * single fixed positive offset, which cannot express the thrower's hanging (negative) offset, and a
 * passenger could not be picked off as an ordinary mob either. Instead it is a real, independent
 * vanilla creeper that the phantom re-anchors every tick - killable on its own, dropping its own
 * gunpowder.
 *
 * <h2>Ownership</h2>
 * The carried creeper is stamped with a scoreboard tag {@code forbidden_chimera_cargo:<phantom uuid>}.
 * Tags are part of the save format, so ownership survives a chunk unload or a server restart: without
 * it the phantom would forget its cargo on reload and spawn a second one, leaving the old limp creeper
 * hanging around forever (that was a real bug).
 *
 * <h2>Geometry</h2>
 * The carry heights are <b>derived constants</b>, not config values: a stale config value would
 * silently misalign the creeper. See {@link #RIDER_CARRY_HEIGHT} / {@link #THROWER_CARRY_HEIGHT}.
 */
public final class PhantomCargo {

    /** Vanilla creeper blast radius, so the rider's charge still feels like a creeper. */
    public static final float CARGO_EXPLOSION_RADIUS = 3.0F;
    /** Weaker blast when the cargo was shot off before the charge connected. */
    public static final float CHARGE_EXPLOSION_RADIUS = 1.5F;

    /** Ownership tag prefix; the suffix is the owning phantom's UUID. Part of the save format. */
    private static final String TAG_PREFIX = "forbidden_chimera_cargo:";

    /** How far from the phantom an owned cargo may be and still be recognised after a reload. */
    private static final double SEARCH_RADIUS = 16.0D;

    /**
     * Vanilla phantom model band above the phantom's feet, in blocks.
     *
     * <p>Derivation (26.3 {@code LivingEntityRenderer#submit} + {@code PhantomRenderer#scale}):
     * the model transform ends with {@code translate(0, -1.501, 0)} and the phantom renderer
     * contributes {@code +1.3125} before it, and the model itself spans -0.125 .. +0.125 around its
     * origin, so the band is {@code 1.501 - 1.3125 ± 0.125} = 0.0635 .. 0.3135 - inside the
     * phantom's 0.5 tall hitbox.
     */
    public static final double MODEL_BAND_BOTTOM = 0.0635D;
    public static final double MODEL_BAND_TOP = 0.3135D;

    /** Vanilla creeper hitbox height, needed to hang the thrower's cargo by its head. */
    private static final double CREEPER_HEIGHT = 1.7D;

    /** Rider: the creeper stands on the model's back. */
    public static final double RIDER_CARRY_HEIGHT = MODEL_BAND_TOP;
    /** Thrower: the creeper's head sits just under the model's belly. */
    public static final double THROWER_CARRY_HEIGHT = MODEL_BAND_BOTTOM - CREEPER_HEIGHT;

    private PhantomCargo() {
    }

    /**
     * Returns the phantom's own cargo, spawning one if it has none.
     *
     * <p>Adoption first: an owned creeper may already exist (chunk reload, server restart, or a
     * different tick ordering), and spawning unconditionally would duplicate it.
     */
    public static @Nullable Creeper adoptOrSpawn(ServerLevel level, Phantom phantom) {
        Creeper owned = findOwned(phantom);
        return owned != null ? owned : spawn(level, phantom);
    }

    /** Spawns a new limp creeper owned by the phantom: no AI, no gravity, never despawns. */
    public static @Nullable Creeper spawn(ServerLevel level, Phantom phantom) {
        Creeper creeper = EntityTypes.CREEPER.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (creeper == null) {
            return null;
        }
        creeper.setNoAi(true);
        creeper.setNoGravity(true);
        creeper.setPersistenceRequired();
        creeper.addTag(tagFor(phantom));
        level.addFreshEntity(creeper);
        anchor(phantom, creeper, variantOf(phantom));
        return creeper;
    }

    /** Re-anchors the cargo at its variant's offset. Called every tick while the phantom lives. */
    public static void anchor(Phantom phantom, Creeper cargo, ChimeraVariant variant) {
        if (variant == ChimeraVariant.RIDER) {
            cargo.snapTo(phantom.getX(), phantom.getY() + RIDER_CARRY_HEIGHT, phantom.getZ(),
                    phantom.getYRot(), 0.0F);
        } else {
            // Small pendulum so the hanging creeper does not look welded to the phantom.
            double sway = Math.sin(phantom.tickCount * 0.15D) * 0.12D;
            cargo.snapTo(phantom.getX() + sway, phantom.getY() + THROWER_CARRY_HEIGHT,
                    phantom.getZ(), phantom.getYRot(), 0.0F);
        }
    }

    /**
     * Hands the creeper back to the world: it loses its tag, its limbo and becomes an ordinary
     * creeper that walks, primes and chases on its own. Used both when the thrower drops it and when
     * the phantom dies - "the survivor carries on with vanilla AI".
     */
    public static void release(Creeper creeper) {
        // 26.3 exposes the persistent tag set through entityTags(); copy before removing.
        creeper.entityTags().stream().filter(tag -> tag.startsWith(TAG_PREFIX)).toList()
                .forEach(creeper::removeTag);
        creeper.setNoAi(false);
        creeper.setNoGravity(false);
    }

    /** Releases everything the phantom still owns - the reload-safe death cleanup. */
    public static void releaseAllOwned(Phantom phantom) {
        for (Creeper owned : ownedCreepers(phantom)) {
            release(owned);
        }
    }

    /** @return the phantom's live owned cargo, or {@code null}. */
    public static @Nullable Creeper findOwned(Phantom phantom) {
        for (Creeper candidate : ownedCreepers(phantom)) {
            if (candidate.isAlive()) {
                return candidate;
            }
        }
        return null;
    }

    private static List<Creeper> ownedCreepers(Phantom phantom) {
        String tag = tagFor(phantom);
        return phantom.level().getEntitiesOfClass(Creeper.class,
                phantom.getBoundingBox().inflate(SEARCH_RADIUS),
                candidate -> candidate.entityTags().contains(tag));
    }

    private static String tagFor(Phantom phantom) {
        UUID uuid = phantom.getUUID();
        return TAG_PREFIX + uuid;
    }

    private static ChimeraVariant variantOf(Phantom phantom) {
        return ((ChimeraPhantom) phantom).forbiddenChimera$variant();
    }
}
