package net.morimori0317.bestylewither.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.EquipmentDispenseItemBehavior;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.morimori0317.bestylewither.BEStyleWither;
import net.morimori0317.bestylewither.entity.BEWitherBossEntity;
import net.morimori0317.bestylewither.entity.BEWitherSkeletonEntity;
import net.morimori0317.bestylewither.neoforge.handler.BEWitherHostilityHandler;
import net.morimori0317.bestylewither.neoforge.handler.BEWitherSummonHandler;
import net.morimori0317.bestylewither.neoforge.handler.CommonHandlerNeoForge;
import net.morimori0317.bestylewither.neoforge.networking.BSWPacketsNeoForge;
import net.morimori0317.bestylewither.registry.BSWEntities;

@Mod(BEStyleWither.MODID)
public final class BEStyleWitherNeoForge {
    public static final BESConfigNeoForge CONFIG = new BESConfigNeoForge();

    public BEStyleWitherNeoForge(IEventBus modEventBus, ModContainer container) {
        BESConfigNeoForge.init(container);
        BEStyleWither.init();
        BSWPacketsNeoForge.init();
        BSWEntities.ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(BEStyleWitherNeoForge::registerEntityAttributes);
        modEventBus.addListener(BEStyleWitherNeoForge::commonSetup);
        modEventBus.addListener(CommonHandlerNeoForge::registerPayload);
        NeoForge.EVENT_BUS.register(BEWitherSummonHandler.class);
        NeoForge.EVENT_BUS.register(BEWitherHostilityHandler.class);
    }

    private static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(BSWEntities.BE_WITHER.get(), BEWitherBossEntity.createAttributes().build());
        event.put(BSWEntities.BE_WITHER_SKELETON.get(), BEWitherSkeletonEntity.createAttributes().build());
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> DispenserBlock.registerBehavior(
                Items.SKELETON_SKULL,
                new OptionalDispenseItemBehavior() {
                    @Override
                    protected ItemStack execute(BlockSource source, ItemStack dispensed) {
                        Level level = source.level();
                        Direction direction = source.state().getValue(DispenserBlock.FACING);
                        BlockPos target = source.pos().relative(direction);
                        if (level.isEmptyBlock(target) && BEWitherSummonHandler.canSpawnMob(level, target, dispensed)) {
                            level.setBlock(
                                    target,
                                    Blocks.SKELETON_SKULL.defaultBlockState()
                                            .setValue(SkullBlock.ROTATION, RotationSegment.convertToSegment(direction)),
                                    3
                            );
                            level.gameEvent(null, GameEvent.BLOCK_PLACE, target);
                            BEWitherSummonHandler.trySpawn(level, target);
                            dispensed.shrink(1);
                            this.setSuccess(true);
                        } else {
                            this.setSuccess(EquipmentDispenseItemBehavior.dispenseEquipment(source, dispensed));
                        }
                        return dispensed;
                    }
                }
        ));
    }
}
