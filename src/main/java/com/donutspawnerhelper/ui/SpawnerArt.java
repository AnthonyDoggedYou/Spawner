package com.donutspawnerhelper.ui;

import java.util.SplittableRandom;

/**
 * Procedural, texture-free art: a pink caged spawner that spins in pseudo-3D, a yellow skeleton
 * inside it, and particles drifting out of the top.
 *
 * VISUAL ONLY. Nothing here reads or changes server state.
 *
 * Pure Java (no Minecraft imports) so the geometry can be rendered and checked outside the game.
 * Performance: roughly 200-350 axis-aligned fill() calls per frame, zero allocations per frame
 * (fixed-size particle arrays, reused scratch arrays).
 */
public final class SpawnerArt {

    /** Minimal drawing target. In-game this is GuiGraphics::fill. */
    @FunctionalInterface
    public interface Canvas {
        void fill(int x1, int y1, int x2, int y2, int argb);
    }

    // ---- palette -------------------------------------------------------------------------
    static final int PINK_BASE_FRONT = 0x66FF6EC0;
    static final int PINK_BASE_TOP   = 0x88FF9AD5;
    static final int PINK_BAR        = 0xFFFF8CCB;
    static final int PINK_FRAME      = 0xFFD93A93;
    static final int PINK_BACK       = 0xFF8A2563;
    static final int SKEL_YELLOW     = 0xFFFFE04A;
    static final int SKEL_DARK       = 0xFFB38F00;
    static final int SKEL_EYE        = 0xFF1A1400;

    private static final float TILT = 0.42f;
    private static final float COS_A = (float) Math.cos(TILT);
    private static final float SIN_A = (float) Math.sin(TILT);

    // 8 wide x 14 tall skeleton. Y = bone, D = shadow, K = eye socket.
    private static final String[] SKELETON = {
            "..YYYY..",
            ".YYYYYY.",
            ".YKYYKY.",
            ".YYYYYY.",
            "..YDDY..",
            "...YY...",
            ".YYYYYY.",
            "Y.YDDY.Y",
            "Y.YYYY.Y",
            "Y.YDDY.Y",
            "..YYYY..",
            "..Y..Y..",
            "..Y..Y..",
            "..Y..Y..",
    };

    // ---- particle pool (struct of arrays, no per-frame allocation) -----------------------
    private static final int MAX_PARTICLES = 40;
    private static final float[] PX = new float[MAX_PARTICLES];
    private static final float[] PY = new float[MAX_PARTICLES];
    private static final float[] VX = new float[MAX_PARTICLES];
    private static final float[] VY = new float[MAX_PARTICLES];
    private static final float[] LIFE = new float[MAX_PARTICLES];
    private static final float[] MAX_LIFE = new float[MAX_PARTICLES];
    private static final int[] RGB = new int[MAX_PARTICLES];
    private static final boolean[] TALL = new boolean[MAX_PARTICLES];
    private static final SplittableRandom RNG = new SplittableRandom(0xD0A7L);
    private static final int[] PARTICLE_COLORS = {
            0xFFE04A, 0xFFE04A, 0xFFB62E, 0xFF6EC0, 0xFF6EC0, 0xFFFFFF
    };

    private static long lastNanos;
    private static float spawnAccumulator;

    // scratch (render thread only)
    private static final float[] QX = new float[4];
    private static final float[] QY = new float[4];
    private static final float[] TMP = new float[2];

    private SpawnerArt() {}

    /**
     * @param cx,cy      centre of the spawner on screen
     * @param half       half-size of the cube in pixels (26 is a good panel size)
     * @param skeleton   draw the yellow skeleton inside (only for skeleton spawners)
     */
    public static void render(Canvas c, int cx, int cy, int half, boolean skeleton) {
        render(c, cx, cy, half, skeleton, System.nanoTime());
    }

    /** Same as above with an explicit clock (used by the offline preview renderer). */
    public static void render(Canvas c, int cx, int cy, int half, boolean skeleton, long nowNanos) {
        stepParticles(nowNanos, cx, cy, half);

        double t = nowNanos / 1.0e9;
        float theta = (float) ((t * 1.1) % (Math.PI * 2));
        float cosT = (float) Math.cos(theta);
        float sinT = (float) Math.sin(theta);

        // 1) back faces (cage bars only, dim)
        for (int i = 0; i < 4; i++) {
            if (faceDepth(i, cosT, sinT) <= 0) drawSide(c, i, cosT, sinT, cx, cy, half, false);
        }
        // 2) skeleton in the middle of the cage
        if (skeleton) drawSkeleton(c, cx, cy + Math.round((float) Math.sin(t * 2.0) * 1.5f), half, t);
        // 3) particles behind the front glass
        drawParticles(c);
        // 4) front faces (translucent pink + bars) and the lid
        for (int i = 0; i < 4; i++) {
            if (faceDepth(i, cosT, sinT) > 0) drawSide(c, i, cosT, sinT, cx, cy, half, true);
        }
        drawTop(c, cosT, sinT, cx, cy, half);
    }

    // ---- geometry ------------------------------------------------------------------------

    /** z of the face normal after rotation: > 0 means the face points at the viewer. */
    private static float faceDepth(int face, float cosT, float sinT) {
        float beta = face * (float) (Math.PI / 2);
        float nx = (float) Math.sin(beta);
        float nz = (float) Math.cos(beta);
        return -nx * sinT + nz * cosT;
    }

    private static void project(float x, float y, float z, float cosT, float sinT,
                                float cx, float cy, float s) {
        float x1 = x * cosT + z * sinT;
        float z1 = -x * sinT + z * cosT;
        float y2 = y * COS_A - z1 * SIN_A;
        TMP[0] = cx + x1 * s;
        TMP[1] = cy - y2 * s;
    }

    private static void drawSide(Canvas c, int face, float cosT, float sinT,
                                 int cx, int cy, int half, boolean front) {
        float beta = face * (float) (Math.PI / 2);
        float nx = (float) Math.sin(beta), nz = (float) Math.cos(beta);
        float tx = (float) Math.cos(beta), tz = -(float) Math.sin(beta);

        // top-left, top-right, bottom-left (vertical edges project vertically)
        project(nx - tx, 1, nz - tz, cosT, sinT, cx, cy, half);
        float ax = TMP[0], ay = TMP[1];
        project(nx + tx, 1, nz + tz, cosT, sinT, cx, cy, half);
        float bx = TMP[0], by = TMP[1];
        project(nx - tx, -1, nz - tz, cosT, sinT, cx, cy, half);
        float h = TMP[1] - ay;

        float dx = bx - ax;
        if (Math.abs(dx) < 1.5f) return; // edge-on, nothing to draw

        int frame = front ? PINK_FRAME : PINK_BACK;
        int bar = front ? PINK_BAR : PINK_BACK;
        int x0 = (int) Math.floor(Math.min(ax, bx));
        int x1 = (int) Math.ceil(Math.max(ax, bx));

        for (int k = x0; k < x1; k += 2) {
            float tt = (k + 1 - ax) / dx;
            if (tt < 0 || tt > 1) continue;
            int yt = Math.round(ay + (by - ay) * tt);
            int yb = Math.round(ay + (by - ay) * tt + h);
            int kr = Math.min(k + 2, x1);
            if (front) c.fill(k, yt, kr, yb, PINK_BASE_FRONT);
            c.fill(k, yt, kr, yt + 3, frame);
            c.fill(k, yb - 3, kr, yb, frame);
        }
        // vertical bars: both corner posts and two in between
        for (int b = 0; b < 4; b++) {
            float tb = b / 3f;
            int xk = Math.round(ax + dx * tb);
            int yt = Math.round(ay + (by - ay) * tb);
            int yb = Math.round(ay + (by - ay) * tb + h);
            c.fill(xk - 1, yt, xk + 1, yb, (b == 0 || b == 3) ? frame : bar);
        }
    }

    private static void drawTop(Canvas c, float cosT, float sinT, int cx, int cy, int half) {
        float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int i = 0; i < 4; i++) {
            project(corners[i][0], 1, corners[i][1], cosT, sinT, cx, cy, half);
            QX[i] = TMP[0];
            QY[i] = TMP[1];
        }
        fillQuad(c, PINK_BASE_TOP);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            line(c, QX[i], QY[i], QX[j], QY[j], PINK_FRAME);
        }
    }

    private static void fillQuad(Canvas c, int argb) {
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            minY = Math.min(minY, QY[i]);
            maxY = Math.max(maxY, QY[i]);
        }
        for (int y = (int) Math.floor(minY); y <= (int) Math.ceil(maxY); y++) {
            float yy = y + 0.5f;
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int e = 0; e < 4; e++) {
                int f = (e + 1) & 3;
                float y0 = QY[e], y1 = QY[f];
                if ((yy < y0) == (yy < y1)) continue;
                float x = QX[e] + (yy - y0) / (y1 - y0) * (QX[f] - QX[e]);
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
            }
            if (hi > lo) c.fill(Math.round(lo), y, Math.round(hi), y + 1, argb);
        }
    }

    private static void line(Canvas c, float x0, float y0, float x1, float y1, int argb) {
        int n = (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        if (n < 1) n = 1;
        for (int i = 0; i <= n; i += 1) {
            float f = i / (float) n;
            int x = Math.round(x0 + (x1 - x0) * f);
            int y = Math.round(y0 + (y1 - y0) * f);
            c.fill(x, y, x + 2, y + 2, argb);
        }
    }

    // ---- skeleton ------------------------------------------------------------------------

    private static void drawSkeleton(Canvas c, int cx, int cy, int half, double t) {
        float p = half / 10f;
        float sx = Math.max(0.4f, Math.abs((float) Math.cos(t * 0.66 + 1.0)));
        float widthPx = 8 * p * sx;
        float left = cx - widthPx / 2f;
        float top = cy - 14 * p / 2f;
        for (int row = 0; row < SKELETON.length; row++) {
            String line = SKELETON[row];
            int y0 = Math.round(top + row * p);
            int y1 = Math.round(top + (row + 1) * p);
            int col = 0;
            while (col < line.length()) {
                char ch = line.charAt(col);
                int end = col;
                while (end < line.length() && line.charAt(end) == ch) end++;
                if (ch != '.') {
                    int color = ch == 'Y' ? SKEL_YELLOW : ch == 'D' ? SKEL_DARK : SKEL_EYE;
                    int xa = Math.round(left + col * p * sx);
                    int xb = Math.max(xa + 1, Math.round(left + end * p * sx));
                    c.fill(xa, y0, xb, Math.max(y0 + 1, y1), color);
                }
                col = end;
            }
        }
    }

    // ---- particles -----------------------------------------------------------------------

    private static void stepParticles(long nowNanos, int cx, int cy, int half) {
        if (lastNanos == 0 || nowNanos - lastNanos > 250_000_000L || nowNanos < lastNanos) {
            // first frame, or the panel was not drawn for a while / moved: start fresh
            for (int i = 0; i < MAX_PARTICLES; i++) LIFE[i] = 0;
            spawnAccumulator = 0;
            lastNanos = nowNanos;
            return;
        }
        float dt = Math.min(0.05f, (nowNanos - lastNanos) / 1.0e9f);
        lastNanos = nowNanos;
        if (dt <= 0) return;

        spawnAccumulator += dt * 30f;
        int toSpawn = (int) spawnAccumulator;
        spawnAccumulator -= toSpawn;
        for (int s = 0; s < toSpawn; s++) {
            for (int i = 0; i < MAX_PARTICLES; i++) {
                if (LIFE[i] > 0) continue;
                PX[i] = cx + (RNG.nextFloat() - 0.5f) * half * 1.3f;
                PY[i] = cy - half * 0.85f + RNG.nextFloat() * 4f;
                VX[i] = (RNG.nextFloat() - 0.5f) * 14f;
                VY[i] = -(24f + RNG.nextFloat() * 34f);
                MAX_LIFE[i] = LIFE[i] = 0.8f + RNG.nextFloat() * 0.9f;
                RGB[i] = PARTICLE_COLORS[RNG.nextInt(PARTICLE_COLORS.length)];
                TALL[i] = RNG.nextInt(3) == 0;
                break;
            }
        }
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (LIFE[i] <= 0) continue;
            LIFE[i] -= dt;
            PX[i] += VX[i] * dt;
            PY[i] += VY[i] * dt;
            VX[i] *= 1f - 0.8f * dt;
        }
    }

    private static void drawParticles(Canvas c) {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (LIFE[i] <= 0) continue;
            int alpha = Math.max(0, Math.min(255, (int) (255 * (LIFE[i] / MAX_LIFE[i]))));
            int argb = (alpha << 24) | RGB[i];
            int x = Math.round(PX[i]);
            int y = Math.round(PY[i]);
            c.fill(x, y, x + 2, y + (TALL[i] ? 4 : 2), argb);
        }
    }
}
