package com.jiuzhuan.event;

import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.config.ServerConfig;
import com.jiuzhuan.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PlayerDeathHandler {

    // 死亡时保存的Curios栏物品：玩家UUID -> 物品列表，复活时放回对应饰品槽
    private static final Map<UUID, List<ItemStack>> deathSavedCurios = new HashMap<>();
    // 死亡时从掉落物中移除的背包物品：玩家UUID -> 物品列表，复活时放回背包
    private static final Map<UUID, List<ItemStack>> deathSavedInventory = new HashMap<>();
    // 复活后待装备的物品：玩家UUID -> (物品列表, 剩余尝试tick数)
    // 复活时Curios栏可能还没重建好，延迟几tick再装备
    private static final Map<UUID, java.util.AbstractMap.SimpleEntry<List<ItemStack>, Integer>> pendingEquip = new HashMap<>();

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            // 7转不死：死亡事件中只检查isActivated状态，不检查饰品是否在槽中
            if (data.isActivated(7)) {
                long now = System.currentTimeMillis();
                if (!data.isInCooldown(now)) {
                    // 7转触发复活：取消死亡，玩家没死，不需要保存
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

            // 玩家真死了：从Curios饰品栏取出所有本模组物品保存，复活时放回
            // 背包里的本模组物品不处理，正常掉落
            List<ItemStack> saved = extractAllModItemsFromCurios(player);
            if (!saved.isEmpty()) {
                deathSavedCurios.put(player.getUUID(), saved);
            }
        });
    }

    /**
     * 兜底：从掉落物列表中移除所有本模组物品，用最低优先级确保在所有模组添加完掉落物后再移除
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public void onLivingDrops(LivingDropsEvent event) {
        // 不处理掉落物：饰品栏的本模组物品已在 onLivingDeath 中取出保存
        // 背包里的本模组物品正常掉落，不做任何干预
    }

    /**
     * 玩家复活时：把死亡时保存的所有本模组物品放回对应饰品槽，重置饰品状态
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            // 从死亡保存中取回Curios栏物品，放到待装备列表（延迟装备，等Curios栏重建）
            List<ItemStack> curiosSaved = deathSavedCurios.remove(player.getUUID());
            if (curiosSaved != null && !curiosSaved.isEmpty()) {
                pendingEquip.put(player.getUUID(), new java.util.AbstractMap.SimpleEntry<>(curiosSaved, 100));
            } else if (!hasRing(player)) {
                // 兜底：没有保存的物品且玩家没有戒指，给一个新的九转戒（也延迟装备）
                List<ItemStack> fallback = new ArrayList<>();
                fallback.add(new ItemStack(ModItems.NINE_TURN_RING.get()));
                pendingEquip.put(player.getUUID(), new java.util.AbstractMap.SimpleEntry<>(fallback, 100));
            }
            // 重置戒指装备状态，等Curios栏重建后由tick校验重新设置
            data.setRingEquipped(false);
            // 清除所有转的激活状态（死亡后轮转槽已清空）
            for (int i = 1; i <= 10; i++) {
                data.setActivated(i, false);
            }
            // 清除饰品快照（死亡后饰品栏已清空，旧快照无效，避免错误恢复）
            data.getAccessorySnapshot().clear();
            data.syncToClient(player);
        });
    }

    /**
     * 玩家tick：尝试装备复活后待装备的物品
     * 只装备死亡时保存的物品，不碰玩家背包里原有的备用件
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        var entry = pendingEquip.get(player.getUUID());
        if (entry == null) return;
        List<ItemStack> items = entry.getKey();
        int remaining = entry.getValue();
        if (items.isEmpty() || remaining <= 0) {
            // 超时或没有物品，剩下的放到背包
            for (ItemStack stack : items) {
                if (!player.getInventory().add(stack)) {
                    player.spawnAtLocation(stack);
                }
            }
            pendingEquip.remove(player.getUUID());
            return;
        }
        // 尝试逐个装备
        List<ItemStack> failed = new ArrayList<>();
        for (ItemStack stack : items) {
            if (!returnModItemToCurios(player, stack)) {
                failed.add(stack);
            }
        }
        if (failed.isEmpty()) {
            pendingEquip.remove(player.getUUID());
        } else {
            entry.setValue(remaining - 1);
            pendingEquip.put(player.getUUID(), new java.util.AbstractMap.SimpleEntry<>(failed, remaining - 1));
        }
    }

    /**
     * 从Curios饰品栏取出所有本模组物品（九转戒 + 所有轮转物品），同时清空对应槽位
     */
    private List<ItemStack> extractAllModItemsFromCurios(Player player) {
        List<ItemStack> result = new ArrayList<>();
        try {
            Optional<ICuriosItemHandler> curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isPresent()) {
                ICuriosItemHandler inv = curiosOpt.get();
                for (var entry : inv.getCurios().entrySet()) {
                    var handler = entry.getValue();
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStacks().getStackInSlot(i);
                        if (isModItem(stack)) {
                            result.add(stack.copy());
                            handler.getStacks().setStackInSlot(i, ItemStack.EMPTY);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    /**
     * 把本模组物品放回对应饰品槽：九转戒放回ring槽，轮转物品放回rotation槽
     */
    private boolean returnModItemToCurios(Player player, ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            Optional<ICuriosItemHandler> curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isPresent()) {
                ICuriosItemHandler inv = curiosOpt.get();
                boolean isRing = stack.is(ModItems.NINE_TURN_RING.get());
                // 遍历所有槽位，找到对应类型的空槽位（用endsWith匹配，兼容带命名空间的槽id）
                for (var entry : inv.getCurios().entrySet()) {
                    String slotId = entry.getKey();
                    if (isRing) {
                        if (!slotId.equals("ring") && !slotId.endsWith(":ring")) continue;
                    } else {
                        if (!slotId.equals("rotation") && !slotId.endsWith(":rotation")) continue;
                    }
                    var handler = entry.getValue();
                    for (int i = 0; i < handler.getSlots(); i++) {
                        if (handler.getStacks().getStackInSlot(i).isEmpty()) {
                            handler.getStacks().setStackInSlot(i, stack);
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * 判断物品是否为本模组物品（九转戒或任意轮转物品）
     */
    private boolean isModItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(ModItems.NINE_TURN_RING.get())) return true;
        return getRotationLevel(stack) > 0;
    }

    /**
     * 检查玩家背包或Curios饰品栏中是否已有九转戒
     */
    private boolean hasRing(Player player) {
        // 检查背包
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.NINE_TURN_RING.get())) {
                return true;
            }
        }
        // 检查Curios饰品栏
        try {
            var curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isPresent()) {
                var inv = curiosOpt.get();
                for (var entry : inv.getCurios().entrySet()) {
                    var handler = entry.getValue();
                    for (int i = 0; i < handler.getSlots(); i++) {
                        if (handler.getStacks().getStackInSlot(i).is(ModItems.NINE_TURN_RING.get())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * 实时校验某转物品是否真的在轮转槽中（防止activated状态残留）
     */
    private boolean isRotationInSlot(Player player, int rotation) {
        try {
            var curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isEmpty()) return false;
            var inv = curiosOpt.get();
            var handler = inv.getCurios().get("rotation");
            if (handler == null) return false;
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStacks().getStackInSlot(i);
                if (getRotationLevel(stack) == rotation) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private int getRotationLevel(ItemStack stack) {
        if (stack.is(ModItems.ROTATION_1_POWER.get())) return 1;
        if (stack.is(ModItems.ROTATION_2_SATIETY.get())) return 2;
        if (stack.is(ModItems.ROTATION_3_NIGHT_VISION.get())) return 3;
        if (stack.is(ModItems.ROTATION_4_REGEN.get())) return 4;
        if (stack.is(ModItems.ROTATION_5_HEALTH.get())) return 5;
        if (stack.is(ModItems.ROTATION_6_RESISTANCE.get())) return 6;
        if (stack.is(ModItems.ROTATION_7_UNDYING.get())) return 7;
        if (stack.is(ModItems.ROTATION_8_LUCK.get())) return 8;
        if (stack.is(ModItems.ROTATION_9_IMMORTAL.get())) return 9;
        if (stack.is(ModItems.ROTATION_10_ADAPTATION.get())) return 10;
        return 0;
    }
}
