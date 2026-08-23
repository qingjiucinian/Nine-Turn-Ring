package com.jiuzhuan.client;

import com.jiuzhuan.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class HudPositionScreen extends Screen {
    private static final int ICON_COLS = 5;
    private static final int ICON_ROWS = 2;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_SPACING = 2;
    private static final int ICONS_WIDTH = ICON_COLS * SLOT_SIZE + (ICON_COLS - 1) * SLOT_SPACING;
    private static final int ICONS_HEIGHT = ICON_ROWS * SLOT_SIZE + (ICON_ROWS - 1) * SLOT_SPACING;

    private static final ItemStack[] ICON_STACKS = new ItemStack[11];
    static {
        ICON_STACKS[1] = new ItemStack(ModItems.ROTATION_1_POWER.get());
        ICON_STACKS[2] = new ItemStack(ModItems.ROTATION_2_SATIETY.get());
        ICON_STACKS[3] = new ItemStack(ModItems.ROTATION_3_NIGHT_VISION.get());
        ICON_STACKS[4] = new ItemStack(ModItems.ROTATION_4_REGEN.get());
        ICON_STACKS[5] = new ItemStack(ModItems.ROTATION_5_HEALTH.get());
        ICON_STACKS[6] = new ItemStack(ModItems.ROTATION_6_RESISTANCE.get());
        ICON_STACKS[7] = new ItemStack(ModItems.ROTATION_7_UNDYING.get());
        ICON_STACKS[8] = new ItemStack(ModItems.ROTATION_8_LUCK.get());
        ICON_STACKS[9] = new ItemStack(ModItems.ROTATION_9_IMMORTAL.get());
        ICON_STACKS[10] = new ItemStack(ModItems.ROTATION_10_ADAPTATION.get());
    }

    private boolean draggingIcons = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private int tempIconsX;
    private int tempIconsY;
    private float tempScale;

    public HudPositionScreen() {
        super(Component.translatable("nine_turn_ring.screen.hud.title"));
        this.tempIconsX = HudConfig.getIconsX();
        this.tempIconsY = HudConfig.getIconsY();
        this.tempScale = (float) HudConfig.getHudScale();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height - 30;
        addRenderableWidget(Button.builder(Component.translatable("nine_turn_ring.screen.hud.save"), b -> saveAndClose())
                .bounds(centerX - 105, y, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("nine_turn_ring.screen.hud.reset"), b -> resetPositions())
                .bounds(centerX - 35, y, 70, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("nine_turn_ring.screen.hud.cancel"), b -> onClose())
                .bounds(centerX + 45, y, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("nine_turn_ring.screen.hud.zoom_out"), b -> {
                    tempScale = Math.max(0.5f, tempScale - 0.1f);
                }).bounds(10, y, 50, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("nine_turn_ring.screen.hud.zoom_in"), b -> {
                    tempScale = Math.min(2.0f, tempScale + 0.1f);
                }).bounds(70, y, 50, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font,
                Component.translatable("nine_turn_ring.screen.hud.header").getString(),
                this.width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("nine_turn_ring.screen.hud.hint").getString(),
                this.width / 2, 28, 0xAAAAAA);
        renderIconsPreview(graphics, mouseX, mouseY);
        graphics.drawString(this.font,
                Component.translatable("nine_turn_ring.screen.hud.coords", tempIconsX, tempIconsY).getString(),
                10, this.height - 55, 0xFFFFFF);
        graphics.drawString(this.font,
                Component.translatable("nine_turn_ring.screen.hud.scale", String.format("%.1f", tempScale)).getString(),
                10, this.height - 42, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderIconsPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = tempIconsX;
        int y = tempIconsY;
        int borderColor;
        if (draggingIcons) {
            borderColor = 0xFFFF00;
        } else if (isInIconsArea(mouseX, mouseY)) {
            borderColor = 0x00FF00;
        } else {
            borderColor = 0xFFFFFF;
        }
        graphics.fill(x - 2, y - 2, x + ICONS_WIDTH + 2, y + ICONS_HEIGHT + 2, 0x80000000);
        drawBorder(graphics, x - 2, y - 2, ICONS_WIDTH + 4, ICONS_HEIGHT + 4, borderColor);
        for (int i = 1; i <= 10; i++) {
            int col = (i - 1) % ICON_COLS;
            int row = (i - 1) / ICON_COLS;
            int ix = x + col * (SLOT_SIZE + SLOT_SPACING);
            int iy = y + row * (SLOT_SIZE + SLOT_SPACING);
            graphics.renderItem(ICON_STACKS[i], ix, iy);
        }
        graphics.drawString(this.font,
                Component.translatable("nine_turn_ring.screen.hud.icons_label").getString(),
                x, y - 12, 0xFFFFFF);
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private boolean isInIconsArea(double mouseX, double mouseY) {
        return mouseX >= tempIconsX - 2 && mouseX <= tempIconsX + ICONS_WIDTH + 2
                && mouseY >= tempIconsY - 2 && mouseY <= tempIconsY + ICONS_HEIGHT + 2;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isInIconsArea(mouseX, mouseY)) {
                draggingIcons = true;
                dragOffsetX = (int) mouseX - tempIconsX;
                dragOffsetY = (int) mouseY - tempIconsY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingIcons) {
            tempIconsX = Math.max(0, Math.min(this.width - ICONS_WIDTH, (int) mouseX - dragOffsetX));
            tempIconsY = Math.max(0, Math.min(this.height - ICONS_HEIGHT - 40, (int) mouseY - dragOffsetY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingIcons = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void saveAndClose() {
        HudConfig.setIconsPos(tempIconsX, tempIconsY);
        HudConfig.setHudScale(tempScale);
        onClose();
    }

    private void resetPositions() {
        tempIconsX = 5;
        tempIconsY = 5;
        tempScale = 1.0f;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
