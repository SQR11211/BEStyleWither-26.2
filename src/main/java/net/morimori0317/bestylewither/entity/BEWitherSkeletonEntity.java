package net.morimori0317.bestylewither.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FleeSunGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.morimori0317.bestylewither.neoforge.handler.BEWitherHostilityHandler;
import org.jspecify.annotations.Nullable;

/**
 * Bedrock Edition Wither Skeleton minion summoned by the BE Wither:
 * - Official Bedrock wither_skeleton.json attributes & movement (20 HP, 0.25 speed, 1.25 melee chase, lava pathable)
 * - Uses Vanilla Java Wither Skeleton model & texture via WitherSkeletonRenderer
 * - Does not proactively attack non-player mobs, but retaliates against any attacker (including undead mobs)
 *   while all attack-capable mobs (except frogs) treat it as hostile
 */
public class BEWitherSkeletonEntity extends WitherSkeleton {
    private final RangedBowAttackGoal<AbstractSkeleton> beBowGoal = new RangedBowAttackGoal<>(this, 1.0, 20, 15.0F);
    private final MeleeAttackGoal beMeleeGoal = new MeleeAttackGoal(this, 1.25, true);

    public BEWitherSkeletonEntity(EntityType<? extends BEWitherSkeletonEntity> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.LAVA, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new RestrictSunGoal(this));
        this.goalSelector.addGoal(3, new FleeSunGoal(this, 1.0));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractPiglin.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Turtle.class, 10, true, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    @Override
    public void reassessWeaponGoal() {
        if (this.level() != null && !this.level().isClientSide() && this.beMeleeGoal != null && this.beBowGoal != null) {
            this.goalSelector.removeGoal(this.beMeleeGoal);
            this.goalSelector.removeGoal(this.beBowGoal);
            ItemStack weapon = this.getItemInHand(ProjectileUtil.getWeaponHoldingHand(this, item -> item instanceof BowItem));
            if (weapon.is(Items.BOW)) {
                int interval = this.level().getDifficulty() == Difficulty.HARD ? 20 : 40;
                this.beBowGoal.setMinAttackInterval(interval);
                this.goalSelector.addGoal(4, this.beBowGoal);
            } else {
                this.goalSelector.addGoal(4, this.beMeleeGoal);
            }
        }
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            EntitySpawnReason spawnReason,
            @Nullable SpawnGroupData groupData
    ) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
        this.setCanPickUpLoot(true);
        this.reassessWeaponGoal();
        return data;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof BEWitherBossEntity || target instanceof BEWitherSkeletonEntity) {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && (source.getEntity() instanceof BEWitherBossEntity || source.getEntity() instanceof BEWitherSkeletonEntity)) {
            return false;
        }
        return super.hurtServer(level, source, damage);
    }

    @Override
    public void push(Entity entity) {
        super.push(entity);
        BEWitherHostilityHandler.handleCubeMobContact(this, entity);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (target instanceof WitherBoss vanillaWither && !(target instanceof BEWitherBossEntity)) {
            float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
            DamageSource proxy = new DamageSource(
                    this.damageSources().mobAttack(this).typeHolder(),
                    this.position()
            );
            if (vanillaWither.hurtServer(level, proxy, damage)) {
                vanillaWither.setLastHurtByMob(this);
                if (vanillaWither.getTarget() == null) {
                    vanillaWither.setTarget(this);
                }
                return true;
            }
            return false;
        }
        return super.doHurtTarget(level, target);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if ((this.tickCount + this.getId()) % 20 == 0) {
            BEWitherHostilityHandler.provokeNearbyMobs(level, this, 24.0);
        }
    }
}
