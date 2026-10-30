package com.donutspawnerhelper;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Read-only helpers that look at what the client already knows. Never sends anything. */
public final class SpawnerInspector {

    public enum Kind { NONE, SPAWNER, YELLOW_GLASS }

    public record Target(Kind kind, BlockPos pos, String mobName) {
        static final Target NONE = new Target(Kind.NONE, null, null);
    }

    private SpawnerInspector() {}

    public static Target lookingAt(Minecraft mc) {
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return Target.NONE;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.level.getBlockState(pos);
        if (state.is(Blocks.SPAWNER)) {
            return new Target(Kind.SPAWNER, pos, readMobName(mc.level, pos));
        }
        if (state.is(Blocks.YELLOW_STAINED_GLASS) || state.is(Blocks.YELLOW_STAINED_GLASS_PANE)) {
            return new Target(Kind.YELLOW_GLASS, pos, null);
        }
        return Target.NONE;
    }

    /**
     * Mob type the server has synced to this client for the spawner block entity, or null when the
     * data is missing (servers that hide or fake spawner data will return null here).
     */
    public static String readMobName(Level level, BlockPos pos) {
        try {
            if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
                Entity display = spawner.getSpawner().getOrCreateDisplayEntity(level, pos);
                if (display != null) {
                    return display.getType().getDescription().getString();
                }
            }
        } catch (RuntimeException e) {
            DonutSpawnerHelperClient.LOG.debug("Could not read spawner data at {}", pos, e);
        }
        return null;
    }
}
