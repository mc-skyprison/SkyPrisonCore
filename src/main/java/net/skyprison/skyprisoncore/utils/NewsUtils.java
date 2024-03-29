package net.skyprison.skyprisoncore.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.skyprison.skyprisoncore.SkyPrisonCore;
import net.skyprison.skyprisoncore.inventories.misc.NewsMessageEdit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class NewsUtils {
    public static boolean saveNewsMessage(NewsMessageEdit newsEdit, DatabaseHook db) {
        if(newsEdit.getNewsMessage() != 0) {
            try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE news SET title = ?, content = ?, hover = ?, click_type = ?, click_data = ?,  " +
                    "permission = ?, priority = ?, last_updated = ?, limited_time = ?, limited_start = ?, limited_end = ? WHERE id = ?")) {
                ps.setString(1, newsEdit.getTitle());
                ps.setString(2, newsEdit.getContent());
                ps.setString(3, newsEdit.getHover());
                ps.setString(4, newsEdit.getClickType());
                ps.setString(5, newsEdit.getClickData());
                ps.setString(6, newsEdit.getPermission());
                ps.setInt(7, newsEdit.getPriority());
                ps.setLong(8, System.currentTimeMillis());
                ps.setInt(9, newsEdit.getLimitedTime());
                ps.setLong(10, newsEdit.getLimitedStart());
                ps.setLong(11, newsEdit.getLimitedEnd());
                ps.setInt(12, newsEdit.getNewsMessage());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("INSERT INTO news (title, content, hover, click_type, click_data, permission, priority, " +
                    "created_on, last_updated, limited_time, limited_start, limited_end) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, newsEdit.getTitle());
                ps.setString(2, newsEdit.getContent());
                ps.setString(3, newsEdit.getHover());
                ps.setString(4, newsEdit.getClickType());
                ps.setString(5, newsEdit.getClickData());
                ps.setString(6, newsEdit.getPermission());
                ps.setInt(7, newsEdit.getPriority());
                ps.setLong(8, System.currentTimeMillis());
                ps.setLong(9, System.currentTimeMillis());
                ps.setInt(10, newsEdit.getLimitedTime());
                ps.setLong(11, newsEdit.getLimitedStart());
                ps.setLong(12, newsEdit.getLimitedEnd());
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
    }
    public static void sendNewsMessage(Player player, int newsMessage) {
        Component msg = Component.newline().append(MiniMessage.miniMessage().deserialize("<b><#0fc3ff>Sky<#ff0000>Prison <#e65151>News</b>").appendNewline().appendSpace());
        HoverEvent<Component> hoverEvent = null;
        ClickEvent clickEvent = null;
        if(newsMessage == 0) {
            LinkedHashMap<HashMap<String, Object>, Integer> newsMessages = new LinkedHashMap<>();

            try(Connection conn = SkyPrisonCore.db.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT content, hover, click_type, click_data, permission, priority, " +
                    "limited_time, limited_start, limited_end FROM news")) {
                ps.setInt(1, newsMessage);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    if(rs.getInt(7) != 0) {
                        long start = rs.getLong(8);
                        long end = rs.getLong(9);
                        long curr = System.currentTimeMillis();
                        if(start < curr || end < curr) continue;
                    }
                    if(player.hasPermission("skyprisoncore.news." + rs.getString(5))) {
                        HashMap<String, Object> messageComps = new HashMap<>();
                        Component message = MiniMessage.miniMessage().deserialize(rs.getString(1));
                        messageComps.put("content", message);
                        if(!rs.getString(2).isEmpty()) {
                            messageComps.put("hover", HoverEvent.showText(MiniMessage.miniMessage().deserialize(rs.getString(2))));
                        }
                        if(!rs.getString(3).isEmpty()) {
                            ClickEvent.Action action = Objects.requireNonNull(ClickEvent.Action.NAMES.value(rs.getString(3).toLowerCase()));
                            String value = "";
                            switch (action) {
                                case OPEN_URL, SUGGEST_COMMAND, COPY_TO_CLIPBOARD -> value = rs.getString(4);
                                case RUN_COMMAND -> value = "/" + rs.getString(4);
                            }
                            clickEvent = ClickEvent.clickEvent(action, value);
                            messageComps.put("click", clickEvent);
                        }
                        newsMessages.put(messageComps, rs.getInt(6));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }

            if(!newsMessages.isEmpty()) {
                List<Integer> cumulativeWeights = new ArrayList<>();
                int totalWeight = 0;

                for (Integer weight : newsMessages.values()) {
                    totalWeight += weight;
                    cumulativeWeights.add(totalWeight);
                }

                Random rand = new Random();
                int randomWeight = rand.nextInt(totalWeight);

                int index = Collections.binarySearch(cumulativeWeights, randomWeight);

                if (index < 0) {
                    index = Math.abs(index + 1);
                }
                HashMap<String, Object> finalMsg = new ArrayList<>(newsMessages.keySet()).get(index);
                Component content = (Component) finalMsg.get("content");
                hoverEvent = (HoverEvent<Component>) finalMsg.getOrDefault("hover", null);
                clickEvent = (ClickEvent) finalMsg.getOrDefault("click", null);
                msg = msg.append(content);
            }
        } else {
            try(Connection conn = SkyPrisonCore.db.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT content, hover, click_type, click_data FROM news WHERE id = ?")) {
                ps.setInt(1, newsMessage);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    Component message = MiniMessage.miniMessage().deserialize(rs.getString(1));
                    msg = msg.append(message);
                    if(!rs.getString(2).isEmpty()) {
                        hoverEvent = HoverEvent.showText(MiniMessage.miniMessage().deserialize(rs.getString(2)));
                    }
                    if(!rs.getString(3).isEmpty()) {
                        ClickEvent.Action action = Objects.requireNonNull(ClickEvent.Action.NAMES.value(rs.getString(3).toLowerCase()));
                        String value = "";
                        switch (action) {
                            case OPEN_URL, SUGGEST_COMMAND, COPY_TO_CLIPBOARD -> value = rs.getString(4);
                            case RUN_COMMAND -> value = "/" + rs.getString(4);
                        }
                        clickEvent = ClickEvent.clickEvent(action, value);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        msg = msg.appendNewline().hoverEvent(hoverEvent).clickEvent(clickEvent);
        player.sendMessage(msg);
    }
}
