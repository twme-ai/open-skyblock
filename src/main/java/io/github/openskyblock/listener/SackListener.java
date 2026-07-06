package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

public final class SackListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public SackListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }
        ItemStack itemStack = event.getItem().getItemStack();
        int stored = plugin.sacks().storePickup(player, itemStack);
        if (stored <= 0) {
            return;
        }
        int remaining = itemStack.getAmount() - stored;
        if (remaining <= 0) {
            event.setCancelled(true);
            event.getItem().remove();
            return;
        }
        itemStack.setAmount(remaining);
        event.getItem().setItemStack(itemStack);
    }
}
