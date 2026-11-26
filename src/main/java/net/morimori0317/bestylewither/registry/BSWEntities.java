package net.morimori0317.bestylewither.registry;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.morimori0317.bestylewither.BEStyleWither;
import net.morimori0317.bestylewither.entity.BEWitherBossEntity;
import net.morimori0317.bestylewither.entity.BEWitherSkeletonEntity;
import net.morimori0317.bestylewither.entity.BEWitherSkullEntity;

public final class BSWEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(BEStyleWither.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<BEWitherBossEntity>> BE_WITHER =
            ENTITY_TYPES.registerEntityType(
                    "be_wither",
                    BEWitherBossEntity::new,
                    MobCategory.MONSTER,
                    builder -> builder
                            .fireImmune()
                            .immuneTo(BlockTags.WITHER_IMMUNE_TO)
                            .sized(1.0F, 3.0F)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .notInPeaceful()
            );

    public static final DeferredHolder<EntityType<?>, EntityType<BEWitherSkullEntity>> BE_WITHER_SKULL =
            ENTITY_TYPES.registerEntityType(
                    "be_wither_skull",
                    BEWitherSkullEntity::new,
                    MobCategory.MISC,
                    builder -> builder
                            .noLootTable()
                            .sized(0.3125F, 0.3125F)
                            .clientTrackingRange(4)
                            .updateInterval(10)
            );

    public static final DeferredHolder<EntityType<?>, EntityType<BEWitherSkeletonEntity>> BE_WITHER_SKELETON =
            ENTITY_TYPES.registerEntityType(
                    "be_wither_skeleton",
                    BEWitherSkeletonEntity::new,
                    MobCategory.MONSTER,
                    builder -> builder
                            .fireImmune()
                            .immuneTo(BlockTags.WITHER_IMMUNE_TO)
                            .sized(0.7F, 2.4F)
                            .eyeHeight(2.1F)
                            .ridingOffset(-0.875F)
                            .clientTrackingRange(8)
                            .notInPeaceful()
            );

    private BSWEntities() {
    }
}
