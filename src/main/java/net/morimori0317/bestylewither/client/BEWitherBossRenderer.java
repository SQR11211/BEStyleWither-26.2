package net.morimori0317.bestylewither.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.morimori0317.bestylewither.BEStyleWither;
import net.morimori0317.bestylewither.entity.BEWitherBossEntity;
import net.morimori0317.bestylewither.entity.ExplodeByDieConstants;

public class BEWitherBossRenderer extends MobRenderer<BEWitherBossEntity, BEWitherRenderState, BEWitherBossModel> {
    private static final Identifier WITHER_INVULNERABLE_LOCATION = Identifier.withDefaultNamespace("textures/entity/wither/wither_invulnerable.png");
    private static final Identifier WITHER_LOCATION = Identifier.withDefaultNamespace("textures/entity/wither/wither.png");

    public BEWitherBossRenderer(EntityRendererProvider.Context context) {
        super(context, new BEWitherBossModel(context.bakeLayer(ModelLayers.WITHER)), 1.0F);
        this.addLayer(new BEWitherArmorLayer(this, context.getModelSet()));
    }

    @Override
    protected int getBlockLightLevel(BEWitherBossEntity entity, BlockPos blockPos) {
        return 15;
    }

    @Override
    public Identifier getTextureLocation(BEWitherRenderState state) {
        if (state.isExplodeByDie && state.rawDeathTime > 0) {
            return WITHER_INVULNERABLE_LOCATION;
        }
        int invulnerableTicks = Mth.floor(state.invulnerableTicks);
        // Official Mojang wither.entity.json: (invulnerable <= 0) || (invulnerable <= 80 && (invulnerable / 5) % 2 == 1)
        boolean displayNormalSkin = invulnerableTicks <= 0
                || (invulnerableTicks <= 80 && (invulnerableTicks / 5) % 2 == 1);
        return displayNormalSkin ? WITHER_LOCATION : WITHER_INVULNERABLE_LOCATION;
    }

    @Override
    public BEWitherRenderState createRenderState() {
        return new BEWitherRenderState();
    }

    @Override
    protected void scale(BEWitherRenderState state, PoseStack poseStack) {
        float baseScale = 2.0F;
        if (state.invulnerableTicks > 0.0F) {
            baseScale -= (state.invulnerableTicks / 220.0F) * 1.0F;
        }

        if (state.swellAmount > 0.0F) {
            float sc = Mth.clamp(state.swellAmount, 0.0F, 1.0F);
            float wobble = 1.0F + Mth.sin(state.swellAmount * 100.0F) * state.swellAmount * 0.01F;
            float adj = sc * sc * sc * sc;
            float scaleXZ = (1.0F + adj * 0.4F) * wobble;
            float scaleY = (1.0F + adj * 0.1F) / wobble;
            poseStack.scale(baseScale * scaleXZ, baseScale * scaleY, baseScale * scaleXZ);
        } else {
            poseStack.scale(baseScale, baseScale, baseScale);
        }
    }

    @Override
    protected void setupRotations(BEWitherRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.spawnRotation));
        if (state.shakeOffsetX != 0.0F || state.shakeOffsetZ != 0.0F) {
            poseStack.translate(state.shakeOffsetX, 0.0F, state.shakeOffsetZ);
        }

        if (state.isExplodeByDie) {
            float savedDeathTime = state.deathTime;
            state.deathTime = 0.0F;
            try {
                super.setupRotations(state, poseStack, bodyRot, entityScale);
            } finally {
                state.deathTime = savedDeathTime;
            }
        } else {
            super.setupRotations(state, poseStack, bodyRot, entityScale);
        }
    }

    @Override
    protected float getWhiteOverlayProgress(BEWitherRenderState state) {
        return state.whiteOverlayAlpha;
    }

    @Override
    public void extractRenderState(BEWitherBossEntity entity, BEWitherRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        int invulnerableTicks = entity.getInvulnerableTicks();
        state.invulnerableTicks = invulnerableTicks > 0 ? invulnerableTicks - partialTicks : 0.0F;
        for (int i = 0; i < Math.min(state.xHeadRots.length, state.yHeadRots.length); i++) {
            state.xHeadRots[i] = entity.getHeadXRot(i, partialTicks);
            state.yHeadRots[i] = entity.getHeadYRot(i, partialTicks);
        }
        state.isPowered = entity.isPowered();

        state.rawDeathTime = entity.deathTime;
        state.spawnRotation = BEWitherClient.spawnRotation(state.invulnerableTicks);
        state.isExplodeByDie = BEStyleWither.getConfig().isEnableExplodeByDie();
        state.explodeByDieEnergyLayerVisible = entity.beStyleWither$getInstance().isExplodeByDieEnergyLayerVisible();

        state.dashProgress = entity.getDashProgress(partialTicks);

        float swell = 0.0F;
        float whiteAlpha = 0.0F;
        float shakeX = 0.0F;
        float shakeZ = 0.0F;

        if (BEStyleWither.getConfig().isEnableSpinAndWhiteSummon() && invulnerableTicks > 0 && invulnerableTicks <= 60) {
            float p = Mth.clamp(1.0F - state.invulnerableTicks / 60.0F, 0.0F, 1.0F);
            swell = p;
            whiteAlpha = p;
        }

        if (state.isExplodeByDie && entity.deathTime > 0) {
            float p = Mth.clamp((entity.deathTime + partialTicks) / (float) ExplodeByDieConstants.EXPLODE_TICK, 0.0F, 1.0F);
            swell = p;
            whiteAlpha = Mth.clamp(p * 1.25F, 0.0F, 1.0F);
            float t = entity.tickCount + partialTicks;
            shakeX = Mth.sin(t * 2.7F) * 0.08F * p;
            shakeZ = Mth.cos(t * 3.1F) * 0.08F * p;
        }

        state.swellAmount = swell;
        state.whiteOverlayAlpha = whiteAlpha;
        state.shakeOffsetX = shakeX;
        state.shakeOffsetZ = shakeZ;

        if (state.isExplodeByDie) {
            state.hasRedOverlay = entity.hurtTime > 0;
        }
    }
}
