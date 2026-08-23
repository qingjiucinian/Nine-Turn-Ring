package com.jiuzhuan.item;

import com.jiuzhuan.JiuZhuanMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, JiuZhuanMod.MOD_ID);

    public static final RegistryObject<Item> NINE_TURN_RING = ITEMS.register("nine_turn_ring",
            () -> new NineTurnRingItem(new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> ROTATION_1_POWER = ITEMS.register("rotation_1_power",
            () -> new PowerStoneItem(new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> ROTATION_2_SATIETY = ITEMS.register("rotation_2_satiety",
            () -> new RotationItem(2, "二转·辟谷", new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> ROTATION_3_NIGHT_VISION = ITEMS.register("rotation_3_night_vision",
            () -> new RotationItem(3, "三转·洞幽", new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> ROTATION_4_REGEN = ITEMS.register("rotation_4_regen",
            () -> new RotationItem(4, "四转·生息", new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> ROTATION_5_HEALTH = ITEMS.register("rotation_5_health",
            () -> new RotationItem(5, "五转·凝魄", new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> ROTATION_6_RESISTANCE = ITEMS.register("rotation_6_resistance",
            () -> new RotationItem(6, "六转·不破", new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> ROTATION_7_UNDYING = ITEMS.register("rotation_7_undying",
            () -> new RotationItem(7, "七转·涅槃", new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> ROTATION_8_LUCK = ITEMS.register("rotation_8_luck",
            () -> new RotationItem(8, "八转·天眷", new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> ROTATION_9_IMMORTAL = ITEMS.register("rotation_9_immortal",
            () -> new RotationItem(9, "九转·不灭", new Item.Properties().stacksTo(1).fireResistant()));

    public static final RegistryObject<Item> ROTATION_10_ADAPTATION = ITEMS.register("rotation_10_adaptation",
            () -> new RotationItem(10, "十转·万象", new Item.Properties().stacksTo(1).fireResistant()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
