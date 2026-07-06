package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class IslandProtectionListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public IslandProtectionListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        protect(event.getPlayer(), event.getBlock().getWorld(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        protect(event.getPlayer(), event.getBlock().getWorld(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        protect(event.getPlayer(), event.getBlock().getWorld(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onBucketFill(PlayerBucketFillEvent event) {
        protect(event.getPlayer(), event.getBlock().getWorld(), event);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        protect(event.getPlayer(), event.getClickedBlock().getWorld(), event);
    }

    private void protect(Player player, org.bukkit.World world, org.bukkit.event.Cancellable event) {
        if (plugin.islands().canModify(player, world)) {
            return;
        }
        event.setCancelled(true);
        plugin.text().send(player, "commands.island-protected");
    }
}
