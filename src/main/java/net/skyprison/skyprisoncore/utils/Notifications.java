package net.skyprison.skyprisoncore.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.skyprison.skyprisoncore.SkyPrisonCore;
import org.bukkit.OfflinePlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class Notifications {
    private static final DatabaseHook db = SkyPrisonCore.db;
    public static String hasNotification(String id, OfflinePlayer player) {
        String notification = "";
        try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT extra_data FROM notifications WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                notification = rs.getString(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return notification;
    }

    public static HashMap<Integer, List<String>> getNotificationsFromExtra(List<String> extraData) {
        HashMap<Integer, List<String>> notifications = new HashMap<>();
        if(extraData.isEmpty()) return notifications;
        try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(
                "SELECT type, extra_data, user_id FROM notifications WHERE extra_data IN " + SkyPrisonCore.getQuestionMarks(extraData))) {
            for (int i = 0; i < extraData.size(); i++) {
                ps.setString(i + 1, extraData.get(i));
            }
            ResultSet rs = ps.executeQuery();
            int i = 0;
            while (rs.next()) {
                List<String> data = new ArrayList<>();
                data.add(rs.getString(1)); // type
                data.add(rs.getString(2)); // data
                data.add(rs.getString(3)); // uuid
                notifications.put(i, data);
                i++;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return notifications;
    }


    public static List<String> hasNotifications(String type, List<String> extraData, OfflinePlayer player) {
        List<String> notifications = new ArrayList<>();
        if(extraData.isEmpty()) return notifications;
        try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT extra_data FROM notifications WHERE type = ? AND user_id = ? AND extra_data IN "
                + SkyPrisonCore.getQuestionMarks(extraData))) {
            ps.setString(1, type);
            ps.setString(2, player.getUniqueId().toString());

            for (int i = 0; i < extraData.size(); i++) {
                ps.setString(i + 3, extraData.get(i));
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                notifications.add(rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return notifications;
    }



    public static void scheduleForOnline(String pUUID, String type, String content) {
        try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("INSERT INTO schedule_online (user_id, type, content) VALUES (?, ?, ?)")) {
            ps.setString(1, pUUID);
            ps.setString(2, type);
            ps.setString(3, content);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void createNotification(String type, String extraData, String pUUID, Component msg, String id, boolean deleteOnView) {
        if(id == null || id.isEmpty()) id = UUID.randomUUID().toString();
        try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("INSERT INTO notifications (id, type, extra_data, user_id, message, delete_on_view) VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, type);
            ps.setString(3, extraData);
            ps.setString(4, pUUID);
            ps.setString(5, GsonComponentSerializer.gson().serialize(msg));
            ps.setInt(6, deleteOnView ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void deleteNotification(String id) {
        try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM notifications WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void deleteNotification(String type, String extraData, OfflinePlayer player) {
        try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM notifications WHERE extra_data = ? AND user_id = ? AND type = ?")) {
            ps.setString(1, extraData);
            ps.setString(2, player.getUniqueId().toString());
            ps.setString(3, type);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}