package com.jiuzhuan.item;

import com.jiuzhuan.JiuZhuanMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, JiuZhuanMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> JIUZHUAN_TAB = CREATIVE_MODE_TABS.register("nine_turn_ring_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nine_turn_ring"))
                    .icon(() -> new ItemStack(ModItems.NINE_TURN_RING.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.NINE_TURN_RING.get());
                        output.accept(ModItems.ROTATION_1_POWER.get());
                        output.accept(ModItems.ROTATION_2_SATIETY.get());
                        output.accept(ModItems.ROTATION_3_NIGHT_VISION.get());
                        output.accept(ModItems.ROTATION_4_REGEN.get());
                        output.accept(ModItems.ROTATION_5_HEALTH.get());
                        output.accept(ModItems.ROTATION_6_RESISTANCE.get());
                        output.accept(ModItems.ROTATION_7_UNDYING.get());
                        output.accept(ModItems.ROTATION_8_LUCK.get());
                        output.accept(ModItems.ROTATION_9_IMMORTAL.get());
                        output.accept(ModItems.ROTATION_10_ADAPTATION.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
