package com.jiuzhuan;

import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.client.HudConfig;
import com.jiuzhuan.config.ServerConfig;
import com.jiuzhuan.curios.RingCurio;
import com.jiuzhuan.event.*;
import com.jiuzhuan.item.ModCreativeTabs;
import com.jiuzhuan.item.ModItems;
import com.jiuzhuan.network.NetworkHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(JiuZhuanMod.MOD_ID)
public class JiuZhuanMod {
    public static final String MOD_ID = "nine_turn_ring";
    public static final Logger LOGGER = LogManager.getLogger();

    public JiuZhuanMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(modBus);
        ModCreativeTabs.register(modBus);
        PlayerDataProvider.register();
        NetworkHandler.register();
        // 注册客户端配置（HUD位置等），生成在 config/nine_turn_ring/client.toml
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HudConfig.SPEC, "nine_turn_ring/client.toml");
        // 注册通用配置（数值平衡等），生成在 config/nine_turn_ring/common.toml
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ServerConfig.SPEC, "nine_turn_ring/common.toml");
        modBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(new ModEventHandlers());
        MinecraftForge.EVENT_BUS.register(new PlayerKillHandler());
        MinecraftForge.EVENT_BUS.register(new PlayerDamageHandler());
        MinecraftForge.EVENT_BUS.register(new PlayerDeathHandler());
        MinecraftForge.EVENT_BUS.register(new ChestLootHandler());
        MinecraftForge.EVENT_BUS.register(new RingCurio());
        MinecraftForge.EVENT_BUS.register(new NaturalGainHandler());
        MinecraftForge.EVENT_BUS.register(new AccessoryProtectionHandler());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("九转戒模组加载完成");
    }
}
