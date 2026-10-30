package com.donutspawnerhelper;

import com.donutspawnerhelper.StackMessageParser.TitleInfo;
import com.donutspawnerhelper.StackTracker.Outcome;
import com.donutspawnerhelper.ui.SpawnerPanel;
import com.donutspawnerhelper.ui.SpawnerPreviewScreen;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only, read-only helper. It never sends packets of its own, never clicks for you and never
 * cancels or alters your interactions. Everything it shows is either
 *   (a) parsed from messages / menu titles the server already sent you, or
 *   (b) labelled as visual-only.
 */
public final class DonutSpawnerHelperClient implements ClientModInitializer {

    public static final String MOD_ID = "donutspawnerhelper";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    /** The exact chat line requested. */
    static final String SUCCESS_MESSAGE = "Added another spawner into the stack.";

    private static final StackTracker TRACKER = new StackTracker();
    private static volatile boolean openPreviewNextTick;

    /** Set when the player right-clicks a spawner while holding a spawn egg. */
    private record PendingEgg(BlockPos pos, String before, long startTick) {}
    private static PendingEgg pendingEgg;
    private static long clientTick;

    public static StackTracker tracker() {
        return TRACKER;
    }

    @Override
    public void onInitializeClient() {
        // 1) Server-confirmed stacking: react only to the server's own confirmation message.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onServerMessage(message.getString());
        });

        // 2) Remember which spawner you interacted with (read-only; always PASS).
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            try {
                if (level.getBlockState(hit.getBlockPos()).is(Blocks.SPAWNER)) {
                    TRACKER.bind(hit.getBlockPos().asLong(), System.currentTimeMillis());
                    ItemStack held = player.getItemInHand(hand);
                    if (held.getItem() instanceof SpawnEggItem && pendingEgg == null) {
                        Minecraft mc = Minecraft.getInstance();
                        pendingEgg = new PendingEgg(hit.getBlockPos().immutable(),
                                SpawnerInspector.readMobName(mc.level, hit.getBlockPos()), clientTick);
                    }
                }
            } catch (RuntimeException e) {
                LOG.debug("UseBlock observer failed", e);
            }
            return InteractionResult.PASS;
        });

        // 3) Tick: spawn-egg confirmation + opening the preview screen.
        ClientTickEvents.END_CLIENT_TICK.register(DonutSpawnerHelperClient::onTick);

        // 4) Animated panel next to the server's "N <Mob> Spawners" menu.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            Optional<TitleInfo> title = StackMessageParser.parseTitle(screen.getTitle().getString());
            if (title.isEmpty()) return;
            TitleInfo info = title.get();
            ScreenEvents.afterRender(screen).register((s, g, mouseX, mouseY, tickDelta) -> {
                int container = 176; // vanilla chest-style menu width
                int left = (s.width - container) / 2;
                int right = left + container;
                int x;
                if (left - 4 >= SpawnerPanel.WIDTH) x = left - 4 - SpawnerPanel.WIDTH;
                else if (s.width - right - 4 >= SpawnerPanel.WIDTH) x = right + 4;
                else return; // no room: draw nothing rather than cover the menu
                int y = Math.max(4, (s.height - SpawnerPanel.HEIGHT) / 2);
                boolean skeleton = info.mob().equalsIgnoreCase("skeleton");
                SpawnerPanel.draw(g, client.font, x, y,
                        new SpawnerPanel.Data(info.mob(), info.count(), true, skeleton));
            });
        });

        // 5) HUD while looking at a spawner / yellow glass.
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "spawner_info"), SpawnerHud::render);

        // 6) /spawnerui preview (client command, never sent to the server).
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommandManager.literal("spawnerui").executes(ctx -> {
                    openPreviewNextTick = true; // opened next tick so the chat screen doesn't close it
                    return 1;
                })));

        LOG.info("Donut Spawner Helper loaded (client-only, read-only).");
    }

    private static void onServerMessage(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            Outcome outcome = TRACKER.onServerMessage(text, System.currentTimeMillis());
            if (outcome == Outcome.IGNORED || outcome == Outcome.DUPLICATE) return;

            int count = TRACKER.lastCount();
            // Stack count goes to the action bar so chat only gets the one requested line.
            mc.player.displayClientMessage(
                    Component.literal("Spawner stack: " + String.format("%,d", count)), true);
            if (outcome == Outcome.ACCEPTED) {
                mc.player.displayClientMessage(
                        Component.literal(SUCCESS_MESSAGE).withStyle(ChatFormatting.GREEN), false);
            }
        });
    }

    private static void onTick(Minecraft mc) {
        clientTick++;
        if (openPreviewNextTick) {
            openPreviewNextTick = false;
            mc.setScreen(new SpawnerPreviewScreen());
        }
        PendingEgg egg = pendingEgg;
        if (egg == null || mc.level == null || mc.player == null) return;

        long age = clientTick - egg.startTick();
        if (age < 3) return; // give the server a moment to sync the block entity
        String now = SpawnerInspector.readMobName(mc.level, egg.pos());
        if (now != null && !now.equals(egg.before())) {
            mc.player.displayClientMessage(Component.literal(
                    "Spawner mob type is now " + now + " (server-confirmed)."), false);
            pendingEgg = null;
        } else if (age > 40) {
            mc.player.displayClientMessage(Component.literal(
                    "Spawn egg: no mob type change was reported by the server."), true);
            pendingEgg = null;
        }
    }
}
