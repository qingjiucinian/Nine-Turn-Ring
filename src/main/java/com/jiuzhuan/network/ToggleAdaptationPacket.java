package com.jiuzhuan.network;

import com.jiuzhuan.capability.PlayerDataProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端→服务端：右键切换适应类型的禁用/启用状态
 */
public class ToggleAdaptationPacket {
    private final boolean isDamageType; // true=伤害类型, false=负面效果
    private final String id;
    private final boolean disabled;

    public ToggleAdaptationPacket(boolean isDamageType, String id, boolean disabled) {
        this.isDamageType = isDamageType;
        this.id = id;
        this.disabled = disabled;
    }

    public static void encode(ToggleAdaptationPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isDamageType);
        buf.writeUtf(msg.id);
        buf.writeBoolean(msg.disabled);
    }

    public static ToggleAdaptationPacket decode(FriendlyByteBuf buf) {
        return new ToggleAdaptationPacket(buf.readBoolean(), buf.readUtf(), buf.readBoolean());
    }

    public static void handle(ToggleAdaptationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null) return;
            sender.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                if (msg.isDamageType) {
                    data.setDamageAdaptationDisabled(msg.id, msg.disabled);
                } else {
                    data.setEffectAdaptationDisabled(msg.id, msg.disabled);
                }
                data.syncToClient(sender);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
