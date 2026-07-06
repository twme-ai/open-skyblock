package io.github.openskyblock.listener;

import io.github.openskyblock.OpenSkyBlockPlugin;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.recipe.SkyBlockRecipe;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;

public final class RecipeListener implements Listener {
    private final OpenSkyBlockPlugin plugin;

    public RecipeListener(OpenSkyBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        SkyBlockRecipe recipe = plugin.recipes().recipe(event.getRecipe()).orElse(null);
        if (recipe == null || plugin.recipes().canCraft(player, recipe)) {
            return;
        }
        event.getInventory().setResult(null);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        SkyBlockRecipe recipe = plugin.recipes().recipe(event.getRecipe()).orElse(null);
        if (recipe == null || plugin.recipes().canCraft(player, recipe)) {
            return;
        }
        event.setCancelled(true);
        plugin.text().send(player, "commands.recipe-blocked", List.of(TextService.parsed("recipe", recipe.displayName())));
    }
}
