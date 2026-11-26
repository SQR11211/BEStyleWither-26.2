package net.morimori0317.bestylewither.entity.goal;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.morimori0317.bestylewither.BEStyleWither;
import net.morimori0317.bestylewither.entity.BEWitherBossEntity;
import net.morimori0317.bestylewither.entity.BEWitherSkeletonEntity;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

/**
 * Authentic Bedrock Edition Wither combat rhythm (modeled after BDS WitherBoss.h):
 * - Phase 1 flies 4-6 blocks above target; Phase 2 hugs ground level within melee reach
 * - Variable HP-tiered cadence ("fast vs slow" beat): 3 black skulls (20/15/10 ticks, resetting to slow at half HP)
 *   -> slower pause (30/24/20 ticks) -> center blue skull -> post-volley linger (30/26/22 ticks) -> relocate / dash
 * - Phase 2 triggers horizontal dash / ground-dive dash every second volley (6x8x6 destruct, stops on obsidian)
 */
public class BEWitherCombatGoal extends Goal {
    private enum State {
        RELOCATE,
        VOLLEY,
        DASH_WINDUP,
        DASH_ACTIVE
    }

    private static final int DASH_WINDUP_TICKS = 20;
    private static final int MAX_DASH_TICKS = 20;
    private static final double DASH_SPEED = 1.3;

    private final BEWitherBossEntity mob;
    private State state = State.RELOCATE;
    private int stateTicks;
    private int shotCooldown;
    private int shotInVolley;
    private int lastFiredHead;
    private int framesTillMove;
    private boolean secondVolley;
    private boolean seenPhaseTwo;
    private @Nullable Vec3 anchorPos;
    private @Nullable Vec3 dashDirection;
    private float dashBodyRot;
    private int nextBreakSoundTick;
    private boolean isGroundDive;
    private int dashActiveDuration;
    private final Set<UUID> dashHitEntities = new HashSet<>();

    public BEWitherCombatGoal(BEWitherBossEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.getInvulnerableTicks() > 0
                || this.mob.isDiving()
                || this.mob.isPostDiveStunned()
                || this.mob.isDeadOrDying()) {
            return false;
        }
        LivingEntity target = this.mob.getTarget();
        return target != null && target.isAlive() && this.mob.distanceToSqr(target) <= 70.0 * 70.0;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.seenPhaseTwo = this.mob.isPowered();
        this.secondVolley = false;
        this.enterRelocate();
    }

    @Override
    public void stop() {
        this.endDash();
        this.state = State.RELOCATE;
        this.mob.setCombatVelocityOverride(null);
        this.mob.setAlternativeTarget(0, 0);
        this.mob.setAlternativeTarget(1, 0);
        this.mob.setAlternativeTarget(2, 0);
        this.anchorPos = null;
        this.dashDirection = null;
        this.dashHitEntities.clear();
    }

    @Override
    public void tick() {
        if (!(this.mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        LivingEntity target = this.mob.getTarget();
        if (target == null || !target.isAlive()) {
            this.mob.setCombatVelocityOverride(null);
            return;
        }

        // Leave slot 0 clear so vanilla WitherBoss#aiStep doesn't inject Java chase velocity or snap yRot
        this.mob.setAlternativeTarget(0, 0);
        this.mob.setAlternativeTarget(1, target.getId());
        this.mob.setAlternativeTarget(2, target.getId());

        if (this.mob.isPowered() && !this.seenPhaseTwo) {
            this.seenPhaseTwo = true;
            this.secondVolley = false;
        }

        this.stateTicks++;
        switch (this.state) {
            case RELOCATE -> this.tickRelocate(serverLevel, target);
            case VOLLEY -> this.tickVolley(serverLevel, target);
            case DASH_WINDUP -> this.tickDashWindup(target);
            case DASH_ACTIVE -> this.tickDashActive(serverLevel);
        }
    }

    private void enterRelocate() {
        this.endDash();
        this.state = State.RELOCATE;
        this.stateTicks = 0;
        this.anchorPos = null;
        LivingEntity target = this.mob.getTarget();
        if (target != null && this.mob.level() instanceof ServerLevel serverLevel) {
            this.anchorPos = this.pickRelocateAnchor(serverLevel, target);
            if (this.anchorPos == null
                    && (serverLevel.getDifficulty() == Difficulty.NORMAL || serverLevel.getDifficulty() == Difficulty.HARD)) {
                double rx = this.mob.getX() + Mth.nextDouble(this.mob.getRandom(), -10.0, 10.0);
                double ry = this.mob.getY() + Mth.nextDouble(this.mob.getRandom(), -4.0, 4.0);
                double rz = this.mob.getZ() + Mth.nextDouble(this.mob.getRandom(), -10.0, 10.0);
                int sideHead = 1 + this.mob.getRandom().nextInt(2);
                this.mob.shootSkullAtCoords(sideHead, rx, ry, rz, true);
            }
        }
    }

    private double findGroundHuggingY(ServerLevel level, double x, double z, double targetY) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int topY = Mth.floor(targetY + 3.0);
        int minY = Math.max(level.getMinY() + 1, Mth.floor(targetY - 6.0));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = topY; y >= minY; y--) {
            cursor.set(bx, y, bz);
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                boolean headroomClear = true;
                for (int dy = 1; dy <= 3; dy++) {
                    cursor.set(bx, y + dy, bz);
                    if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                        headroomClear = false;
                        break;
                    }
                }
                if (headroomClear) {
                    return (y + 1) + 0.15;
                }
            }
        }
        return targetY + 0.15;
    }

    private @Nullable Vec3 pickRelocateAnchor(ServerLevel level, LivingEntity target) {
        boolean powered = this.mob.isPowered();
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = this.mob.getRandom().nextDouble() * Math.PI * 2.0;
            double dist = powered
                    ? 4.5 + this.mob.getRandom().nextDouble() * 6.5
                    : 8.0 + this.mob.getRandom().nextDouble() * 8.0;
            double x = target.getX() + Math.cos(angle) * dist;
            double z = target.getZ() + Math.sin(angle) * dist;
            double y = powered
                    ? this.findGroundHuggingY(level, x, z, target.getY()) + this.mob.getRandom().nextDouble() * 0.45
                    : target.getY() + Mth.lerp(this.mob.getRandom().nextDouble(), 4.0, 6.0);
            if (y < level.getMinY() + 2) {
                y = powered ? level.getMinY() + 2 : target.getY() + 4.0;
            }
            AABB box = this.mob.getType().getSpawnAABB(x, y, z);
            if (level.noCollision(this.mob, box)) {
                return new Vec3(x, y, z);
            }
        }
        return null;
    }

    private void tickRelocate(ServerLevel level, LivingEntity target) {
        this.faceTarget(target);
        boolean powered = this.mob.isPowered();
        if (this.anchorPos == null) {
            double fallbackY = powered
                    ? this.findGroundHuggingY(level, target.getX(), target.getZ(), target.getY())
                    : target.getY() + 4.8;
            this.anchorPos = new Vec3(target.getX(), fallbackY, target.getZ());
        }

        Vec3 toAnchor = this.anchorPos.subtract(this.mob.position());
        double dist = toAnchor.length();
        if (dist <= 2.0 || this.stateTicks >= 32) {
            this.enterVolley();
            return;
        }

        Vec3 dir = toAnchor.normalize();
        Vec3 perp = new Vec3(-dir.z, 0.0, dir.x);
        double strafe = Math.sin(this.stateTicks * 0.35) * (powered ? 0.18 : 0.25);
        Vec3 desired = dir.scale(Math.min(0.85, dist * 0.20)).add(perp.scale(strafe));
        this.mob.steerTowards(desired, 0.35);
    }

    private void enterVolley() {
        this.state = State.VOLLEY;
        this.stateTicks = 0;
        this.shotInVolley = 0;
        this.lastFiredHead = 1;
        this.shotCooldown = 14;
        this.framesTillMove = -1;
    }

    private void tickVolley(ServerLevel level, LivingEntity target) {
        this.faceTarget(target);

        double desiredY = this.mob.isPowered()
                ? this.findGroundHuggingY(level, this.mob.getX(), this.mob.getZ(), target.getY())
                : target.getY() + 4.5;
        double dy = Mth.clamp((desiredY - this.mob.getY()) * 0.14, -0.22, 0.32);
        this.mob.steerTowards(new Vec3(0.0, dy, 0.0), 0.4);

        if (!this.mob.hasLineOfSight(target) && this.stateTicks > 18 && this.framesTillMove < 0) {
            this.enterRelocate();
            return;
        }

        if (this.framesTillMove >= 0) {
            this.framesTillMove--;
            if (this.framesTillMove <= 0) {
                this.secondVolley = !this.secondVolley;
                if (this.mob.isPowered()
                        && BEStyleWither.getConfig().isEnableChargeAttack()
                        && !this.secondVolley) {
                    boolean dive = target.getY() < this.mob.getY() - 3.0 && this.mob.getRandom().nextFloat() < 0.20F;
                    this.enterDashWindup(dive);
                } else {
                    this.enterRelocate();
                }
            }
            return;
        }

        this.shotCooldown--;
        if (this.shotCooldown <= 0) {
            if (this.shotInVolley < 3) {
                // 3-head black skull burst cycling heads 1 -> 2 -> 0
                int head = switch (this.lastFiredHead) {
                    case 1 -> 1;
                    case 2 -> 2;
                    default -> 0;
                };
                this.mob.shootSkullAtCoords(
                        head,
                        target.getX(),
                        target.getY() + target.getEyeHeight() * 0.5,
                        target.getZ(),
                        false
                );
                this.lastFiredHead = switch (head) {
                    case 1 -> 2;
                    case 2 -> 0;
                    default -> 1;
                };
                this.shotInVolley++;
                if (this.shotInVolley < 3) {
                    this.shotCooldown = this.computeFireRateTicks();
                } else {
                    // Distinctly slower pause before the center head's blue skull
                    this.shotCooldown = this.computeBlueSkullDelayTicks();
                }
            } else {
                // 4th shot: blue skull from center head (0)
                boolean dangerous = BEStyleWither.getConfig().isEnableShootMoreBlueWitherSkull()
                        || this.mob.getRandom().nextFloat() < 0.001F;
                this.mob.shootSkullAtCoords(
                        0,
                        target.getX(),
                        target.getY() + target.getEyeHeight() * 0.5,
                        target.getZ(),
                        dangerous
                );
                // Linger beat before relocating / dashing
                this.framesTillMove = this.computeFramesTillMoveTicks();
            }
        }
    }

    private int computeCadenceTier() {
        float ratio = this.mob.getHealth() / Math.max(1.0F, this.mob.getMaxHealth());
        if (!this.mob.isPowered()) {
            if (ratio > 5.0F / 6.0F) {
                return 0;
            }
            return ratio > 4.0F / 6.0F ? 1 : 2;
        } else {
            if (ratio > 2.0F / 6.0F) {
                return 0;
            }
            return ratio > 1.0F / 6.0F ? 1 : 2;
        }
    }

    private int computeFireRateTicks() {
        return switch (this.computeCadenceTier()) {
            case 0 -> 20;
            case 1 -> 15;
            default -> 10;
        };
    }

    private int computeBlueSkullDelayTicks() {
        return switch (this.computeCadenceTier()) {
            case 0 -> 30;
            case 1 -> 24;
            default -> 20;
        };
    }

    private int computeFramesTillMoveTicks() {
        return switch (this.computeCadenceTier()) {
            case 0 -> 30;
            case 1 -> 26;
            default -> 22;
        };
    }

    private void enterDashWindup(boolean groundDive) {
        this.state = State.DASH_WINDUP;
        this.stateTicks = 0;
        this.isGroundDive = groundDive;
        this.nextBreakSoundTick = 0;
        this.dashHitEntities.clear();
        this.mob.getNavigation().stop();

        int totalTicks = DASH_WINDUP_TICKS + MAX_DASH_TICKS;
        this.mob.setDashing(true, totalTicks);
    }

    private void tickDashWindup(LivingEntity target) {
        this.mob.steerTowards(Vec3.ZERO, 0.45);

        if (this.isGroundDive) {
            this.dashDirection = new Vec3(target.getX() - this.mob.getX(), -14.0, target.getZ() - this.mob.getZ()).normalize();
        } else {
            Vec3 targetCenter = new Vec3(target.getX(), target.getY(0.5), target.getZ());
            Vec3 selfCenter = new Vec3(this.mob.getX(), this.mob.getY(0.5), this.mob.getZ());
            Vec3 aim = targetCenter.subtract(selfCenter);
            this.dashDirection = aim.lengthSqr() > 1.0E-4 ? aim.normalize() : this.mob.getLookAngle();
        }

        double hDist = Math.hypot(this.dashDirection.x, this.dashDirection.z);
        float yaw = (float) (Mth.atan2(this.dashDirection.z, this.dashDirection.x) * (180.0 / Math.PI)) - 90.0F;
        this.dashBodyRot = yaw;
        this.mob.setYRot(Mth.approachDegrees(this.mob.getYRot(), yaw, 20.0F));
        this.mob.setYBodyRot(this.mob.getYRot());
        this.mob.setYHeadRot(this.mob.getYRot());
        float pitch = (float) -Math.toDegrees(Math.atan2(this.dashDirection.y, hDist));
        this.mob.setXRot(Mth.approachDegrees(this.mob.getXRot(), pitch, 12.0F));

        if (this.stateTicks >= DASH_WINDUP_TICKS) {
            this.state = State.DASH_ACTIVE;
            this.stateTicks = 0;
            this.dashActiveDuration = Math.min(MAX_DASH_TICKS, Mth.ceil((this.mob.distanceTo(target) + 2.0) / DASH_SPEED));
        }
    }

    private void endDash() {
        if (this.state == State.DASH_ACTIVE || this.state == State.DASH_WINDUP) {
            this.mob.setDashing(false, 0);
            this.mob.setCombatVelocityOverride(this.mob.getDeltaMovement().scale(0.35));
        }
    }

    private void tickDashActive(ServerLevel level) {
        if (this.dashDirection == null || this.stateTicks > this.dashActiveDuration) {
            this.enterRelocate();
            return;
        }

        this.mob.setYRot(this.dashBodyRot);
        this.mob.setYBodyRot(this.dashBodyRot);
        this.mob.setYHeadRot(this.dashBodyRot);
        Vec3 movement = this.dashDirection.scale(DASH_SPEED);
        AABB path = this.mob.getBoundingBox().expandTowards(movement).deflate(1.0E-5);
        if (this.hitsDashBlockingBarrier(level, path)) {
            this.enterRelocate();
            return;
        }
        if (EventHooks.canEntityGrief(level, this.mob)) {
            this.destroyDashBlocks(level);
        }
        Vec3 allowed = Entity.collideBoundingBox(this.mob, movement,
                this.mob.getBoundingBox(), level, List.of());
        if (allowed.distanceToSqr(movement) > 1.0E-6) {
            this.enterRelocate();
            return;
        }
        this.mob.setCombatVelocityOverride(movement);
        this.damageEntitiesOnDashPath(level, path);
    }

    private void faceTarget(LivingEntity target) {
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double dx = target.getX() - this.mob.getX();
        double dy = (target.getY() + target.getEyeHeight() * 0.5) - this.mob.getEyeY();
        double dz = target.getZ() - this.mob.getZ();
        double hDistSqr = dx * dx + dz * dz;
        if (hDistSqr > 1.0E-4) {
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            this.mob.setYRot(Mth.approachDegrees(this.mob.getYRot(), yaw, 18.0F));
            this.mob.setYBodyRot(Mth.approachDegrees(this.mob.yBodyRot, yaw, 14.0F));
            this.mob.setYHeadRot(Mth.approachDegrees(this.mob.getYHeadRot(), yaw, 22.0F));
        }
        double hDist = Math.sqrt(hDistSqr);
        if (hDist > 1.0E-4 || Math.abs(dy) > 1.0E-4) {
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, hDist));
            this.mob.setXRot(Mth.approachDegrees(this.mob.getXRot(), pitch, 12.0F));
        }
    }

    private boolean hitsDashBlockingBarrier(ServerLevel level, AABB path) {
        for (BlockPos pos : BlockPos.betweenClosed(path)) {
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() && !canDashBreak(level, pos, state)) {
                return true;
            }
        }
        return false;
    }

    private void destroyDashBlocks(ServerLevel level) {
        int baseX = this.mob.getBlockX();
        int baseY = this.mob.getBlockY();
        int baseZ = this.mob.getBlockZ();
        boolean destroyed = false;
        int particleBudget = 6;

        // Bedrock Edition 6x8x6 dash destruction volume rounded south-east
        for (int dx = -2; dx <= 3; dx++) {
            for (int dy = -2; dy <= 5; dy++) {
                for (int dz = -2; dz <= 3; dz++) {
                    BlockPos pos = new BlockPos(baseX + dx, baseY + dy, baseZ + dz);
                    BlockState state = level.getBlockState(pos);
                    if (canDashBreak(level, pos, state) && EventHooks.onEntityDestroyBlock(this.mob, pos, state)) {
                        // Keep loot, block updates and vibration events, with one audible impact per batch.
                        Block.dropResources(state, level, pos, level.getBlockEntity(pos), this.mob, ItemStack.EMPTY);
                        if (level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL)) {
                            level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(this.mob, state));
                            destroyed = true;
                            if (particleBudget > 0 && this.mob.getRandom().nextInt(3) == 0) {
                                particleBudget--;
                                level.sendParticles(
                                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                                        pos.getX() + 0.5,
                                        pos.getY() + 0.5,
                                        pos.getZ() + 0.5,
                                        3,
                                        0.35,
                                        0.35,
                                        0.35,
                                        0.08
                                );
                            }
                        }
                    }
                }
            }
        }

        if (destroyed && this.stateTicks >= this.nextBreakSoundTick && !this.mob.isSilent()) {
            this.nextBreakSoundTick = this.stateTicks + 4;
            level.playSound(null, this.mob.getX(), this.mob.getY(), this.mob.getZ(),
                    SoundEvents.WITHER_BREAK_BLOCK, this.mob.getSoundSource(), 1.0F, 1.0F);
        }
    }

    private boolean canDashBreak(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F || state.is(BlockTags.WITHER_IMMUNE)) {
            return false;
        }
        // Bedrock Wither dash cannot break obsidian or equally blast-resistant blocks (>= 1200)
        if (state.getBlock().getExplosionResistance() >= 1200.0F) {
            return false;
        }
        return state.canEntityDestroy(level, pos, this.mob);
    }

    private void damageEntitiesOnDashPath(ServerLevel level, AABB path) {
        float dashDamage = switch (level.getDifficulty()) {
            case EASY -> 8.5F;
            case HARD -> 22.5F;
            default -> 15.0F;
        };

        List<LivingEntity> hitList = level.getEntitiesOfClass(
                LivingEntity.class,
                path.inflate(0.25),
                entity -> entity != this.mob
                        && entity.isAlive()
                        && !(entity instanceof BEWitherBossEntity)
                        && !(entity instanceof BEWitherSkeletonEntity)
                        && !this.dashHitEntities.contains(entity.getUUID())
        );

        for (LivingEntity victim : hitList) {
            this.dashHitEntities.add(victim.getUUID());
            if (victim instanceof WitherBoss vanillaWither && !(victim instanceof BEWitherBossEntity)) {
                DamageSource proxy = new DamageSource(
                        this.mob.damageSources().mobAttack(this.mob).typeHolder(),
                        this.mob.position()
                );
                if (vanillaWither.hurtServer(level, proxy, dashDamage)) {
                    vanillaWither.setLastHurtByMob(this.mob);
                    if (vanillaWither.getTarget() == null) {
                        vanillaWither.setTarget(this.mob);
                    }
                }
            } else {
                // Player.hurtServer applies difficulty scaling and LivingEntity handles contact knockback.
                victim.hurtServer(level, this.mob.damageSources().mobAttack(this.mob), victim instanceof Player ? 15.0F : dashDamage);
            }
        }
    }
}
