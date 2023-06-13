package net.skyprison.skyprisoncore.inventories;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.skyprison.skyprisoncore.SkyPrisonCore;
import net.skyprison.skyprisoncore.utils.DatabaseHook;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ClaimMembers implements CustomInventory {

    private final Inventory inventory;
    private final String category;
    private final String claimName;
    private final HashMap<UUID, String> members;
    private final int page;

    private final DatabaseHook db;

    public ClaimMembers(SkyPrisonCore plugin, DatabaseHook db, String claimName, HashMap<UUID, String> members, String category, int page) {
        this.db = db;
        this.claimName = claimName;
        this.members = members;
        this.category = category;
        this.page = page;
        this.inventory = plugin.getServer().createInventory(this, 54, Component.text(claimName).color(TextColor.fromHexString("#0fc3ff")).decoration(TextDecoration.ITALIC, false));
        ItemStack redPane = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta redMeta = redPane.getItemMeta();
        redMeta.displayName(Component.text(" "));
        redPane.setItemMeta(redMeta);

        ItemStack blackPane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta blackMeta = blackPane.getItemMeta();
        blackMeta.displayName(Component.text(" "));
        blackPane.setItemMeta(blackMeta);

        ItemStack nextPage = new ItemStack(Material.PAPER);
        ItemMeta nextMeta = nextPage.getItemMeta();
        nextMeta.displayName(Component.text("Next Page").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        nextPage.setItemMeta(nextMeta);
        ItemStack prevPage = new ItemStack(Material.PAPER);
        ItemMeta prevMeta = prevPage.getItemMeta();
        prevMeta.displayName(Component.text("Previous Page").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        prevPage.setItemMeta(prevMeta);

        int totalPages = (int) Math.ceil((double) members.size() / 28);

        if(page > totalPages) {
            page = 1;
        }

        List<UUID> membersToShow = new ArrayList<>(members.keySet());
        if(!category.isEmpty()) membersToShow.removeIf(member -> !members.get(member).equalsIgnoreCase(category));

        int toRemove = 28 * (page - 1);
        if(toRemove != 0) {
            membersToShow = membersToShow.subList(toRemove, membersToShow.size()-1);
        }
        Iterator<UUID> memberUUIDs = membersToShow.iterator();
        int finalPage = page;
        new BukkitRunnable() {
            @Override
            public void run() {
                for(int i = 0; i < inventory.getSize();i++) {
                    if (i == 47 && finalPage != 1) {
                        inventory.setItem(i, prevPage);
                    } else if (i == 51 && totalPages > 1 && finalPage != totalPages) {
                        inventory.setItem(i, nextPage);
                    } else if (i == 49) {
                        ItemStack itemSort = new ItemStack(Material.WRITABLE_BOOK);
                        ItemMeta sortMeta = itemSort.getItemMeta();
                        TextColor color = NamedTextColor.GRAY;
                        TextColor selectedColor = TextColor.fromHexString("#0fffc3");
                        sortMeta.displayName(Component.text("Toggle Members", TextColor.fromHexString("#20df80")).decoration(TextDecoration.ITALIC, false));
                        List<Component> lore = new ArrayList<>();
                        lore.add(Component.text("All Members").color(category.equalsIgnoreCase("") ? selectedColor : color).decoration(TextDecoration.ITALIC, false));
                        lore.add(Component.text("Owner").color(category.equalsIgnoreCase("owner") ? selectedColor : color).decoration(TextDecoration.ITALIC, false));
                        lore.add(Component.text("Co-owners").color(category.equalsIgnoreCase("co-owner") ? selectedColor : color).decoration(TextDecoration.ITALIC, false));
                        lore.add(Component.text("Members").color(category.equalsIgnoreCase("member") ? selectedColor : color).decoration(TextDecoration.ITALIC, false));
                        sortMeta.lore(lore);
                        itemSort.setItemMeta(sortMeta);
                        inventory.setItem(i, itemSort);
                    } else if (i == 0 || i == 8 || i == 9 || i == 17 || i == 18 || i == 26 || i == 27 || i == 35 || i == 36 || i == 44 || i == 45 || i == 53) {
                        inventory.setItem(i, redPane);
                    } else if (i < 8 || i > 45 && i < 53) {
                        inventory.setItem(i, blackPane);
                    } else {
                        if (memberUUIDs.hasNext()) {
                            UUID memberUUID = memberUUIDs.next();
                            OfflinePlayer oPlayer = Bukkit.getOfflinePlayer(memberUUID);
                            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
                            SkullMeta itemMeta = (SkullMeta) item.getItemMeta();
                            itemMeta.setOwningPlayer(oPlayer);
                            String name = "";
                            try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT current_name FROM users WHERE user_id = ?")) {
                                ps.setString(1, memberUUID.toString());
                                ResultSet rs = ps.executeQuery();
                                if (rs.next()) {
                                    name = rs.getString(1);
                                }
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }

                            itemMeta.displayName(Component.text(Objects.requireNonNull(name)).color(TextColor.fromHexString("#0fffc3")).decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
                            List<Component> lore = new ArrayList<>();
                            lore.add(Component.text(WordUtils.capitalize(members.get(memberUUID)), TextColor.fromHexString("#ffba75")).decoration(TextDecoration.ITALIC, false));
                            itemMeta.lore(lore);
                            item.setItemMeta(itemMeta);
                            inventory.setItem(i, item);
                        }
                    }
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public String getNextCategory(String category) {
        String nextCat = "";
        switch (category) {
            case "" -> nextCat = "owner";
            case "owner" -> nextCat = "co-owner";
            case "co-owner" -> nextCat = "member";
            case "member" -> nextCat = "";
        }
        return nextCat;
    }

    public String getClaimName() {
        return this.claimName;
    }

    public HashMap<UUID, String> getMembers() {
        return this.members;
    }

    public String getCategory() {
        return this.category;
    }

    public DatabaseHook getDatabase() {
        return this.db;
    }

    @Override
    public ClickBehavior defaultClickBehavior() {
        return ClickBehavior.DISABLE_ALL;
    }

    @Override
    public List<Object> customClickList() {
        return null;
    }

    public int getPage() {
        return this.page;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return this.inventory;
    }
}