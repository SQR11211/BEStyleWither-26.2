package net.morimori0317.bestylewither.explatform;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.morimori0317.bestylewither.config.BESConfig;
import net.morimori0317.bestylewither.explatform.neoforge.BSWExpectPlatformImpl;

public class BSWExpectPlatform {
    public static void sendWhitherChargePacket(ServerLevel level, ChunkPos chunkPos, int entityId, int durationTicks) {
        BSWExpectPlatformImpl.sendWhitherChargePacket(level, chunkPos, entityId, durationTicks);
    }

    public static BESConfig getConfig() {
        return BSWExpectPlatformImpl.getConfig();
    }
}
