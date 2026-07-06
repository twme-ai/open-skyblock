package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.cake.CakeDefinition;
import io.github.openskyblock.cake.CakePlacement;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class CakeListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public CakeListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCakePlace(BlockPlaceEvent event) {
        CakeDefinition definition = plugin.cakes().definition(event.getItemInHand()).orElse(null);
        if (definition == null) {
            return;
        }
        Location location = event.getBlockPlaced().getLocation();
        if (!plugin.islands().isIslandWorld(location.getWorld()) || !plugin.islands().canModify(event.getPlayer(), location.getWorld())) {
            event.setCancelled(true);
            plugin.text().send(event.getPlayer(), "commands.cake-place-island-only");
            return;
        }
        if (!plugin.cakes().place(event.getPlayer(), definition, location)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCakeBreak(BlockBreakEvent event) {
        Optional<CakePlacement> placement = plugin.cakes().find(event.getBlock().getLocation());
        if (placement.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (!plugin.islands().canModify(event.getPlayer(), event.getBlock().getWorld())) {
            plugin.text().send(event.getPlayer(), "commands.island-protected");
            return;
        }
        plugin.cakes().pickup(event.getPlayer(), placement.get());
    }

    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onCakeInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Optional<CakePlacement> placement = plugin.cakes().find(event.getClickedBlock().getLocation());
        if (placement.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (!plugin.cakes().canActivate(event.getPlayer(), placement.get())) {
            plugin.text().send(event.getPlayer(), "commands.island-protected");
            return;
        }
        plugin.cakes().activate(event.getPlayer(), placement.get());
    }
}
