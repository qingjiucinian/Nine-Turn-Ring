package com.jiuzhuan.event;

import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.config.ServerConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 死亡相关处理。
 * <p>
 * 死亡保留采用 Curios 原生规则（对齐七咒之戒）：
 * <ul>
 *   <li>九转戒：{@link com.jiuzhuan.item.NineTurnRingItem#getDropRule} 返回
 *       {@code DropRule.ALWAYS_KEEP}，死亡时始终保留在戒指槽，不离槽、不掉落。</li>
 *   <li>轮转物品：使用默认 {@code DropRule.DEFAULT}，开启死亡掉落时随死亡正常掉落，
 *       开启死亡不掉落（keepInventory）时随原版规则保留。</li>
 * </ul>
 * 本类只负责：7转涅槃免死，以及死亡掉落时清除轮转的饰品快照/激活状态，
 * 防止饰品保护监控把正常掉落的轮转恢复回槽位。
 */
public class PlayerDeathHandler {

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            // 7转涅槃：致死伤害时直接回血免死，不触发死亡（冷却由配置控制）
            if (data.isActivated(7)) {
                long now = System.currentTimeMillis();
                if (!data.isInCooldown(now)) {
                    event.setCanceled(true);
                    float maxHealth = player.getMaxHealth();
                    player.setHealth(maxHealth * (float) ServerConfig.getRot7HealRatio());
                    data.setInvincibleEnd(now + (long) ServerConfig.getRot7InvincibleSeconds() * 1000L);
                    data.setUndyingCooldownEnd(now + (long) ServerConfig.getRot7CooldownSeconds() * 1000L);
                    data.syncToClient(player);
                    player.sendSystemMessage(Component.translatable("nine_turn_ring.message.seven_triggered"));
                    return;
                }
            }

            // 开启死亡不掉落：戒指与轮转全部由原版/Curios保留，不干预
            if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                return;
            }

            // 死亡掉落开启：九转戒由 ALWAYS_KEEP 留槽，轮转按默认规则掉落。
            // 清除轮转饰品快照，避免饰品保护监控在死亡/复活后把已掉落的轮转恢复回轮转槽。
            data.getAccessorySnapshot().keySet().removeIf(key -> key.startsWith("rotation:"));
        });
    }

    /**
     * 复活后校准：死亡掉落开启时轮转已正常掉落，清除其激活状态与残留快照。
     * 九转戒 ALWAYS_KEEP 未离槽，其装备状态由 {@link com.jiuzhuan.capability.PlayerDataProvider}
     * 在复活时（LOWEST 优先级）按 Curios 槽位实际状态校准，此处不改动。
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        // 死亡不掉落：物品与激活状态全部保留，不作处理
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            for (int i = 1; i <= 10; i++) {
                data.setActivated(i, false);
            }
            data.getAccessorySnapshot().keySet().removeIf(key -> key.startsWith("rotation:"));
            data.syncToClient(player);
        });
    }
}
