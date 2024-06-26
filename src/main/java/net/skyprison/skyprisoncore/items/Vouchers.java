package net.skyprison.skyprisoncore.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.skyprison.skyprisoncore.SkyPrisonCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;

public class Vouchers {
    public static ItemStack getVoucherFromType(SkyPrisonCore plugin, String type, int amount) {
        ItemStack voucherItem = null;
        switch(type.toLowerCase()) {
            case "token-shop" -> voucherItem = Vouchers.getTokenShopVoucher(plugin, amount);
            case "mine-reset" -> voucherItem = Vouchers.getMineResetVoucher(plugin, amount);
            case "single-use-enderchest" -> voucherItem = Vouchers.getSingleUseEnderchest(plugin, amount);
        }
        return voucherItem;
    }
    public static ItemStack getTokenShopVoucher(SkyPrisonCore plugin, int amount) {
        ItemStack voucher = new ItemStack(Material.PAPER, amount);
        voucher.editMeta(meta -> {
            meta.displayName(Component.text( "Token Shop Voucher", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            ArrayList<Component> lore = new ArrayList<>();
            lore.add(Component.text("Can be used instead of tokens to buy", NamedTextColor.GRAY));
            lore.add(Component.text("items & perks from the Token Shop.", NamedTextColor.GRAY));
            meta.setEnchantmentGlintOverride(true);
            meta.lore(lore);
            PersistentDataContainer vouchData = meta.getPersistentDataContainer();
            NamespacedKey key = new NamespacedKey(plugin, "voucher");
            vouchData.set(key, PersistentDataType.STRING, "token-shop");
        });
        return voucher;
    }
    public static ItemStack getMineResetVoucher(SkyPrisonCore plugin, int amount) {
        ItemStack voucher = new ItemStack(Material.PAPER, amount);
        voucher.editMeta(meta -> {
            meta.displayName(Component.text( "Mine Reset Voucher", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            ArrayList<Component> lore = new ArrayList<>();
            lore.add(Component.text("Use on a cooldown Mine Reset", NamedTextColor.GRAY));
            lore.add(Component.text("sign to instantly reset the mine.", NamedTextColor.GRAY));
            meta.setEnchantmentGlintOverride(true);
            meta.lore(lore);
            PersistentDataContainer vouchData = meta.getPersistentDataContainer();
            NamespacedKey key = new NamespacedKey(plugin, "voucher");
            vouchData.set(key, PersistentDataType.STRING, "mine-reset");
        });
        return voucher;
    }
    public static ItemStack getSingleUseEnderchest(SkyPrisonCore plugin, int amount) {
        ItemStack voucher = new ItemStack(Material.ENDER_CHEST, amount);
        voucher.editMeta(meta -> {
            meta.displayName(Component.text( "Single-Use Enderchest", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            ArrayList<Component> lore = new ArrayList<>();
            lore.add(Component.text("Right click to open your ender chest!", NamedTextColor.GRAY));
            meta.setEnchantmentGlintOverride(true);
            meta.lore(lore);
            PersistentDataContainer vouchData = meta.getPersistentDataContainer();
            NamespacedKey key = new NamespacedKey(plugin, "voucher");
            vouchData.set(key, PersistentDataType.STRING, "single-use-enderchest");
        });
        return voucher;
    }
}
