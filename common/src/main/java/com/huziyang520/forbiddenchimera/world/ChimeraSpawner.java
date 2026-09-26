package com.huziyang520.forbiddenchimera.world;

import com.huziyang520.forbiddenchimera.config.ForbiddenChimeraConfig;
import com.huziyang520.forbiddenchimera.entity.ChimeraPhantom;
import com.huziyang520.forbiddenchimera.entity.ChimeraVariant;
import com.huziyang520.forbiddenchimera.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;

/**
 * The insomnia driven spawner that replaces the vanilla phantom.
 *
 * <p>Vanilla phantoms are <b>not</b> part of any biome spawn list: they are produced exclusively by
 * {@code PhantomSpawner}, a {@code CustomSpawner} owned by each {@code ServerLevel} that rolls
 * {@code 60 + rand(60)} second intervals, requires dark sky (or a sky-less dimension), a player above
 * sea level with open sky, a difficulty roll, and finally the
 * {@code floor(random * timeSinceRest) >= 72000} insomnia check.
 *
 * <p>{@link com.huziyang520.forbiddenchimera.mixin.PhantomSpawnerMixin} cancels that spawner and hands
 * control here, so the chimeras inherit every one of those rules verbatim - they simply take the
 * phantom's place in the world instead of spawning alongside it.
 *
 * <p>mob1 and mob2 spawn as real vanilla phantoms carrying a vanilla creeper: this spawner only
 * assigns the variant through {@link ChimeraPhantom#forbiddenChimera$setVariant(int)} and the
 * behaviour (cargo, goals) is attached by {@code PhantomMixin}. mob3 is a real entity type.
 *
 * <p>Sunlight is deliberately not a threat: daylight burning in 26.3 is driven by the
 * {@code minecraft:burn_in_daylight} entity type tag ({@code Mob#aiStep} checks it before calling
 * {@code burnUndead}), and none of the chimeras are added to that tag. The vanilla phantom is not in
 * the tag either, so that behaviour matches vanilla exactly.
 */
public final class ChimeraSpawner {

    /** Vanilla cadence: 60 to 120 seconds between attempts. */
    private static final int MIN_ATTEMPT_DELAY_TICKS = 60 * 20;
    private static final int ATTEMPT_DELAY_JITTER_TICKS = 60 * 20;
    /** Vanilla insomnia threshold, in ticks of {@code TIME_SINCE_REST}. */
    private static final int INSOMNIA_THRESHOLD = 72000;
    /** Vanilla spawn ring around the player. */
    private static final int MIN_SPAWN_HEIGHT = 20;
    private static final int SPAWN_HEIGHT_JITTER = 15;
    private static final int SPAWN_OFFSET_RADIUS = 10;

    private ChimeraSpawner() {
    }

    /**
     * Advances the spawner by one tick, mirroring {@code PhantomSpawner#tick}.
     *
     * @param level        the level to spawn in.
     * @param spawnEnemies whether the level currently allows hostile spawning at all.
     * @param nextTick     the countdown carried by the owning spawner instance.
     * @return the new countdown value.
     */
    public static int tick(ServerLevel level, boolean spawnEnemies, int nextTick) {
        if (!spawnEnemies) {
            return nextTick;
        }
        // Phantoms are gated behind their own game rule; the chimeras inherit it so server owners can
        // still switch the whole mechanic off. The toggle exists so a server owner who wants the
        // chimeras to ignore the vanilla phantom rule can decouple them.
        if (ForbiddenChimeraConfig.get().chimeraSpawnerUsesPhantomGameRule
                && !level.getGameRules().get(GameRules.SPAWN_PHANTOMS)) {
            return nextTick;
        }

        RandomSource random = level.getRandom();
        nextTick--;
        if (nextTick > 0) {
            return nextTick;
        }
        nextTick += MIN_ATTEMPT_DELAY_TICKS + random.nextInt(ATTEMPT_DELAY_JITTER_TICKS);

        // Phantoms only fly when the sky is dark enough, or in dimensions without a sky.
        if (level.getSkyDarken() < 5 && level.dimensionType().hasSkyLight()) {
            return nextTick;
        }

        ForbiddenChimeraConfig config = ForbiddenChimeraConfig.get();
        List<Candidate> candidates = candidates(config);
        if (candidates.isEmpty()) {
            return nextTick;
        }

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }

            BlockPos playerPos = player.blockPosition();
            if (level.dimensionType().hasSkyLight()
                    && (playerPos.getY() < level.getSeaLevel() || !level.canSeeSky(playerPos))) {
                continue;
            }

            DifficultyInstance difficulty = level.getCurrentDifficultyAt(playerPos);
            if (!difficulty.isHarderThan(random.nextFloat() * 3.0F)) {
                continue;
            }

            // Vanilla insomnia roll: the longer the player has stayed awake, the smaller the chance
            // that random.nextInt(...) still clears the 72000 tick bar.
            int timeSinceRest = Mth.clamp(
                    player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)), 1, Integer.MAX_VALUE);
            if (random.nextInt(timeSinceRest) < INSOMNIA_THRESHOLD) {
                continue;
            }

            BlockPos spawnPos = playerPos.above(MIN_SPAWN_HEIGHT + random.nextInt(SPAWN_HEIGHT_JITTER))
                    .east(-SPAWN_OFFSET_RADIUS + random.nextInt(SPAWN_OFFSET_RADIUS * 2 + 1))
                    .south(-SPAWN_OFFSET_RADIUS + random.nextInt(SPAWN_OFFSET_RADIUS * 2 + 1));

            Candidate candidate = pick(candidates, random);

            BlockState blockState = level.getBlockState(spawnPos);
            FluidState fluidState = level.getFluidState(spawnPos);
            EntityType<?> checkType = candidate.type() != null ? candidate.type() : EntityTypes.PHANTOM;
            if (!NaturalSpawner.isValidEmptySpawnBlock(level, spawnPos, blockState, fluidState, checkType)) {
                continue;
            }

            // Same leader plus followers rule as the vanilla phantom.
            int groupSize = 1 + random.nextInt(difficulty.getDifficulty().getId() + 1);
            SpawnGroupData groupData = null;
            for (int i = 0; i < groupSize; i++) {
                groupData = spawnOne(level, spawnPos, difficulty, candidate, groupData);
            }
        }

        return nextTick;
    }

    /** Weighted spawn table rebuilt on every attempt so config edits take effect without a restart. */
    private static List<Candidate> candidates(ForbiddenChimeraConfig config) {
        List<Candidate> candidates = new ArrayList<>(3);
        if (config.enablePhantomRiderCreeper && config.riderSpawnWeight > 0) {
            candidates.add(new Candidate(ChimeraVariant.RIDER, null, config.riderSpawnWeight));
        }
        if (config.enableCreeperThrowerPhantom && config.throwerSpawnWeight > 0) {
            candidates.add(new Candidate(ChimeraVariant.THROWER, null, config.throwerSpawnWeight));
        }
        if (config.enableCreeperPhantom && config.diverSpawnWeight > 0) {
            candidates.add(new Candidate(ChimeraVariant.UNDECIDED, ModEntities.creeperPhantom(),
                    config.diverSpawnWeight));
        }
        if (config.enableLightningCreeperPhantom && config.lightningDiverSpawnWeight > 0) {
            candidates.add(new Candidate(ChimeraVariant.UNDECIDED, ModEntities.lightningCreeperPhantom(),
                    config.lightningDiverSpawnWeight));
        }
        if (config.enableNuclearCreeperPhantom && config.nuclearSpawnWeight > 0) {
            candidates.add(new Candidate(ChimeraVariant.UNDECIDED, ModEntities.nuclearCreeperPhantom(),
                    config.nuclearSpawnWeight));
        }
        if (config.enableEndermanPhantom && config.endermanSpawnWeight > 0) {
            candidates.add(new Candidate(ChimeraVariant.UNDECIDED, ModEntities.endermanPhantom(),
                    config.endermanSpawnWeight));
        }
        return candidates;
    }

    private static @Nullable Candidate pick(List<Candidate> candidates, RandomSource random) {
        int total = 0;
        for (Candidate candidate : candidates) {
            total += candidate.weight();
        }
        int roll = random.nextInt(total);
        for (Candidate candidate : candidates) {
            roll -= candidate.weight();
            if (roll < 0) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static @Nullable SpawnGroupData spawnOne(ServerLevel level, BlockPos spawnPos,
                                                     DifficultyInstance difficulty, Candidate candidate,
                                                     @Nullable SpawnGroupData groupData) {
        Mob mob;
        if (candidate.type() != null) {
            // mob3: a real registered entity type.
            mob = candidate.type().create(level, EntitySpawnReason.NATURAL);
            if (mob == null) {
                return groupData;
            }
            mob.snapTo(spawnPos, 0.0F, 0.0F);
        } else {
            // mob1/mob2: a real vanilla phantom, tagged with its chimera variant. The cargo creeper
            // and the goals are attached by PhantomMixin on its first tick.
            Phantom phantom = EntityTypes.PHANTOM.create(level, EntitySpawnReason.NATURAL);
            if (phantom == null) {
                return groupData;
            }
            ((ChimeraPhantom) phantom).forbiddenChimera$setVariant(candidate.variant());
            phantom.snapTo(spawnPos, 0.0F, 0.0F);
            mob = phantom;
        }
        groupData = mob.finalizeSpawn(level, difficulty, EntitySpawnReason.NATURAL, groupData);
        level.addFreshEntity(mob);
        return groupData;
    }

    /**
     * One entry of the insomnia spawn table.
     *
     * @param variant the phantom chimera variant to stamp onto a vanilla phantom.
     * @param type    the entity type for real entities (mob3); {@code null} for phantom variants.
     * @param weight  strictly positive spawn weight.
     */
    private record Candidate(ChimeraVariant variant, @Nullable EntityType<? extends Mob> type, int weight) {
    }
}
