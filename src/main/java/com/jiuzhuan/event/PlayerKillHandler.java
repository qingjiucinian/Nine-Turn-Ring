package com.jiuzhuan.event;

import com.jiuzhuan.capability.PlayerDataProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class PlayerKillHandler {

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;

        // 获取击杀者
        if (event.getSource().getEntity() instanceof Player killer) {
            killer.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                if (!data.isRingEquipped()) return;

                // 1转力量：每击杀1只生物+1计数（包括未佩戴一转时的击杀）
                data.addPowerKill(1);
                // 5转血量：每击杀1只生物+1计数（包括未佩戴五转时的击杀）
                data.addHealthKill(1);
                // 实时同步到客户端，刷新tooltip显示
                data.syncToClient(killer);
            });
        }
    }
}
