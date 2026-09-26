package com.huziyang520.forbiddenchimera.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.huziyang520.forbiddenchimera.Constants;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plain JSON configuration written to {@code config/forbidden_chimera.json}.
 *
 * <p>Gson is used instead of night-config on purpose: night-config is only shipped by NeoForge, and
 * this class lives in the shared {@code common} module which also has to run on Fabric.
 */
public final class ForbiddenChimeraConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = Constants.MOD_ID + ".json";

    private static ForbiddenChimeraConfig instance = new ForbiddenChimeraConfig();
    private static Path configFile;

    // --- mob toggles -------------------------------------------------------
    public boolean enablePhantomRiderCreeper = true;
    public boolean enableCreeperThrowerPhantom = true;
    public boolean enableCreeperPhantom = true;
    public boolean enableLightningCreeperPhantom = true;

    // --- shared behaviour --------------------------------------------------
    /**
     * Vanilla combat targeting never sees a creative player as an enemy
     * ({@code Player#canBeSeenAsEnemy} returns false while invulnerable), so the chimeras look
     * completely passive when they are tested from creative mode. Keep this on while testing and set
     * it to false for vanilla-like behaviour in normal play.
     */
    public boolean attackCreativePlayers = true;

    // --- phantom model geometry (read-only facts, kept here as documentation) ----------
    //
    // Vanilla 26.3 renders every living entity through:
    //     poseStack.scale(state.scale, ...);
    //     setupRotations(...);
    //     poseStack.scale(-1, -1, 1);
    //     this.scale(state, poseStack);        <- PhantomRenderer translates +1.3125 here
    //     poseStack.translate(0, -1.501, 0);   <- and this runs afterwards
    // so the phantom model lands at  1.501 - 1.3125 = +0.1885  (centre), spanning
    //     +0.0635 .. +0.3135 above the phantom's feet
    // which is inside its 0.9 x 0.5 hitbox (0 .. 0.5). Vanilla is therefore already aligned: the
    // 1.3125 must NOT be patched, and the carry heights below are tuned against this band.

    // --- phantom rider creeper --------------------------------------------
    /** Move speed modifier handed to the flying move control while charging. */
    public double riderChargeSpeed = 1.6D;
    /** Squared distance at which the carried creeper is detonated. */
    public double riderDetonateDistanceSqr = 4.0D;

    // --- creeper thrower phantom ------------------------------------------
    /** How many blocks above the target the thrower hovers before throwing. */
    public double throwerHoverHeight = 6.0D;
    public double throwerApproachSpeed = 1.2D;
    public int throwerThrowCooldownTicks = 160;
    /** Initial speed of a thrown creeper. */
    public double throwerThrowSpeed = 0.9D;

    // --- creeper phantom ---------------------------------------------------
    /** How many blocks above the target the dive starts. */
    public double diverClimbHeight = 8.0D;
    /**
     * Horizontal distance from the target at which the phantom takes up its attack position.
     *
     * <p>It hovers this far away (diagonally above the player) and then dives in, so its shots come
     * from a distance instead of from straight overhead. 0 would put it directly above the target.
     */
    public double diverApproachOffset = 12.0D;
    public double diverClimbSpeed = 1.4D;
    public double diverDiveSpeed = 1.8D;
    /**
     * Stop firing once the dive closes to within this many blocks (horizontal distance). Shots fired
     * from directly above the player are nearly undodgeable, so the phantom only shoots on approach.
     */
    public double diverFireMinDistance = 7.0D;
    /**
     * How long a dive run may continue after its single shot before the phantom breaks off.
     *
     * <p>This used to be a "pull back to the attack position" hold, which made the phantom fly away
     * from the player and come back - the player read that loop as the mob circling aimlessly. Now the
     * phantom simply keeps pressing and passes by the target once, then breaks off.
     */
    public int diverPassByTicks = 40;
    public int diverFireCooldownTicks = 60;
    /** Dive is aborted after this many ticks even without a hit. */
    public int diverMaxDiveTicks = 80;
    /** Ticks spent circling between two dive runs. Higher makes the mob far less relentless. */
    public int diverDiveCooldownTicks = 180;

    // --- 持矛变体 (spear variant, mob3 / mob4) -----------------------------
    /**
     * Whether Creeper Phantoms may roll the spear variant at all.
     *
     * <p>The variant is a <b>weapon</b> variant, not a separate mob: the chimera keeps its model and
     * AI, and simply carries one of the seven vanilla spears under a wing. On a dive the contact hit
     * is then resolved with the spear's own kinetic weapon data instead of the default melee attack.
     */
    public boolean enableSpearVariant = true;
    /** Chance per freshly spawned Creeper Phantom / Lightning Creeper Phantom to carry a spear. */
    public double spearVariantChance = 0.10D;
    /**
     * Extra multiplier applied on top of the spear's own dive damage. Purely a tuning dial: 1.0 means
     * "exactly what the spear's {@code kinetic_weapon} component says". Never below 0.
     */
    public double spearDiveDamageScale = 1.0D;

    // --- 苦力怕幻翼骑士变体 (knight variant, mob3) --------------------------
    /**
     * Whether the Creeper Phantom may roll the knight variant at all - a vanilla baby zombie riding on
     * its back, swinging a mace as the chimera dives.
     *
     * <p>Like the spear variant this is a <b>variant</b> of an existing chimera, not a new mob: no new
     * entity type is registered. The two variants are mutually exclusive - a knight never carries a
     * spear as well.
     */
    public boolean enableKnightVariant = true;
    /**
     * Chance per freshly spawned Creeper Phantom to be the knight variant.
     *
     * <p>Not fixed by the design plan, which only specifies the behaviour: this is the spawn dial, set
     * to the same order of magnitude as the spear variant so both show up at a similar rate.
     */
    public double knightVariantChance = 0.10D;
    /**
     * Base chance the rider rolls armour, replacing vanilla {@code Mob.MAX_WEARING_ARMOR_CHANCE}
     * (0.15F, still scaled by the local difficulty multiplier exactly like vanilla).
     */
    public float knightArmorChance = 0.15F;
    /** Chance the rider's mace is enchanted on spawn; vanilla weapon value ({@code MAX_ENCHANTED_WEAPON_CHANCE}). */
    public float knightWeaponEnchantChance = 0.25F;
    /** Chance each piece of the rider's armour is enchanted on spawn; vanilla armour value. */
    public float knightArmorEnchantChance = 0.5F;
    /** How high above the target the knight climbs before committing to the charge. */
    public double knightClimbHeight = 10.0D;
    public double knightClimbSpeed = 1.4D;
    /** Speed of the downward charge. Must be fast: the mace smash depends on the fall. */
    public double knightChargeSpeed = 2.0D;
    /** Ticks between two charge attempts. The roll below only happens once per period. */
    public int knightChargeCooldownTicks = 200;
    /**
     * Chance that a charge attempt actually happens once the cooldown expires. Deliberately low: the
     * knight is a rare event, the mount keeps firing skulls the rest of the time.
     */
    public double knightChargeChance = 0.35D;
    /** A charge that never connects is broken off after this many ticks. */
    public int knightMaxChargeTicks = 80;
    /**
     * Descent the rider needs for the mace smash to count ({@code MaceItem} only adds its fall bonus
     * above 1.5 blocks). Below this the strike is treated as a plain mace hit.
     */
    public double knightSmashMinFallDistance = 1.5D;
    /**
     * Whether a dead rider is replaced. Off by default: the design is "whichever of the two dies, the
     * other carries on", and a knight is a one-off encounter, not a recurring threat.
     */
    public boolean knightRespawnRider = false;

    // --- 末影人幻翼 (enderman phantom) ------------------------------------
    public boolean enableEndermanPhantom = true;
    /**
     * Chance per server AI step of a random idle teleport. Keep it tiny: the point of the variant is
     * that it dodges projectiles, not that it flickers around the sky.
     */
    public double endermanIdleTeleportChance = 0.002D;

    // --- 核弹苦力怕幻翼 (nuclear creeper phantom) --------------------------
    public boolean enableNuclearCreeperPhantom = true;
    /** Height above the target it hovers at before diving in; the dive is diagonal, see the offset. */
    public double nuclearHoverHeight = 10.0D;
    /**
     * Horizontal distance from the target at which it hovers before the dive.
     *
     * <p>Deliberately not 0: hovering straight above the target made the attack a purely vertical drop,
     * so the player never saw it coming. With this offset the dive is a diagonal that stays in view -
     * 10.0 up / 8.0 sideways by default is roughly 51 degrees.
     */
    public double nuclearHoverOffset = 8.0D;
    /** Cruise speed while circling and hovering; slow on purpose, it is a showpiece. */
    public double nuclearApproachSpeed = 1.0D;
    /** How long it circles at altitude before committing to the attack. */
    public int nuclearCircleTicks = 100;
    /** How long it hovers - the wing flap accelerates over exactly this span - before the charge. */
    public int nuclearHoverTicks = 80;
    /** Charge speed. It is a missile at this point; nothing about it is dodgeable. */
    public double nuclearChargeSpeed = 2.2D;
    /** The charge self-detonates after this many ticks even if it never touches the target. */
    public int nuclearChargeMaxTicks = 60;
    /** Blast radius of the nuclear charge. Deliberately huge. */
    public double nuclearExplosionRadius = 8.0D;
    /** Wing flap speed at the end of the hover ramp (it starts at 1.0). */
    public double nuclearFlapSpeedMax = 3.0D;

    // --- 首领 · 闪电苦力怕幻翼骑士 (boss) -----------------------------------
    // There is deliberately no enable toggle and no spawn weight: the boss never spawns naturally, it
    // can only be summoned with the 禁忌板材 item.
    // Health (150) is a design constant in ModAttributes, not a config value.
    /**
     * Fraction of explosion damage the boss actually takes. 0.1 = a 90% reduction, applied to
     * <b>every</b> explosion (vanilla TNT, creepers, its own skulls), not just this mod's blasts.
     */
    public double bossExplosionDamageFactor = 0.1D;
    /** Height above the target it circles at between attacks. */
    public double bossOrbitHeight = 12.0D;
    /** Horizontal radius of that orbit. */
    public double bossOrbitRadius = 16.0D;
    /** Cruise speed while orbiting. */
    public double bossOrbitSpeed = 1.2D;
    /**
     * How long it orbits before committing to a special attack.
     *
     * <p>Lowered from 80 to 30 on 2026-09-26: the player reported that everything except the lightning
     * skull side arm (which runs on its own beat) happened far too rarely. The orbit was the single
     * biggest dead time in the cycle, and cutting it is what actually raises the frequency of the
     * salvo / dragon breath / darkness / rocket-dive rotation.
     */
    public int bossOrbitTicks = 30;
    /**
     * 平时（非俯冲）的常驻射击间隔：每隔这么多 tick 发射一发<b>闪电苦力怕头颅</b>。
     *
     * <p>这是玩家最常看到的那一招；<b>凋灵之手</b>是连发，见下面的 salvo 两项。
     */
    public int bossSideArmCooldownTicks = 60;
    /** 连发（齐射）里发射多少颗<b>凋灵之手</b>。 */
    public int bossSalvoCount = 5;
    /** 连发中每两颗凋灵之手之间的间隔。 */
    public int bossSalvoIntervalTicks = 3;
    /** Dragon fireballs per breath attack. */
    public int bossDragonBreathCount = 3;
    /** Ticks between two dragon fireballs. */
    public int bossDragonBreathIntervalTicks = 8;
    /**
     * Tick inside the dragon breath animation at which the first fireball leaves the mouth.
     *
     * <p>Matches the asset: {@code dragon_breath} runs 2 s and holds the jaws open from 0.35 s to
     * 1.6 s, so 7 ticks (0.35 s) is the first frame the shot is visible leaving the mouth. The server
     * timeline drives the shot, the client animation only mirrors it.
     */
    public int bossDragonBreathFirstTick = 7;
    /** Tick inside the sonic boom animation at which the blast goes off. */
    public int bossSonicBoomTick = 20;
    /** Radius of the darkness pulse (the effect is applied to players inside it). */
    public double bossDarknessRadius = 16.0D;
    /** Duration of the darkness the boss applies. */
    public int bossDarknessDurationTicks = 200;
    /** How long the rocket boost burn lasts before the dive commits. */
    public int bossRocketBoostTicks = 24;
    /** Dive speed multiplier while the rockets are burning. */
    public double bossRocketBoostMultiplier = 1.6D;
    /** Speed of the dive itself. */
    public double bossDiveSpeed = 2.0D;
    /** The dive is broken off after this many ticks even if it never connects. */
    public int bossMaxDiveTicks = 80;

    // --- natural spawning (replaces the vanilla phantom spawner) ----------
    /**
     * Weight of each chimera in the insomnia spawn table. A weight of 0 removes that chimera from
     * natural spawning without touching its {@code enable*} toggle.
     */
    public int riderSpawnWeight = 3;
    public int throwerSpawnWeight = 3;
    public int diverSpawnWeight = 4;
    /** Naturally rarer than the plain diver: it hits much harder. */
    public int lightningDiverSpawnWeight = 1;
    /** Rarest of all: the nuclear variant is a spectacle, not a common encounter. */
    public int nuclearSpawnWeight = 1;
    public int endermanSpawnWeight = 3;
    /**
     * Additional group members are rolled exactly like the vanilla phantom
     * ({@code 1 + difficultyId} total), so only the leader's type is weighted.
     */
    public boolean chimeraSpawnerUsesPhantomGameRule = true;

    // --- carried creeper placement ---------------------------------------
    // NOT configurable on purpose. The two carry heights are pure geometry off the phantom model band
    // (see PhantomCargo: RIDER_CARRY_HEIGHT = 0.3135, THROWER_CARRY_HEIGHT = -1.6365). A config value
    // here would silently keep an old number after a model change and misalign the creeper with no
    // warning - which is exactly what happened once already.

    // --- creeper skull projectile -----------------------------------------
    /** 0 = flies straight, 1 = full homing. Keep it low: the skull only tracks slightly. */
    public double skullHomingStrength = 0.06D;
    /** Lower than a vanilla creeper (3). Terrain is still destroyed and every block still drops. */
    public double skullExplosionRadius = 2.0D;
    public int skullMaxLifetimeTicks = 100;
    /**
     * Fraction of its own blast damage the shooter takes. 0.1 means the Creeper Phantom only eats 10%
     * of the damage from the skulls it fired, so it can no longer kill itself mid dive.
     */
    public double skullOwnerDamageFactor = 0.1D;

    // --- lightning creeper skull (闪电苦力怕头颅) ---------------------------
    /** Higher than the plain skull (2.0): the charged variant is meant to hit clearly harder. */
    public double lightningSkullExplosionRadius = 3.0D;
    /** 0.02 = the shooter takes only 2% of its own lightning skull blast (98% reduction). */
    public double lightningSkullOwnerDamageFactor = 0.02D;

    private ForbiddenChimeraConfig() {
    }

    public static ForbiddenChimeraConfig get() {
        return instance;
    }

    public static Path file() {
        return configFile;
    }

    public static void load(Path configDir) {
        configFile = configDir.resolve(FILE_NAME);
        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                instance = readMerged(reader);
            } catch (Exception exception) {
                Constants.LOG.error("Could not read {}, falling back to defaults", configFile, exception);
                instance = new ForbiddenChimeraConfig();
            }
        } else {
            instance = new ForbiddenChimeraConfig();
        }

        save();
    }

    /**
     * Reads a stored config and back-fills every key the file does not have yet.
     *
     * <p>Why this exists: Gson builds the target through {@code Unsafe} and never runs field
     * initialisers, so a key that is <b>missing</b> from the file silently becomes {@code false} /
     * {@code 0} instead of its declared default. Upgrading the mod then looks like "the new feature is
     * broken" - a config written before the spear/knight variants existed turned
     * {@code enableSpearVariant} / {@code enableKnightVariant} off and their chances to zero, so no
     * chimera ever rolled a variant again. Merging the missing keys in first, then writing the file
     * back, keeps old configs working and makes new keys visible to the player.
     */
    private static ForbiddenChimeraConfig readMerged(Reader reader) {
        JsonObject stored = GSON.fromJson(reader, JsonObject.class);
        if (stored == null) {
            return new ForbiddenChimeraConfig();
        }

        JsonObject defaults = GSON.toJsonTree(new ForbiddenChimeraConfig()).getAsJsonObject();
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : defaults.entrySet()) {
            if (!stored.has(entry.getKey())) {
                stored.add(entry.getKey(), entry.getValue());
                added.add(entry.getKey());
            }
        }
        if (!added.isEmpty()) {
            Constants.LOG.info("Added {} new config key(s) to {}: {}",
                    added.size(), FILE_NAME, String.join(", ", added));
        }

        ForbiddenChimeraConfig merged = GSON.fromJson(stored, ForbiddenChimeraConfig.class);
        return merged != null ? merged : new ForbiddenChimeraConfig();
    }

    public static void save() {
        if (configFile == null) {
            return;
        }

        try {
            Files.createDirectories(configFile.getParent());
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException exception) {
            Constants.LOG.error("Could not write {}", configFile, exception);
        }
    }

    public static void resetDefaults() {
        instance = new ForbiddenChimeraConfig();
        save();
    }
}
