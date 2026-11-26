package net.morimori0317.bestylewither.entity;

import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.morimori0317.bestylewither.BEStyleWither;

public class WitherSkullModification {
    public static boolean isBounce(WitherSkull witherSkull) {
        return BEStyleWither.getConfig().isEnableBounceBlueWitherSkull() && witherSkull.isDangerous();
    }

    public static boolean isMoreInertial(WitherSkull witherSkull) {
        return BEStyleWither.getConfig().isEnableMoreInertialBlueWitherSkull() && witherSkull.isDangerous();
    }
}
