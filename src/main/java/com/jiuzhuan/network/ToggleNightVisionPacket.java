package com.jiuzhuan.network;

import com.jiuzhuan.capability.PlayerDataProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：切换三转夜视手动开关
 */
public class ToggleNightVisionPacket {

    public ToggleNightVisionPacket() {
    }

    public static void encode(ToggleNightVisionPacket msg, FriendlyByteBuf buf) {
        // 无数据，仅作为信号包
    }

    public static ToggleNightVisionPacket decode(FriendlyByteBuf buf) {
        return new ToggleNightVisionPacket();
    }

    public static void handle(ToggleNightVisionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                // 只有装备了戒指且三转已激活时才允许切换
                if (!data.isRingEquipped() || !data.isActivated(3)) {
                    player.sendSystemMessage(Component.translatable("nine_turn_ring.message.night_vision_unavailable"));
                    return;
                }
                boolean newState = !data.isNightVisionEnabled();
                data.setNightVisionEnabled(newState);
                data.syncToClient(player);
                // 关闭时立即移除夜视效果，避免残留
                if (!newState) {
                    player.removeEffect(MobEffects.NIGHT_VISION);
                }
                player.sendSystemMessage(Component.translatable(
                        newState ? "nine_turn_ring.message.night_vision_on" : "nine_turn_ring.message.night_vision_off"
                ));
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
