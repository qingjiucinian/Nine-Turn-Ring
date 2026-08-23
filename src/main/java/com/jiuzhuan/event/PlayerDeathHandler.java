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

    // 死亡时保存的所有本模组饰品：玩家UUID -> 物品列表
    // 包括九转戒和所有轮转物品，复活时放回对应饰品槽，防止死亡掉落或背包满丢失
    private static final Map<UUID, List<ItemStack>> deathSavedItems = new HashMap<>();

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.isRingEquipped()) return;

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
            // 包括九转戒和所有轮转物品，防止死亡掉落或背包满丢失
            List<ItemStack> saved = extractAllModItemsFromCurios(player);
            if (!saved.isEmpty()) {
                deathSavedItems.put(player.getUUID(), saved);
            }
        });
    }

    /**
     * 兜底：从掉落物列表中移除所有本模组物品，防止极端情况下仍出现在地上
     */
    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        event.getDrops().removeIf(itemEntity -> isModItem(itemEntity.getItem()));
    }

    /**
     * 玩家复活时：把死亡时保存的所有本模组物品放回对应饰品槽，重置饰品状态
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            // 从死亡保存中取回所有本模组物品，放回对应饰品槽
            List<ItemStack> saved = deathSavedItems.remove(player.getUUID());
            if (saved != null && !saved.isEmpty()) {
                for (ItemStack stack : saved) {
                    returnModItemToCurios(player, stack);
                }
            } else if (!hasRing(player)) {
                // 兜底：没有保存的物品且玩家没有戒指，给一个新的九转戒
                ItemStack ring = new ItemStack(ModItems.NINE_TURN_RING.get());
                if (!player.getInventory().add(ring)) {
                    player.spawnAtLocation(ring);
                }
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
    private void returnModItemToCurios(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        try {
            Optional<ICuriosItemHandler> curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isPresent()) {
                ICuriosItemHandler inv = curiosOpt.get();
                String targetSlot = stack.is(ModItems.NINE_TURN_RING.get()) ? "ring" : "rotation";
                var handler = inv.getCurios().get(targetSlot);
                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        if (handler.getStacks().getStackInSlot(i).isEmpty()) {
                            handler.getStacks().setStackInSlot(i, stack);
                            return;
                        }
                    }
                }
                // 目标槽没有空位，放到背包
                if (!player.getInventory().add(stack)) {
                    player.spawnAtLocation(stack);
                }
            }
        } catch (Exception ignored) {
        }
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
