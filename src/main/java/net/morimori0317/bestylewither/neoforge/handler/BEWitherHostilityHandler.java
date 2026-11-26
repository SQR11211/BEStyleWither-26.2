package net.morimori0317.bestylewither.neoforge.handler;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.morimori0317.bestylewither.entity.BEWitherBossEntity;
import net.morimori0317.bestylewither.entity.BEWitherSkeletonEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Ensures all attack-capable mobs (except frogs and BE Wither allies) proactively
 * target and engage both the BE Wither and its summoned BE Wither Skeletons,
 * including other undead mobs.
 */
public final class BEWitherHostilityHandler {
    private BEWitherHostilityHandler() {
    }

    public static boolean isAttackCapableMob(Mob mob) {
        if (mob instanceof Frog
                || mob instanceof BEWitherBossEntity
                || mob instanceof BEWitherSkeletonEntity) {
            return false;
        }
        if (mob instanceof Enemy || mob instanceof RangedAttackMob) {
            return true;
        }
        return mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                && mob.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) > 0.0;
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        if (!isAttackCapableMob(mob)) {
            return;
        }
        for (WrappedGoal wrapped : mob.targetSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof BEWitherHostileTargetGoal) {
                return;
            }
        }
        if (mob instanceof WitherBoss) {
            mob.targetSelector.addGoal(1, new BEWitherHostileTargetGoal(mob, 0, false));
        } else {
            mob.targetSelector.addGoal(2, new BEWitherHostileTargetGoal(mob, 10, true));
        }
    }

    public static void provokeNearbyMobs(ServerLevel level, LivingEntity self, double radius) {
        List<Mob> mobs = level.getEntitiesOfClass(
                Mob.class,
                self.getBoundingBox().inflate(radius),
                mob -> mob.isAlive()
                        && isAttackCapableMob(mob)
                        && (!(mob instanceof TamableAnimal tamable) || !tamable.isOrderedToSit())
        );

        for (Mob mob : mobs) {
            if (mob instanceof WitherBoss vanillaWither) {
                if (vanillaWither.getInvulnerableTicks() <= 0) {
                    if (vanillaWither.getTarget() == null || !vanillaWither.getTarget().isAlive()) {
                        vanillaWither.setTarget(self);
                        vanillaWither.setAlternativeTarget(0, self.getId());
                    }
                    if (vanillaWither.distanceToSqr(self) <= 900.0 && vanillaWither.hasLineOfSight(self)) {
                        if (vanillaWither.getAlternativeTarget(1) <= 0) {
                            vanillaWither.setAlternativeTarget(1, self.getId());
                        }
                        if (vanillaWither.getAlternativeTarget(2) <= 0) {
                            vanillaWither.setAlternativeTarget(2, self.getId());
                        }
                    }
                }
                continue;
            }

            if (mob instanceof Warden warden) {
                if (mob.getTarget() == null) {
                    warden.increaseAngerAt(self, 80, false);
                }
                continue;
            }

            if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                    && !mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                    && mob.canAttack(self)
                    && mob.hasLineOfSight(self)) {
                mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, self);
                if (mob.getBrain().checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED)) {
                    mob.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, self.getUUID(), 600L);
                }
            }

            if (mob instanceof NeutralMob neutral && mob.getTarget() == self && !neutral.isAngry()) {
                neutral.setPersistentAngerTarget(EntityReference.of(self));
                neutral.startPersistentAngerTimer();
            }
        }
    }

    private static final class BEWitherHostileTargetGoal extends NearestAttackableTargetGoal<LivingEntity> {
        BEWitherHostileTargetGoal(Mob mob, int interval, boolean mustSee) {
            super(
                    mob,
                    LivingEntity.class,
                    interval,
                    mustSee,
                    false,
                    (target, level) -> (target instanceof BEWitherBossEntity || target instanceof BEWitherSkeletonEntity)
                            && target.isAlive()
                            && (!(mob instanceof TamableAnimal tamable) || !tamable.isOrderedToSit())
            );
        }

        @Override
        public void start() {
            super.start();
            if (this.mob instanceof NeutralMob neutral && this.target != null) {
                neutral.setPersistentAngerTarget(EntityReference.of(this.target));
                neutral.startPersistentAngerTimer();
            }
        }
    }

    public static void handleCubeMobContact(LivingEntity victim, Entity pusher) {
        if (!(pusher instanceof AbstractCubeMob cubeMob)
                || !(victim.level() instanceof ServerLevel serverLevel)
                || !cubeMob.isAlive()
                || cubeMob.isRemoved()
                || !(cubeMob instanceof MagmaCube || !cubeMob.isTiny())
                || !cubeMob.isEffectiveAi()) {
            return;
        }
        if (!(cubeMob.isWithinMeleeAttackRange(victim)
                || cubeMob.getBoundingBox().inflate(0.15).intersects(victim.getBoundingBox()))
                || !cubeMob.hasLineOfSight(victim)) {
            return;
        }

        float damage = (float) cubeMob.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (cubeMob instanceof MagmaCube) {
            damage += 2.0F;
        }
        DamageSource source = cubeMob.damageSources().mobAttack(cubeMob);
        if (victim.hurtServer(serverLevel, source, damage)) {
            float pitch = (cubeMob.getRandom().nextFloat() - cubeMob.getRandom().nextFloat()) * 0.2F + 1.0F;
            cubeMob.playSound(SoundEvents.SLIME_ATTACK, 1.0F, pitch);
            EnchantmentHelper.doPostAttackEffects(serverLevel, victim, source);
        }
    }
}
