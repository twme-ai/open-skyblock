package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.service.CustomItemDefinition;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerLifecycleListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public PlayerLifecycleListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.profiles().profile(event.getPlayer());
        giveStarterMenuItem(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.profiles().save(event.getPlayer());
    }

    private void giveStarterMenuItem(org.bukkit.entity.Player player) {
        if (!plugin.configService().main().getBoolean("features.starter-menu-item", true)) {
            return;
        }
        boolean hasMenuItem = java.util.Arrays.stream(player.getInventory().getContents())
                .filter(itemStack -> itemStack != null && !itemStack.getType().isAir())
                .map(plugin.customItems()::definition)
                .anyMatch(optional -> optional.map(CustomItemDefinition::id).filter("SKYBLOCK_MENU"::equals).isPresent());
        if (hasMenuItem) {
            return;
        }
        CustomItemDefinition definition = plugin.customItems().definition("SKYBLOCK_MENU").orElse(null);
        if (definition == null) {
            return;
        }
        ItemStack itemStack = plugin.customItems().createItem(definition);
        if (player.getInventory().getItem(8) == null) {
            player.getInventory().setItem(8, itemStack);
            return;
        }
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
