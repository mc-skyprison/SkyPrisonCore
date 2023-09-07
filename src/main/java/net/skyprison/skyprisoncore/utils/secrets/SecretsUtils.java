package net.skyprison.skyprisoncore.utils.secrets;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.skyprison.skyprisoncore.SkyPrisonCore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static net.skyprison.skyprisoncore.SkyPrisonCore.db;

public class SecretsUtils {
    public static int getFoundAmount(int secretId, String playerId) {
        int found = 0;
        try (Connection sConn = db.getConnection(); PreparedStatement sPs = sConn.prepareStatement(
                "SELECT COUNT(id) FROM secrets_userdata WHERE secret_id = ? AND user_id = ?")) {
            sPs.setInt(1, secretId);
            sPs.setString(2, playerId);
            ResultSet set = sPs.executeQuery();
            if (set.next()) {
                found = set.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return found;
    }
    public static List<String> getCategoryNames() {
        List<String> categories = new ArrayList<>();
        try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM secrets_categories")) {
            ResultSet rs = ps.executeQuery();
            while(rs.next()) {
                categories.add(rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return categories;
    }
    public static List<String> getTypes() {
        return new ArrayList<>(Arrays.asList("secret", "parkour", "puzzle"));
    }
    public static List<String> getRewardTypes() {
        return new ArrayList<>(Collections.singleton("tokens"));
    }
    public static ItemStack getSign(SkyPrisonCore plugin, int secretId, String secretName, Material material) {
        ItemStack sign = new ItemStack(material);
        sign.editMeta(meta -> {
            meta.displayName(Component.text("Secret Sign", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Secret Name: ", NamedTextColor.GOLD).append(MiniMessage.miniMessage().deserialize(secretName)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Place sign to add Secret Location", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            NamespacedKey key = new NamespacedKey(plugin, "secret-sign");
            meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, secretId);
        });
        return sign;
    }
    public static Secret getSecretFromId(int id) {
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT name, display_item, category, type, reward_type, reward, cooldown FROM secrets WHERE id = ?")) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String name = rs.getString(1);
                ItemStack displayItem = ItemStack.deserializeBytes(rs.getBytes(2));
                String sCategory = rs.getString(3);
                String type = rs.getString(4);
                String rewardType = rs.getString(5);
                int reward = rs.getInt(6);
                String cooldown = rs.getString(7);
                return new Secret(id, name, displayItem, sCategory, type, rewardType, reward, cooldown);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    public static long coolInMillis(String cooldown) {
        int days = Integer.parseInt(cooldown.substring(0, cooldown.length() - 1));
        return TimeUnit.DAYS.toMillis(days);
    }
    public static Component formatTime(long collected) {
        Component coolText = null;
        LocalDateTime currTime = LocalDateTime.now();
        LocalDateTime cooldownDate = Instant.ofEpochMilli(collected).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay();
        if (currTime.getDayOfYear() < cooldownDate.getDayOfYear()) {
            coolText = Component.empty();
            Duration duration = Duration.between(currTime, cooldownDate);
            long days = duration.toDaysPart();
            long hours = duration.toHoursPart();
            long minutes = duration.toMinutesPart();
            long seconds = duration.toSecondsPart();
            if (days != 0.0)
                coolText = coolText.append(Component.text(days, NamedTextColor.YELLOW).append(Component.text(" day" +
                        (days > 1 ? "s " : " "), NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false));
            if (hours != 0.0)
                coolText = coolText.append(Component.text(hours, NamedTextColor.YELLOW).append(Component.text(" hour" +
                        (hours > 1 ? "s " : " "), NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false));
            if (minutes != 0.0 && days == 0.0)
                coolText = coolText.append(Component.text(minutes, NamedTextColor.YELLOW).append(Component.text(" min" +
                        (minutes > 1 ? "s " : " "), NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false));
            if (seconds != 0.0 && days == 0.0 && hours == 0.0)
                coolText = coolText.append(Component.text(seconds, NamedTextColor.YELLOW).append(Component.text(" sec" +
                        (seconds > 1 ? "s " : " "), NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false));
        }
        return coolText;
    }
    public static long getPlayerCooldown(int secretId, UUID pUUID) {
        long collected = 0;
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT collect_time FROM secrets_userdata WHERE secret_id = ? AND user_id = ? ORDER BY collect_time DESC LIMIT 1")) {
            ps.setInt(1, secretId);
            ps.setString(2, pUUID.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                collected = rs.getLong(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return collected;
    }
    public static Component getTimeLeft(int secretId, String cooldown, UUID pUUID) {
        Component coolText = null;
        long collected = getPlayerCooldown(secretId, pUUID);

        if(collected != 0) {
            collected += coolInMillis(cooldown);
            coolText = formatTime(collected);
        }

        if(coolText == null) {
            coolText = Component.text("Available Now!", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false);
        }
        return coolText;
    }
}