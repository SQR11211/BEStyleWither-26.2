package net.morimori0317.bestylewither.neoforge.handler;

import net.morimori0317.bestylewither.neoforge.networking.BSWPacketsNeoForge;
import net.morimori0317.bestylewither.networking.BSWPackets;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class CommonHandlerNeoForge {
    public static void registerPayload(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BSWPacketsNeoForge.PROTOCOL_VERSION);
        registrar.playToClient(
                BSWPackets.WHITHER_CHARGE_TYPE,
                BSWPackets.WITHER_CHARGE_CODEC,
                BSWPacketsNeoForge::handleWitherChargeDataOnClient
        );
    }
}
