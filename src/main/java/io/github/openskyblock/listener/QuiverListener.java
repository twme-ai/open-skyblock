package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public final class QuiverListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public QuiverListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) {
            return;
        }
        ItemStack itemStack = event.getItem().getItemStack();
        int stored = plugin.quiver().storePickup(player, itemStack);
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBowUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack itemStack = event.getItem();
        if (itemStack == null || itemStack.getType() != Material.BOW && itemStack.getType() != Material.CROSSBOW) {
            return;
        }
        if (plugin.quiver().prepareShot(event.getPlayer())) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.quiver().removeProxyArrows(event.getPlayer()), 200L);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack consumable = event.getConsumable();
        if (!plugin.quiver().isProxyArrow(consumable)) {
            return;
        }
        if (!plugin.quiver().consumeShot(player, consumable)) {
            event.setCancelled(true);
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.quiver().removeProxyArrows(player));
    }
}
