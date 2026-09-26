package com.huziyang520.forbiddenchimera.item;

import com.huziyang520.forbiddenchimera.entity.BossLightningCreeperPhantomKnightEntity;
import com.huziyang520.forbiddenchimera.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 禁忌板材 - the only way to summon the boss.
 *
 * <p>Hold right click for three seconds and it tears the boss out of the sky a short distance in front
 * of wherever the player is aiming. The long press is deliberate: a one click summon would be far too
 * easy to trigger by accident, and the boss is not something a player wants to meet while sorting a
 * chest.
 *
 * <p>The item is <b>consumed</b> on a successful summon, like a spawn egg: the recipe costs a dragon
 * head, so an unlimited use item would be a permanent boss on demand. This is a design choice, not a
 * vanilla rule, and is recorded in the project decision log.
 */
public class ForbiddenPlankItem extends Item {

    /** Ticks the player has to hold the button: 3 seconds. */
    private static final int SUMMON_TICKS = 60;
    /** How far in front of the player the summon point is looked for, in blocks. */
    private static final double SUMMON_RANGE = 10.0D;

    public ForbiddenPlankItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * Right clicking a block also starts the hold, so the player can aim at the ground and summon there
     * instead of always getting the boss dropped at the aim ray's far end.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        player.startUsingItem(context.getHand());
        return InteractionResult.CONSUME;
    }

    /** Same as {@link #useOn}: using it in empty air starts the same hold. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    /** The "long press" itself. */
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return SUMMON_TICKS;
    }

    /** The bow pose reads clearly as "charging something up" and exists in vanilla. */
    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    /** The hold completed: find the aim point and put the boss there. */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof Player player)) {
            return stack;
        }

        Vec3 target = this.aimPoint(player);
        BossLightningCreeperPhantomKnightEntity boss =
                ModEntities.bossLightningCreeperPhantomKnight()
                        .create(serverLevel, EntitySpawnReason.MOB_SUMMONED);
        if (boss == null) {
            return stack;
        }

        boss.snapTo(target.x, target.y, target.z, player.getYRot(), 0.0F);
        // A summoned boss must not be despawned by the distance check; it is an encounter, not a spawn.
        boss.setPersistenceRequired();
        serverLevel.addFreshEntity(boss);

        serverLevel.playSound(null, target.x, target.y, target.z, SoundEvents.WITHER_SPAWN,
                SoundSource.HOSTILE, 4.0F, 1.0F);
        stack.consume(1, player);
        return stack;
    }

    /**
     * Where the boss appears.
     *
     * <p>Aim at a block within range and it lands on top of that block - the readable, intentional case.
     * Aiming at the sky falls back to the far end of the aim ray, so the boss is never summoned on top of
     * the player.
     */
    private Vec3 aimPoint(Player player) {
        HitResult hit = player.pick(SUMMON_RANGE, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit) {
            return Vec3.atBottomCenterOf(blockHit.getBlockPos().above());
        }
        return hit.getLocation();
    }
}
