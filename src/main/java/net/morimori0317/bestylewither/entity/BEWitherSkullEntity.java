package net.morimori0317.bestylewither.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.morimori0317.bestylewither.registry.BSWEntities;
import org.jspecify.annotations.Nullable;

/**
 * Bedrock Edition Wither Skull:
 * - Zero drag (inertia = 1.0, liquid_inertia = 1.0, accelerationPower = 0.0)
 * - Speeds: 1.2F (black) and 0.6F (blue/dangerous) with difficulty-scaled spread
 * - Blue skulls support reflect_on_hurt via both deflection and hurtServer
 */
public class BEWitherSkullEntity extends WitherSkull {
    private boolean deflected;

    public BEWitherSkullEntity(EntityType<? extends BEWitherSkullEntity> type, Level level) {
        super(type, level);
        this.accelerationPower = 0.0;
    }

    public BEWitherSkullEntity(Level level, LivingEntity mob, double spawnX, double spawnY, double spawnZ) {
        this(BSWEntities.BE_WITHER_SKULL.get(), level);
        this.setOwner(mob);
        this.snapTo(spawnX, spawnY, spawnZ, mob.getYRot(), mob.getXRot());
        this.accelerationPower = 0.0;
    }

    public void shootBedrock(double dx, double dy, double dz, boolean dangerous, Difficulty difficulty) {
        this.setDangerous(dangerous);
        float speed = dangerous ? 0.6F : 1.2F;
        float inaccuracy = switch (difficulty) {
            case EASY -> 9.0F;
            case HARD -> 3.5F;
            default -> 6.0F;
        };
        this.shoot(dx, dy, dz, speed, inaccuracy);
        this.needsSync = true;
    }

    @Override
    protected float getInertia() {
        return 1.0F;
    }

    @Override
    protected float getLiquidInertia() {
        return 1.0F;
    }

    @Override
    public boolean isPickable() {
        return WitherSkullModification.isBounce(this);
    }

    @Override
    protected void onDeflection(boolean byAttack) {
        super.onDeflection(byAttack);
        this.deflected = true;
        this.accelerationPower = 0.0;
    }

    @Override
    public boolean deflect(ProjectileDeflection deflection, @Nullable Entity deflectingEntity, @Nullable EntityReference<Entity> newOwner, boolean byAttack) {
        if (!WitherSkullModification.isBounce(this)) {
            return false;
        }
        boolean result = super.deflect(deflection, deflectingEntity, newOwner, byAttack);
        if (result && !this.level().isClientSide() && newOwner == null) {
            this.setOwner(deflectingEntity);
        }
        return result;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (!WitherSkullModification.isBounce(this) || this.isInvulnerableToBase(source)) {
            return false;
        }
        if (source.getEntity() instanceof BEWitherBossEntity || source.getEntity() instanceof BEWitherSkeletonEntity) {
            return false;
        }

        this.markHurt();
        Entity attacker = source.getEntity();
        Vec3 dir = null;
        if (attacker != null) {
            dir = attacker.getLookAngle();
        } else if (source.getSourcePosition() != null) {
            dir = this.position().subtract(source.getSourcePosition());
        }

        if (dir != null && dir.lengthSqr() > 1.0E-5) {
            this.setDeltaMovement(dir.normalize().scale(0.9));
        } else {
            this.setDeltaMovement(this.getDeltaMovement().scale(-0.9));
        }

        this.setOwner(attacker);
        this.deflected = true;
        this.needsSync = true;
        return true;
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Entity target = hitResult.getEntity();
        float directDamage = switch (serverLevel.getDifficulty()) {
            case EASY -> 5.0F;
            case HARD -> 12.0F;
            default -> 8.0F;
        };

        boolean wasHurt;
        Entity owner = this.getOwner();
        if (!this.deflected && owner instanceof BEWitherBossEntity
                && (target instanceof BEWitherBossEntity || target instanceof BEWitherSkeletonEntity)) {
            return;
        }
        if (target instanceof WitherBoss vanillaWither && !(target instanceof BEWitherBossEntity)) {
            DamageSource proxy = owner instanceof LivingEntity livingOwner
                    ? new DamageSource(this.damageSources().witherSkull(this, livingOwner).typeHolder(), this.position())
                    : this.damageSources().magic();
            wasHurt = vanillaWither.hurtServer(serverLevel, proxy, directDamage);
            if (wasHurt && owner instanceof LivingEntity livingOwner) {
                vanillaWither.setLastHurtByMob(livingOwner);
                if (vanillaWither.getTarget() == null) {
                    vanillaWither.setTarget(livingOwner);
                }
            }
        } else if (this.deflected && target instanceof BEWitherBossEntity
                && (owner == null || owner instanceof WitherBoss)) {
            wasHurt = target.hurtServer(serverLevel, this.damageSources().magic(), directDamage);
        } else if (owner instanceof LivingEntity livingOwner) {
            DamageSource damageSource = this.damageSources().witherSkull(this, livingOwner);
            float skullDamage = target instanceof Player ? 8.0F : directDamage;
            wasHurt = target.hurtServer(serverLevel, damageSource, skullDamage);
            if (wasHurt && target.isAlive()) {
                EnchantmentHelper.doPostAttackEffects(serverLevel, target, damageSource);
            }
        } else {
            wasHurt = target.hurtServer(serverLevel, this.damageSources().magic(), directDamage);
        }

        if (wasHurt && target instanceof LivingEntity livingEntity) {
            int witherSeconds = 0;
            if (serverLevel.getDifficulty() == Difficulty.NORMAL) {
                witherSeconds = 10;
            } else if (serverLevel.getDifficulty() == Difficulty.HARD) {
                witherSeconds = 40;
            }

            if (witherSeconds > 0) {
                livingEntity.addEffect(new MobEffectInstance(MobEffects.WITHER, 20 * witherSeconds, 1), this.getEffectSource());
            }
        }
    }
}
