package com.jiuzhuan.capability;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
@Mod.EventBusSubscriber(modid = "nine_turn_ring")
public class PlayerDataProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    public static final Capability<IPlayerData> PLAYER_DATA = CapabilityManager.get(new CapabilityToken<>() {});
    private final IPlayerData instance = new PlayerData();
    private final LazyOptional<IPlayerData> holder = LazyOptional.of(() -> instance);
    public static final ResourceLocation ID = new ResourceLocation("nine_turn_ring", "player_data");
    // rotation 槽位修饰符 UUID：戒指装备时通过此修饰符提供 10 个槽位
    private static final java.util.UUID ROTATION_SLOT_UUID = java.util.UUID.fromString("c1d2e3f4-5a6b-7c8d-9e0f-1a2b3c4d5e6f");
    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return cap == PLAYER_DATA ? holder.cast() : LazyOptional.empty();
    }
    @Override
    public CompoundTag serializeNBT() {
        return instance.saveNBT();
    }
    @Override
    public void deserializeNBT(CompoundTag nbt) {
        instance.loadNBT(nbt);
    }
    // 注册Capability
    public static void register() {
        // Capability通过CapabilityToken自动注册，无需额外操作
    }
    // 附加到玩家
    @SubscribeEvent
    public static void attachPlayerCap(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(ID, new PlayerDataProvider());
        }
    }
    // 玩家死亡或维度切换（传送门）后保留数据
    @SubscribeEvent
    public static void onPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        // 死亡和维度切换都会触发 Clone，两种情况都需要保留玩家数据
        CompoundTag saved = null;
        var oldCap = event.getOriginal().getCapability(PLAYER_DATA).resolve();
        if (oldCap.isPresent()) {
            saved = oldCap.get().saveNBT();
        } else {
            net.minecraft.nbt.CompoundTag root = new net.minecraft.nbt.CompoundTag();
            event.getOriginal().saveWithoutId(root);
            if (root.contains("ForgeCaps") && root.getCompound("ForgeCaps").contains(ID.toString())) {
                saved = root.getCompound("ForgeCaps").getCompound(ID.toString());
            }
        }
        if (saved != null) {
            CompoundTag finalSaved = saved;
            event.getEntity().getCapability(PLAYER_DATA).ifPresent(newData -> {
                newData.loadNBT(finalSaved);
            });
        }
    }
    // 玩家复活后重新校验戒指装备状态并同步
    @SubscribeEvent
    public static void onPlayerRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        player.getCapability(PLAYER_DATA).ifPresent(data -> {
            // 复活后 Curios 饰品栏可能重建，重新确认戒指是否仍装备
            boolean equipped = false;
            try {
                var curiosOpt = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
                if (curiosOpt.isPresent()) {
                    var inv = curiosOpt.get();
                    outer:
                    for (var entry : inv.getCurios().entrySet()) {
                        var handler = entry.getValue();
                        for (int i = 0; i < handler.getSlots(); i++) {
                            if (handler.getStacks().getStackInSlot(i).is(com.jiuzhuan.item.ModItems.NINE_TURN_RING.get())) {
                                equipped = true;
                                break outer;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            data.setRingEquipped(equipped);
            setRotationSlots(player, equipped ? 10 : 0);
            data.syncToClient(player);
        });
    }

    /**
     * 使用 Curios 5.x 槽位修饰符机制设置 rotation 槽位数量。
     * 数据包默认 size=0，通过永久修饰符动态提供目标数量的槽位。
     */
    public static void setRotationSlots(Player player, int target) {
        try {
            var curiosOpt = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isEmpty()) return;
            var inv = curiosOpt.get();
            // 先移除已有修饰符，避免重复叠加
            inv.removeSlotModifier("rotation", ROTATION_SLOT_UUID);
            if (target > 0) {
                inv.addPermanentSlotModifier(
                        "rotation",
                        ROTATION_SLOT_UUID,
                        "nine_turn_ring_rotation_bonus",
                        target,
                        AttributeModifier.Operation.ADDITION
                );
            }
        } catch (Exception ignored) {}
    }
    // 玩家加入时同步数据
    @SubscribeEvent
    public static void onPlayerJoin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        event.getEntity().getCapability(PLAYER_DATA).ifPresent(data -> {
            data.syncToClient(event.getEntity());
        });
    }
}
