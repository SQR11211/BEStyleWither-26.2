package net.morimori0317.bestylewither.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.morimori0317.bestylewither.BEStyleWither;
import net.morimori0317.bestylewither.entity.ExplodeByDieConstants;

public class BEWitherArmorLayer extends EnergySwirlLayer<BEWitherRenderState, BEWitherBossModel> {
    private static final Identifier WITHER_ARMOR_WHITE_LOCATION =
            Identifier.fromNamespaceAndPath(BEStyleWither.MODID, "textures/entity/wither/wither_armor_white.png");
    private static final Identifier WITHER_ARMOR_BLUE_LOCATION =
            Identifier.fromNamespaceAndPath(BEStyleWither.MODID, "textures/entity/wither/wither_armor_blue.png");
    private final BEWitherBossModel model;

    public BEWitherArmorLayer(RenderLayerParent<BEWitherRenderState, BEWitherBossModel> renderer, EntityModelSet modelSet) {
        super(renderer);
        this.model = new BEWitherBossModel(modelSet.bakeLayer(ModelLayers.WITHER_ARMOR));
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, BEWitherRenderState state, float yRot, float xRot) {
        boolean dyingOverlay = state.isExplodeByDie && state.rawDeathTime > ExplodeByDieConstants.ENERGY_LAYER_START_TICK;
        boolean shouldRender = dyingOverlay ? state.explodeByDieEnergyLayerVisible : state.isPowered;
        if (!shouldRender) {
            return;
        }

        float t = dyingOverlay ? state.ageInTicks * (1.0F + state.swellAmount) : state.ageInTicks;

        // Official Mojang bedrock render_controllers: controller.render.wither_boss_armor_white + armor_blue
        float whiteU = (Mth.cos(t * 0.02F) * 3.0F) % 1.0F;
        float whiteV = (t * 0.01F) % 1.0F;
        submitNodeCollector.order(1).submitModel(
                this.model,
                state,
                poseStack,
                RenderTypes.energySwirl(WITHER_ARMOR_WHITE_LOCATION, whiteU, whiteV),
                lightCoords,
                OverlayTexture.NO_OVERLAY,
                -8355712,
                null,
                state.outlineColor,
                null
        );

        float blueOffset = (t * 0.01F) % 1.0F;
        submitNodeCollector.order(2).submitModel(
                this.model,
                state,
                poseStack,
                RenderTypes.energySwirl(WITHER_ARMOR_BLUE_LOCATION, blueOffset, blueOffset),
                lightCoords,
                OverlayTexture.NO_OVERLAY,
                -8355712,
                null,
                state.outlineColor,
                null
        );
    }

    @Override
    protected boolean isPowered(BEWitherRenderState state) {
        return state.isPowered;
    }

    @Override
    protected float xOffset(float t) {
        return Mth.cos(t * 0.02F) * 3.0F;
    }

    @Override
    protected Identifier getTextureLocation() {
        return WITHER_ARMOR_WHITE_LOCATION;
    }

    @Override
    protected BEWitherBossModel model() {
        return this.model;
    }
}
