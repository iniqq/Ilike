package dev.maceswap;

import com.mojang.blaze3d.platform.InputConstants;
import dev.maceswap.config.SwapConfig;
import dev.maceswap.logic.SwapController;
import dev.maceswap.ui.ConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

public final class MaceSwapClient implements ClientModInitializer {
    public static final SwapController CONTROLLER = new SwapController();
    private static KeyMapping kMace;
    private static KeyMapping kBreach;
    private static KeyMapping kSmart;
    private static KeyMapping kMenu;

    private static KeyMapping key(String id, int code) {
        return KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.maceswap." + id, InputConstants.Type.KEYSYM, code, KeyMapping.Category.MISC));
    }

    @Override
    public void onInitializeClient() {
        SwapConfig.load();
        kMace = key("mace", 85);     // U
        kBreach = key("breach", 73); // I
        kSmart = key("smart", 79);   // O
        kMenu = key("menu", 344);    // Right Shift

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide()) {
                CONTROLLER.onAttack(player, entity);
            }
            return InteractionResult.PASS;
        });

        ClientTickEvents.START_CLIENT_TICK.register(CONTROLLER::startTick);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            SwapConfig c = SwapConfig.get();
            while (kMace.consumeClick()) {
                c.maceSwapEnabled = !c.maceSwapEnabled;
                toast(mc, "Mace Swap", c.maceSwapEnabled, c);
            }
            while (kBreach.consumeClick()) {
                c.breachSwapEnabled = !c.breachSwapEnabled;
                toast(mc, "Breach Swap", c.breachSwapEnabled, c);
            }
            while (kSmart.consumeClick()) {
                c.smartSwapEnabled = !c.smartSwapEnabled;
                toast(mc, "Smart Swap", c.smartSwapEnabled, c);
            }
            while (kMenu.consumeClick()) {
                mc.setScreen(new ConfigScreen(mc.screen));
            }
            CONTROLLER.tick(mc);
        });

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("maceswap", "status"),
            (graphics, tracker) -> {
                SwapConfig c = SwapConfig.get();
                Minecraft mc = Minecraft.getInstance();
                if (!c.hudEnabled) {
                    return;
                }
                String s = "Swap: " + CONTROLLER.activeLabel() + (CONTROLLER.isSwapped() ? " *" : "");
                graphics.drawString(mc.font, s, 4, 4, 0xFF55FF55, true);
                if (c.debug) {
                    graphics.drawString(mc.font, CONTROLLER.getStatus(), 4, 16, 0xFFFFFF55, true);
                }
            });
    }

    private static void toast(Minecraft mc, String name, boolean on, SwapConfig c) {
        c.save();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(name + ": " + (on ? "ON" : "OFF")), true);
        }
    }
}
