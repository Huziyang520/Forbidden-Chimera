package com.huziyang520.forbiddenchimera.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.huziyang520.forbiddenchimera.Constants;
import com.huziyang520.forbiddenchimera.entity.CreeperPhantomEntity;
import net.minecraft.resources.Identifier;

/**
 * GeckoLib model shared by every model based chimera.
 *
 * <p>Geometry, texture and animation set are all constructor parameters:
 * <ul>
 *   <li>the creeper / lightning / nuclear variants share one geometry and differ in texture and (for
 *       the nuclear one) animation set;</li>
 *   <li>the enderman variant reuses the <b>same bone names and the same wings and tail</b> but swaps the
 *       body and head, so it needs its own geometry file while still playing the shared animation set -
 *       which only works because the bone names are identical.</li>
 * </ul>
 * Nothing here is resolved per frame: all three ids are handed in once at construction.
 */
public class ChimeraPhantomGeoModel extends GeoModel<CreeperPhantomEntity> {

    // GeckoLib 5 moved its assets into a dedicated root and derives the cache key by stripping
    //   prefix  ^(geckolib/)((animations/)|(models/))?
    //   suffix  ((\.geo)|((\.animation)s?))?(\.json)$
    // so the ids below are simply the bare names, and the files live in
    //   assets/<ns>/geckolib/models/<name>.geo.json
    //   assets/<ns>/geckolib/animations/<name>.animation.json
    /** Geometry of the creeper chimera family; also used by the lightning and nuclear variants. */
    public static final Identifier CREEPER_GEOMETRY = Constants.id("creeper_phantom");
    /** Geometry of the enderman chimera: identical wings and tail, enderman body and head. */
    public static final Identifier ENDERMAN_GEOMETRY = Constants.id("enderman_phantom");
    /** Geometry of the boss: creeper phantom base plus the head cluster and rocket. */
    public static final Identifier BOSS_GEOMETRY = Constants.id("boss_lightning_creeper_phantom_knight");

    /** Animation set of the creeper phantom and its lightning sibling. */
    public static final Identifier PLAIN_ANIMATION = Constants.id("creeper_phantom");
    /** Animation set of the nuclear variant (fly / hover / charge / detonate). */
    public static final Identifier NUCLEAR_ANIMATION = Constants.id("nuclear_creeper_phantom");
    /** Animation set of the boss (fly / dive / volley / dragon breath / sonic boom / darkness / dash). */
    public static final Identifier BOSS_ANIMATION = Constants.id("boss_lightning_creeper_phantom_knight");

    /** Shared texture of the creeper phantom and its lightning sibling. */
    public static final Identifier PLAIN_TEXTURE = Constants.id("textures/entity/creeper_phantom.png");
    /** Nuclear variant: same pattern, red filtered on the creeper torso and head only. */
    public static final Identifier NUCLEAR_TEXTURE = Constants.id("textures/entity/nuclear_creeper_phantom.png");
    /** Enderman variant: vanilla enderman skin on the body and head, phantom pixels untouched. */
    public static final Identifier ENDERMAN_TEXTURE = Constants.id("textures/entity/enderman_phantom.png");
    /** Boss texture: creeper phantom base plus the vanilla head textures it assembles. */
    public static final Identifier BOSS_TEXTURE =
            Constants.id("textures/entity/boss_lightning_creeper_phantom_knight.png");

    private final Identifier geometry;
    private final Identifier texture;
    private final Identifier animation;

    /** Creeper family convenience constructor: the shared geometry is implied. */
    public ChimeraPhantomGeoModel(Identifier texture, Identifier animation) {
        this(CREEPER_GEOMETRY, texture, animation);
    }

    public ChimeraPhantomGeoModel(Identifier geometry, Identifier texture, Identifier animation) {
        this.geometry = geometry;
        this.texture = texture;
        this.animation = animation;
    }

    @Override
    public Identifier getModelResource(GeoRenderState state) {
        return this.geometry;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state) {
        return this.texture;
    }

    @Override
    public Identifier getAnimationResource(CreeperPhantomEntity animatable) {
        return this.animation;
    }
}
