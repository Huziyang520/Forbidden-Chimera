package com.huziyang520.forbiddenchimera.item;

import com.huziyang520.forbiddenchimera.entity.ChimeraPhantom;
import com.huziyang520.forbiddenchimera.entity.ChimeraVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Spawn egg for the vanilla-combination chimeras.
 *
 * <p>mob1 and mob2 are not registered entity types - they are real vanilla phantoms stamped with a
 * {@link ChimeraVariant} - so the data driven {@code SpawnEggItem}, which reads its entity type from
 * {@code minecraft:entity_data}, cannot express them. This egg spawns a vanilla phantom and assigns
 * the variant directly.
 */
public class ChimeraSpawnEggItem extends Item {

    private final ChimeraVariant variant;

    public ChimeraSpawnEggItem(Properties properties, ChimeraVariant variant) {
        super(properties);
        this.variant = variant;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clicked = context.getClickedPos();
        BlockState state = level.getBlockState(clicked);
        BlockPos spawnPos = state.getCollisionShape(level, clicked).isEmpty()
                ? clicked
                : clicked.relative(context.getClickedFace());

        Phantom phantom = EntityTypes.PHANTOM.create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
        if (phantom == null) {
            return InteractionResult.FAIL;
        }
        ((ChimeraPhantom) phantom).forbiddenChimera$setVariant(this.variant);
        phantom.snapTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!serverLevel.addFreshEntity(phantom)) {
            return InteractionResult.FAIL;
        }

        Player player = context.getPlayer();
        context.getItemInHand().consume(1, player);
        level.gameEvent(player, GameEvent.ENTITY_PLACE, spawnPos);
        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResult.SUCCESS;
    }
}
