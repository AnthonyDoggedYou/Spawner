package com.donutspawnerhelper.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the info panel: dark card, animated spinning pink spawner with yellow skeleton,
 * and rows that clearly label SERVER-sourced data vs VISUAL-only effects.
 */
public final class SpawnerPanel {

    public static final int WIDTH = 132;
    public static final int HEIGHT = 178;

    private static final int BG = 0xE0101015;
    private static final int BORDER = 0xFF3C3C48;
    private static final int ART_BG = 0x80000000;
    private static final int TITLE = 0xFFFFE04A;      // yellow
    private static final int LABEL = 0xFFAAAAAA;
    private static final int VALUE = 0xFFFFFFFF;
    private static final int TAG_SERVER = 0xFF55FF55;
    private static final int TAG_VISUAL = 0xFF8888AA;
    private static final int PINK = 0xFFFF6EC0;

    /** @param stack  -1 when unknown. */
    public record Data(String mob, int stack, boolean stackFromServer, boolean skeleton) {}

    private SpawnerPanel() {}

    public static void draw(GuiGraphics g, Font font, int x, int y, Data d) {
        g.fill(x, y, x + WIDTH, y + HEIGHT, BG);
        outline(g, x, y, WIDTH, HEIGHT, BORDER);

        String title = (d.mob() == null || d.mob().isBlank() ? "Unknown" : d.mob()) + " Spawner";
        g.drawString(font, title, x + 6, y + 6, TITLE, true);

        // art box
        int ax = x + 6, ay = y + 20, aw = WIDTH - 12, ah = 92;
        g.fill(ax, ay, ax + aw, ay + ah, ART_BG);
        outline(g, ax, ay, aw, ah, 0xFF26262E);
        SpawnerArt.render(g::fill, ax + aw / 2, ay + ah / 2 + 12, 26, d.skeleton());
        g.drawString(font, "visual only", ax + 3, ay + ah - 10, TAG_VISUAL, false);

        // rows
        int ry = ay + ah + 8;
        row(g, font, x, ry, "Mob", d.mob() == null ? "?" : d.mob(), "SERVER", TAG_SERVER);
        String stack = d.stack() >= 0 ? String.format("%,d", d.stack()) : "unknown";
        row(g, font, x, ry + 13, "Stack", stack,
                d.stack() >= 0 && d.stackFromServer() ? "SERVER" : "", TAG_SERVER);
        g.drawString(font, "Spin + particles: VISUAL", x + 6, ry + 30, TAG_VISUAL, false);
        g.drawString(font, "Spawn egg: sets mob type", x + 6, ry + 42, PINK, false);
    }

    private static void row(GuiGraphics g, Font font, int x, int y, String label, String value,
                            String tag, int tagColor) {
        g.drawString(font, label + ":", x + 6, y, LABEL, false);
        g.drawString(font, value, x + 6 + font.width(label + ": "), y, VALUE, false);
        if (!tag.isEmpty()) {
            g.drawString(font, tag, x + WIDTH - 6 - font.width(tag), y, tagColor, false);
        }
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
