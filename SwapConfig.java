package dev.maceswap.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SwapConfig {
    private static final Logger LOG = LoggerFactory.getLogger("maceswap");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("maceswap.json");
    private static SwapConfig instance;

    public boolean maceSwapEnabled = true;
    public boolean breachSwapEnabled = false;
    public boolean smartSwapEnabled = false;
    public boolean hudEnabled = true;
    public boolean debug = false;
    public double minFallDistance = 1.0;
    public double minDownVelocity = 0.4;
    public double minTargetDistance = 0.0;
    public double maxTargetDistance = 7.0;
    public double attackReach = 3.0;
    public double swapLeadTicks = 4.0;
    public double spearReach = 4.5;
    public double spearLeadTicks = 1.0;
    public int confirmTicks = 1;
    public int cooldownTicks = 10;
    public int revertDelayTicks = 0;
    public int cancelGraceTicks = 6;
    public boolean revertAfterAttack = true;
    public boolean swapOnHit = true;
    public int configVersion = 0;
    public boolean stayOnBreach = true;
    public double minTargetArmor = 8.0;
    public double densityMinFall = 5.0;
    public List<String> priority = new ArrayList<>(List.of("BREACH", "DENSITY", "WIND_BURST", "PLAIN"));

    public static SwapConfig get() {
        if (instance == null) {
            load();
        }
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                instance = GSON.fromJson(Files.readString(FILE), SwapConfig.class);
            }
        } catch (Exception e) {
            LOG.warn("Could not read config, using defaults", e);
        }
        if (instance == null) {
            instance = new SwapConfig();
        }
        instance.sanitize();
        instance.save();
    }

    public void save() {
        try {
            Files.writeString(FILE, GSON.toJson(this));
        } catch (Exception e) {
            LOG.warn("Could not save config", e);
        }
    }

    private void sanitize() {
        List<String> all = List.of("BREACH", "DENSITY", "WIND_BURST", "PLAIN");
        if (priority == null) {
            priority = new ArrayList<>();
        }
        priority.removeIf(s -> !all.contains(s));
        for (String s : all) {
            if (!priority.contains(s)) {
                priority.add(s);
            }
        }
        if (configVersion < 2) {
            revertDelayTicks = 0;
            swapOnHit = true;
            configVersion = 2;
        }
        confirmTicks = Math.max(1, confirmTicks);
        cooldownTicks = Math.max(0, cooldownTicks);
    }
}
