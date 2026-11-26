package net.morimori0317.bestylewither.explatform.neoforge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.morimori0317.bestylewither.config.BESConfig;
import net.morimori0317.bestylewither.neoforge.BEStyleWitherNeoForge;
import net.morimori0317.bestylewither.networking.BSWPackets;
import net.neoforged.neoforge.network.PacketDistributor;

public class BSWExpectPlatformImpl {
    public static void sendWhitherChargePacket(ServerLevel level, ChunkPos chunkPos, int entityId, int durationTicks) {
        PacketDistributor.sendToPlayersTrackingChunk(level, chunkPos, new BSWPackets.WitherChargeMessage(entityId, durationTicks));
    }

    public static BESConfig getConfig() {
        return BEStyleWitherNeoForge.CONFIG;
    }
}
