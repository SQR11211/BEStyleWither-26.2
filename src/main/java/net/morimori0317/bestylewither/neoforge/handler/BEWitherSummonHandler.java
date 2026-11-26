package net.morimori0317.bestylewither.neoforge.handler;

import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.morimori0317.bestylewither.entity.BEWitherBossEntity;
import net.morimori0317.bestylewither.registry.BSWEntities;
import org.jspecify.annotations.Nullable;

/**
 * Summons the independent {@link BEWitherBossEntity} when 3 regular
 * {@link Blocks#SKELETON_SKULL} / {@link Blocks#SKELETON_WALL_SKULL} blocks are
 * placed (by hand or via dispenser) on a Soul Sand / Soul Soil T-structure,
 * leaving Vanilla Wither summoning completely untouched (Special Rule #3).
 */
public final class BEWitherSummonHandler {
    private static @Nullable BlockPattern beWitherPatternFull;
    private static @Nullable BlockPattern beWitherPatternBase;

    private BEWitherSummonHandler() {
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        boolean isSkeletonSkull = placed.is(Blocks.SKELETON_SKULL) || placed.is(Blocks.SKELETON_WALL_SKULL);
        if (isSkeletonSkull) {
            trySpawn(level, event.getPos());
        }
    }

    public static boolean canSpawnMob(Level level, BlockPos pos, ItemStack itemStack) {
        return itemStack.is(Items.SKELETON_SKULL)
                && pos.getY() >= level.getMinY() + 2
                && level.getDifficulty() != Difficulty.PEACEFUL
                && !level.isClientSide()
                && getOrCreatePatternBase().find(level, pos) != null;
    }

    public static void trySpawn(Level level, BlockPos pos) {
        if (level.isClientSide() || pos.getY() < level.getMinY() || level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.SKELETON_SKULL) && !state.is(Blocks.SKELETON_WALL_SKULL)) {
            return;
        }

        BlockPattern.BlockPatternMatch match = getOrCreatePattern().find(level, pos);
        if (match == null) {
            return;
        }

        BEWitherBossEntity wither = BSWEntities.BE_WITHER.get().create(level, EntitySpawnReason.TRIGGERED);
        if (wither != null) {
            CarvedPumpkinBlock.clearPatternBlocks(level, match);
            BlockPos spawnPos = match.getBlock(1, 2, 0).getPos();
            float yaw = match.getForwards().getAxis() == Direction.Axis.X ? 0.0F : 90.0F;
            wither.snapTo(
                    spawnPos.getX() + 0.5,
                    spawnPos.getY() + 0.55,
                    spawnPos.getZ() + 0.5,
                    yaw,
                    0.0F
            );
            wither.yBodyRot = yaw;
            if (level instanceof ServerLevel serverLevel) {
                wither.applyDifficultyHealth(serverLevel);
            }
            wither.makeInvulnerable();

            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, wither.getBoundingBox().inflate(50.0))) {
                CriteriaTriggers.SUMMONED_ENTITY.trigger(player, wither);
            }

            level.addFreshEntity(wither);
            CarvedPumpkinBlock.updatePatternBlocks(level, match);
        }
    }

    private static BlockPattern getOrCreatePattern() {
        if (beWitherPatternFull == null) {
            beWitherPatternFull = BlockPatternBuilder.start()
                    .aisle("^^^", "###", "~#~")
                    .where('#', block -> block.getState().is(BlockTags.WITHER_SUMMON_BASE_BLOCKS))
                    .where(
                            '^',
                            BlockInWorld.hasState(
                                    BlockStatePredicate.forBlock(Blocks.SKELETON_SKULL)
                                            .or(BlockStatePredicate.forBlock(Blocks.SKELETON_WALL_SKULL))
                            )
                    )
                    .where('~', block -> block.getState().isAir())
                    .build();
        }
        return beWitherPatternFull;
    }

    private static BlockPattern getOrCreatePatternBase() {
        if (beWitherPatternBase == null) {
            beWitherPatternBase = BlockPatternBuilder.start()
                    .aisle("   ", "###", "~#~")
                    .where('#', block -> block.getState().is(BlockTags.WITHER_SUMMON_BASE_BLOCKS))
                    .where('~', block -> block.getState().isAir())
                    .build();
        }
        return beWitherPatternBase;
    }
}
