package com.huziyang520.forbiddenchimera.platform.services;

import net.minecraft.world.item.Item;

/**
 * Builds an item from properties that already carry its id.
 *
 * <p>This exists instead of {@code java.util.function.Function<Item.Properties, Item>} on purpose: in
 * this multi-loader project the Fabric and NeoForge modules compile against <b>different</b> Minecraft
 * artifacts, so a generic type argument spelled with {@code Item.Properties} is not necessarily the same
 * type in both. The Java compiler copes (erasure makes the signatures identical), but the IDE's compiler
 * reports a bogus "name clash" for the Fabric implementation. Naming the type once, here in the shared
 * module, removes the ambiguity entirely.
 */
@FunctionalInterface
public interface ItemFactory {

    /** @param properties item properties that already have their registry id set. */
    Item create(Item.Properties properties);
}
