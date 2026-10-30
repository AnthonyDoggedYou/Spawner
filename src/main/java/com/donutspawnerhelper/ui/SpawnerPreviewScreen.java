package com.donutspawnerhelper.ui;

import com.donutspawnerhelper.DonutSpawnerHelperClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Opened with /spawnerui. Shows the panel using the last server-confirmed stack size. */
public final class SpawnerPreviewScreen extends Screen {

    public SpawnerPreviewScreen() {
        super(Component.literal("Spawner Preview"));
    }

    @Override
    protected void init() {
        int bx = this.width / 2 - 40;
        int by = (this.height + SpawnerPanel.HEIGHT) / 2 + 8;
        this.addRenderableWidget(
                Button.builder(Component.literal("Close"), b -> this.onClose()).bounds(bx, by, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        int px = (this.width - SpawnerPanel.WIDTH) / 2;
        int py = Math.max(4, (this.height - SpawnerPanel.HEIGHT) / 2 - 10);
        int last = DonutSpawnerHelperClient.tracker().lastCount();
        SpawnerPanel.draw(g, this.font, px, py,
                new SpawnerPanel.Data("Skeleton", last, last >= 0, true));
        g.drawCenteredString(this.font, "Preview. Open a real spawner menu to see live data.",
                this.width / 2, py - 12, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
