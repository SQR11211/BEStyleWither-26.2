package net.morimori0317.bestylewither.entity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.morimori0317.bestylewither.BEStyleWither;
import net.morimori0317.bestylewither.entity.goal.BEWitherCombatGoal;
import net.morimori0317.bestylewither.explatform.BSWExpectPlatform;
import net.morimori0317.bestylewither.neoforge.handler.BEWitherHostilityHandler;
import net.morimori0317.bestylewither.registry.BSWEntities;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;

public class BEWitherBossEntity extends WitherBoss implements BEWitherBoss {
    public static final int SPAWN_TICKS = 220;
    public static final int POST_DIVE_STUN_TICKS = 50;
    private static final EntityDataAccessor<Boolean> DATA_ID_ONCE_WEAKENED = SynchedEntityData.defineId(
            BEWitherBossEntity.class, EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Boolean> DATA_DIVING = SynchedEntityData.defineId(
            BEWitherBossEntity.class, EntityDataSerializers.BOOLEAN
    );
    private static final EntityDataAccessor<Boolean> DATA_DASHING = SynchedEntityData.defineId(
            BEWitherBossEntity.class, EntityDataSerializers.BOOLEAN
    );

    private final WitherBossInstance witherBossInstance = new WitherBossInstance(this, DATA_ID_ONCE_WEAKENED);
    private final Map<UUID, Float> damageByAttacker = new HashMap<>();
    private final float[] prevHeadXRots = new float[2];
    private final float[] prevHeadYRots = new float[2];
    private @Nullable Vec3 combatVelocityOverride;
    private double diveSpeedY;
    private int postDiveStunTicks;
    private float dashPoseOld;
    private float dashPose;
    private int idleBlueSkullTimer = 60;

    public BEWitherBossEntity(EntityType<? extends BEWitherBossEntity> type, Level level) {
        super(type, level);
        this.bossEvent.setColor(BossEvent.BossBarColor.PURPLE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        double maxHealth = BEStyleWither.getConfig().isEnableDoubleHealth() ? 600.0 : 300.0;
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.MOVEMENT_SPEED, 0.6F)
                .add(Attributes.FLYING_SPEED, 0.6F)
                .add(Attributes.FOLLOW_RANGE, 70.0)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    public WitherBossInstance beStyleWither$getInstance() {
        return this.witherBossInstance;
    }

    public void setCombatVelocityOverride(@Nullable Vec3 velocity) {
        this.combatVelocityOverride = velocity;
    }

    public void steerTowards(Vec3 desired, double fraction) {
        Vec3 previous = this.combatVelocityOverride != null ? this.combatVelocityOverride : this.getDeltaMovement();
        this.combatVelocityOverride = previous.lerp(desired, fraction);
    }

    public boolean isDiving() {
        return this.entityData.get(DATA_DIVING);
    }

    public boolean isPostDiveStunned() {
        return this.postDiveStunTicks > 0;
    }

    public void setDashing(boolean dashing, int durationTicks) {
        this.entityData.set(DATA_DASHING, dashing);
        int syncTicks = dashing ? durationTicks : 0;
        this.witherBossInstance.setClientChargeDuration(syncTicks);
        this.witherBossInstance.setClientChargeTick(syncTicks);
        this.witherBossInstance.setClientChargeTickOld(syncTicks);
        if (this.level() instanceof ServerLevel serverLevel) {
            BSWExpectPlatform.sendWhitherChargePacket(serverLevel, this.chunkPosition(), this.getId(), syncTicks);
        }
    }

    public void setDashDuration(int totalTicks) {
        this.setDashing(totalTicks > 0, totalTicks);
    }

    public float getDashProgress(float partialTicks) {
        return Mth.lerp(partialTicks, this.dashPoseOld, this.dashPose);
    }

    public float getHeadXRot(int index, float partialTicks) {
        float current = this.getHeadXRots()[index];
        return Mth.lerp(partialTicks, this.prevHeadXRots[index], current);
    }

    public float getHeadYRot(int index, float partialTicks) {
        float current = this.getHeadYRots()[index];
        return Mth.rotLerp(partialTicks, this.prevHeadYRots[index], current);
    }

    public void shootSkullAtCoords(int head, double tx, double ty, double tz, boolean dangerous) {
        this.performRangedAttack(head, tx, ty, tz, dangerous);
    }

    public void applyDifficultyHealth(ServerLevel level) {
        double targetMaxHealth;
        if (BEStyleWither.getConfig().isEnableDoubleHealth()) {
            targetMaxHealth = switch (level.getDifficulty()) {
                case EASY -> 300.0;
                case NORMAL -> 450.0;
                case HARD -> 600.0;
                default -> 300.0;
            };
        } else {
            targetMaxHealth = 300.0;
        }

        AttributeInstance maxHealthAttr = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            float oldMax = Math.max(1.0F, this.getMaxHealth());
            float oldRatio = this.getHealth() / oldMax;
            maxHealthAttr.setBaseValue(targetMaxHealth);
            if (this.getInvulnerableTicks() > 0) {
                this.setHealth((float) (targetMaxHealth / 3.0));
            } else {
                this.setHealth((float) Math.min(targetMaxHealth, Math.max(1.0F, oldRatio * targetMaxHealth)));
            }
        }
        this.witherBossInstance.setDifficultyHealthApplied(true);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            EntitySpawnReason spawnReason,
            @Nullable SpawnGroupData groupData
    ) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        this.applyDifficultyHealth(level.getLevel());
        if (this.getInvulnerableTicks() <= 0) {
            this.makeInvulnerable();
        }
        return data;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new BEWitherDoNothingGoal());
        this.goalSelector.addGoal(1, new BEWitherCombatGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new WitherTargetHighestDamageGoal());
        this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                this,
                LivingEntity.class,
                0,
                true,
                false,
                (target, level) -> !(target instanceof BEWitherBossEntity)
                        && !(target instanceof BEWitherSkeletonEntity)
                        && target.attackable()
        ));
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof BEWitherBossEntity || target instanceof BEWitherSkeletonEntity) {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_ID_ONCE_WEAKENED, false);
        entityData.define(DATA_DIVING, false);
        entityData.define(DATA_DASHING, false);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        this.witherBossInstance.save(output.child("BEStyleWitherData"));
        output.putBoolean("BEWitherDiving", this.isDiving());
        output.putInt("BEWitherStunTicks", this.postDiveStunTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.witherBossInstance.load(input.childOrEmpty("BEStyleWitherData"));
        this.entityData.set(DATA_DIVING, input.getBooleanOr("BEWitherDiving", false));
        this.postDiveStunTicks = Math.max(0, input.getIntOr("BEWitherStunTicks", 0));
    }

    @Override
    public boolean isPowered() {
        if (BEStyleWither.getConfig().isEnableMaintainWeakenedState() && this.witherBossInstance.isOnceWeakened()) {
            return true;
        }
        return super.isPowered();
    }

    @Override
    public void heal(float healAmount) {
        if (this.getInvulnerableTicks() > 0 && healAmount == 10.0F) {
            healAmount = this.getMaxHealth() / 30.0F;
        }
        super.heal(healAmount);
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        if (this.getInvulnerableTicks() > 0 || this.isDiving() || this.isPostDiveStunned() || this.isDeadOrDying()) {
            return null;
        }
        return super.getAmbientSound();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (this.isInvulnerableTo(level, source) || source.is(DamageTypeTags.WITHER_IMMUNE_TO)) {
            return false;
        }
        Entity attackerEntity = source.getEntity();
        if (attackerEntity == this
                || (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && (attackerEntity instanceof BEWitherBossEntity || attackerEntity instanceof BEWitherSkeletonEntity))) {
            return false;
        }
        if (this.getInvulnerableTicks() > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (this.isPowered()
                && (source.getDirectEntity() instanceof AbstractArrow || source.getDirectEntity() instanceof WindCharge)) {
            return false;
        }

        boolean hurt;
        if (attackerEntity != null && (attackerEntity.is(EntityTypeTags.WITHER_FRIENDS) || attackerEntity instanceof WitherBoss)) {
            // Bypass vanilla WitherBoss#hurtServer's undead/WitherBoss friendly-fire block while keeping attack-damage & retaliation.
            DamageSource proxy = new DamageSource(
                    source.typeHolder(),
                    source.getSourcePosition() != null ? source.getSourcePosition() : attackerEntity.position()
            );
            hurt = super.hurtServer(level, proxy, damage);
            if (hurt && attackerEntity instanceof LivingEntity attacker) {
                this.setLastHurtByMob(attacker);
            }
        } else {
            hurt = super.hurtServer(level, source, damage);
        }

        if (hurt
                && attackerEntity instanceof LivingEntity attacker
                && !(attacker instanceof BEWitherBossEntity)
                && !(attacker instanceof BEWitherSkeletonEntity)
                && attacker.isAlive()
                && attacker.canBeSeenAsEnemy()) {
            this.damageByAttacker.merge(attacker.getUUID(), damage, Float::sum);
        }
        return hurt;
    }

    @Override
    public void push(Entity entity) {
        super.push(entity);
        BEWitherHostilityHandler.handleCubeMobContact(this, entity);
    }

    @Override
    public void travel(Vec3 input) {
        if (this.level() instanceof ServerLevel level) {
            if (this.isDiving()) {
                this.diveSpeedY = Math.max(-2.0, this.diveSpeedY - 0.25);
                Vec3 descent = new Vec3(0.0, this.diveSpeedY, 0.0);
                this.setDeltaMovement(descent);
                this.move(MoverType.SELF, descent);
                if (this.onGround()) {
                    this.finishDive(level, true);
                } else if (this.getY() < level.getMinY()) {
                    this.finishDive(level, false);
                }
                return;
            }
            this.diveSpeedY = 0.0;
            if (this.isPostDiveStunned()) {
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
                super.travel(Vec3.ZERO);
                return;
            }
            if (this.combatVelocityOverride != null) {
                this.setDeltaMovement(this.combatVelocityOverride);
                super.travel(Vec3.ZERO);
                return;
            }
        }
        super.travel(input);
    }

    @Override
    public void aiStep() {
        if (BEStyleWither.getConfig().isEnableExplodeByDie() && this.isDeadOrDying()) {
            return;
        }

        float[] headXRots = this.getHeadXRots();
        float[] headYRots = this.getHeadYRots();
        System.arraycopy(headXRots, 0, this.prevHeadXRots, 0, Math.min(this.prevHeadXRots.length, headXRots.length));
        System.arraycopy(headYRots, 0, this.prevHeadYRots, 0, Math.min(this.prevHeadYRots.length, headYRots.length));

        super.aiStep();

        this.dashPoseOld = this.dashPose;
        boolean charging = this.entityData.get(DATA_DASHING)
                || (this.level().isClientSide() && this.witherBossInstance.getClientChargeTick() > 0);
        this.dashPose = Mth.approach(this.dashPose, charging ? 1.0F : 0.0F, charging ? 1.0F / 12.0F : 1.0F / 8.0F);

        this.witherBossInstance.setChargeCoolDown(Math.max(0, this.witherBossInstance.getChargeCoolDown() - 1));
        if (this.level().isClientSide()) {
            this.witherBossInstance.setClientChargeTickOld(this.witherBossInstance.getClientChargeTick());
            this.witherBossInstance.setClientChargeTick(Math.max(0, this.witherBossInstance.getClientChargeTick() - 1));
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (!this.witherBossInstance.isDifficultyHealthApplied()) {
            this.applyDifficultyHealth(level);
        }

        // Pure Bedrock server AI step (intentionally omits Java's WitherBoss#customServerAiStep
        // so Java's 1HP/s regen, extra side-head volleys, and random levelEvent(1022) don't run).
        if (this.getInvulnerableTicks() > 0) {
            int newCount = this.getInvulnerableTicks() - 1;
            this.bossEvent.setProgress(1.0F - newCount / (float) SPAWN_TICKS);
            if (newCount <= 0) {
                this.destroyBlocksTick = 0;
                level.explode(this, this.getX(), this.getEyeY(), this.getZ(), 7.0F, false, Level.ExplosionInteraction.MOB);
                this.damageNearbyVanillaWithersFromExplosion(level, this.getX(), this.getEyeY(), this.getZ(), 7.0F);
                if (!this.isSilent()) {
                    level.globalLevelEvent(LevelEvent.SOUND_WITHER_BOSS_SPAWN, this.blockPosition(), 0);
                }
            }
            this.setInvulnerableTicks(newCount);
            if (this.tickCount % 10 == 0) {
                this.heal(this.getMaxHealth() / 30.0F);
            }
            return;
        }

        if (this.isPowered() && !this.witherBossInstance.isOnceWeakened()) {
            this.witherBossInstance.setOnceWeakened(true);
        }

        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

        if (BEStyleWither.getConfig().isEnableExplodeByHalfHealth()
                && !this.witherBossInstance.isHalfHealthExploded() && (this.isDiving() || this.isPowered())) {
            if (!this.isDiving()) {
                this.diveSpeedY = Math.min(0.0, this.getDeltaMovement().y);
                this.entityData.set(DATA_DIVING, true);
                this.getNavigation().stop();
                this.setCombatVelocityOverride(null);
                for (int head = 0; head < 3; head++) {
                    this.setAlternativeTarget(head, 0);
                }
            }
            return;
        }
        if (this.isDiving()) {
            this.entityData.set(DATA_DIVING, false);
        }

        if (this.postDiveStunTicks > 0) {
            this.postDiveStunTicks--;
            this.destroyBlocksTick = 0;
            this.setCombatVelocityOverride(null);
            this.getNavigation().stop();
            for (int head = 0; head < 3; head++) {
                this.setAlternativeTarget(head, 0);
            }
            return;
        }

        // Hurt-triggered 3x4x3 block destruction using Bedrock break sound (1.0F, 1.0F)
        if (this.destroyBlocksTick > 0) {
            this.destroyBlocksTick--;
            if (this.destroyBlocksTick == 0 && !this.isDiving() && EventHooks.canEntityGrief(level, this)) {
                boolean destroyed = false;
                int width = Mth.floor(this.getBbWidth() / 2.0F + 1.0F);
                int height = Mth.floor(this.getBbHeight());

                for (BlockPos blockPos : BlockPos.betweenClosed(
                        this.getBlockX() - width,
                        this.getBlockY(),
                        this.getBlockZ() - width,
                        this.getBlockX() + width,
                        this.getBlockY() + height,
                        this.getBlockZ() + width
                )) {
                    BlockState state = level.getBlockState(blockPos);
                    if (state.canEntityDestroy(level, blockPos, this) && EventHooks.onEntityDestroyBlock(this, blockPos, state)) {
                        destroyed = level.destroyBlock(blockPos, true, this) || destroyed;
                    }
                }

                if (destroyed && !this.isSilent()) {
                    level.playSound(null, this.getX(), this.getY(), this.getZ(),
                            SoundEvents.WITHER_BREAK_BLOCK, this.getSoundSource(), 1.0F, 1.0F);
                }
            }
        }

        // Idle random blue skulls on Normal/Hard when no target is selected
        if (this.getTarget() == null
                && !this.isDiving()
                && (level.getDifficulty() == Difficulty.NORMAL || level.getDifficulty() == Difficulty.HARD)) {
            this.idleBlueSkullTimer--;
            if (this.idleBlueSkullTimer <= 0) {
                this.idleBlueSkullTimer = 60 + this.random.nextInt(40);
                double xt = Mth.nextDouble(this.random, this.getX() - 10.0, this.getX() + 10.0);
                double yt = Mth.nextDouble(this.random, this.getY() - 4.0, this.getY() + 4.0);
                double zt = Mth.nextDouble(this.random, this.getZ() - 10.0, this.getZ() + 10.0);
                this.performRangedAttack(1 + this.random.nextInt(2), xt, yt, zt, true);
            }
        }

        if ((this.tickCount + this.getId()) % 20 == 0) {
            BEWitherHostilityHandler.provokeNearbyMobs(level, this, 48.0);
        }
    }

    private void finishDive(ServerLevel level, boolean landed) {
        this.witherBossInstance.setHalfHealthExploded(true);
        this.entityData.set(DATA_DIVING, false);
        this.diveSpeedY = 0.0;
        this.destroyBlocksTick = 0;
        this.setCombatVelocityOverride(null);
        this.setDeltaMovement(Vec3.ZERO);
        if (!landed) {
            return;
        }
        level.explode(this, this.getX(), this.getY(), this.getZ(), 7.0F, false, Level.ExplosionInteraction.MOB);
        this.damageNearbyVanillaWithersFromExplosion(level, this.getX(), this.getY(), this.getZ(), 7.0F);
        if (level.getDifficulty() == Difficulty.NORMAL || level.getDifficulty() == Difficulty.HARD) {
            int existing = level.getEntitiesOfClass(
                    BEWitherSkeletonEntity.class, this.getBoundingBox().inflate(32.0)).size();
            int toSpawn = Math.max(0, Math.min(3, 4 - existing));
            RandomSource randomSource = this.getRandom();
            double baseAngle = randomSource.nextDouble() * Math.PI * 2.0;
            for (int i = 0; i < toSpawn; i++) {
                BEWitherSkeletonEntity ws = BSWEntities.BE_WITHER_SKELETON.get().create(level, EntitySpawnReason.MOB_SUMMONED);
                if (ws != null) {
                    double angle = baseAngle + (Math.PI * 2.0 / toSpawn) * i + (randomSource.nextDouble() - 0.5) * 0.4;
                    double sx = this.getX() + Math.cos(angle) * 1.2;
                    double sz = this.getZ() + Math.sin(angle) * 1.2;
                    ws.snapTo(sx, this.getY(), sz, randomSource.nextFloat() * 360.0F, 0.0F);
                    if (!level.noCollision(ws)) {
                        ws.snapTo(this.getX(), this.getY(), this.getZ(), ws.getYRot(), 0.0F);
                    }
                    ws.finalizeSpawn(level, level.getCurrentDifficultyAt(this.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
                    level.addFreshEntity(ws);
                }
            }
        }
        this.postDiveStunTicks = POST_DIVE_STUN_TICKS;
        this.getNavigation().stop();
        for (int head = 0; head < 3; head++) {
            this.setAlternativeTarget(head, 0);
        }
    }

    private void damageNearbyVanillaWithersFromExplosion(ServerLevel level, double ex, double ey, double ez, float radius) {
        double maxRange = radius * 2.0;
        AABB bounds = new AABB(ex, ey, ez, ex, ey, ez).inflate(maxRange);
        Vec3 center = new Vec3(ex, ey, ez);
        DamageSource proxy = new DamageSource(level.damageSources().explosion(null, null).typeHolder(), center);
        for (WitherBoss vanillaWither : level.getEntitiesOfClass(
                WitherBoss.class,
                bounds,
                w -> !(w instanceof BEWitherBossEntity) && w.isAlive() && w.getInvulnerableTicks() <= 0
        )) {
            double dist = Math.sqrt(vanillaWither.distanceToSqr(center));
            if (dist <= maxRange) {
                float impact = (float) Math.max(0.0, 1.0 - dist / maxRange);
                float dmg = ((impact * impact + impact) / 2.0F) * 7.0F * (float) maxRange + 1.0F;
                if (vanillaWither.hurtServer(level, proxy, dmg)) {
                    vanillaWither.setLastHurtByMob(this);
                }
            }
        }
    }

    @Override
    protected void performRangedAttack(int head, LivingEntity target) {
        boolean dangerous = BEStyleWither.getConfig().isEnableShootMoreBlueWitherSkull() && head == 0;
        if (!dangerous && head == 0) {
            dangerous = this.random.nextFloat() < 0.001F;
        }
        this.performRangedAttack(head, target.getX(), target.getY() + target.getEyeHeight() * 0.5, target.getZ(), dangerous);
    }

    @Override
    protected void performRangedAttack(int head, double tx, double ty, double tz, boolean dangerous) {
        if (!this.isSilent()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.WITHER_SHOOT, this.getSoundSource(), 3.0F, 1.0F);
        }

        double hx = this.getHeadX(head);
        double hy = this.getHeadY(head);
        double hz = this.getHeadZ(head);
        double xd = tx - hx;
        double yd = ty - hy;
        double zd = tz - hz;

        BEWitherSkullEntity skull = new BEWitherSkullEntity(this.level(), this, hx, hy, hz);
        skull.shootBedrock(xd, yd, zd, dangerous, this.level().getDifficulty());
        this.level().addFreshEntity(skull);
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return true;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        ItemEntity netherStar = this.spawnAtLocation(level, Items.NETHER_STAR);
        if (netherStar != null) {
            netherStar.setUnlimitedLifetime();
        }
        if (source.getDirectEntity() instanceof BEWitherSkullEntity) {
            this.spawnAtLocation(level, Items.WITHER_ROSE);
        }
    }

    @Override
    protected @Nullable SoundEvent getDeathSound() {
        return BEStyleWither.getConfig().isEnableExplodeByDie() ? null : super.getDeathSound();
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        if (this.witherBossInstance.isSkipLootDrop()) {
            return;
        }
        super.dropAllDeathLoot(level, source);
    }

    @Override
    public void die(DamageSource source) {
        this.destroyBlocksTick = 0;
        this.postDiveStunTicks = 0;
        if (BEStyleWither.getConfig().isEnableExplodeByDie()) {
            this.witherBossInstance.setSkipLootDrop(true);
            this.witherBossInstance.setExplodeByDieDeathDamageSource(source);
        }
        super.die(source);
    }

    @Override
    protected void tickDeath() {
        if (BEStyleWither.getConfig().isEnableExplodeByDie()) {
            this.deathTime++;
            this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());

            Level level = this.level();
            if (!level.isClientSide()) {
                if (this.deathTime == 1 || this.deathTime == 60 || this.deathTime == 120) {
                    SoundEvent deathSound = super.getDeathSound();
                    if (deathSound != null) {
                        float vol = switch (this.deathTime) {
                            case 1 -> 1.0F;
                            case 60 -> 0.75F;
                            default -> 0.50F;
                        };
                        this.playSound(deathSound, this.getSoundVolume() * vol, this.getVoicePitch());
                    }
                } else if (this.deathTime == ExplodeByDieConstants.EXPLODE_TICK - 1) {
                    level.explode(this, this.getX(), this.getEyeY(), this.getZ(), 7.0F, false, Level.ExplosionInteraction.MOB);
                    if (level instanceof ServerLevel serverLevel) {
                        this.damageNearbyVanillaWithersFromExplosion(serverLevel, this.getX(), this.getEyeY(), this.getZ(), 7.0F);
                    }
                } else if (this.deathTime >= ExplodeByDieConstants.EXPLODE_TICK && !this.isRemoved()) {
                    this.lastHurtByPlayerMemoryTime = Math.max(this.lastHurtByPlayerMemoryTime, 1);
                    DamageSource deathDamage = this.witherBossInstance.getExplodeByDieDeathDamageSource();
                    if (deathDamage == null) {
                        deathDamage = level.damageSources().fellOutOfWorld();
                    }

                    if (level instanceof ServerLevel serverLevel) {
                        this.witherBossInstance.setSkipLootDrop(false);
                        this.dropAllDeathLoot(serverLevel, deathDamage);
                    }

                    if (!this.isRemoved()) {
                        level.broadcastEntityEvent(this, (byte) 60);
                        this.remove(RemovalReason.KILLED);
                    }
                }
            } else {
                boolean energyLayerVisible;
                if (this.deathTime > ExplodeByDieConstants.ENERGY_LAYER_START_TICK) {
                    if (this.deathTime <= ExplodeByDieConstants.ENERGY_LAYER_BLINKING_START_TICK) {
                        energyLayerVisible = true;
                    } else {
                        int energyTick = this.witherBossInstance.getExplodeByDieDeathBlinkingTick();
                        boolean preVisible = this.witherBossInstance.isExplodeByDieEnergyLayerVisible();
                        if (energyTick > 0) {
                            this.witherBossInstance.setExplodeByDieDeathBlinkingTick(energyTick - 1);
                            energyLayerVisible = preVisible;
                        } else {
                            RandomSource rnd = this.getRandom();
                            boolean longBlinking;
                            if (this.deathTime > ExplodeByDieConstants.ENERGY_LAYER_LONG_BLINKING_END_TICK) {
                                longBlinking = false;
                            } else {
                                float par = (this.deathTime - ExplodeByDieConstants.ENERGY_LAYER_BLINKING_START_TICK) / 106.0F;
                                longBlinking = rnd.nextInt(4 + (int) ((1.0F - par) * 40.0F)) == 0;
                            }

                            int blinkingTick;
                            if (preVisible) {
                                blinkingTick = ExplodeByDieConstants.ENERGY_LAYER_BLINKING_OFF_MIN_DURATION;
                                blinkingTick += rnd.nextInt(ExplodeByDieConstants.ENERGY_LAYER_BLINKING_OFF_MAX_DURATION - blinkingTick + 1);
                            } else {
                                blinkingTick = longBlinking
                                        ? ExplodeByDieConstants.ENERGY_LAYER_LONG_BLINKING_ON_DURATION
                                        : ExplodeByDieConstants.ENERGY_LAYER_SHORT_BLINKING_ON_DURATION;
                            }

                            this.witherBossInstance.setExplodeByDieDeathBlinkingTick(blinkingTick);
                            energyLayerVisible = !preVisible;
                        }
                    }
                } else {
                    energyLayerVisible = true;
                }

                this.witherBossInstance.setExplodeByDieEnergyLayerVisible(energyLayerVisible);
            }
        } else {
            this.deathTime++;
            if (this.deathTime >= 20 && !this.level().isClientSide() && !this.isRemoved()) {
                if (this.witherBossInstance.isSkipLootDrop() && this.level() instanceof ServerLevel serverLevel) {
                    this.witherBossInstance.setSkipLootDrop(false);
                    DamageSource deathDamage = this.witherBossInstance.getExplodeByDieDeathDamageSource();
                    if (deathDamage == null) {
                        deathDamage = serverLevel.damageSources().fellOutOfWorld();
                    }
                    this.dropAllDeathLoot(serverLevel, deathDamage);
                }

                this.level().broadcastEntityEvent(this, (byte) 60);
                this.remove(RemovalReason.KILLED);
            }
        }
    }

    private class BEWitherDoNothingGoal extends Goal {
        BEWitherDoNothingGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return BEWitherBossEntity.this.getInvulnerableTicks() > 0
                    || BEWitherBossEntity.this.isDiving()
                    || BEWitherBossEntity.this.isPostDiveStunned();
        }
    }

    private class WitherTargetHighestDamageGoal extends Goal {
        private @Nullable LivingEntity candidateTarget;

        WitherTargetHighestDamageGoal() {
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!(BEWitherBossEntity.this.level() instanceof ServerLevel serverLevel)) {
                return false;
            }
            LivingEntity highest = this.findHighestDamageAttacker(serverLevel);
            if (highest != null && highest != BEWitherBossEntity.this.getTarget()) {
                this.candidateTarget = highest;
                return true;
            }
            return false;
        }

        @Override
        public void start() {
            BEWitherBossEntity.this.setTarget(this.candidateTarget);
        }

        @Override
        public boolean canContinueToUse() {
            if (!(BEWitherBossEntity.this.level() instanceof ServerLevel serverLevel)) {
                return false;
            }
            LivingEntity current = BEWitherBossEntity.this.getTarget();
            if (current == null || !current.isAlive() || !current.canBeSeenAsEnemy()
                    || BEWitherBossEntity.this.distanceToSqr(current) > 70.0 * 70.0) {
                return false;
            }
            LivingEntity highest = this.findHighestDamageAttacker(serverLevel);
            return highest == null || highest == current;
        }

        private @Nullable LivingEntity findHighestDamageAttacker(ServerLevel level) {
            LivingEntity best = null;
            float bestDamage = 0.0F;
            Iterator<Map.Entry<UUID, Float>> it = BEWitherBossEntity.this.damageByAttacker.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Float> entry = it.next();
                if (level.getEntity(entry.getKey()) instanceof LivingEntity living
                        && living.isAlive()
                        && living.canBeSeenAsEnemy()
                        && !(living instanceof BEWitherBossEntity)
                        && !(living instanceof BEWitherSkeletonEntity)
                        && BEWitherBossEntity.this.distanceToSqr(living) <= 70.0 * 70.0) {
                    if (entry.getValue() > bestDamage) {
                        bestDamage = entry.getValue();
                        best = living;
                    }
                } else {
                    it.remove();
                }
            }
            return best;
        }
    }
}
