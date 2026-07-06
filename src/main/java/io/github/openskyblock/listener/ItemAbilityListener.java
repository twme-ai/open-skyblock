package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class ItemAbilityListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public ItemAbilityListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (plugin.itemAbilities().activate(event.getPlayer(), event.getItem())) {
            event.setCancelled(true);
        }
    }
}
