package net.skyprison.skyprisoncore.listeners.pvpmanager;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import me.NoChance.PvPManager.Events.PlayerUntagEvent;
import net.skyprison.skyprisoncore.utils.claims.ClaimUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class PlayerUntag implements Listener {
    public PlayerUntag() {}

    @EventHandler
    public void onPlayerUntag(PlayerUntagEvent event) {
        Player player = event.getPlayer();
        com.sk89q.worldedit.util.Location toLoc = BukkitAdapter.adapt(player.getLocation());
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();
        if (query.testState(toLoc, localPlayer, ClaimUtils.FLY)) {
            player.setAllowFlight(true);
        }
    }
}
