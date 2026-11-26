package net.morimori0317.bestylewither.neoforge.networking;

import net.morimori0317.bestylewither.networking.BSWPackets;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class BSWPacketsNeoForge {
    public static final String PROTOCOL_VERSION = "1";

    public static void handleWitherChargeDataOnClient(BSWPackets.WitherChargeMessage data, IPayloadContext context) {
        context.enqueueWork(() -> BSWPackets.onWhitherChargePacket(data.entityId(), data.durationTicks()));
    }

    public static void init() {
    }
}
