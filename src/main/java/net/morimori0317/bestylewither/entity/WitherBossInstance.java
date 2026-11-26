package net.morimori0317.bestylewither.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class WitherBossInstance {
    public static final int HALF_HEALTH_EXPLODE_RETENTION_TICK = 40;
    public static final int HALF_HEALTH_EXPLODE_MAX_ELAPSED_TICK = 60;
    private final WitherBoss witherBoss;
    private final EntityDataAccessor<Boolean> onceWeakenedDataAccessor;
    private int chargeCoolDown;
    private int clientChargeTick;
    private int clientChargeTickOld;
    private int clientChargeDuration = 75;
    private boolean difficultyHealthApplied;
    private boolean halfHealthExploded;
    private int halfHealthExplodeFallTick;
    private int halfHealthExplodeElapsedTick;
    private int explodeByDieDeathBlinkingTick;
    private boolean explodeByDieEnergyLayerVisible;
    private DamageSource explodeByDieDeathDamageSource;
    private boolean skipLootDrop;

    public WitherBossInstance(WitherBoss witherBoss, EntityDataAccessor<Boolean> onceWeakenedDataAccessor) {
        this.witherBoss = witherBoss;
        this.onceWeakenedDataAccessor = onceWeakenedDataAccessor;
    }

    public void save(ValueOutput output) {
        output.putBoolean("OnceWeakened", this.isOnceWeakened());
        output.putBoolean("HalfHealthExploded", this.isHalfHealthExploded());
        output.putBoolean("SkipLootDrop", this.isSkipLootDrop());
        output.putBoolean("DifficultyHealthApplied", this.isDifficultyHealthApplied());
    }

    public void load(ValueInput input) {
        this.setOnceWeakened(input.getBooleanOr("OnceWeakened", false));
        this.setHalfHealthExploded(input.getBooleanOr("HalfHealthExploded", false));
        this.setSkipLootDrop(input.getBooleanOr("SkipLootDrop", false));
        this.setDifficultyHealthApplied(input.getBooleanOr("DifficultyHealthApplied", false));
    }

    public int getChargeCoolDown() {
        return this.chargeCoolDown;
    }

    public void setChargeCoolDown(int chargeCoolDown) {
        this.chargeCoolDown = chargeCoolDown;
    }

    public void setClientChargeTick(int clientChargeTick) {
        this.clientChargeTick = clientChargeTick;
    }

    public float getClientChargeTick(float delta) {
        return Mth.lerp(delta, this.clientChargeTickOld, this.clientChargeTick);
    }

    public int getClientChargeTick() {
        return this.clientChargeTick;
    }

    public int getClientChargeTickOld() {
        return this.clientChargeTickOld;
    }

    public void setClientChargeTickOld(int clientChargeTickOld) {
        this.clientChargeTickOld = clientChargeTickOld;
    }

    public int getClientChargeDuration() {
        return this.clientChargeDuration;
    }

    public void setClientChargeDuration(int clientChargeDuration) {
        this.clientChargeDuration = Math.max(1, clientChargeDuration);
    }

    public boolean isDifficultyHealthApplied() {
        return this.difficultyHealthApplied;
    }

    public void setDifficultyHealthApplied(boolean difficultyHealthApplied) {
        this.difficultyHealthApplied = difficultyHealthApplied;
    }

    public void setOnceWeakened(boolean onceWeakened) {
        this.witherBoss.getEntityData().set(this.onceWeakenedDataAccessor, onceWeakened);
    }

    public boolean isOnceWeakened() {
        return this.witherBoss.getEntityData().get(this.onceWeakenedDataAccessor);
    }

    public void setHalfHealthExploded(boolean halfHealthExploded) {
        this.halfHealthExploded = halfHealthExploded;
    }

    public boolean isHalfHealthExploded() {
        return this.halfHealthExploded;
    }

    public void setHalfHealthExplodeFallTick(int halfHealthExplodeFallTick) {
        this.halfHealthExplodeFallTick = halfHealthExplodeFallTick;
    }

    public int getHalfHealthExplodeFallTick() {
        return this.halfHealthExplodeFallTick;
    }

    public void setHalfHealthExplodeElapsedTick(int halfHealthExplodeElapsedTick) {
        this.halfHealthExplodeElapsedTick = halfHealthExplodeElapsedTick;
    }

    public int getHalfHealthExplodeElapsedTick() {
        return this.halfHealthExplodeElapsedTick;
    }

    public void setExplodeByDieDeathDamageSource(DamageSource explodeByDieDeathDamageSource) {
        this.explodeByDieDeathDamageSource = explodeByDieDeathDamageSource;
    }

    public DamageSource getExplodeByDieDeathDamageSource() {
        return this.explodeByDieDeathDamageSource;
    }

    public int getExplodeByDieDeathBlinkingTick() {
        return this.explodeByDieDeathBlinkingTick;
    }

    public void setExplodeByDieDeathBlinkingTick(int explodeByDieDeathBlinkingTick) {
        this.explodeByDieDeathBlinkingTick = explodeByDieDeathBlinkingTick;
    }

    public boolean isExplodeByDieEnergyLayerVisible() {
        return this.explodeByDieEnergyLayerVisible;
    }

    public void setExplodeByDieEnergyLayerVisible(boolean explodeByDieEnergyLayerVisible) {
        this.explodeByDieEnergyLayerVisible = explodeByDieEnergyLayerVisible;
    }

    public void setSkipLootDrop(boolean skipLootDrop) {
        this.skipLootDrop = skipLootDrop;
    }

    public boolean isSkipLootDrop() {
        return this.skipLootDrop;
    }
}
