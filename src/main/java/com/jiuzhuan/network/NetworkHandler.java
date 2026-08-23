package com.jiuzhuan.network;

import com.jiuzhuan.JiuZhuanMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(JiuZhuanMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        INSTANCE.registerMessage(id++,
                SyncPlayerDataPacket.class,
                SyncPlayerDataPacket::encode,
                SyncPlayerDataPacket::decode,
                SyncPlayerDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        INSTANCE.registerMessage(id++,
                ToggleNightVisionPacket.class,
                ToggleNightVisionPacket::encode,
                ToggleNightVisionPacket::decode,
                ToggleNightVisionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
