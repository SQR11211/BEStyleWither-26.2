package net.morimori0317.bestylewither.client;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.WitherSkeletonRenderer;
import net.minecraft.client.renderer.entity.WitherSkullRenderer;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.morimori0317.bestylewither.BEStyleWither;
import net.morimori0317.bestylewither.entity.BEWitherBoss;
import net.morimori0317.bestylewither.entity.BEWitherBossEntity;
import net.morimori0317.bestylewither.registry.BSWEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = BEStyleWither.MODID, value = Dist.CLIENT)
public final class BEWitherClient {
    private record SpawnCue(int revolution, EntityBoundSoundInstance sound) {}

    private static final Map<BEWitherBossEntity, SpawnCue> spawnCues = new IdentityHashMap<>();

    static float spawnRotation(float remaining) {
        if (remaining <= 0 || !BEStyleWither.getConfig().isEnableSpinAndWhiteSummon()) {
            return 0;
        }
        // Accelerate from five degrees per tick and finish facing the original direction.
        float progress = 1.0F - Math.clamp(remaining / BEWitherBossEntity.SPAWN_TICKS, 0.0F, 1.0F);
        return progress * (5.0F * BEWitherBossEntity.SPAWN_TICKS * (1.0F - progress) + 21.0F * 360.0F * progress);
    }

    @SubscribeEvent
    public static void tickSpawnSound(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof BEWitherBossEntity boss) || !boss.level().isClientSide()) {
            return;
        }
        if (boss.getInvulnerableTicks() <= 0 || !boss.isAlive() || boss.isSilent() || !BEStyleWither.getConfig().isEnableSpinAndWhiteSummon()) {
            stopSpawnCue(boss);
            return;
        }

        int revolution = (int) (spawnRotation(boss.getInvulnerableTicks()) / 360.0F);
        SpawnCue cue = spawnCues.get(boss);
        if (cue == null || cue.revolution() != revolution) {
            stopSpawnCue(boss);
            var sound = new EntityBoundSoundInstance(SoundEvents.WITHER_SPAWN, boss.getSoundSource(), 3.0F, 1.0F,
                    boss, boss.getRandom().nextLong());
            spawnCues.put(boss, new SpawnCue(revolution, sound));
            Minecraft.getInstance().getSoundManager().play(sound);
        }
    }

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof BEWitherBossEntity boss) {
            stopSpawnCue(boss);
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (!spawnCues.isEmpty()) {
            var soundManager = Minecraft.getInstance().getSoundManager();
            for (SpawnCue cue : spawnCues.values()) {
                soundManager.stop(cue.sound());
            }
            spawnCues.clear();
        }
    }

    private static void stopSpawnCue(BEWitherBossEntity boss) {
        SpawnCue cue = spawnCues.remove(boss);
        if (cue != null) {
            Minecraft.getInstance().getSoundManager().stop(cue.sound());
        }
    }

    private BEWitherClient() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BSWEntities.BE_WITHER.get(), BEWitherBossRenderer::new);
        event.registerEntityRenderer(BSWEntities.BE_WITHER_SKULL.get(), WitherSkullRenderer::new);
        event.registerEntityRenderer(BSWEntities.BE_WITHER_SKELETON.get(), WitherSkeletonRenderer::new);
    }

    public static void onWitherChargePacket(int entityId, int durationTicks) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(entityId) instanceof BEWitherBoss wither) {
            wither.beStyleWither$getInstance().setClientChargeDuration(durationTicks);
            wither.beStyleWither$getInstance().setClientChargeTick(durationTicks);
        }
    }
}
