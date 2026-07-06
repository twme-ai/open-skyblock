package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.pet.AutoPetTrigger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;

public final class AutoPetListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public AutoPetListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        plugin.pets().triggerAutoPet(event.getPlayer(), AutoPetTrigger.ITEM_HELD);
    }
}
