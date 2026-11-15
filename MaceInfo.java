package dev.maceswap.logic;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public record MaceInfo(int slot, int breach, int density, int windBurst) {

    public enum Type {
        BREACH,
        DENSITY,
        WIND_BURST,
        PLAIN
    }

    public Type primaryType() {
        if (breach > 0) {
            return Type.BREACH;
        }
        if (density > 0) {
            return Type.DENSITY;
        }
        if (windBurst > 0) {
            return Type.WIND_BURST;
        }
        return Type.PLAIN;
    }

    public static List<MaceInfo> scanHotbar(Inventory inv) {
        List<MaceInfo> out = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !s.is(Items.MACE)) {
                continue;
            }
            if (s.isDamageableItem() && s.getDamageValue() >= s.getMaxDamage() - 1) {
                continue;
            }
            int br = 0;
            int de = 0;
            int wb = 0;
            ItemEnchantments ench = s.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            for (Object2IntMap.Entry<Holder<Enchantment>> e : ench.entrySet()) {
                Holder<Enchantment> h = e.getKey();
                int lvl = e.getIntValue();
                if (h.is(Enchantments.BREACH)) {
                    br = lvl;
                } else if (h.is(Enchantments.DENSITY)) {
                    de = lvl;
                } else if (h.is(Enchantments.WIND_BURST)) {
                    wb = lvl;
                }
            }
            out.add(new MaceInfo(i, br, de, wb));
        }
        return out;
    }
}
