package com.jiuzhuan.event;

import com.jiuzhuan.capability.IPlayerData;
import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioUnequipEvent;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.*;

/**
 * 饰品防收取处理器
 *
 * 多层防御机制，防止九转戒和1-10转物品被BOSS（如诡厄巫法启示录的下界亚波伦）
 * 或其他机制强制摘取/收取：
 *
 * 第一层：RotationItem.canUnequip() 返回 false（非创造模式）
 * 第二层：RotationItem.onDroppedByPlayer() 返回 false，禁止丢弃
 * 第三层：ItemTossEvent 监听，阻止戒指/轮转物品被扔出
 * 第四层（本类核心）：Tick 级快照监控，每5tick扫描Curios栏，
 *   若发现已装备的戒指/轮转物品被强制移除，立即从快照恢复到原槽位
 * 第五层：背包扫描兜底，若物品被移到背包中，自动装备回Curios槽位
 */
public class AccessoryProtectionHandler {

    // 需要保护的物品RegistryObject列表（引用本身安全，.get()需等注册完成后调用）
    @SuppressWarnings("unchecked")
    private static final RegistryObject<net.minecraft.world.item.Item>[] PROTECTED_REFS = new RegistryObject[]{
            ModItems.NINE_TURN_RING,
            ModItems.ROTATION_1_POWER,
            ModItems.ROTATION_2_SATIETY,
            ModItems.ROTATION_3_NIGHT_VISION,
            ModItems.ROTATION_4_REGEN,
            ModItems.ROTATION_5_HEALTH,
            ModItems.ROTATION_6_RESISTANCE,
            ModItems.ROTATION_7_UNDYING,
            ModItems.ROTATION_8_LUCK,
            ModItems.ROTATION_9_IMMORTAL,
            ModItems.ROTATION_10_ADAPTATION
    };

    // 懒加载：首次调用时才构建实际Item集合（此时注册已完成）
    private static volatile Set<net.minecraft.world.item.Item> protectedItemsCache = null;
    // 记录玩家最近一次手动取下饰品的时间戳，冷却期内不自动从背包移回（区分手动取下与BOSS强制收取）
    private static final Map<UUID, Long> manualUnequipTime = new HashMap<>();
    private static final long MANUAL_UNEQUIP_COOLDOWN_MS = 15000; // 15秒冷却

    private static Set<net.minecraft.world.item.Item> getProtectedItems() {
        Set<net.minecraft.world.item.Item> cache = protectedItemsCache;
        if (cache != null) return cache;
        synchronized (AccessoryProtectionHandler.class) {
            cache = protectedItemsCache;
            if (cache != null) return cache;
            cache = new HashSet<>();
            for (RegistryObject<net.minecraft.world.item.Item> ref : PROTECTED_REFS) {
                if (ref.isPresent()) {
                    cache.add(ref.get());
                }
            }
            protectedItemsCache = cache;
            return cache;
        }
    }

    /**
     * 判断物品是否为受保护的九转戒/轮转物品
     */
    public static boolean isProtectedItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return getProtectedItems().contains(stack.getItem());
    }

    /**
     * 第三层防御：阻止受保护物品被玩家扔出
     */
    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (isProtectedItem(stack)) {
            event.setCanceled(true);
        }
    }

    /**
     * 玩家主动通过Curios界面取下受保护物品时，删除该槽位的快照并设置手动取下冷却。
     * BOSS强制摘取通常直接操作物品栏，不触发此事件，快照保留并正常恢复。
     * 冷却期内第三步（背包扫描移回）不生效，让玩家可以自由取下轮转物品。
     */
    @SubscribeEvent
    public void onCurioUnequip(CurioUnequipEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        ItemStack stack = event.getStack();
        if (!isProtectedItem(stack)) return;
        String slotKey = event.getSlotContext().identifier() + ":" + event.getSlotContext().index();
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            data.getAccessorySnapshot().remove(slotKey);
        });
        // 记录手动取下时间，启动冷却
        manualUnequipTime.put(player.getUUID(), System.currentTimeMillis());
    }

    /**
     * 清除玩家的手动取下冷却（戒指被卸下时调用，确保重新装备后轮转物品能正常自动装备）
     */
    public static void clearManualUnequipCooldown(UUID playerId) {
        manualUnequipTime.remove(playerId);
    }

    /**
     * 第四层+第五层+第六层防御：Tick级快照监控、槽位保护与戒指自动恢复
     * 每5tick扫描一次，确保所有受保护物品始终在Curios槽位中，且槽位数量不被篡改
     * LOWEST优先级：确保在其他模组（如Boss禁饰品）之后执行，强制恢复被篡改的状态
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.tickCount % 5 != 0) return; // 每5tick检查一次（0.25秒）

        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            // 只在戒指装备状态下启用防护（戒指没装备时轮转槽也不存在）
            if (!data.isRingEquipped()) return;

            try {
                Optional<ICuriosItemHandler> curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
                if (curiosOpt.isEmpty()) return;
                ICuriosItemHandler inv = curiosOpt.get();

                // ===== 第六层：槽位数量保护 =====
                // 防止Boss/其他模组通过减少槽位数量来"封禁"饰品
                // 检查 rotation 槽位数量，戒指装备时必须为10
                var rotationHandler = inv.getCurios().get("rotation");
                int rotationSlots = rotationHandler != null ? rotationHandler.getSlots() : 0;
                if (rotationSlots != 10) {
                    // 重新应用我们的槽位修饰符（内部会先移除旧的再添加，避免叠加）
                    PlayerDataProvider.setRotationSlots(player, 10);
                    // 刷新引用
                    curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
                    if (curiosOpt.isPresent()) {
                        inv = curiosOpt.get();
                    }
                }

                // ===== 第五层（恢复）：戒指自动装备保护 =====
                // 戒指是核心物品，若被强制移到背包且ring槽有空位，自动装备回去
                // （轮转物品仍需玩家手动装备，不在此自动恢复范围内）
                var ringHandler = inv.getCurios().get("ring");
                boolean ringInCurios = false;
                if (ringHandler != null) {
                    for (int i = 0; i < ringHandler.getSlots(); i++) {
                        if (ringHandler.getStacks().getStackInSlot(i).is(ModItems.NINE_TURN_RING.get())) {
                            ringInCurios = true;
                            break;
                        }
                    }
                }
                if (!ringInCurios && ringHandler != null && ringHandler.getSlots() > 0) {
                    // 在背包中找戒指
                    int ringSlot = -1;
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        if (player.getInventory().getItem(i).is(ModItems.NINE_TURN_RING.get())) {
                            ringSlot = i;
                            break;
                        }
                    }
                    if (ringSlot >= 0) {
                        // 找一个空的 ring 槽位放入
                        for (int i = 0; i < ringHandler.getSlots(); i++) {
                            if (ringHandler.getStacks().getStackInSlot(i).isEmpty()) {
                                ItemStack ring = player.getInventory().removeItem(ringSlot, 1);
                                ringHandler.getStacks().setStackInSlot(i, ring);
                                break;
                            }
                        }
                    }
                }

                // ===== 第一步：扫描当前Curios栏中所有受保护物品，更新快照 =====
                Map<String, ItemStack> currentEquipped = new HashMap<>();
                for (var entry : inv.getCurios().entrySet()) {
                    String identifier = entry.getKey();
                    var handler = entry.getValue();
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStacks().getStackInSlot(i);
                        if (isProtectedItem(stack)) {
                            String slotKey = identifier + ":" + i;
                            currentEquipped.put(slotKey, stack);
                            // 更新快照（保存NBT，用于恢复）
                            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                            stack.save(tag);
                            data.putAccessory(slotKey, tag);
                        }
                    }
                }

                // ===== 第二步：检查快照中是否有物品被移除，若有则恢复 =====
                boolean restored = false;
                for (Map.Entry<String, net.minecraft.nbt.CompoundTag> snapEntry : data.getAccessorySnapshot().entrySet()) {
                    String slotKey = snapEntry.getKey();
                    if (currentEquipped.containsKey(slotKey)) continue; // 还在原位，跳过

                    // 该槽位的受保护物品不见了，需要恢复
                    String[] parts = slotKey.split(":");
                    if (parts.length != 2) continue;
                    String identifier = parts[0];
                    int slotIdx;
                    try {
                        slotIdx = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    // 从NBT恢复物品
                    ItemStack restoredStack = ItemStack.of(snapEntry.getValue());
                    if (restoredStack.isEmpty()) continue;

                    // 检查目标槽位是否存在且为空
                    var handler = inv.getCurios().get(identifier);
                    if (handler == null || slotIdx >= handler.getSlots()) continue;

                    ItemStack currentInSlot = handler.getStacks().getStackInSlot(slotIdx);
                    if (currentInSlot.isEmpty()) {
                        // 槽位空了，直接放回去
                        handler.getStacks().setStackInSlot(slotIdx, restoredStack);
                        restored = true;
                    } else if (isProtectedItem(currentInSlot)) {
                        // 槽位被另一个受保护物品占据（可能是换位了），更新快照即可
                        net.minecraft.nbt.CompoundTag newTag = new net.minecraft.nbt.CompoundTag();
                        currentInSlot.save(newTag);
                        data.putAccessory(slotKey, newTag);
                    } else {
                        // 槽位被其他物品占据，把入侵者移到背包，再放回受保护物品
                        ItemStack intruder = currentInSlot.copy();
                        handler.getStacks().setStackInSlot(slotIdx, ItemStack.EMPTY);
                        if (!player.getInventory().add(intruder)) {
                            player.spawnAtLocation(intruder);
                        }
                        handler.getStacks().setStackInSlot(slotIdx, restoredStack);
                        restored = true;
                    }
                }

                // ===== 第三步已移除：不再从背包自动装备到Curios =====
                // 玩家要求手动装备，物品放入背包后保持原位，需玩家手动拖入Curios槽位。
                // 防BOSS强制摘取仍由第二步（快照恢复到原槽位）保障。

                if (restored) {
                    data.syncToClient(player);
                    // 静默恢复，不发送聊天提示
                }

            } catch (Exception ignored) {
                // Curios API 调用异常时静默跳过，不影响游戏
            }
        });
    }
}
