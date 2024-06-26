package net.skyprison.skyprisoncore.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.shop.ShopManager;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.skyprison.skyprisoncore.SkyPrisonCore;
import net.skyprison.skyprisoncore.inventories.economy.BountiesList;
import net.skyprison.skyprisoncore.inventories.economy.BuyBack;
import net.skyprison.skyprisoncore.inventories.economy.EconomyCheck;
import net.skyprison.skyprisoncore.inventories.economy.MoneyHistory;
import net.skyprison.skyprisoncore.inventories.economy.tokens.TokensCheck;
import net.skyprison.skyprisoncore.inventories.economy.tokens.TokensHistory;
import net.skyprison.skyprisoncore.inventories.misc.Daily;
import net.skyprison.skyprisoncore.inventories.misc.DatabaseInventory;
import net.skyprison.skyprisoncore.utils.ChatUtils;
import net.skyprison.skyprisoncore.utils.DatabaseHook;
import net.skyprison.skyprisoncore.utils.players.PlayerManager;
import net.skyprison.skyprisoncore.utils.TokenUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.Command;
import org.incendo.cloud.component.DefaultValue;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.suggestion.SuggestionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static net.skyprison.skyprisoncore.SkyPrisonCore.bountyCooldown;
import static org.incendo.cloud.bukkit.parser.MaterialParser.materialParser;
import static org.incendo.cloud.bukkit.parser.PlayerParser.playerParser;
import static org.incendo.cloud.parser.standard.DoubleParser.doubleParser;
import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;
import static org.incendo.cloud.parser.standard.LongParser.longParser;
import static org.incendo.cloud.parser.standard.StringParser.quotedStringParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

public class EconomyCommands {
    private final SkyPrisonCore plugin;
    private final DatabaseHook db;
    private final PaperCommandManager<CommandSourceStack> manager;
    private final Component bountyPrefix = Component.text("Bounties", NamedTextColor.RED).append(Component.text(" | ", NamedTextColor.WHITE));

    public EconomyCommands(SkyPrisonCore plugin, DatabaseHook db, PaperCommandManager<CommandSourceStack> manager) {
        this.plugin = plugin;
        this.db = db;
        this.manager = manager;
        createBountyCommands();
        createShopCommands();
        createMiscCommands();
        createTokenCommands();
    }

    private void createMiscCommands() {
        manager.command(manager.commandBuilder("daily")
                .permission("skyprisoncore.command.daily")
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new Daily(db, player).getInventory()));
                }));
        manager.command(manager.commandBuilder("buyback")
                .permission("skyprisoncore.command.buyback")
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new BuyBack(plugin, db, player).getInventory()));
                }));

        manager.command(manager.commandBuilder("casino")
                .permission("skyprisoncore.command.casino")
                .required("player", playerParser())
                .required("key", stringParser())
                .required("price", doubleParser(0))
                .required("cooldown", longParser(0))
                .handler(c -> {
                    Player player = c.get("player");
                    String key = c.get("key");
                    double price = c.get("price");
                    long cooldown = c.get("cooldown");

                    if(PlayerManager.getBalance(player) < price) {
                        player.sendMessage(Component.text("You do not have enough money..", NamedTextColor.RED));
                        return;
                    }

                    if(!player.hasPermission("skyprisoncore.command.casino.bypass")) {
                        HashMap<String, Long> casinoCools = new HashMap<>();
                        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT casino_name, casino_cooldown FROM casino_cooldowns WHERE user_id = ?")) {
                            ps.setString(1, player.getUniqueId().toString());
                            ResultSet rs = ps.executeQuery();
                            while (rs.next()) {
                                casinoCools.put(rs.getString(1), rs.getLong(2));
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        if (!casinoCools.isEmpty() && casinoCools.containsKey(key) && casinoCools.get(key) > System.currentTimeMillis()) {
                            long distance = casinoCools.get(key) - System.currentTimeMillis();
                            int days = (int) (distance / (1000L * 60 * 60 * 24));
                            int hours = (int) (distance / (1000L * 60 * 60) % 24);
                            int minutes = (int) (distance / (1000L * 60) % 60);
                            int seconds = (int) (distance / 1000L % 60);

                            StringBuilder message = new StringBuilder("You are still on cooldown! Available in: ");
                            if (days > 0) message.append(days).append("d ");
                            if (hours > 0) message.append(hours).append("h ");
                            if (minutes > 0) message.append(minutes).append("m ");
                            message.append(seconds).append("s");

                            player.sendMessage(Component.text(message.toString(), NamedTextColor.RED));
                            return;
                        }
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "cmi money take " + player.getName() + " " + price);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crates key give " + player.getName() + " " + key + " 1");
                    });
                    long nCooldown = (cooldown * 1000) + System.currentTimeMillis();
                    try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO casino_cooldowns (user_id, casino_name, casino_cooldown) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE casino_cooldown = VALUE(casino_cooldown)")) {
                        ps.setString(1, player.getUniqueId().toString());
                        ps.setString(2, key);
                        ps.setLong(3, nCooldown);
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }));

        manager.command(manager.commandBuilder("transportpass")
                .permission("skyprisoncore.command.transportpass")
                .required("player", playerParser())
                .required("type", stringParser(), SuggestionProvider.suggestingStrings(List.of("bus", "train")))
                .handler(c -> {
                    Player player = c.get("player");
                    String type = c.get("type");
                    int amount = type.equalsIgnoreCase("bus") ? 250 : 500;
                    if(player.hasPermission("skyprisoncore.command.transportpass." + type)) {
                        player.sendMessage(Component.text("You already have a " + type + " pass!", NamedTextColor.RED));
                        return;
                    }
                    if(TokenUtils.getTokens(player.getUniqueId()) < amount) {
                        player.sendMessage(Component.text("You do not have enough money..", NamedTextColor.RED));
                        return;
                    }
                    TokenUtils.removeTokens(player.getUniqueId(), amount, "transportpass", type);
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "lp user " + player.getName() + " permission settemp skyprisoncore.command.transportpass." + type + " true 7d"));
                    player.sendMessage(Component.text("You have bought a " + type + " pass!", NamedTextColor.GREEN));
                }));

    }
    private void createShopCommands() {

        // Dontsell

        Command.Builder<CommandSourceStack> dontSell = manager.commandBuilder("dontsell")
                .permission("skyprisoncore.command.dontsell");
        manager.command(dontSell);

        manager.command(dontSell.literal("list")
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    List<String> blockedSales = getDontSells(player);
                    if(!blockedSales.isEmpty()) {
                        Component blockMsg = Component.text("---=== ", NamedTextColor.AQUA).append(Component.text("Blocked Items", NamedTextColor.RED, TextDecoration.BOLD))
                                .append(Component.text(" ===---", NamedTextColor.AQUA));
                        for(String blockedSale : blockedSales) {
                            blockMsg = blockMsg.append(Component.text("\n- ", NamedTextColor.AQUA).append(Component.text(blockedSale, NamedTextColor.DARK_AQUA)));
                        }
                        player.sendMessage(blockMsg);
                    } else {
                        player.sendMessage(Component.text("You havn't blocked any items!", NamedTextColor.RED));
                    }
                }));

        manager.command(dontSell.optional("item", materialParser(), DefaultValue.constant(Material.AIR))
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    Material item = c.get("item");
                    Material blockItem = player.getInventory().getItemInMainHand().getType();
                    if(!item.isAir()) blockItem = item;

                    if(!blockItem.isItem() || ShopGuiPlusApi.getItemStackShopItem(new ItemStack(blockItem)) == null) {
                        player.sendMessage(Component.text("This item can't be sold!", NamedTextColor.RED));
                        return;
                    }

                    List<String> blockedSales = getDontSells(player);
                    boolean isBlocked = blockedSales.contains(blockItem.name());

                    String sql = isBlocked ? "DELETE FROM block_sells WHERE user_id = ? AND block_item = ?" : "INSERT INTO block_sells (user_id, block_item) VALUES (?, ?)";
                    Component msg = Component.text("Successfully ", NamedTextColor.GREEN).append(Component.text(isBlocked ? "REMOVED" : "ADDED",
                            NamedTextColor.GREEN, TextDecoration.BOLD)).append(Component.text(" item!", NamedTextColor.GREEN));

                    try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, player.getUniqueId().toString());
                        ps.setString(2, blockItem.name());
                        ps.executeUpdate();
                        player.sendMessage(msg);
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }));

        // Permshop

        manager.command(manager.commandBuilder("permshop")
                .permission("skyprisoncore.command.permshop")
                .required("player", playerParser())
                .required("shop", stringParser())
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    Player player = c.get("player");
                    String shop = c.get("shop");
                    ShopManager shopManager = ShopGuiPlusApi.getPlugin().getShopManager();
                    if(shopManager.getShopById(shop) == null) {
                        sender.sendMessage(Component.text("Not a valid shop!", NamedTextColor.RED));
                        return;
                    }
                    if(!player.hasPermission("shopguiplus.shops." + shop) && !player.isOp()) {
                        boolean isFree = player.hasPermission("group.free");
                        boolean isCenter = shop.equalsIgnoreCase("center");
                        Component msg = Component.text(isFree ? "You can't use prison shops!" : (isCenter ? "You must be Desert+ to use this shop!" : "You must be Free+ to use this shop!"), NamedTextColor.RED);
                        player.sendMessage(msg);
                        return;
                    }
                    shopManager.openShopMenu(player, shop, 1, true);
                }));

        // Econcheck

        manager.command(manager.commandBuilder("econcheck")
                .permission("skyprisoncore.command.econcheck")
                .optional("player", stringParser())
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    String targetName = c.getOrDefault("player", null);

                    if(targetName != null) {
                        UUID targetId = PlayerManager.getPlayerId(targetName);
                        if(targetId == null) {
                            player.sendMessage(Component.text("Player doesn't exist!", NamedTextColor.RED));
                            return;
                        } else {
                            targetName = targetId.toString();
                        }
                    }

                    String finalTargetName = targetName;
                    Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new EconomyCheck(plugin, db, finalTargetName).getInventory()));
                }));

        // Moneyhistory

        Command.Builder<CommandSourceStack> mHistory = manager.commandBuilder("moneyhistory", "mhistory")
                .permission("skyprisoncore.command.moneyhistory")
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new MoneyHistory(plugin, db, player.getUniqueId().toString()).getInventory()));
                });
        manager.command(mHistory);
        manager.command(mHistory
                .permission("skyprisoncore.command.moneyhistory.others")
                .required("player", stringParser())
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    UUID pUUID = PlayerManager.getPlayerId(c.get("player"));
                    if(pUUID != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new MoneyHistory(plugin, db, pUUID.toString()).getInventory()));
                    } else {
                        player.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
                    }
                }));

        // Shopban

        Command.Builder<CommandSourceStack> shopBan = manager.commandBuilder("shopban")
                .permission("skyprisoncore.command.shopban")
                .handler(c -> c.sender().getSender().sendMessage(Component.text("----==== ", NamedTextColor.GRAY)
                        .append(Component.text("ShopBan", NamedTextColor.GOLD))
                        .append(Component.text(" ===---", NamedTextColor.GRAY))
                        .append(Component.text("\n/shopban list", NamedTextColor.GRAY)
                                .append(Component.text(" - Lists all players banned from your shops", NamedTextColor.WHITE)))
                        .append(Component.text("\n/shopban add <player>", NamedTextColor.GRAY)
                                .append(Component.text(" - Bans a player from your shops", NamedTextColor.WHITE)))
                        .append(Component.text("\n/shopban remove <player>", NamedTextColor.GRAY)
                                .append(Component.text(" - Unbans a player from your shops", NamedTextColor.WHITE)))));
        manager.command(shopBan);

        manager.command(shopBan.literal("list")
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    List<String> bannedPlayers = getShopBanned(player);
                    if(bannedPlayers.isEmpty()) {
                        player.sendMessage(Component.text("You havn't banned anyone from your shops!", NamedTextColor.RED));
                        return;
                    }
                    Component bannedList = Component.text("---=== ", NamedTextColor.GRAY).append(Component.text("ShopBan", NamedTextColor.GOLD).append(Component.text(" ===---", NamedTextColor.GRAY)));
                    for(String bannedPlayer : bannedPlayers) {
                        bannedList = bannedList.append(Component.text("\n- ", NamedTextColor.GRAY)
                                .append(Component.text(Objects.requireNonNullElse(PlayerManager.getPlayerName(UUID.fromString(bannedPlayer)), "Couldn't get name!"), NamedTextColor.WHITE)));
                    }
                    player.sendMessage(bannedList);

                }));
        manager.command(shopBan.literal("add")
                .required("player", stringParser())
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    List<String> bannedPlayers = getShopBanned(player);
                    UUID targetId = PlayerManager.getPlayerId(c.get("player"));
                    if(targetId == null) {
                        player.sendMessage(Component.text("Player doesn't exist!", NamedTextColor.RED));
                        return;
                    }
                    if(targetId.equals(player.getUniqueId())) {
                        player.sendMessage(Component.text("You can't ban yourself!", NamedTextColor.RED));
                        return;
                    }
                    if(bannedPlayers.contains(targetId.toString())) {
                        player.sendMessage(Component.text("That player is already banned from your shops!", NamedTextColor.RED));
                        return;
                    }
                    if(PlayerManager.hasPermission(targetId, "skyprisoncore.command.shopban.bypass")) {
                        player.sendMessage(Component.text("You can't ban this player!", NamedTextColor.RED));
                        return;
                    }
                    try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("INSERT INTO shop_banned (user_id, banned_user) VALUES (?, ?)")) {
                        ps.setString(1, player.getUniqueId().toString());
                        ps.setString(2, targetId.toString());
                        ps.executeUpdate();
                        player.sendMessage(Component.text("Successfully banned " + PlayerManager.getPlayerName(targetId) + " from your shops!", NamedTextColor.GREEN));
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }

                }));
        manager.command(shopBan.literal("remove")
                .required("player", stringParser())
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    List<String> bannedPlayers = getShopBanned(player);
                    UUID targetId = PlayerManager.getPlayerId(c.get("player"));
                    if(targetId == null) {
                        player.sendMessage(Component.text("Player doesn't exist!", NamedTextColor.RED));
                        return;
                    }
                    if(!bannedPlayers.contains(targetId.toString())) {
                        player.sendMessage(Component.text("That player isn't banned from your shops!", NamedTextColor.RED));
                        return;
                    }
                    try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM shop_banned WHERE user_id = ? AND banned_user = ?")) {
                        ps.setString(1, player.getUniqueId().toString());
                        ps.setString(2, targetId.toString());
                        ps.executeUpdate();
                        player.sendMessage(Component.text("Successfully unbanned " + PlayerManager.getPlayerName(targetId) + " from your shops!", NamedTextColor.GREEN));
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }));
    }
    private void createTokenCommands() {
        Command.Builder<CommandSourceStack> tokensMain = manager.commandBuilder("tokens", "token")
                .permission("skyprisoncore.command.tokens")
                .handler(c -> TokenUtils.sendTokensHelp(c.sender().getSender()));
        manager.command(tokensMain);
        manager.command(tokensMain.literal("help")
                .handler(c -> TokenUtils.sendTokensHelp(c.sender().getSender())));
        manager.command(tokensMain.literal("top")
                .handler(c -> Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(c.sender().getSender(), "lb tokens"))));
        Command<CommandSourceStack> tokenShop = tokensMain.literal("shop")
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    boolean canEdit = player.hasPermission("skyprisoncore.inventories.tokenshop.editing");
                    Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new DatabaseInventory(plugin, db, player, canEdit, "tokenshop").getInventory()));
                }).build();
        manager.command(tokenShop);

        manager.command(manager.commandBuilder("tokenshop", "tshop")
                .proxies(tokenShop));

        manager.command(tokensMain.literal("balance", "bal")
                .optional("player", stringParser())
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    String target = c.getOrDefault("player", null);
                    Map<UUID, Integer> tokensData = TokenUtils.getTokensData();
                    Component prefix = TokenUtils.getPrefix();

                    if (target != null && PlayerManager.getPlayerId(target) == null) {
                        sender.sendMessage(prefix.append(Component.text("Player doesn't exist!", NamedTextColor.RED)));
                        return;
                    }
                    if(target == null && !(sender instanceof Player)) {
                        sender.sendMessage(prefix.append(Component.text("You must specify a player!", NamedTextColor.RED)));
                        return;
                    }

                    UUID targetId = target != null ? PlayerManager.getPlayerId(target) : ((Player) sender).getUniqueId();
                    int tokens = tokensData.getOrDefault(targetId, TokenUtils.getTokens(targetId));
                    Component targetComp = Component.text((target != null ? target + "'s" : "Your") + " balance is ", NamedTextColor.GRAY);
                    Component amountComp = Component.text(ChatUtils.formatNumber(tokens) + " tokens", NamedTextColor.AQUA);
                    sender.sendMessage(prefix.append(targetComp).append(amountComp));
                }));

        manager.command(tokensMain.literal("set")
                .permission("skyprisoncore.command.tokens.admin")
                .required("player", stringParser())
                .required("amount", integerParser(0))
                .optional("source", quotedStringParser())
                .optional("source_data", quotedStringParser())
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    String target = c.get("player");
                    int amount = c.get("amount");
                    String source = c.getOrDefault("source", "admin");
                    String sourceData = c.getOrDefault("source_data", null);
                    UUID targetId = PlayerManager.getPlayerId(target);
                    Component prefix = TokenUtils.getPrefix();
                    if (targetId == null) {
                        sender.sendMessage(prefix.append(Component.text("Player doesn't exist!", NamedTextColor.RED)));
                        return;
                    }
                    TokenUtils.setTokens(targetId, amount, source, sourceData);
                    sender.sendMessage(TokenUtils.getPrefix().append(Component.text("Set " + PlayerManager.getPlayerName(targetId) + "'s tokens to ", NamedTextColor.GRAY)
                            .append(Component.text(ChatUtils.formatNumber(amount), NamedTextColor.AQUA))));
                }));
        manager.command(tokensMain.literal("add")
                .permission("skyprisoncore.command.tokens.admin")
                .required("player", stringParser())
                .required("amount", integerParser(1))
                .optional("source", quotedStringParser())
                .optional("source_data", quotedStringParser())
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    String target = c.get("player");
                    int amount = c.get("amount");
                    String source = c.getOrDefault("source", "admin");
                    String sourceData = c.getOrDefault("source_data", null);
                    UUID targetId = PlayerManager.getPlayerId(target);
                    Component prefix = TokenUtils.getPrefix();
                    if (targetId == null) {
                        sender.sendMessage(prefix.append(Component.text("Player doesn't exist!", NamedTextColor.RED)));
                        return;
                    }
                    TokenUtils.addTokens(targetId, amount, source, sourceData);
                    sender.sendMessage(prefix.append(Component.text("Added ", NamedTextColor.GRAY).append(Component.text(ChatUtils.formatNumber(amount) + " tokens ",
                            NamedTextColor.AQUA).append(Component.text("to " + PlayerManager.getPlayerName(targetId), NamedTextColor.GRAY)))));
                }));
        manager.command(tokensMain.literal("remove")
                .permission("skyprisoncore.command.tokens.admin")
                .required("player", stringParser())
                .required("amount", integerParser(1))
                .optional("source", quotedStringParser())
                .optional("source_data", quotedStringParser())
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    String target = c.get("player");
                    int amount = c.get("amount");
                    String source = c.getOrDefault("source", "admin");
                    String sourceData = c.getOrDefault("source_data", null);;
                    UUID targetId = PlayerManager.getPlayerId(target);
                    Component prefix = TokenUtils.getPrefix();
                    if (targetId == null) {
                        sender.sendMessage(prefix.append(Component.text("Player doesn't exist!", NamedTextColor.RED)));
                        return;
                    }
                    TokenUtils.removeTokens(targetId, amount, source, sourceData);
                    sender.sendMessage(prefix.append(Component.text("Removed ", NamedTextColor.GRAY).append(Component.text(ChatUtils.formatNumber(amount) + " tokens ",
                            NamedTextColor.AQUA).append(Component.text("from " + PlayerManager.getPlayerName(targetId), NamedTextColor.GRAY)))));
                }));
        manager.command(tokensMain.literal("giveall")
                .permission("skyprisoncore.command.tokens.admin")
                .required("amount", integerParser(1))
                .optional("source", quotedStringParser())
                .optional("source_data", quotedStringParser())
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    int amount = c.get("amount");
                    String source = c.getOrDefault("source", "admin").replace("_", " ");
                    String sourceData = c.getOrDefault("source_data", null);
                    if(sourceData != null) sourceData = sourceData.replace("_", " ");
                    Component prefix = TokenUtils.getPrefix();
                    String finalSourceData = sourceData;
                    Bukkit.getOnlinePlayers().forEach(player -> TokenUtils.addTokens(player.getUniqueId(), amount, source, finalSourceData));
                    sender.sendMessage(prefix.append(Component.text("Added ", NamedTextColor.GRAY).append(Component.text(ChatUtils.formatNumber(amount) + " tokens ",
                            NamedTextColor.AQUA).append(Component.text("to everyone online!", NamedTextColor.GRAY)))));
                }));
        manager.command(tokensMain.literal("check")
                .permission("skyprisoncore.command.tokens.admin")
                .optional("player", stringParser())
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    String targetName = c.getOrDefault("player", null);

                    if(targetName != null) {
                        UUID targetId = PlayerManager.getPlayerId(targetName);
                        if(targetId == null) {
                            player.sendMessage(Component.text("Player doesn't exist!", NamedTextColor.RED));
                            return;
                        } else {
                            targetName = targetId.toString();
                        }
                    }

                    String finalTargetName = targetName;
                    Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new TokensCheck(plugin, db, finalTargetName).getInventory()));
                }));
        Command.Builder<CommandSourceStack> tokenHistory = tokensMain.literal("history")
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new TokensHistory(plugin, db, player.getUniqueId().toString()).getInventory()));
                });
        manager.command(tokenHistory);
        manager.command(tokenHistory
                .permission("skyprisoncore.command.tokens.admin")
                .required("player", stringParser())
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    UUID pUUID = PlayerManager.getPlayerId(c.get("player"));
                    if(pUUID != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new TokensHistory(plugin, db, pUUID.toString()).getInventory()));
                    } else {
                        player.sendMessage(Component.text("Player not found!", NamedTextColor.RED));
                    }
                }));
    }
    private void createBountyCommands() {
        Command.Builder<CommandSourceStack> bounty = manager.commandBuilder("bounty")
                .permission("skyprisoncore.command.bounty")
                .handler(c -> c.sender().getSender().sendMessage(getBountyHelp()));

        manager.command(bounty);

        manager.command(bounty.literal("set")
                .required("player", stringParser())
                .required("amount", doubleParser(100))
                .handler(c -> {
                    String bountyTarget = c.get("player");
                    UUID bountyTargetId = PlayerManager.getPlayerId(bountyTarget);
                    if(bountyTargetId == null) {
                        c.sender().getSender().sendMessage(Component.text("Player doesn't exist!", NamedTextColor.RED));
                        return;
                    }
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }

                    if(player.getUniqueId().equals(bountyTargetId)) {
                        player.sendMessage(Component.text("You can't put a bounty on yourself!", NamedTextColor.RED));
                        return;
                    }

                    if(!player.hasPermission("skyprisoncore.command.bounty.bypass") &&
                            bountyCooldown.containsKey(player.getUniqueId()) && bountyCooldown.get(player.getUniqueId()) > System.currentTimeMillis()) {
                        long timeTill = bountyCooldown.get(player.getUniqueId()) - System.currentTimeMillis();
                        int minutes = (int) Math.floor((timeTill % (1000.0 * 60.0 * 60.0)) / (1000.0 * 60.0));
                        int seconds = (int) Math.floor((timeTill % (1000.0 * 60.0)) / 1000.0);
                        player.sendMessage(Component.text("You can't put another bounty yet! Available in: " + minutes + "m " + seconds + "s", NamedTextColor.RED));
                        return;
                    }

                    boolean hasBypass = PlayerManager.hasPermission(bountyTargetId, "skyprisoncore.command.bounty.bypass");

                    if(hasBypass) {
                        c.sender().getSender().sendMessage(Component.text("You can't put a bounty on this player!", NamedTextColor.RED));
                        return;
                    }

                    double prize = c.get("amount");

                    double bountyPrize = SkyPrisonCore.round(prize, 2);
                    if (PlayerManager.getBalance(player) < bountyPrize) {
                        player.sendMessage(Component.text("You do not have enough money..", NamedTextColor.RED));
                        return;
                    }


                    String bountiedBy = "";
                    boolean hasBounty = false;
                    try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT bountied_by FROM bounties WHERE user_id = ?")) {
                        ps.setString(1, bountyTargetId.toString());
                        ResultSet rs = ps.executeQuery();
                        while(rs.next()) {
                            hasBounty = true;
                            bountiedBy = rs.getString(1);
                            bountiedBy = bountiedBy.replace("[", "");
                            bountiedBy = bountiedBy.replace("]", "");
                            bountiedBy = bountiedBy.replace(" ", "");
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    Component bountyMsg = bountyPrefix;
                    Component targetMsg = bountyPrefix;

                    String targetName = PlayerManager.getPlayerName(bountyTargetId);

                    if (hasBounty) {
                        bountyMsg = bountyMsg.append(Component.text(player.getName() + " has increased the bounty on " + targetName + " by ", NamedTextColor.YELLOW)
                                .append(Component.text("$" + ChatUtils.formatNumber(bountyPrize) + "!", NamedTextColor.GREEN)));

                        targetMsg = targetMsg.append(Component.text(player.getName() + " has increased the bounty on you by ", NamedTextColor.YELLOW)
                                .append(Component.text("$" + ChatUtils.formatNumber(bountyPrize) + "!", NamedTextColor.GREEN)));

                        bountiedBy += "," + player.getUniqueId();

                        try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("UPDATE bounties SET prize = prize + ?, bountied_by = ? WHERE user_id = ?")) {
                            ps.setDouble(1, bountyPrize);
                            ps.setString(2, bountiedBy);
                            ps.setString(3, bountyTargetId.toString());
                            ps.executeUpdate();
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    } else {
                        bountyMsg = bountyMsg.append(Component.text(player.getName() + " has put a ", NamedTextColor.YELLOW)
                                .append(Component.text("$" + ChatUtils.formatNumber(bountyPrize), NamedTextColor.GREEN))
                                .append(Component.text(" bounty on " + targetName + "!", NamedTextColor.YELLOW)));

                        targetMsg = targetMsg.append(Component.text(player.getName() + " has put a ", NamedTextColor.YELLOW)
                                .append(Component.text("$" + ChatUtils.formatNumber(bountyPrize), NamedTextColor.GREEN))
                                .append(Component.text(" bounty on you!", NamedTextColor.YELLOW)));

                        try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("INSERT INTO bounties (user_id, prize, bountied_by) VALUES (?, ?, ?)")) {
                            ps.setString(1, bountyTargetId.toString());
                            ps.setDouble(2, bountyPrize);
                            ps.setString(3, player.getUniqueId().toString());
                            ps.executeUpdate();
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(),
                            "cmi money take " + player.getName() + " " + bountyPrize));
                    bountyCooldown.put(player.getUniqueId(), System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1));
                    Audience receivers = Bukkit.getServer().filterAudience(audience -> {
                        if(audience instanceof Player onlinePlayer) {
                            return !onlinePlayer.hasPermission("skyprisoncore.command.bounty.silent") && !onlinePlayer.getUniqueId().equals(bountyTargetId);
                        }
                        return true;
                    });
                    receivers.sendMessage(bountyMsg);
                    PlayerManager.sendMessage(bountyTargetId, targetMsg, "bountied");
                }));

        manager.command(bounty.literal("help")
                .handler(c -> c.sender().getSender().sendMessage(getBountyHelp())));

        manager.command(bounty.literal("list")
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(new BountiesList(plugin, db).getInventory()));
                }));

        manager.command(bounty.literal("mute")
                .handler(c -> {
                    CommandSender sender = c.sender().getSender();
                    if(!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("You must be a player to use this command!", NamedTextColor.RED));
                        return;
                    }
                    if(!player.hasPermission("skyprisoncore.command.bounty.silent")) {
                        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(),
                                "lp user " + player.getName() + " permission set skyprisoncore.command.bounty.silent true"));
                        player.sendMessage(bountyPrefix.append(Component.text("Bounty messages muted!", NamedTextColor.YELLOW)));
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(),
                                "lp user " + player.getName() + " permission set skyprisoncore.command.bounty.silent false"));
                        player.sendMessage(bountyPrefix.append(Component.text("Bounty messages unmuted!", NamedTextColor.YELLOW)));
                    }
                }));
    }
    private List<String> getDontSells(Player player) {
        List<String> blockedSales = new ArrayList<>();
        try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT block_item FROM block_sells WHERE user_id = ?")) {
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            while(rs.next()) {
                blockedSales.add(rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return blockedSales;
    }
    private List<String> getShopBanned(Player player) {
        List<String> bannedUsers = new ArrayList<>();
        try(Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement("SELECT banned_user FROM shop_banned WHERE user_id = ?")) {
            ps.setString(1, player.getUniqueId().toString());
            ResultSet rs = ps.executeQuery();
            while(rs.next()) {
                bannedUsers.add(rs.getString(1));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return bannedUsers;
    }
    private Component getBountyHelp() {
        return Component.textOfChildren(Component.text("----==== ", NamedTextColor.WHITE)
                        .append(Component.text("Bounties", NamedTextColor.RED))
                        .append(Component.text("====----", NamedTextColor.WHITE)))
                .append(Component.text("\n/bounty set <player> <amount>", NamedTextColor.YELLOW)
                        .append(Component.text(" - Set a bounty on a player", NamedTextColor.WHITE)))
                .append(Component.text("\n/bounty help", NamedTextColor.YELLOW)
                        .append(Component.text(" - Shows this", NamedTextColor.WHITE)))
                .append(Component.text("\n/bounty list", NamedTextColor.YELLOW)
                        .append(Component.text(" - Shows all players with bountiesr", NamedTextColor.WHITE)))
                .append(Component.text("\n/bounty mute", NamedTextColor.YELLOW)
                        .append(Component.text(" - Mutes/Unmutes bounty messages except for bounties towards yourself", NamedTextColor.WHITE)));
    }
}
