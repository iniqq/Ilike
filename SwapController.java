package dev.maceswap.logic;

import dev.maceswap.config.SwapConfig;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SwapController {
    private static final Logger LOG = LoggerFactory.getLogger("maceswap");

    private enum State {
        IDLE,
        SWAPPED
    }

    private State state = State.IDLE;
    private int prevSlot = -1;
    private int swappedSlot = -1;
    private long tick;
    private long lastSwapTick = -1000L;
    private long revertAt = -1L;
    private int confirm;
    private int nonViable;
    private boolean attacked;
    private boolean swappedBreach;
    private boolean holdUntilLanding;
    private String activeReason = "";
    private boolean prevAttackDown;
    private String status = "";

    public boolean isSwapped() {
        return state == State.SWAPPED;
    }

    public String activeLabel() {
        SwapConfig c = SwapConfig.get();
        if (c.smartSwapEnabled) {
            return "SMART";
        }
        StringBuilder sb = new StringBuilder();
        if (c.breachSwapEnabled) {
            sb.append("BREACH ");
        }
        if (c.maceSwapEnabled) {
            sb.append("MACE");
        }
        return sb.length() == 0 ? "OFF" : sb.toString().trim();
    }

    public String getStatus() {
        return status;
    }

    public void startTick(Minecraft mc) {
        boolean down = mc.options.keyAttack.isDown();
        boolean pressed = down && !prevAttackDown;
        prevAttackDown = down;
        if (!pressed) {
            return;
        }
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.screen != null) {
            return;
        }
        Entity e = mc.crosshairPickEntity;
        if (e == null) {
            status = "click: no target under crosshair";
            return;
        }
        onAttack(p, e);
    }

    public void onAttack(Player p, Entity e) {
        SwapConfig cfg = SwapConfig.get();
        if (state == State.SWAPPED) {
            attacked = true;
            dbg("attack detected");
            return;
        }
        if (!cfg.swapOnHit) {
            status = "swap on hit is OFF";
            return;
        }
        if (holdUntilLanding) {
            status = "holding Breach mace until landing";
            return;
        }
        if (!(cfg.maceSwapEnabled || cfg.breachSwapEnabled || cfg.smartSwapEnabled)) {
            status = "all swap modes OFF";
            return;
        }
        if (!(e instanceof LivingEntity target) || !target.isAlive() || p.isSpectator()) {
            status = "target is not a living entity";
            return;
        }
        if (!falling(p, cfg)) {
            status = String.format(
                "not falling enough: fall=%.2f (min %.2f) vy=%.2f (max -%.2f) onGround=%b",
                p.fallDistance, cfg.minFallDistance, p.getDeltaMovement().y, cfg.minDownVelocity, p.onGround());
            return;
        }
        Inventory inv = p.getInventory();
        List<MaceInfo> maces = MaceInfo.scanHotbar(inv);
        if (maces.isEmpty()) {
            status = "no usable mace in HOTBAR";
            return;
        }
        MaceInfo pick = select(cfg, maces, target, p, inv);
        if (pick == null) {
            status = "no suitable mace (Breach-only: need armor >= " + cfg.minTargetArmor + ")";
            return;
        }
        if (inv.getSelectedSlot() == pick.slot()) {
            status = "already holding the best mace";
            return;
        }
        doSwap(inv, pick, "hit", p, target);
        status = "SWAPPED to slot " + (pick.slot() + 1);
        attacked = true;
    }

    public void tick(Minecraft mc) {
        tick++;
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            reset();
            holdUntilLanding = false;
            return;
        }
        SwapConfig cfg = SwapConfig.get();
        Inventory inv = p.getInventory();
        if (state == State.SWAPPED) {
            tickSwapped(mc, p, inv, cfg);
            return;
        }
        if (holdUntilLanding) {
            if (p.onGround() || p.isInWater()) {
                holdUntilLanding = false;
            } else {
                return;
            }
        }
        if (cfg.swapOnHit) {
            return;
        }
        if (!(cfg.maceSwapEnabled || cfg.breachSwapEnabled || cfg.smartSwapEnabled)) {
            return;
        }
        if (mc.screen != null || p.isUsingItem() || p.isSpectator()) {
            return;
        }
        if (tick - lastSwapTick < cfg.cooldownTicks) {
            return;
        }
        LivingEntity target = findTarget(mc, p, cfg);
        if (target == null || !falling(p, cfg) || !timingOk(p, target, cfg)) {
            confirm = 0;
            return;
        }
        if (++confirm < cfg.confirmTicks) {
            return;
        }
        List<MaceInfo> maces = MaceInfo.scanHotbar(inv);
        if (maces.isEmpty()) {
            return;
        }
        String reason = cfg.smartSwapEnabled ? "smart" : (cfg.maceSwapEnabled ? "mace" : "breach");
        MaceInfo pick = select(cfg, maces, target, p, inv);
        if (pick == null) {
            return;
        }
        if (inv.getSelectedSlot() == pick.slot()) {
            return;
        }
        doSwap(inv, pick, reason, p, target);
    }

    private MaceInfo select(SwapConfig cfg, List<MaceInfo> maces, LivingEntity target, Player p, Inventory inv) {
        if (cfg.smartSwapEnabled || cfg.maceSwapEnabled) {
            return bestByDamage(maces, target, p, cfg, inv);
        }
        if (cfg.breachSwapEnabled && (double) target.getArmorValue() >= cfg.minTargetArmor) {
            List<MaceInfo> br = maces.stream().filter(m -> m.breach() > 0).toList();
            if (!br.isEmpty()) {
                return bestByDamage(br, target, p, cfg, inv);
            }
        }
        return null;
    }

    private void doSwap(Inventory inv, MaceInfo pick, String reason, Player p, LivingEntity target) {
        prevSlot = inv.getSelectedSlot();
        swappedSlot = pick.slot();
        swappedBreach = pick.breach() > 0;
        inv.setSelectedSlot(swappedSlot);
        state = State.SWAPPED;
        lastSwapTick = tick;
        attacked = false;
        revertAt = -1L;
        nonViable = 0;
        confirm = 0;
        activeReason = reason;
        dbg("SWAP " + prevSlot + " -> " + swappedSlot + " (" + reason + ") fall="
            + String.format("%.2f", p.fallDistance) + " vy=" + String.format("%.2f", p.getDeltaMovement().y)
            + " dist=" + String.format("%.2f", dist(p, target)));
    }

    private void tickSwapped(Minecraft mc, Player p, Inventory inv, SwapConfig cfg) {
        if (inv.getSelectedSlot() != swappedSlot) {
            dbg("user changed slot, abandoning");
            reset();
            return;
        }
        if (attacked) {
            if (cfg.stayOnBreach && swappedBreach) {
                dbg("attack with Breach mace: staying on it until landing");
                holdUntilLanding = true;
                reset();
                return;
            }
            if (revertAt < 0L) {
                revertAt = tick + cfg.revertDelayTicks;
            }
        } else {
            LivingEntity t = findTarget(mc, p, cfg);
            boolean viable = t != null && falling(p, cfg);
            nonViable = viable ? 0 : nonViable + 1;
            if (nonViable >= cfg.cancelGraceTicks) {
                revert(inv, "cancelled (no longer viable)");
                return;
            }
        }
        if (attacked && cfg.revertAfterAttack && revertAt >= 0L && tick >= revertAt) {
            revert(inv, "post-attack");
        } else if (attacked && !cfg.revertAfterAttack) {
            reset();
        }
    }

    private void revert(Inventory inv, String why) {
        if (prevSlot >= 0 && prevSlot < 9 && inv.getSelectedSlot() == swappedSlot) {
            inv.setSelectedSlot(prevSlot);
        }
        dbg("REVERT -> " + prevSlot + " : " + why);
        reset();
    }

    private void reset() {
        state = State.IDLE;
        swappedSlot = -1;
        prevSlot = -1;
        revertAt = -1L;
        nonViable = 0;
        confirm = 0;
        attacked = false;
    }

    private static boolean falling(Player p, SwapConfig c) {
        return !p.onGround()
            && !p.isInWater()
            && !p.isFallFlying()
            && !p.isPassenger()
            && !p.getAbilities().flying
            && p.fallDistance >= c.minFallDistance
            && p.getDeltaMovement().y <= -c.minDownVelocity;
    }

    private static double dist(Player p, Entity t) {
        return Math.sqrt(t.getBoundingBox().distanceToSqr(p.getEyePosition()));
    }

    private static boolean isSpear(ItemStack st) {
        return !st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().endsWith("_spear");
    }

    private static boolean timingOk(Player p, Entity t, SwapConfig c) {
        double d = dist(p, t);
        if (d < c.minTargetDistance || d > c.maxTargetDistance) {
            return false;
        }
        boolean spear = isSpear(p.getMainHandItem());
        double reach = spear ? c.spearReach : c.attackReach;
        double lead = spear ? c.spearLeadTicks : c.swapLeadTicks;
        if (d <= reach + 0.5) {
            return true;
        }
        Vec3 dir = t.getBoundingBox().getCenter().subtract(p.getEyePosition()).normalize();
        double closing = p.getDeltaMovement().dot(dir);
        if (closing < 0.05) {
            return false;
        }
        return (d - reach) / closing <= lead;
    }

    private static LivingEntity findTarget(Minecraft mc, Player p, SwapConfig c) {
        Entity e = mc.crosshairPickEntity;
        if (e instanceof LivingEntity le && le.isAlive() && le != p && dist(p, le) <= c.maxTargetDistance) {
            return le;
        }
        Player best = null;
        double bestD = Double.MAX_VALUE;
        Vec3 look = p.getLookAngle();
        for (Player o : mc.level.players()) {
            if (o == p || !o.isAlive() || o.isSpectator()) {
                continue;
            }
            double d = dist(p, o);
            if (d > c.maxTargetDistance) {
                continue;
            }
            Vec3 dir = o.getBoundingBox().getCenter().subtract(p.getEyePosition()).normalize();
            if (look.dot(dir) < 0.6 || !(d < bestD)) {
                continue;
            }
            bestD = d;
            best = o;
        }
        return best;
    }

    private static double predictedFall(Player p, LivingEntity t) {
        double remaining = Math.max(0.0, p.getY() - t.getY() - 1.0);
        return Math.min(60.0, p.fallDistance + remaining);
    }

    private static double expectedDamage(MaceInfo m, double fall, double armor) {
        double raw = 6.0;
        if (fall > 1.5) {
            raw += Math.min(fall, 3.0) * 4.0
                + Math.max(0.0, Math.min(fall - 3.0, 5.0)) * 2.0
                + Math.max(0.0, fall - 8.0)
                + 0.5 * m.density() * fall;
        } else {
            raw *= 1.5;
        }
        double eff = armor * (1.0 - 0.15 * m.breach());
        double reduction = Math.min(20.0, Math.max(eff / 5.0, eff - raw / 2.0)) / 25.0;
        return raw * (1.0 - reduction);
    }

    private MaceInfo bestByDamage(List<MaceInfo> maces, LivingEntity t, Player p, SwapConfig c, Inventory inv) {
        double fall = predictedFall(p, t);
        double armor = t.getArmorValue();
        MaceInfo best = null;
        double bestScore = -1.0;
        for (MaceInfo m : maces) {
            int rank = c.priority.indexOf(m.primaryType().name());
            double score = expectedDamage(m, fall, armor)
                + 0.01 * (c.priority.size() - rank)
                + (m.slot() == inv.getSelectedSlot() ? 0.05 : 0.0);
            dbg(String.format("slot %d (%s) expected=%.2f predFall=%.1f armor=%.0f",
                m.slot(), m.primaryType(), score, fall, armor));
            if (!(score > bestScore)) {
                continue;
            }
            bestScore = score;
            best = m;
        }
        return best;
    }

    private void dbg(String msg) {
        if (SwapConfig.get().debug) {
            LOG.info("[t={}] {}", tick, msg);
        }
    }
}
