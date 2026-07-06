package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

public final class SeaCreatureListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public SeaCreatureListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (!plugin.seaCreatures().trySpawn(event.getPlayer(), event.getHook().getLocation())) {
            return;
        }
        if (plugin.seaCreatures().removeCaughtItem()) {
            Entity caught = event.getCaught();
            if (caught != null) {
                caught.remove();
            }
        }
    }
}
