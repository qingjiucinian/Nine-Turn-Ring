package com.jiuzhuan.util;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class AdvancementUtil {
    public static final String MOD_ID = "nine_turn_ring";

    public static void grant(ServerPlayer player, String advancementId, String criterion) {
        if (player == null || player.server == null) return;
        Advancement adv = player.server.getAdvancements().getAdvancement(new ResourceLocation(MOD_ID, advancementId));
        if (adv != null) {
            player.getAdvancements().award(adv, criterion);
        }
    }

    public static boolean isDone(ServerPlayer player, String advancementId) {
        if (player == null || player.server == null) return false;
        Advancement adv = player.server.getAdvancements().getAdvancement(new ResourceLocation(MOD_ID, advancementId));
        if (adv == null) return false;
        return player.getAdvancements().getOrStartProgress(adv).isDone();
    }
}
