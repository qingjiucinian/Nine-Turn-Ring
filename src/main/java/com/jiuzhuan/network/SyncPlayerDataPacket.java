package com.jiuzhuan.network;

import com.jiuzhuan.capability.PlayerDataProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncPlayerDataPacket {
    private final CompoundTag data;

    public SyncPlayerDataPacket(CompoundTag data) {
        this.data = data;
    }

    public static void encode(SyncPlayerDataPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.data);
    }

    public static SyncPlayerDataPacket decode(FriendlyByteBuf buf) {
        return new SyncPlayerDataPacket(buf.readNbt());
    }

    public static void handle(SyncPlayerDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                    data.loadNBT(msg.data);
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
