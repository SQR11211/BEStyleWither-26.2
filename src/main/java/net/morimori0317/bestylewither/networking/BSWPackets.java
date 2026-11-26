package net.morimori0317.bestylewither.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.morimori0317.bestylewither.BEStyleWither;
import net.morimori0317.bestylewither.client.BEWitherClient;

public class BSWPackets {
    public static final CustomPacketPayload.Type<WitherChargeMessage> WHITHER_CHARGE_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(BEStyleWither.MODID, "whither_charge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WitherChargeMessage> WITHER_CHARGE_CODEC = new StreamCodec<>() {
        @Override
        public void encode(RegistryFriendlyByteBuf buf, WitherChargeMessage msg) {
            buf.writeInt(msg.entityId());
            buf.writeInt(msg.durationTicks());
        }

        @Override
        public WitherChargeMessage decode(RegistryFriendlyByteBuf buf) {
            return new WitherChargeMessage(buf.readInt(), buf.readInt());
        }
    };

    public static void onWhitherChargePacket(int entityId, int durationTicks) {
        BEWitherClient.onWitherChargePacket(entityId, durationTicks);
    }

    public record WitherChargeMessage(int entityId, int durationTicks) implements CustomPacketPayload {
        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return WHITHER_CHARGE_TYPE;
        }
    }
}
