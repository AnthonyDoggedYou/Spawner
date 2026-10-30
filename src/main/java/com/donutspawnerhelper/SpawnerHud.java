package com.donutspawnerhelper;

import java.util.OptionalInt;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Small, unobtrusive HUD box shown only while the crosshair is on a spawner or yellow glass. */
public final class SpawnerHud {

    private SpawnerHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null) return;

        SpawnerInspector.Target t = SpawnerInspector.lookingAt(mc);
        if (t.kind() == SpawnerInspector.Kind.NONE) return;

        String l1;
        String l2;
        String l3 = null;
        int accent;
        if (t.kind() == SpawnerInspector.Kind.SPAWNER) {
            accent = 0xFFFF6EC0;
            String mob = t.mobName();
            l1 = (mob == null ? "Spawner" : mob + " Spawner");
            l2 = mob == null ? "Mob: no data from server" : "Mob: " + mob + " (server data)";
            OptionalInt stack = DonutSpawnerHelperClient.tracker().confirmedCountAt(t.pos().asLong());
            l3 = stack.isPresent()
                    ? "Stack: " + String.format("%,d", stack.getAsInt()) + " (server-confirmed)"
                    : "Stack: unknown. Open its menu or add one";
        } else {
            accent = 0xFFFFE04A;
            l1 = "Yellow stained glass";
            l2 = "Plain block. A client mod can't turn it into a spawner.";
        }

        int w = Math.max(mc.font.width(l1), Math.max(mc.font.width(l2), l3 == null ? 0 : mc.font.width(l3))) + 12;
        int h = l3 == null ? 34 : 45;
        int x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
        int y = 8;
        g.fill(x, y, x + w, y + h, 0xB0101015);
        g.fill(x, y, x + 2, y + h, accent);
        g.drawString(mc.font, l1, x + 7, y + 5, accent, true);
        g.drawString(mc.font, l2, x + 7, y + 17, 0xFFDDDDDD, false);
        if (l3 != null) g.drawString(mc.font, l3, x + 7, y + 29, 0xFFDDDDDD, false);
    }
}
