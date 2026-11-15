package dev.maceswap.ui;

import dev.maceswap.config.SwapConfig;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ConfigScreen extends Screen {
    private static final int W = 150;
    private static final int H = 20;
    private static final int GAP = 24;

    private final Screen parent;
    private final SwapConfig c = SwapConfig.get();

    public ConfigScreen(Screen parent) {
        super(Component.literal("Mace Swap"));
        this.parent = parent;
    }

    private int x(int col) {
        return this.width / 2 - W - 3 + col * 156;
    }

    private int y(int r) {
        return 36 + r * GAP;
    }

    private void toggle(int col, int r, String label, BooleanSupplier get, Runnable flip) {
        this.addRenderableWidget(Button.builder(txt(label, get.getAsBoolean()), b -> {
            flip.run();
            b.setMessage(txt(label, get.getAsBoolean()));
        }).bounds(x(col), y(r), W, H).build());
    }

    private static Component txt(String l, boolean on) {
        return Component.literal(l + ": " + (on ? "ON" : "OFF"));
    }

    private void slider(int col, int r, String label, double min, double max, double val, DoubleConsumer set) {
        this.addRenderableWidget(new ValueSlider(x(col), y(r), W, H, label, min, max, val, set));
    }

    @Override
    protected void init() {
        toggle(0, 0, "Mace Swap", () -> c.maceSwapEnabled, () -> c.maceSwapEnabled = !c.maceSwapEnabled);
        toggle(1, 0, "Breach Swap", () -> c.breachSwapEnabled, () -> c.breachSwapEnabled = !c.breachSwapEnabled);
        toggle(0, 1, "Smart Swap", () -> c.smartSwapEnabled, () -> c.smartSwapEnabled = !c.smartSwapEnabled);
        toggle(1, 1, "Revert after attack", () -> c.revertAfterAttack, () -> c.revertAfterAttack = !c.revertAfterAttack);
        slider(0, 2, "Min fall (blocks)", 0.0, 10.0, c.minFallDistance, v -> c.minFallDistance = v);
        slider(1, 2, "Min down speed", 0.0, 2.0, c.minDownVelocity, v -> c.minDownVelocity = v);
        slider(0, 3, "Min target dist", 0.0, 8.0, c.minTargetDistance, v -> c.minTargetDistance = v);
        slider(1, 3, "Max target dist", 2.0, 12.0, c.maxTargetDistance, v -> c.maxTargetDistance = v);
        slider(0, 4, "Lead time (ticks)", 0.0, 20.0, c.swapLeadTicks, v -> c.swapLeadTicks = v);
        slider(1, 4, "Cooldown (ticks)", 0.0, 40.0, c.cooldownTicks, v -> c.cooldownTicks = (int) Math.round(v));
        slider(0, 5, "Revert delay (ticks)", 0.0, 20.0, c.revertDelayTicks, v -> c.revertDelayTicks = (int) Math.round(v));
        slider(1, 5, "Cancel grace (ticks)", 1.0, 20.0, c.cancelGraceTicks, v -> c.cancelGraceTicks = (int) Math.round(v));
        slider(1, 6, "Spear lead (ticks)", 0.0, 10.0, c.spearLeadTicks, v -> c.spearLeadTicks = v);
        slider(0, 7, "Spear reach", 3.0, 6.0, c.spearReach, v -> c.spearReach = v);
        slider(0, 6, "Breach min armor", 0.0, 20.0, c.minTargetArmor, v -> c.minTargetArmor = v);
        this.addRenderableWidget(Button.builder(prio(), b -> {
            c.priority.add(c.priority.remove(0));
            b.setMessage(prio());
        }).bounds(x(0), y(8), 306, H).build());
        toggle(1, 7, "Stay on Breach after hit", () -> c.stayOnBreach, () -> c.stayOnBreach = !c.stayOnBreach);
        toggle(0, 10, "Swap on hit (attribute)", () -> c.swapOnHit, () -> c.swapOnHit = !c.swapOnHit);
        toggle(0, 9, "HUD indicator", () -> c.hudEnabled, () -> c.hudEnabled = !c.hudEnabled);
        toggle(1, 9, "Debug log", () -> c.debug, () -> c.debug = !c.debug);
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
            .bounds(this.width / 2 - 75, y(11) + 6, W, H).build());
    }

    private Component prio() {
        return Component.literal("Priority (click to rotate): " + String.join(" > ", c.priority));
    }

    @Override
    public void onClose() {
        c.save();
        this.minecraft.setScreen(parent);
    }

    private static final class ValueSlider extends AbstractSliderButton {
        private final String label;
        private final double min;
        private final double max;
        private final DoubleConsumer setter;

        ValueSlider(int x, int y, int w, int h, String label, double min, double max, double val, DoubleConsumer setter) {
            super(x, y, w, h, Component.empty(), Math.max(0.0, Math.min(1.0, (val - min) / (max - min))));
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            if (this.label == null) {
                return;
            }
            this.setMessage(Component.literal(this.label + ": " + String.format("%.2f", this.min + (this.max - this.min) * this.value)));
        }

        @Override
        protected void applyValue() {
            this.setter.accept(this.min + (this.max - this.min) * this.value);
        }
    }
}
