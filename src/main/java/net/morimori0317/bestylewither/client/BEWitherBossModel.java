package net.morimori0317.bestylewither.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.WitherRenderState;
import net.minecraft.util.Mth;

public class BEWitherBossModel extends EntityModel<BEWitherRenderState> {
    private final ModelPart centerHead;
    private final ModelPart rightHead;
    private final ModelPart leftHead;
    private final ModelPart ribcage;
    private final ModelPart tail;

    public BEWitherBossModel(ModelPart root) {
        super(root);
        this.ribcage = root.getChild("ribcage");
        this.tail = root.getChild("tail");
        this.centerHead = root.getChild("center_head");
        this.rightHead = root.getChild("right_head");
        this.leftHead = root.getChild("left_head");
    }

    @Override
    public void setupAnim(BEWitherRenderState state) {
        super.setupAnim(state);
        setupHeadRotation(state, this.rightHead, 0);
        setupHeadRotation(state, this.leftHead, 1);
        float anim = Mth.cos(state.ageInTicks * 0.1F);
        this.ribcage.xRot = (0.065F + 0.05F * anim) * (float) Math.PI;
        this.ribcage.xRot += 1.30F * state.dashProgress;
        this.tail.setPos(-2.0F, 6.9F + Mth.cos(this.ribcage.xRot) * 10.0F, -0.5F + Mth.sin(this.ribcage.xRot) * 10.0F);
        this.tail.xRot = Mth.lerp(state.dashProgress, (0.265F + 0.1F * anim) * (float) Math.PI, this.ribcage.xRot + 0.12F);
        this.centerHead.yRot = state.yRot * (float) (Math.PI / 180.0);
        this.centerHead.xRot = state.xRot * (float) (Math.PI / 180.0);

    }

    private static void setupHeadRotation(WitherRenderState state, ModelPart head, int headIndex) {
        head.yRot = (state.yHeadRots[headIndex] - state.bodyRot) * (float) (Math.PI / 180.0);
        head.xRot = state.xHeadRots[headIndex] * (float) (Math.PI / 180.0);
    }
}
