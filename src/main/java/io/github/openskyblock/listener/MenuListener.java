package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.menu.MenuAction;
import io.github.openskyblock.menu.MinionMenuAction;
import io.github.openskyblock.menu.MinionMenuHolder;
import io.github.openskyblock.menu.SkyBlockMenuHolder;
import io.github.openskyblock.service.CustomItemDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class MenuListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public MenuListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof SkyBlockMenuHolder holder)) {
            if (event.getView().getTopInventory().getHolder() instanceof MinionMenuHolder minionHolder) {
                handleMinionClick(event, player, minionHolder);
            }
            return;
        }
        event.setCancelled(true);
        MenuAction action = holder.action(event.getRawSlot());
        if (action == MenuAction.NONE) {
            return;
        }
        player.closeInventory();
        plugin.menus().runAction(player, action);
    }

    private void handleMinionClick(InventoryClickEvent event, Player player, MinionMenuHolder holder) {
        event.setCancelled(true);
        MinionMenuAction action = holder.action(event.getRawSlot());
        if (action == MinionMenuAction.NONE) {
            return;
        }
        plugin.menus().runMinionAction(player, holder, action);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onMenuItemUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        boolean isMenuItem = plugin.customItems()
                .definition(event.getItem())
                .map(CustomItemDefinition::id)
                .filter("SKYBLOCK_MENU"::equals)
                .isPresent();
        if (!isMenuItem) {
            return;
        }
        event.setCancelled(true);
        plugin.menus().openSkyBlockMenu(event.getPlayer());
    }
}
