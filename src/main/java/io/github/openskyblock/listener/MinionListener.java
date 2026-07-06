package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.service.MinionDefinition;
import io.github.openskyblock.service.MinionPlacement;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class MinionListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public MinionListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onMinionPlace(BlockPlaceEvent event) {
        MinionDefinition definition = plugin.minions().definition(event.getItemInHand()).orElse(null);
        if (definition == null) {
            return;
        }
        Player player = event.getPlayer();
        Location location = event.getBlockPlaced().getLocation();
        if (!plugin.islands().isIslandWorld(location.getWorld()) || !plugin.islands().canModify(player, location.getWorld())) {
            event.setCancelled(true);
            plugin.text().send(player, "commands.minion-place-island-only");
            return;
        }
        if (!plugin.minions().placeMinion(player, definition, location)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onMinionBreak(BlockBreakEvent event) {
        Optional<MinionPlacement> placement = plugin.minions().find(event.getBlock().getLocation());
        if (placement.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (!plugin.islands().canModify(event.getPlayer(), event.getBlock().getWorld())) {
            plugin.text().send(event.getPlayer(), "commands.island-protected");
            return;
        }
        plugin.minions().pickup(event.getPlayer(), placement.get());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onMinionInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        Optional<MinionPlacement> placement = plugin.minions().find(event.getClickedBlock().getLocation());
        if (placement.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (!plugin.islands().canModify(event.getPlayer(), event.getClickedBlock().getWorld())) {
            plugin.text().send(event.getPlayer(), "commands.island-protected");
            return;
        }
        plugin.menus().openMinionMenu(event.getPlayer(), placement.get());
    }
}
